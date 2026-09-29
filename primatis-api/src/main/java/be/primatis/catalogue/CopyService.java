package be.primatis.catalogue;

import be.primatis.catalogue.dto.CopyResponse;
import be.primatis.catalogue.dto.CreateCopyRequest;
import be.primatis.catalogue.dto.UpdateCopyAvailabilityRequest;
import be.primatis.catalogue.dto.UpdateCopyRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Service de consultation et de gestion des exemplaires du catalogue.
 */
@Service
public class CopyService {

    private static final String TITLE_NOT_FOUND_CODE = "TITLE_NOT_FOUND";
    private static final String COPY_NOT_FOUND_CODE = "COPY_NOT_FOUND";

    private final CopyRepository copyRepository;
    private final TitleRepository titleRepository;
    private final Clock clock;

    public CopyService(
            CopyRepository copyRepository,
            TitleRepository titleRepository,
            Clock clock) {
        this.copyRepository = copyRepository;
        this.titleRepository = titleRepository;
        this.clock = clock;
    }

    // Lecture

    /**
     * Retourne les exemplaires associés à un titre.
     *
     * @param titleId identifiant du titre
     * @return liste des exemplaires triés par code d'inventaire
     */
    @PreAuthorize("hasAuthority('COPY_READ')")
    @Transactional(readOnly = true)
    public List<CopyResponse> listCopiesByTitle(Long titleId) {
        if (!titleRepository.existsById(titleId)) {
            throw new ResourceNotFoundException(
                    TITLE_NOT_FOUND_CODE,
                    "Aucun titre pour l'identifiant " + titleId + ".");
        }

        return copyRepository
                .findByTitleIdOrderByInventoryCodeAsc(titleId)
                .stream()
                .map(CopyResponse::from)
                .toList();
    }

    /**
     * Retourne un exemplaire appartenant à un titre donné.
     *
     * @param titleId identifiant du titre
     * @param copyId  identifiant de l'exemplaire
     * @return exemplaire correspondant
     */
    @PreAuthorize("hasAuthority('COPY_READ')")
    @Transactional(readOnly = true)
    public CopyResponse getCopyById(Long titleId, Long copyId) {
        return CopyResponse.from(requireScopedCopy(titleId, copyId));
    }

    // Écriture

    /**
     * Crée un nouvel exemplaire pour un titre.
     *
     * @param titleId identifiant du titre
     * @param request données de l'exemplaire
     * @return exemplaire créé
     */
    @PreAuthorize("hasAuthority('COPY_MANAGE')")
    @Transactional
    public CopyResponse createCopy(
            Long titleId,
            CreateCopyRequest request) {

        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TITLE_NOT_FOUND_CODE,
                        "Aucun titre pour l'identifiant " + titleId + "."));

        requireManuallyWritableAvailability(request.availabilityStatus());
        requireConditionAvailabilityCoherence(
                request.copyCondition(),
                request.availabilityStatus());

        if (copyRepository.existsByInventoryCode(request.inventoryCode())) {
            throw new ConflictException(
                    "INVENTORY_CODE_ALREADY_EXISTS",
                    "Un exemplaire existe déjà avec ce inventoryCode.");
        }

        Instant now = clock.instant();

        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(request.inventoryCode());
        copy.setLocation(request.location());
        copy.setCopyCondition(request.copyCondition());
        copy.setAvailabilityStatus(request.availabilityStatus());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);

        copyRepository.save(copy);

        return CopyResponse.from(copy);
    }

    /**
     * Modifie les informations d'un exemplaire.
     *
     * @param titleId identifiant du titre
     * @param copyId  identifiant de l'exemplaire
     * @param request données à modifier
     * @return exemplaire mis à jour
     */
    @PreAuthorize("hasAuthority('COPY_MANAGE')")
    @Transactional
    public CopyResponse updateCopy(
            Long titleId,
            Long copyId,
            UpdateCopyRequest request) {

        Copy copy = requireScopedCopy(titleId, copyId);
        boolean mutated = false;

        if (request.isInventoryCodePresent()) {
            if (request.getInventoryCode() == null
                    || request.getInventoryCode().isBlank()) {

                throw new BusinessRuleException(
                        "INVENTORY_CODE_MUST_NOT_BE_BLANK",
                        "inventoryCode ne peut pas être vide.");
            }

            if (!request.getInventoryCode().equals(copy.getInventoryCode())
                    && copyRepository.existsByInventoryCode(request.getInventoryCode())) {

                throw new ConflictException(
                        "INVENTORY_CODE_ALREADY_EXISTS",
                        "Un exemplaire existe déjà avec ce inventoryCode.");
            }

            copy.setInventoryCode(request.getInventoryCode());
            mutated = true;
        }

        if (request.isLocationPresent()) {
            copy.setLocation(request.getLocation());
            mutated = true;
        }

        if (request.isCopyConditionPresent()) {
            if (request.getCopyCondition() == null) {
                throw new BusinessRuleException(
                        "COPY_CONDITION_MUST_NOT_BE_NULL",
                        "copyCondition ne peut pas être effacée.");
            }

            copy.setCopyCondition(request.getCopyCondition());

            if (request.getCopyCondition() == CopyCondition.LOST
                    || request.getCopyCondition() == CopyCondition.OUT_OF_SERVICE) {

                copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
            }

            mutated = true;
        }

        if (mutated) {
            copy.setUpdatedAt(clock.instant());
        }

        return CopyResponse.from(copy);
    }

    /**
     * Modifie manuellement la disponibilité d'un exemplaire.
     *
     * @param titleId identifiant du titre
     * @param copyId  identifiant de l'exemplaire
     * @param request nouveau statut de disponibilité
     * @return exemplaire mis à jour
     */
    @PreAuthorize("hasAuthority('COPY_MANAGE')")
    @Transactional
    public CopyResponse updateCopyAvailability(
            Long titleId,
            Long copyId,
            UpdateCopyAvailabilityRequest request) {

        Copy copy = requireScopedCopy(titleId, copyId);

        requireManuallyWritableAvailability(request.status());

        if (request.status() == AvailabilityStatus.AVAILABLE
                && (copy.getCopyCondition() == CopyCondition.LOST
                        || copy.getCopyCondition() == CopyCondition.OUT_OF_SERVICE)) {

            throw new BusinessRuleException(
                    "COPY_CONDITION_REQUIRES_UNAVAILABLE",
                    "Un exemplaire LOST/OUT_OF_SERVICE ne peut pas être remis AVAILABLE.");
        }

        if (copy.getAvailabilityStatus() != request.status()) {
            copy.setAvailabilityStatus(request.status());
            copy.setUpdatedAt(clock.instant());
        }

        return CopyResponse.from(copy);
    }

    // Utilitaires privés

    private Copy requireScopedCopy(Long titleId, Long copyId) {
        return copyRepository.findByIdAndTitleId(copyId, titleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        COPY_NOT_FOUND_CODE,
                        "Aucun exemplaire pour l'identifiant " + copyId
                                + " sous le titre " + titleId + "."));
    }

    private static void requireManuallyWritableAvailability(
            AvailabilityStatus availabilityStatus) {

        if (availabilityStatus == AvailabilityStatus.ON_LOAN
                || availabilityStatus == AvailabilityStatus.RESERVED) {

            throw new BusinessRuleException(
                    "COPY_AVAILABILITY_WORKFLOW_MANAGED",
                    "ON_LOAN et RESERVED sont exclusivement gérés par les workflows Loan/Reservation.");
        }
    }

    private static void requireConditionAvailabilityCoherence(
            CopyCondition copyCondition,
            AvailabilityStatus availabilityStatus) {

        boolean requiresUnavailable = copyCondition == CopyCondition.LOST
                || copyCondition == CopyCondition.OUT_OF_SERVICE;

        if (requiresUnavailable
                && availabilityStatus != AvailabilityStatus.UNAVAILABLE) {

            throw new BusinessRuleException(
                    "COPY_CONDITION_REQUIRES_UNAVAILABLE",
                    "Un exemplaire LOST/OUT_OF_SERVICE doit être UNAVAILABLE.");
        }
    }
}