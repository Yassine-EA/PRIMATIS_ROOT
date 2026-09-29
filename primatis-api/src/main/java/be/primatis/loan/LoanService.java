package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.InvalidDateRangeException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.FineRepository;
import be.primatis.fine.FineService;
import be.primatis.fine.FineStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.loan.dto.LoanResponse;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationAssignmentService;
import be.primatis.reservation.ReservationRepository;
import be.primatis.reservation.ReservationStatus;
import be.primatis.setting.ApplicationSettingService;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberExpirationPolicy;
import be.primatis.user.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
/**
 * Service de consultation, d'enregistrement et de retour des prêts.
 */
@Service
public class LoanService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String ACCOUNT_DISABLED_CODE = "ACCOUNT_DISABLED";
    private static final String NOT_A_MEMBER_CODE = "NOT_A_MEMBER";
    private static final String MEMBER_BLOCKED_CODE = "MEMBER_BLOCKED";
    private static final String MEMBER_EXPIRED_CODE = "MEMBER_EXPIRED";
    private static final String MEMBER_HAS_UNPAID_FINE_CODE = "MEMBER_HAS_UNPAID_FINE";
    private static final String MAX_ACTIVE_LOANS_REACHED_CODE = "MAX_ACTIVE_LOANS_REACHED";
    private static final String COPY_NOT_FOUND_CODE = "COPY_NOT_FOUND";
    private static final String COPY_ALREADY_ON_LOAN_CODE = "COPY_ALREADY_ON_LOAN";
    private static final String COPY_NOT_LENDABLE_CODE = "COPY_NOT_LENDABLE";
    private static final String COPY_RESERVED_FOR_ANOTHER_MEMBER_CODE = "COPY_RESERVED_FOR_ANOTHER_MEMBER";
    private static final String LOAN_NOT_FOUND_CODE = "LOAN_NOT_FOUND";
    private static final String LOAN_ALREADY_RETURNED_CODE = "LOAN_ALREADY_RETURNED";
    private static final String LOAN_COPY_STATE_INCONSISTENT_CODE = "LOAN_COPY_STATE_INCONSISTENT";

    private static final String LOAN_DURATION_DAYS_KEY = "LOAN_DURATION_DAYS";
    private static final int MAX_ACTIVE_LOANS = 5;
    private static final List<LoanStatus> OPEN_LOAN_STATUSES = List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);

    private final LoanRepository loanRepository;
    private final AppUserRepository appUserRepository;
    private final CopyRepository copyRepository;
    private final FineRepository fineRepository;
    private final FineService fineService;
    private final ReservationRepository reservationRepository;
    private final ApplicationSettingService applicationSettingService;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final ReservationAssignmentService reservationAssignmentService;
    private final NotificationService notificationService;
    private final Clock clock;

    public LoanService(
            LoanRepository loanRepository,
            AppUserRepository appUserRepository,
            CopyRepository copyRepository,
            FineRepository fineRepository,
            FineService fineService,
            ReservationRepository reservationRepository,
            ApplicationSettingService applicationSettingService,
            MemberExpirationPolicy memberExpirationPolicy,
            ReservationAssignmentService reservationAssignmentService,
            NotificationService notificationService,
            Clock clock) {
        this.loanRepository = loanRepository;
        this.appUserRepository = appUserRepository;
        this.copyRepository = copyRepository;
        this.fineRepository = fineRepository;
        this.fineService = fineService;
        this.reservationRepository = reservationRepository;
        this.applicationSettingService = applicationSettingService;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.reservationAssignmentService = reservationAssignmentService;
        this.notificationService = notificationService;
        this.clock = clock;
    }
    /**
     * Retourne l'ensemble des prêts sous forme paginée.
     *
     * @param pageable paramètres de pagination
     * @return page des prêts
     */
    @PreAuthorize("hasAuthority('LOAN_READ')")
    @Transactional(readOnly = true)
    public Page<LoanResponse> listLoans(Pageable pageable) {
        return loanRepository.findAll(pageable).map(LoanResponse::from);
    }
    /**
     * Recherche les prêts selon les critères fournis.
     *
     * @param pageable paramètres de pagination
     * @param q texte recherché
     * @param loanStatus statut recherché
     * @param loanDateFrom date minimale du prêt
     * @param loanDateTo date maximale du prêt
     * @param dueDateFrom date minimale d'échéance
     * @param dueDateTo date maximale d'échéance
     * @return page des prêts correspondants
     */
    @PreAuthorize("hasAuthority('LOAN_READ')")
    @Transactional(readOnly = true)
    public Page<LoanResponse> listLoans(
            Pageable pageable,
            String q,
            LoanStatus loanStatus,
            LocalDate loanDateFrom,
            LocalDate loanDateTo,
            LocalDate dueDateFrom,
            LocalDate dueDateTo) {
        requireOrderedRange("loanDateFrom", loanDateFrom, loanDateTo);
        requireOrderedRange("dueDateFrom", dueDateFrom, dueDateTo);
        return loanRepository
                .findAll(LoanSpecifications.matching(q, loanStatus, loanDateFrom, loanDateTo, dueDateFrom, dueDateTo),
                        pageable)
                .map(LoanResponse::from);
    }

    private static void requireOrderedRange(String fromParameter, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(fromParameter,
                    "La date de début doit être antérieure ou égale à la date de fin.");
        }
    }
    /**
     * Retourne les prêts d'un utilisateur sous forme paginée.
     *
     * @param selfUserId identifiant de l'utilisateur
     * @param pageable paramètres de pagination
     * @return page des prêts de l'utilisateur
     */
    @Transactional(readOnly = true)
    public Page<LoanResponse> listOwnLoans(Long selfUserId, Pageable pageable) {
        return loanRepository.findByUserId(selfUserId, pageable).map(LoanResponse::from);
    }
    /**
     * Enregistre un nouveau prêt après validation de l'emprunteur et de l'exemplaire.
     *
     * @param request données du prêt
     * @return prêt créé
     */
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Transactional
    public LoanResponse registerLoan(CreateLoanRequest request) {
        AppUser borrower = appUserRepository.findById(request.borrowerUserId())
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + request.borrowerUserId() + "."));
        requireEligibleBorrower(borrower);

        Copy copy = copyRepository.findByIdForUpdate(request.copyId())
                .orElseThrow(() -> new ResourceNotFoundException(COPY_NOT_FOUND_CODE,
                        "Aucun exemplaire pour l'identifiant " + request.copyId() + "."));

        if (!loanRepository.findByCopyIdAndLoanStatusIn(copy.getId(), OPEN_LOAN_STATUSES).isEmpty()) {
            throw new BusinessRuleException(COPY_ALREADY_ON_LOAN_CODE,
                    "Un prêt ouvert existe déjà pour cet exemplaire.");
        }
        Reservation fulfilledReservation = requireLendableCopy(copy, borrower);

        int loanDurationDays = applicationSettingService.getInteger(LOAN_DURATION_DAYS_KEY);
        Instant now = clock.instant();

        Loan loan = new Loan();
        loan.setUser(borrower);
        loan.setCopy(copy);
        loan.setLoanDate(now);
        loan.setDueDate(LocalDate.now(clock).plusDays(loanDurationDays));
        loan.setReturnDate(null);
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(now);
        loan.setUpdatedAt(now);
        loanRepository.save(loan);

        if (fulfilledReservation != null) {
            fulfilledReservation.setReservationStatus(ReservationStatus.FULFILLED);
            fulfilledReservation.setFulfilledByLoan(loan);
            fulfilledReservation.setUpdatedAt(now);
        }

        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        copy.setUpdatedAt(now);

        return LoanResponse.from(loan);
    }
    private void requireEligibleBorrower(AppUser borrower) {
        if (borrower.getAccountStatus() == AccountStatus.DISABLED) {
            throw new BusinessRuleException(ACCOUNT_DISABLED_CODE, "Le compte de cet utilisateur est désactivé.");
        }
        if (borrower.getMemberNumber() == null) {
            throw new BusinessRuleException(NOT_A_MEMBER_CODE, "Cet utilisateur n'est pas adhérent.");
        }

        memberExpirationPolicy.syncIfNeeded(borrower);
        if (borrower.getMemberStatus() == MemberStatus.BLOCKED) {
            throw new BusinessRuleException(MEMBER_BLOCKED_CODE, "Cet adhérent est bloqué.");
        }
        if (borrower.getMemberStatus() == MemberStatus.EXPIRED) {
            throw new BusinessRuleException(MEMBER_EXPIRED_CODE, "L'adhésion de cet adhérent est expirée.");
        }

        if (fineRepository.existsByLoanUserIdAndFineStatus(borrower.getId(), FineStatus.UNPAID)) {
            throw new BusinessRuleException(MEMBER_HAS_UNPAID_FINE_CODE, "Cet adhérent a une amende impayée.");
        }
        if (loanRepository.countByUserIdAndLoanStatusIn(borrower.getId(), OPEN_LOAN_STATUSES) >= MAX_ACTIVE_LOANS) {
            throw new BusinessRuleException(MAX_ACTIVE_LOANS_REACHED_CODE,
                    "Cet adhérent a déjà " + MAX_ACTIVE_LOANS + " prêts ouverts.");
        }
    }
    private Reservation requireLendableCopy(Copy copy, AppUser borrower) {
        return switch (copy.getAvailabilityStatus()) {
            case AVAILABLE -> null;
            case ON_LOAN, UNAVAILABLE -> throw new BusinessRuleException(COPY_NOT_LENDABLE_CODE,
                    "Cet exemplaire n'est pas actuellement prêtable.");
            case RESERVED -> requireMatchingReadyReservation(copy, borrower);
        };
    }
    private Reservation requireMatchingReadyReservation(Copy copy, AppUser borrower) {
        return reservationRepository
                .findByAssignedCopyIdAndReservationStatus(copy.getId(), ReservationStatus.READY)
                .filter(reservation -> reservation.getUser().getId().equals(borrower.getId()))
                .filter(reservation -> reservation.getTitle().getId().equals(copy.getTitle().getId()))
                .orElseThrow(() -> new BusinessRuleException(COPY_RESERVED_FOR_ANOTHER_MEMBER_CODE,
                        "Cet exemplaire est réservé pour un autre adhérent."));
    }
    /**
     * Enregistre le retour d'un prêt.
     *
     * @param loanId identifiant du prêt
     * @return prêt retourné
     */
    @PreAuthorize("hasAuthority('LOAN_MANAGE')")
    @Transactional
    public LoanResponse registerReturn(Long loanId) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(LOAN_NOT_FOUND_CODE,
                        "Aucun prêt pour l'identifiant " + loanId + "."));

        if (loan.getLoanStatus() == LoanStatus.RETURNED) {
            throw new BusinessRuleException(LOAN_ALREADY_RETURNED_CODE, "Ce prêt a déjà été retourné.");
        }

        Long copyId = loan.getCopy().getId();
        Copy copy = copyRepository.findByIdForUpdate(copyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Incohérence de données : aucun exemplaire pour l'identifiant " + copyId
                                + ", pourtant référencé par le Loan " + loan.getId()
                                + " (fk_loan_copy_id garantit normalement son existence)."));

        boolean copyUnusable = copy.getCopyCondition() == CopyCondition.LOST
                || copy.getCopyCondition() == CopyCondition.OUT_OF_SERVICE;
        if (!copyUnusable && copy.getAvailabilityStatus() != AvailabilityStatus.ON_LOAN) {
            throw new BusinessRuleException(LOAN_COPY_STATE_INCONSISTENT_CODE,
                    "Ce prêt est ouvert et l'exemplaire n'est ni LOST/OUT_OF_SERVICE ni ON_LOAN.");
        }

        Instant now = clock.instant();
        loan.setReturnDate(LocalDate.now(clock));
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setUpdatedAt(now);

        notificationService.createForLoan(loan, NotificationType.LOAN_RETURNED,
                "Prêt retourné", "Le retour de votre prêt a bien été enregistré.");

        fineService.createForLateReturnIfApplicable(loan, now);

        if (copyUnusable) {
            copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        } else {
            reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, now);
        }
        copy.setUpdatedAt(now);

        return LoanResponse.from(loan);
    }
}
