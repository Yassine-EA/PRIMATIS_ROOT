package be.primatis.fine;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.dto.FineResponse;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;
import be.primatis.setting.ApplicationSetting;
import be.primatis.setting.ApplicationSettingRepository;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FineServiceTests {

    @Autowired
    private FineService fineService;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private ApplicationSettingRepository applicationSettingRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithFineManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("FINE_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithFineRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("FINE_READ"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithoutFineRead() {
        Authentication authentication = new TestingAuthenticationToken("1", null, List.of());
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @PersistenceContext
    private EntityManager entityManager;

    
    // Retour non tardif
    

    @Test
    void createsNoFineWhenReturnedBeforeDueDate() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 18));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-18T09:00:00Z"));

        assertThat(fineRepository.findByLoanId(loan.getId())).isEmpty();
    }

    @Test
    void createsNoFineWhenReturnedExactlyOnDueDate() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        assertThat(fineRepository.findByLoanId(loan.getId())).isEmpty();
    }

    
    
    

    @Test
    void chargesOneWeekForOneDayLate() {
        assertAmountForOverdueDays(1, new BigDecimal("0.80"));
    }

    @Test
    void chargesOneWeekForSevenDaysLate() {
        assertAmountForOverdueDays(7, new BigDecimal("0.80"));
    }

    @Test
    void chargesTwoWeeksForEightDaysLate() {
        assertAmountForOverdueDays(8, new BigDecimal("1.60"));
    }

    @Test
    void chargesTwoWeeksForFourteenDaysLate() {
        assertAmountForOverdueDays(14, new BigDecimal("1.60"));
    }

    @Test
    void chargesThreeWeeksForFifteenDaysLate() {
        assertAmountForOverdueDays(15, new BigDecimal("2.40"));
    }

    
    // Plafond (FINE_MAX_AMOUNT = 25.00)
    

    @Test
    void staysJustUnderTheCapAt217DaysLate() {
        assertAmountForOverdueDays(217, new BigDecimal("24.80"));
    }

    @Test
    void capsAmountExactlyAt218DaysLate() {
        assertAmountForOverdueDays(218, new BigDecimal("25.00"));
    }

    @Test
    void capsAmountFarBeyondTheCapAt300DaysLate() {
        assertAmountForOverdueDays(300, new BigDecimal("25.00"));
    }

    
    // Fine créée — statut, timestamps, motif, Loan
    

    @Test
    void createsAnUnpaidFineWithNullPaidAtAndCancelledAt() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getFineStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(fine.getPaidAt()).isNull();
        assertThat(fine.getCancelledAt()).isNull();
    }

    @Test
    void generatesTheExpectedReasonText() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getReason()).isEqualTo("Retour tardif — 5 jour(s) de retard, 1 semaine(s) entamée(s).");
    }

    @Test
    void linksTheFineToTheCorrectLoan() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getLoan().getId()).isEqualTo(loan.getId());
    }

    @Test
    void usesTheProvidedInstantAsIssuedAt() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Instant now = Instant.parse("2026-08-20T14:32:07Z");

        fineService.createForLateReturnIfApplicable(loan, now);

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getIssuedAt()).isEqualTo(now);
    }

    
    // Settings réellement consommés (aucune valeur codée en dur)
    

    @Test
    void usesTheConfiguredWeeklyRateRatherThanAHardcodedValue() {
        setSettingValue("FINE_WEEKLY_RATE", "2.50");
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        // 5 jours de retard -> 1 semaine entamée x 2.50 (valeur reconfigurée) = 2.50
        assertThat(fine.getAmount()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void usesTheConfiguredMaxAmountRatherThanAHardcodedValue() {
        setSettingValue("FINE_MAX_AMOUNT", "1.00");
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20));

        fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z"));

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        // 19 jours de retard -> 3 semaines entamées x 0.80 = 2.40, plafonné à 1.00 (valeur reconfigurée)
        assertThat(fine.getAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    
    
    

    @Test
    void throwsWhenAFineAlreadyExistsForTheLoan() {
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        persistExistingFine(loan);

        assertThatThrownBy(() -> fineService.createForLateReturnIfApplicable(loan, Instant.parse("2026-08-20T09:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(loan.getId()));
    }

    
    
    

    @Test
    void confirmExternalPaymentTransitionsUnpaidToPaid() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        assertThat(response.fineStatus()).isEqualTo(FineStatus.PAID);

        
        Notification notification = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.fine.id = :id", Notification.class)
                .setParameter("id", fine.getId())
                .getSingleResult();
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.FINE_PAID);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getRecipientUser().getId()).isEqualTo(loan.getUser().getId());
    }

    @Test
    void confirmExternalPaymentSetsPaidAtCloseToNow() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        Instant before = Instant.now();

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        Instant after = Instant.now();
        assertThat(response.paidAt()).isBetween(before.minusSeconds(1), after.plusSeconds(5));
    }

    @Test
    void confirmExternalPaymentNeverChangesAmountReasonOrIssuedAt() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        BigDecimal originalAmount = fine.getAmount();
        String originalReason = fine.getReason();
        Instant originalIssuedAt = fine.getIssuedAt();

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        assertThat(response.amount()).isEqualByComparingTo(originalAmount);
        assertThat(response.reason()).isEqualTo(originalReason);
        assertThat(response.issuedAt()).isEqualTo(originalIssuedAt);
        assertThat(response.loan().id()).isEqualTo(loan.getId());
    }

    @Test
    void confirmExternalPaymentKeepsCancelledAtNull() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.confirmExternalPayment(fine.getId());

        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void confirmExternalPaymentOnUnknownFineThrowsResourceNotFound() {
        authenticateWithFineManage();

        assertThatThrownBy(() -> fineService.confirmExternalPayment(999999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getCode()).isEqualTo("FINE_NOT_FOUND"));
    }

    @Test
    void confirmExternalPaymentOnAlreadyPaidFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.PAID);

        assertThatThrownBy(() -> fineService.confirmExternalPayment(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_PAYABLE"));

        
        List<Notification> notifications = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.fine.id = :id", Notification.class)
                .setParameter("id", fine.getId())
                .getResultList();
        assertThat(notifications).isEmpty();
    }

    @Test
    void confirmExternalPaymentOnCancelledFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.CANCELLED);

        assertThatThrownBy(() -> fineService.confirmExternalPayment(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_PAYABLE"));
    }

    
    
    

    @Test
    void cancelFineTransitionsUnpaidToCancelled() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.cancelFine(fine.getId());

        assertThat(response.fineStatus()).isEqualTo(FineStatus.CANCELLED);

        
        Notification notification = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.fine.id = :id", Notification.class)
                .setParameter("id", fine.getId())
                .getSingleResult();
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.FINE_CANCELLED);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getRecipientUser().getId()).isEqualTo(loan.getUser().getId());
    }

    @Test
    void cancelFineSetsCancelledAtCloseToNow() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        Instant before = Instant.now();

        FineResponse response = fineService.cancelFine(fine.getId());

        Instant after = Instant.now();
        assertThat(response.cancelledAt()).isBetween(before.minusSeconds(1), after.plusSeconds(5));
    }

    @Test
    void cancelFineKeepsPaidAtNull() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);

        FineResponse response = fineService.cancelFine(fine.getId());

        assertThat(response.paidAt()).isNull();
    }

    @Test
    void cancelFineNeverChangesAmountReasonOrIssuedAt() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.UNPAID);
        BigDecimal originalAmount = fine.getAmount();
        String originalReason = fine.getReason();
        Instant originalIssuedAt = fine.getIssuedAt();

        FineResponse response = fineService.cancelFine(fine.getId());

        assertThat(response.amount()).isEqualByComparingTo(originalAmount);
        assertThat(response.reason()).isEqualTo(originalReason);
        assertThat(response.issuedAt()).isEqualTo(originalIssuedAt);
        assertThat(response.loan().id()).isEqualTo(loan.getId());
    }

    @Test
    void cancelFineOnUnknownFineThrowsResourceNotFound() {
        authenticateWithFineManage();

        assertThatThrownBy(() -> fineService.cancelFine(999999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getCode()).isEqualTo("FINE_NOT_FOUND"));
    }

    @Test
    void cancelFineOnAlreadyCancelledFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.CANCELLED);

        assertThatThrownBy(() -> fineService.cancelFine(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_CANCELLABLE"));

        
        List<Notification> notifications = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.fine.id = :id", Notification.class)
                .setParameter("id", fine.getId())
                .getResultList();
        assertThat(notifications).isEmpty();
    }

    @Test
    void cancelFineOnPaidFineIsRejected() {
        authenticateWithFineManage();
        Loan loan = persistReturnedLoan(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20));
        Fine fine = persistFine(loan, FineStatus.PAID);

        assertThatThrownBy(() -> fineService.cancelFine(fine.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode()).isEqualTo("FINE_NOT_CANCELLABLE"));
    }

    
    
    

    @Test
    void listOwnFinesReturnsOnlyFinesOfTheCurrentMemberAcrossAllStatuses() {
        AppUser member = persistUser("fine-own-list-" + System.nanoTime() + "@primatis.test");
        Loan loanUnpaid = persistLoanForUser(member);
        Loan loanPaid = persistLoanForUser(member);
        Loan loanCancelled = persistLoanForUser(member);
        Fine fineUnpaid = persistFine(loanUnpaid, FineStatus.UNPAID);
        Fine finePaid = persistFine(loanPaid, FineStatus.PAID);
        Fine fineCancelled = persistFine(loanCancelled, FineStatus.CANCELLED);

        Page<FineResponse> page = fineService.listOwnFines(member.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(FineResponse::fineStatus)
                .containsExactlyInAnyOrder(FineStatus.UNPAID, FineStatus.PAID, FineStatus.CANCELLED);
        assertThat(page.getContent()).extracting(FineResponse::id)
                .containsExactlyInAnyOrder(fineUnpaid.getId(), finePaid.getId(), fineCancelled.getId());
    }

    @Test
    void listOwnFinesNeverLeaksAnotherMembersFine() {
        AppUser member = persistUser("fine-own-isolation-1-" + System.nanoTime() + "@primatis.test");
        AppUser other = persistUser("fine-own-isolation-2-" + System.nanoTime() + "@primatis.test");
        persistFine(persistLoanForUser(member), FineStatus.UNPAID);
        persistFine(persistLoanForUser(other), FineStatus.UNPAID);

        Page<FineResponse> page = fineService.listOwnFines(member.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).borrower().id()).isEqualTo(member.getId());
    }

    @Test
    void listOwnFinesRespectsPageSize() {
        AppUser member = persistUser("fine-own-pagination-" + System.nanoTime() + "@primatis.test");
        for (int i = 0; i < 3; i++) {
            persistFine(persistLoanForUser(member), FineStatus.UNPAID);
        }

        Page<FineResponse> firstPage = fineService.listOwnFines(member.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void listOwnFinesReturnsEmptyPageForAMemberWithNoFine() {
        AppUser member = persistUser("fine-own-empty-" + System.nanoTime() + "@primatis.test");

        Page<FineResponse> page = fineService.listOwnFines(member.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    
    
    

    @Test
    void listFinesReturnsAllFinesAcrossMembersAndStatuses() {
        authenticateWithFineRead();
        AppUser memberOne = persistUser("fine-staff-list-1-" + System.nanoTime() + "@primatis.test");
        AppUser memberTwo = persistUser("fine-staff-list-2-" + System.nanoTime() + "@primatis.test");
        Fine fineOne = persistFine(persistLoanForUser(memberOne), FineStatus.UNPAID);
        Fine fineTwo = persistFine(persistLoanForUser(memberTwo), FineStatus.PAID);
        Fine fineThree = persistFine(persistLoanForUser(memberTwo), FineStatus.CANCELLED);

        Page<FineResponse> page = fineService.listFines(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(FineResponse::id)
                .contains(fineOne.getId(), fineTwo.getId(), fineThree.getId());
        assertThat(page.getContent()).extracting(fine -> fine.borrower().id())
                .contains(memberOne.getId(), memberTwo.getId());
        assertThat(page.getContent()).extracting(FineResponse::fineStatus)
                .contains(FineStatus.UNPAID, FineStatus.PAID, FineStatus.CANCELLED);
    }

    @Test
    void listFinesRespectsPageSize() {
        authenticateWithFineRead();
        for (int i = 0; i < 3; i++) {
            persistFine(persistLoanForUser(
                    persistUser("fine-staff-pagination-" + i + "-" + System.nanoTime() + "@primatis.test")),
                    FineStatus.UNPAID);
        }

        Page<FineResponse> firstPage = fineService.listFines(PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
    }

    @Test
    void listFinesWithoutFineReadIsDenied() {
        authenticateWithoutFineRead();

        assertThatThrownBy(() -> fineService.listFines(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    
    // Helpers
    

    private void assertAmountForOverdueDays(int overdueDays, BigDecimal expectedAmount) {
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        LocalDate returnDate = dueDate.plusDays(overdueDays);
        Loan loan = persistReturnedLoan(dueDate, returnDate);

        fineService.createForLateReturnIfApplicable(loan, returnDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getAmount()).isEqualByComparingTo(expectedAmount);
    }

    private void setSettingValue(String key, String value) {
        ApplicationSetting setting = applicationSettingRepository.findBySettingKey(key).orElseThrow();
        setting.setSettingValue(value);
    }

    
    
    

    private static final org.springframework.data.domain.Sort FINE_SORT =
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "issuedAt", "id");
    private static final Instant SEP_1 = LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

    private AppUser persistBorrower(String email, String firstName, String lastName, String memberNumber) {
        AppUser user = persistUser(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMemberNumber(memberNumber);
        return user;
    }

    /** Amende avec son propre Loan RETURNED + Copy (un Loan porte au plus une Fine) ; closedAt = paidAt ou cancelledAt selon le statut. */
    private Fine persistFilterFine(AppUser user, String titleName, FineStatus status, Instant issuedAt, Instant closedAt) {
        Title title = persistTitle();
        title.setTitle(titleName);
        Copy copy = persistCopy(title, "F08-" + System.nanoTime());
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(LocalDate.of(2026, 7, 11).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(LocalDate.of(2026, 8, 1));
        loan.setReturnDate(LocalDate.of(2026, 8, 20));
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(new BigDecimal("3.00"));
        fine.setReason("Retard");
        fine.setIssuedAt(issuedAt);
        fine.setFineStatus(status);
        if (status == FineStatus.PAID) {
            fine.setPaidAt(closedAt);
        }
        if (status == FineStatus.CANCELLED) {
            fine.setCancelledAt(closedAt);
        }
        entityManager.persist(fine);
        return fine;
    }

    private List<Long> fineIds(Page<FineResponse> page) {
        return page.getContent().stream().map(FineResponse::id).toList();
    }

    private Page<FineResponse> searchFines(String q, FineStatus status, LocalDate iFrom, LocalDate iTo,
            LocalDate cFrom, LocalDate cTo) {
        return fineService.listFines(PageRequest.of(0, 100, FINE_SORT), q, status, iFrom, iTo, cFrom, cTo);
    }

    @Test
    void listFinesSearchMatchesMemberNumberNamesAndTitle() {
        authenticateWithFineRead();
        AppUser target = persistBorrower("f08-q1@primatis.test", "Fixprenom", "Fixnomunique", "M995000001");
        AppUser other = persistBorrower("f08-q2@primatis.test", "Autre", "Personne", "M995000002");
        Fine byMember = persistFilterFine(target, "Dune", FineStatus.UNPAID, SEP_1, null);
        Fine byTitle = persistFilterFine(other, "Les Misérables Fixhugo", FineStatus.UNPAID, SEP_1.plusSeconds(1), null);
        entityManager.flush();

        for (String q : List.of("fixprenom", "FIXNOMUNIQUE", "  Fixprenom Fixnomunique ", "fixnomunique fixprenom",
                "M995000001", "m995000001", "nomuniq")) {
            assertThat(fineIds(searchFines(q, null, null, null, null, null))).as(q).containsExactly(byMember.getId());
        }
        assertThat(fineIds(searchFines("  fixhugo ", null, null, null, null, null))).containsExactly(byTitle.getId());
        assertThat(searchFines("zzzxyzzy-f08", null, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void listFinesSearchTreatsLikeWildcardsAsLiterals() {
        authenticateWithFineRead();
        persistFilterFine(persistBorrower("f08-wild@primatis.test", "Wildcard", "Nom", "M995000003"),
                "Titre Wildtitle", FineStatus.UNPAID, SEP_1, null);
        entityManager.flush();

        assertThat(searchFines("%", null, null, null, null, null).getContent())
                .extracting(f -> f.borrower().lastName()).doesNotContain("Nom");
        assertThat(searchFines("Wild_ard", null, null, null, null, null).getTotalElements()).isZero();
        assertThat(searchFines("Wild_title", null, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void listFinesFiltersByEachStatus() {
        authenticateWithFineRead();
        AppUser user = persistBorrower("f08-st@primatis.test", "Fixstatus", "Membre", "M995000004");
        Instant closed = SEP_1.plusSeconds(86400);
        Fine unpaid = persistFilterFine(user, "T1", FineStatus.UNPAID, SEP_1, null);
        Fine paid = persistFilterFine(user, "T2", FineStatus.PAID, SEP_1, closed);
        Fine cancelled = persistFilterFine(user, "T3", FineStatus.CANCELLED, SEP_1, closed);
        entityManager.flush();

        assertThat(fineIds(searchFines("Fixstatus", FineStatus.UNPAID, null, null, null, null))).containsExactly(unpaid.getId());
        assertThat(fineIds(searchFines("Fixstatus", FineStatus.PAID, null, null, null, null))).containsExactly(paid.getId());
        assertThat(fineIds(searchFines("Fixstatus", FineStatus.CANCELLED, null, null, null, null))).containsExactly(cancelled.getId());
        assertThat(searchFines("Fixstatus", null, null, null, null, null).getTotalElements()).isEqualTo(3);
    }

    /** Bornes inclusives sur des Instants UTC : 00:00:00Z du jour « from », 23:59:59Z du jour « to ». */
    @Test
    void listFinesIssuedDateRangeIsInclusiveOnUtcDays() {
        authenticateWithFineRead();
        AppUser user = persistBorrower("f08-is@primatis.test", "Fixissued", "Membre", "M995000005");
        Fine aug31 = persistFilterFine(user, "T", FineStatus.UNPAID, SEP_1.minusSeconds(1), null);
        Fine sep1 = persistFilterFine(user, "T", FineStatus.UNPAID, SEP_1, null);
        Fine sep2Late = persistFilterFine(user, "T", FineStatus.UNPAID, SEP_1.plusSeconds(2 * 86400 - 1), null);
        Fine sep3 = persistFilterFine(user, "T", FineStatus.UNPAID, SEP_1.plusSeconds(2 * 86400), null);
        entityManager.flush();
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 2);

        assertThat(fineIds(searchFines("Fixissued", null, d1, null, null, null)))
                .containsExactlyInAnyOrder(sep1.getId(), sep2Late.getId(), sep3.getId());
        assertThat(fineIds(searchFines("Fixissued", null, null, d2, null, null)))
                .containsExactlyInAnyOrder(aug31.getId(), sep1.getId(), sep2Late.getId());
        assertThat(fineIds(searchFines("Fixissued", null, d1, d2, null, null)))
                .containsExactlyInAnyOrder(sep1.getId(), sep2Late.getId());
        assertThat(fineIds(searchFines("Fixissued", null, d1, d1, null, null))).containsExactly(sep1.getId());
    }

    /** « Clôturée le » = paidAt (PAID) ou cancelledAt (CANCELLED) ; UNPAID (NULL) exclue de tout filtre de clôture. */
    @Test
    void listFinesClosedDateRangeCoversPaidAndCancelledAndExcludesUnpaid() {
        authenticateWithFineRead();
        AppUser user = persistBorrower("f08-cl@primatis.test", "Fixclosed", "Membre", "M995000006");
        Instant day20 = LocalDate.of(2026, 9, 20).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Fine paidBefore = persistFilterFine(user, "T", FineStatus.PAID, SEP_1, day20.minusSeconds(1));
        Fine paidStart = persistFilterFine(user, "T", FineStatus.PAID, SEP_1, day20);
        Fine cancelledEnd = persistFilterFine(user, "T", FineStatus.CANCELLED, SEP_1, day20.plusSeconds(86400 - 1));
        Fine cancelledNext = persistFilterFine(user, "T", FineStatus.CANCELLED, SEP_1, day20.plusSeconds(86400));
        Fine unpaid = persistFilterFine(user, "T", FineStatus.UNPAID, SEP_1, null);
        entityManager.flush();
        LocalDate d20 = LocalDate.of(2026, 9, 20);

        assertThat(fineIds(searchFines("Fixclosed", null, null, null, d20, null)))
                .containsExactlyInAnyOrder(paidStart.getId(), cancelledEnd.getId(), cancelledNext.getId());
        assertThat(fineIds(searchFines("Fixclosed", null, null, null, null, d20)))
                .containsExactlyInAnyOrder(paidBefore.getId(), paidStart.getId(), cancelledEnd.getId());
        assertThat(fineIds(searchFines("Fixclosed", null, null, null, d20, d20)))
                .containsExactlyInAnyOrder(paidStart.getId(), cancelledEnd.getId());
        // sans filtre de clôture l'impayée reste visible ; avec un filtre (même très large) : jamais
        assertThat(fineIds(searchFines("Fixclosed", null, null, null, null, null))).contains(unpaid.getId());
        assertThat(fineIds(searchFines("Fixclosed", null, null, null, d20.minusYears(10), d20.plusYears(10))))
                .doesNotContain(unpaid.getId());
    }

    @Test
    void listFinesRejectsInvertedRanges() {
        authenticateWithFineRead();
        LocalDate later = LocalDate.of(2026, 9, 2);
        LocalDate earlier = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> searchFines(null, null, later, earlier, null, null))
                .isInstanceOf(be.primatis.exception.InvalidDateRangeException.class)
                .extracting("parameterName").isEqualTo("issuedDateFrom");
        assertThatThrownBy(() -> searchFines(null, null, null, null, later, earlier))
                .isInstanceOf(be.primatis.exception.InvalidDateRangeException.class)
                .extracting("parameterName").isEqualTo("closedDateFrom");
        assertThat(searchFines("zzzxyzzy-f08", null, earlier, earlier, earlier, earlier).getTotalElements()).isZero();
    }

    @Test
    void listFinesCombinesAllCriteriaWithAnd() {
        authenticateWithFineRead();
        AppUser match = persistBorrower("f08-c1@primatis.test", "Fixcombo", "Cible", "M995000007");
        AppUser otherUser = persistBorrower("f08-c2@primatis.test", "Fixcombo", "Autre", "M995000008");
        Instant issued = SEP_1.plusSeconds(3600);
        Instant closed = LocalDate.of(2026, 9, 22).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Fine hit = persistFilterFine(match, "T", FineStatus.PAID, issued, closed);
        persistFilterFine(match, "T", FineStatus.CANCELLED, issued, closed);
        persistFilterFine(match, "T", FineStatus.PAID, SEP_1.minusSeconds(86400), closed);
        persistFilterFine(match, "T", FineStatus.PAID, issued, closed.plusSeconds(10 * 86400));
        persistFilterFine(otherUser, "T", FineStatus.PAID, issued, closed);
        persistFilterFine(match, "T", FineStatus.UNPAID, issued, null);
        entityManager.flush();

        Page<FineResponse> page = searchFines("cible", FineStatus.PAID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25));

        assertThat(fineIds(page)).containsExactly(hit.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(fineIds(searchFines("cible", FineStatus.PAID, null, null, null, null))).hasSize(3);
        assertThat(fineIds(searchFines("fixcombo", FineStatus.PAID, null, null, LocalDate.of(2026, 9, 1), null))).hasSize(4);
    }

    @Test
    void listFinesFilteredTotalAndPagesAreComputedAfterFiltering() {
        authenticateWithFineRead();
        AppUser user = persistBorrower("f08-pg@primatis.test", "Fixpaging", "Membre", "M995000009");
        for (int i = 1; i <= 5; i++) {
            persistFilterFine(user, "T", FineStatus.PAID, SEP_1.plusSeconds(i), SEP_1.plusSeconds(1000 + i));
        }
        persistFilterFine(user, "T", FineStatus.UNPAID, SEP_1, null);
        entityManager.flush();

        Page<FineResponse> first = fineService.listFines(PageRequest.of(0, 2, FINE_SORT), "Fixpaging", FineStatus.PAID, null, null, null, null);
        Page<FineResponse> third = fineService.listFines(PageRequest.of(2, 2, FINE_SORT), "Fixpaging", FineStatus.PAID, null, null, null, null);

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(third.getContent()).hasSize(1);
    }

    /** Le graphe loan/user/copy/title doit rester appliqué à la variante Specification (sinon N+1), y compris avec la jointure de recherche sur le titre. */
    @Test
    void listFinesFilteredDoesNotIntroduceNPlusOne() {
        authenticateWithFineRead();
        AppUser user = persistBorrower("f08-n1@primatis.test", "Fixnplus", "Membre", "M995000010");
        for (int i = 1; i <= 8; i++) {
            persistFilterFine(user, "Titre nplus " + i, FineStatus.UNPAID, SEP_1.plusSeconds(i), null);
        }
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(org.hibernate.Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Page<FineResponse> page = searchFines("Fixnplus", FineStatus.UNPAID, null, null, null, null);
        page.getContent().forEach(f -> {
            assertThat(f.borrower().lastName()).isEqualTo("Membre");
            assertThat(f.loan().copy().inventoryCode()).startsWith("F08-");
            assertThat(f.loan().copy().titleId()).isNotNull();
        });

        assertThat(page.getContent()).hasSize(8);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2L);
        statistics.setStatisticsEnabled(false);
    }

    @Test
    void listFinesWithFiltersDeniedWithoutFineReadPermission() {
        authenticateWithoutFineRead();

        assertThatThrownBy(() -> searchFines("x", FineStatus.UNPAID, null, null, null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private void persistExistingFine(Loan loan) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.TEN);
        fine.setReason("Fine préexistante (fixture anti-doublon)");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(FineStatus.PAID);
        fine.setPaidAt(Instant.now());
        entityManager.persist(fine);
        entityManager.flush();
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(new BigDecimal("5.00"));
        fine.setReason("Motif de test");
        fine.setIssuedAt(Instant.parse("2026-08-15T09:00:00Z"));
        fine.setFineStatus(status);
        if (status == FineStatus.PAID) {
            fine.setPaidAt(Instant.now());
        }
        if (status == FineStatus.CANCELLED) {
            fine.setCancelledAt(Instant.now());
        }
        entityManager.persist(fine);
        entityManager.flush();
        return fine;
    }

    
    private Loan persistLoanForUser(AppUser user) {
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-SERVICE-" + System.nanoTime());
        LocalDate dueDate = LocalDate.of(2026, 8, 15);
        LocalDate returnDate = LocalDate.of(2026, 8, 20);

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(dueDate.minusDays(21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(dueDate);
        loan.setReturnDate(returnDate);
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        entityManager.flush();
        return loan;
    }

    private Loan persistReturnedLoan(LocalDate dueDate, LocalDate returnDate) {
        AppUser user = persistUser("fine-service-" + System.nanoTime() + "@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "FINE-SERVICE-" + System.nanoTime());

        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(dueDate.minusDays(21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(dueDate);
        loan.setReturnDate(returnDate);
        loan.setLoanStatus(LoanStatus.RETURNED);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        entityManager.flush();
        return loan;
    }

    private AppUser persistUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        entityManager.persist(user);
        return user;
    }

    private Title persistTitle() {
        Title title = new Title();
        title.setTitle("Titre de test");
        title.setLanguage(Language.FR);
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(Instant.now());
        title.setUpdatedAt(Instant.now());
        entityManager.persist(title);
        return title;
    }

    private Copy persistCopy(Title title, String inventoryCode) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(CopyCondition.GOOD);
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }
}
