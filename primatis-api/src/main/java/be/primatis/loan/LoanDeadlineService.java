package be.primatis.loan;

import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.setting.ApplicationSettingService;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
/**
 * Service chargé du traitement des échéances et retards de prêt.
 */
@Service
public class LoanDeadlineService {

    private static final Logger log = LoggerFactory.getLogger(LoanDeadlineService.class);
    static final int OVERDUE_BATCH_SIZE = 100;
    static final int DUE_SOON_BATCH_SIZE = 100;

    private static final String LOAN_DUE_SOON_DAYS_KEY = "LOAN_DUE_SOON_DAYS";
    private static final Set<String> DUE_SOON_DUPLICATE_CONSTRAINTS = Set.of("ux_notification_loan_due_soon");

    private final LoanRepository loanRepository;
    private final ApplicationSettingService applicationSettingService;
    private final NotificationService notificationService;
    private final Clock clock;
    private final LoanDeadlineService self;

    public LoanDeadlineService(
            LoanRepository loanRepository,
            ApplicationSettingService applicationSettingService,
            NotificationService notificationService,
            Clock clock,
            @Lazy LoanDeadlineService self) {
        this.loanRepository = loanRepository;
        this.applicationSettingService = applicationSettingService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.self = self;
    }

    // LOAN_DUE_SOON
    /**
     * Traite les prêts arrivant prochainement à échéance.
     *
     * @param referenceDate date de référence
     */
    public void processDueSoonLoans(LocalDate referenceDate) {
        int dueSoonDays = applicationSettingService.getInteger(LOAN_DUE_SOON_DAYS_KEY);
        LocalDate upperBound = referenceDate.plusDays(dueSoonDays);

        long startedAt = System.nanoTime();
        List<Long> candidateIds = loanRepository.findDueSoonLoanIds(
                LoanStatus.ACTIVE, referenceDate, upperBound, PageRequest.of(0, DUE_SOON_BATCH_SIZE));

        int processed = 0;
        int failed = 0;
        for (Long candidateId : candidateIds) {
            try {
                if (self.createDueSoonIfStillApplicable(candidateId, referenceDate, upperBound)) {
                    processed++;
                }
            } catch (DataIntegrityViolationException ex) {
                if (isDueSoonDuplicateConstraint(ex)) {
                    log.debug("LOAN_DUE_SOON déjà matérialisée pour le Loan {} "
                            + "(course concurrente absorbée localement).", candidateId);
                } else {
                    failed++;
                    log.error("Échec du traitement due-soon pour le Loan {} : {}",
                            candidateId, ex.getMessage(), ex);
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Échec du traitement due-soon pour le Loan {} : {}", candidateId, ex.getMessage(), ex);
            }
        }
        logCycle("prêts à échéance proche", candidateIds.size(), processed, failed, startedAt);
    }
    @Transactional
    /**
     * Crée la notification d'échéance proche si le prêt est toujours concerné.
     *
     * @param loanId identifiant du prêt
     * @param referenceDate date de référence
     * @param upperBound borne supérieure de la fenêtre d'échéance
     * @return true si une notification a été créée
     */
    public boolean createDueSoonIfStillApplicable(Long loanId, LocalDate referenceDate, LocalDate upperBound) {
        Loan loan = loanRepository.findById(loanId).orElse(null);
        if (loan == null || loan.getLoanStatus() != LoanStatus.ACTIVE) {
            return false;
        }

        LocalDate dueDate = loan.getDueDate();
        if (!referenceDate.isBefore(dueDate) || dueDate.isAfter(upperBound)) {
            return false;
        }

        return notificationService.createLoanDueSoonIfAbsent(loan,
                "Échéance de prêt proche", "La date de retour prévue de votre prêt approche.").isPresent();
    }

    private boolean isDueSoonDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return DUE_SOON_DUPLICATE_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }

    // LOAN_OVERDUE
    /**
     * Traite les prêts dont l'échéance est dépassée.
     *
     * @param referenceDate date de référence
     */
    public void processOverdueLoans(LocalDate referenceDate) {
        long startedAt = System.nanoTime();
        List<Long> candidateIds = loanRepository.findOverdueLoanIds(
                LoanStatus.ACTIVE, referenceDate, PageRequest.of(0, OVERDUE_BATCH_SIZE));

        int processed = 0;
        int failed = 0;
        for (Long candidateId : candidateIds) {
            try {
                if (self.markOverdueIfStillDue(candidateId, referenceDate)) {
                    processed++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Échec du traitement overdue pour le Loan {} : {}", candidateId, ex.getMessage(), ex);
            }
        }
        logCycle("prêts en retard", candidateIds.size(), processed, failed, startedAt);
    }
    @Transactional
    /**
     * Marque un prêt comme en retard s'il est toujours actif et échu.
     *
     * @param loanId identifiant du prêt
     * @param referenceDate date de référence
     * @return true si le statut du prêt a été modifié
     */
    public boolean markOverdueIfStillDue(Long loanId, LocalDate referenceDate) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : Loan " + loanId
                                + " disparu entre son identification et son verrouillage."));

        if (loan.getLoanStatus() != LoanStatus.ACTIVE) {
            return false;
        }
        if (!loan.getDueDate().isBefore(referenceDate)) {
            return false;
        }

        loan.setLoanStatus(LoanStatus.OVERDUE);
        loan.setUpdatedAt(clock.instant());

        notificationService.createForLoan(loan, NotificationType.LOAN_OVERDUE,
                "Prêt en retard", "La date de retour prévue de votre prêt est dépassée.");
        return true;
    }
    private void logCycle(String label, int examined, int processed, int failed, long startedAtNanos) {
        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        if (failed > 0) {
            log.warn("Scheduler {} : {} candidat(s), {} traité(s), {} échec(s) en {} ms",
                    label, examined, processed, failed, durationMs);
        } else if (processed > 0) {
            log.info("Scheduler {} : {} candidat(s), {} traité(s) en {} ms", label, examined, processed, durationMs);
        } else {
            log.debug("Scheduler {} : {} candidat(s), 0 traité(s) en {} ms", label, examined, durationMs);
        }
    }
}
