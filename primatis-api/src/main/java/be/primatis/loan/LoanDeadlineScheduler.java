package be.primatis.loan;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
/**
 * Déclenche périodiquement le traitement des échéances de prêt.
 */
@Component
public class LoanDeadlineScheduler {

    private static final String DUE_SOON_FIXED_DELAY_PROPERTY =
            "${primatis.loan.due-soon.fixed-delay-ms:60000}";
    private static final String DUE_SOON_INITIAL_DELAY_PROPERTY =
            "${primatis.loan.due-soon.initial-delay-ms:60000}";
    private static final String OVERDUE_FIXED_DELAY_PROPERTY =
            "${primatis.loan.overdue.fixed-delay-ms:60000}";
    private static final String OVERDUE_INITIAL_DELAY_PROPERTY =
            "${primatis.loan.overdue.initial-delay-ms:60000}";

    private final LoanDeadlineService loanDeadlineService;
    private final Clock clock;

    public LoanDeadlineScheduler(LoanDeadlineService loanDeadlineService, Clock clock) {
        this.loanDeadlineService = loanDeadlineService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = DUE_SOON_FIXED_DELAY_PROPERTY, initialDelayString = DUE_SOON_INITIAL_DELAY_PROPERTY)
    /**
     * Lance le traitement des prêts arrivant prochainement à échéance.
     */
    public void processDueSoonLoans() {
        loanDeadlineService.processDueSoonLoans(LocalDate.now(clock));
    }

    @Scheduled(fixedDelayString = OVERDUE_FIXED_DELAY_PROPERTY, initialDelayString = OVERDUE_INITIAL_DELAY_PROPERTY)
    /**
     * Lance le traitement des prêts dont l'échéance est dépassée.
     */
    public void processOverdueLoans() {
        loanDeadlineService.processOverdueLoans(LocalDate.now(clock));
    }
}
