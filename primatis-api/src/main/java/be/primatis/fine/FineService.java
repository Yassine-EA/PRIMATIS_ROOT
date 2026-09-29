package be.primatis.fine;

import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.InvalidDateRangeException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.dto.FineResponse;
import be.primatis.loan.Loan;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.setting.ApplicationSettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;


/**
 * Service de calcul, de consultation et de gestion des amendes.
 */
@Service
public class FineService {

    private static final String FINE_WEEKLY_RATE_KEY = "FINE_WEEKLY_RATE";
    private static final String FINE_MAX_AMOUNT_KEY = "FINE_MAX_AMOUNT";
    private static final long DAYS_PER_WEEK = 7;

    private static final String FINE_NOT_FOUND_CODE = "FINE_NOT_FOUND";
    private static final String FINE_NOT_PAYABLE_CODE = "FINE_NOT_PAYABLE";
    private static final String FINE_NOT_CANCELLABLE_CODE = "FINE_NOT_CANCELLABLE";

    private final FineRepository fineRepository;
    private final ApplicationSettingService applicationSettingService;
    private final NotificationService notificationService;
    private final Clock clock;

    public FineService(
            FineRepository fineRepository,
            ApplicationSettingService applicationSettingService,
            NotificationService notificationService,
            Clock clock) {
        this.fineRepository = fineRepository;
        this.applicationSettingService = applicationSettingService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    
    /**
     * Crée une amende impayée lorsqu'un prêt est retourné en retard.
     *
     * Le montant est calculé par semaine entamée selon les paramètres applicatifs,
     * dans la limite du plafond configuré.
     *
     * @param loan prêt retourné
     * @param now instant d'émission de l'amende
     */
    public void createForLateReturnIfApplicable(Loan loan, Instant now) {
        LocalDate dueDate = loan.getDueDate();
        LocalDate returnDate = loan.getReturnDate();

        long overdueDays = ChronoUnit.DAYS.between(dueDate, returnDate);
        if (overdueDays <= 0) {
            return;
        }

        if (fineRepository.findByLoanId(loan.getId()).isPresent()) {
            throw new IllegalStateException(
                    "Incohérence de données : une Fine existe déjà pour le Loan " + loan.getId()
                            + ", alors qu'aucune ne devrait exister avant son premier retour"
                            + " (uq_fine_loan_id garantit normalement son unicité).");
        }

        long startedWeeks = (overdueDays + (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK;

        BigDecimal weeklyRate = applicationSettingService.getDecimal(FINE_WEEKLY_RATE_KEY);
        BigDecimal maxAmount = applicationSettingService.getDecimal(FINE_MAX_AMOUNT_KEY);

        BigDecimal rawAmount = weeklyRate.multiply(BigDecimal.valueOf(startedWeeks));
        BigDecimal amount = rawAmount.min(maxAmount);

        String reason = "Retour tardif — " + overdueDays + " jour(s) de retard, "
                + startedWeeks + " semaine(s) entamée(s).";

        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(amount);
        fine.setReason(reason);
        fine.setIssuedAt(now);
        fine.setFineStatus(FineStatus.UNPAID);
        fineRepository.save(fine);

        notificationService.createForFine(fine, NotificationType.FINE_ISSUED,
                "Amende générée", "Une amende a été générée à la suite du retour tardif de votre prêt.");
    }

    
    /**
     * Enregistre la confirmation du paiement externe d'une amende impayée.
     *
     * @param fineId identifiant de l'amende
     * @return amende mise à jour
     */
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Transactional
    public FineResponse confirmExternalPayment(Long fineId) {
        Fine fine = fineRepository.findByIdForUpdate(fineId)
                .orElseThrow(() -> new ResourceNotFoundException(FINE_NOT_FOUND_CODE,
                        "Aucune amende pour l'identifiant " + fineId + "."));

        if (fine.getFineStatus() != FineStatus.UNPAID) {
            throw new BusinessRuleException(FINE_NOT_PAYABLE_CODE,
                    "Cette amende n'est plus payable (statut actuel : " + fine.getFineStatus() + ").");
        }

        fine.setFineStatus(FineStatus.PAID);
        fine.setPaidAt(clock.instant());

        notificationService.createForFine(fine, NotificationType.FINE_PAID,
                "Amende payée", "Le paiement de votre amende a bien été enregistré.");

        return FineResponse.from(fine);
    }

    
    /**
     * Annule une amende impayée.
     *
     * @param fineId identifiant de l'amende
     * @return amende annulée
     */
    @PreAuthorize("hasAuthority('FINE_MANAGE')")
    @Transactional
    public FineResponse cancelFine(Long fineId) {
        Fine fine = fineRepository.findByIdForUpdate(fineId)
                .orElseThrow(() -> new ResourceNotFoundException(FINE_NOT_FOUND_CODE,
                        "Aucune amende pour l'identifiant " + fineId + "."));

        if (fine.getFineStatus() != FineStatus.UNPAID) {
            throw new BusinessRuleException(FINE_NOT_CANCELLABLE_CODE,
                    "Cette amende n'est plus annulable (statut actuel : " + fine.getFineStatus() + ").");
        }

        fine.setFineStatus(FineStatus.CANCELLED);
        fine.setCancelledAt(clock.instant());

        notificationService.createForFine(fine, NotificationType.FINE_CANCELLED,
                "Amende annulée", "Votre amende a été annulée.");

        return FineResponse.from(fine);
    }

    
    /**
     * Retourne les amendes d'un utilisateur sous forme paginée.
     *
     * @param selfUserId identifiant de l'utilisateur
     * @param pageable paramètres de pagination
     * @return page des amendes de l'utilisateur
     */
    @Transactional(readOnly = true)
    public Page<FineResponse> listOwnFines(Long selfUserId, Pageable pageable) {
        return fineRepository.findByLoanUserId(selfUserId, pageable).map(FineResponse::from);
    }

    
    /**
     * Retourne l'ensemble des amendes sous forme paginée.
     *
     * @param pageable paramètres de pagination
     * @return page des amendes
     */
    @PreAuthorize("hasAuthority('FINE_READ')")
    @Transactional(readOnly = true)
    public Page<FineResponse> listFines(Pageable pageable) {
        return fineRepository.findAll(pageable).map(FineResponse::from);
    }

    
    /**
     * Recherche les amendes selon les critères fournis.
     *
     * @param pageable paramètres de pagination
     * @param q texte recherché
     * @param fineStatus statut recherché
     * @param issuedDateFrom date minimale d'émission
     * @param issuedDateTo date maximale d'émission
     * @param closedDateFrom date minimale de clôture
     * @param closedDateTo date maximale de clôture
     * @return page des amendes correspondantes
     */
    @PreAuthorize("hasAuthority('FINE_READ')")
    @Transactional(readOnly = true)
    public Page<FineResponse> listFines(
            Pageable pageable,
            String q,
            FineStatus fineStatus,
            LocalDate issuedDateFrom,
            LocalDate issuedDateTo,
            LocalDate closedDateFrom,
            LocalDate closedDateTo) {
        requireOrderedRange("issuedDateFrom", issuedDateFrom, issuedDateTo);
        requireOrderedRange("closedDateFrom", closedDateFrom, closedDateTo);
        return fineRepository
                .findAll(FineSpecifications.matching(
                        q, fineStatus, issuedDateFrom, issuedDateTo, closedDateFrom, closedDateTo), pageable)
                .map(FineResponse::from);
    }

    private static void requireOrderedRange(String fromParameter, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(fromParameter,
                    "La date de début doit être antérieure ou égale à la date de fin.");
        }
    }
}
