package be.primatis.user;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.ResidenceResponse;
import be.primatis.user.web.UpdateResidenceRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
/**
 * Service de consultation et de gestion des résidences.
 */
@Service
public class ResidenceService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String RESIDENCE_PERIOD_CONFLICT_CODE = "RESIDENCE_PERIOD_CONFLICT";
    private static final Set<String> RESIDENCE_CONFLICT_CONSTRAINTS =
            Set.of("ux_residence_current_per_user", "ck_residence_dates");

    private final AppUserRepository appUserRepository;
    private final CityRepository cityRepository;
    private final AddressRepository addressRepository;
    private final ResidenceRepository residenceRepository;
    private final Clock clock;

    public ResidenceService(
            AppUserRepository appUserRepository,
            CityRepository cityRepository,
            AddressRepository addressRepository,
            ResidenceRepository residenceRepository,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.cityRepository = cityRepository;
        this.addressRepository = addressRepository;
        this.residenceRepository = residenceRepository;
        this.clock = clock;
    }
    @Transactional(readOnly = true)
    /** Retourne la résidence courante de l'utilisateur authentifié. */
    public ResidenceResponse getOwnCurrentResidence(Long selfUserId) {
        return getCurrentResidenceOrThrow(selfUserId);
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public ResidenceResponse getCurrentResidence(Long userId) {
        return getCurrentResidenceOrThrow(userId);
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional(readOnly = true)
    public List<ResidenceResponse> getResidenceHistory(Long userId) {
        requireUser(userId);
        return residenceRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(ResidenceResponse::from)
                .toList();
    }
    @Transactional
    public ResidenceResponse replaceOwnResidence(Long selfUserId, UpdateResidenceRequest request) {
        return doReplaceResidence(selfUserId, request);
    }
    @PreAuthorize("hasAuthority('USER_PROFILE_MANAGE')")
    @Transactional
    public ResidenceResponse replaceResidence(Long userId, UpdateResidenceRequest request) {
        return doReplaceResidence(userId, request);
    }

    private ResidenceResponse getCurrentResidenceOrThrow(Long userId) {
        requireUser(userId);
        Residence current = residenceRepository.findByUserIdAndEndDateIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("CURRENT_RESIDENCE_NOT_FOUND",
                        "Aucune résidence courante pour l'utilisateur " + userId + "."));
        return ResidenceResponse.from(current);
    }
    private ResidenceResponse doReplaceResidence(Long targetUserId, UpdateResidenceRequest request) {
        AppUser targetUser = requireUser(targetUserId);
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("CITY_NOT_FOUND",
                        "Aucune ville pour l'identifiant " + request.cityId() + "."));

        LocalDate today = LocalDate.now(clock);
        Optional<Residence> currentOpt = residenceRepository.findByUserIdAndEndDateIsNull(targetUserId);

        LocalDate candidateEndDateForCurrent = null;
        if (currentOpt.isPresent()) {
            candidateEndDateForCurrent = today.minusDays(1);
            if (candidateEndDateForCurrent.isBefore(currentOpt.get().getStartDate())) {
                throw new BusinessRuleException(RESIDENCE_PERIOD_CONFLICT_CODE,
                        "La résidence courante a déjà été remplacée aujourd'hui.");
            }
        }

        Address address = new Address();
        address.setCity(city);
        address.setStreet(request.street());
        address.setStreetNumber(request.streetNumber());
        address.setBoxNumber(request.boxNumber());
        address.setAdditionalInfo(request.additionalInfo());

        Residence newResidence = new Residence();
        newResidence.setUser(targetUser);
        newResidence.setAddress(address);
        newResidence.setStartDate(today);
        newResidence.setEndDate(null);

        try {
            if (currentOpt.isPresent()) {
                currentOpt.get().setEndDate(candidateEndDateForCurrent);
            }
            if (residenceRepository.existsOverlappingPeriod(targetUserId, today, null)) {
                throw new BusinessRuleException(RESIDENCE_PERIOD_CONFLICT_CODE,
                        "La nouvelle période de résidence chevauche une résidence existante.");
            }
            addressRepository.save(address);
            residenceRepository.saveAndFlush(newResidence);
        } catch (DataIntegrityViolationException ex) {
            if (isResidenceConstraintViolation(ex)) {
                throw new BusinessRuleException(RESIDENCE_PERIOD_CONFLICT_CODE,
                        "Conflit de période de résidence détecté par la base de données.");
            }
            throw ex;
        }

        return ResidenceResponse.from(newResidence);
    }
    private boolean isResidenceConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return RESIDENCE_CONFLICT_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }

    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + userId + "."));
    }
}
