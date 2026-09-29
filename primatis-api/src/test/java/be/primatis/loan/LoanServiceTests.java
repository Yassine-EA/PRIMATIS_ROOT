package be.primatis.loan;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.Fine;
import be.primatis.fine.FineRepository;
import be.primatis.fine.FineStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.loan.dto.LoanResponse;
import be.primatis.reservation.Reservation;
import be.primatis.reservation.ReservationStatus;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanServiceTests {

    @Autowired
    private LoanService loanService;

    @Autowired
    private Clock clock;

    @Autowired
    private FineRepository fineRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithLoanRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("LOAN_READ"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateAsAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
    }

    private static void authenticateWithLoanManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("LOAN_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    
    // listLoans — staff, LOAN_READ
    

    @Test
    void listLoansDeniedWithoutLoanReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> loanService.listLoans(PageRequest.of(0, 20)));
    }

    @Test
    void listLoansReturnsPaginatedAndMappedResultsAcrossBorrowers() {
        authenticateWithLoanRead();
        AppUser borrowerOne = persistUser("service-staff-list-1@primatis.test");
        AppUser borrowerTwo = persistUser("service-staff-list-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "SERVICE-STAFF-1");
        Copy copyTwo = persistCopy(title, "SERVICE-STAFF-2");
        Loan older = persistLoan(borrowerOne, copyOne, LoanStatus.ACTIVE, Instant.now().minusSeconds(120));
        Loan newer = persistLoan(borrowerTwo, copyTwo, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listLoans(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "loanDate", "id")));

        assertThat(page.getContent()).extracting(LoanResponse::id)
                .contains(newer.getId(), older.getId());
        int newerIndex = indexOfLoanId(page.getContent(), newer.getId());
        int olderIndex = indexOfLoanId(page.getContent(), older.getId());
        assertThat(newerIndex).isLessThan(olderIndex);
    }

    @Test
    void listLoansExposesBothBorrowersLoansNotJustOne() {
        authenticateWithLoanRead();
        AppUser borrowerOne = persistUser("service-staff-multi-1@primatis.test");
        AppUser borrowerTwo = persistUser("service-staff-multi-2@primatis.test");
        Title title = persistTitle();
        Copy copyOne = persistCopy(title, "SERVICE-STAFF-MULTI-1");
        Copy copyTwo = persistCopy(title, "SERVICE-STAFF-MULTI-2");
        Loan loanOne = persistLoan(borrowerOne, copyOne, LoanStatus.ACTIVE, Instant.now());
        Loan loanTwo = persistLoan(borrowerTwo, copyTwo, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listLoans(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(LoanResponse::id)
                .contains(loanOne.getId(), loanTwo.getId());
    }

    
    // listOwnLoans — self, ownership structurelle
    

    /**
     * Aucun {@code @PreAuthorize} sur {@code listOwnLoans} : fonctionne même
     * sans authentification RBAC (anonyme), l'ownership reposant uniquement
     * sur le {@code selfUserId} transmis par le Controller.
     */
    @Test
    void listOwnLoansWorksWithoutLoanReadPermission() {
        authenticateAsAnonymous();
        AppUser self = persistUser("service-self-no-permission@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-SELF-NO-PERM");
        Loan loan = persistLoan(self, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listOwnLoans(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(LoanResponse::id).containsExactly(loan.getId());
    }

    @Test
    void listOwnLoansIsolatesLoansBetweenUsers() {
        AppUser self = persistUser("service-self-isolation-1@primatis.test");
        AppUser other = persistUser("service-self-isolation-2@primatis.test");
        Title title = persistTitle();
        Copy selfCopy = persistCopy(title, "SERVICE-SELF-ISOLATION-1");
        Copy otherCopy = persistCopy(title, "SERVICE-SELF-ISOLATION-2");
        Loan selfLoan = persistLoan(self, selfCopy, LoanStatus.ACTIVE, Instant.now());
        persistLoan(other, otherCopy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        Page<LoanResponse> page = loanService.listOwnLoans(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(LoanResponse::id).containsExactly(selfLoan.getId());
        assertThat(page.getContent()).allSatisfy(
                response -> assertThat(response.borrower().id()).isEqualTo(self.getId()));
    }

    @Test
    void listOwnLoansReturnsEmptyPageRatherThanErrorWhenNoLoan() {
        AppUser self = persistUser("service-self-empty@primatis.test");
        entityManager.flush();

        Page<LoanResponse> page = loanService.listOwnLoans(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    
    
    

    @Test
    void registerLoanDeniedWithoutLoanManagePermission() {
        authenticateAsAnonymous();
        AppUser borrower = persistMember("service-manage-denied@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-MANAGE-DENIED");
        entityManager.flush();

        assertThrows(AccessDeniedException.class,
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanOnAvailableCopySucceedsAndTransitionsCopyToOnLoan() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-available@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-AVAILABLE");
        entityManager.flush();

        LoanResponse response = loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId()));

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(response.returnDate()).isNull();
        assertThat(response.borrower().id()).isEqualTo(borrower.getId());
        assertThat(response.copy().id()).isEqualTo(copy.getId());
        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
    }

    @Test
    void registerLoanComputesDueDateFromLoanDurationDaysSetting() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-duedate@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-DUEDATE");
        entityManager.flush();

        LoanResponse response = loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId()));

        // LocalDate.now(clock) — pas LocalDate.now() brut : registerLoan calcule
        // dueDate via LocalDate.now(clock) (UTC, ClockConfig). LocalDate.now() nu
        // utilise le fuseau par défaut de la JVM (local) ; les deux ne
        // coïncident pas pendant la fenêtre quotidienne où minuit local est déjà
        
        
        LocalDate expectedDueDate = LocalDate.now(clock).plusDays(21);
        assertThat(response.dueDate()).isEqualTo(expectedDueDate);
    }

    @Test
    void registerLoanOnReservedCopyWithMatchingReadyReservationFulfillsIt() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-fulfill@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-FULFILL");
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        Reservation reservation = persistReadyReservation(borrower, title, copy);
        entityManager.flush();

        LoanResponse response = loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId()));

        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
        Reservation reloadedReservation = entityManager.find(Reservation.class, reservation.getId());
        assertThat(reloadedReservation.getReservationStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(reloadedReservation.getFulfilledByLoan().getId()).isEqualTo(response.id());
    }

    @Test
    void registerLoanWithUnknownBorrowerThrowsResourceNotFound() {
        authenticateWithLoanManage();
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-NO-BORROWER");
        entityManager.flush();

        assertThrows(ResourceNotFoundException.class,
                () -> loanService.registerLoan(new CreateLoanRequest(-1L, copy.getId())));
    }

    @Test
    void registerLoanWithDisabledAccountIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-disabled@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        borrower.setAccountStatus(AccountStatus.DISABLED);
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-DISABLED");
        entityManager.flush();

        assertBusinessRuleCode("ACCOUNT_DISABLED",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanForNonMemberIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistUser("service-register-nonmember@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-NONMEMBER");
        entityManager.flush();

        assertBusinessRuleCode("NOT_A_MEMBER",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanWithBlockedMemberIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-blocked@primatis.test", MemberStatus.BLOCKED, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-BLOCKED");
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_BLOCKED",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanWithExpiredMemberIsRejected() {
        authenticateWithLoanManage();
        // LocalDate.now(clock) — pas LocalDate.now() brut : MemberExpirationPolicy
        // compare expirationDate à LocalDate.now(clock) (UTC, ClockConfig). Une
        // marge d'un seul jour rend ce fixture sensible à l'écart Bruxelles/UTC
        
        AppUser borrower = persistMember(
                "service-register-expired@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-EXPIRED");
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_EXPIRED",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanWithUnpaidFineIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-unpaidfine@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy pastCopy = persistCopy(title, "SERVICE-REGISTER-UNPAIDFINE-PAST");
        Loan pastLoan = persistLoan(borrower, pastCopy, LoanStatus.RETURNED, Instant.now().minusSeconds(3600));
        persistFine(pastLoan, FineStatus.UNPAID);
        Copy copy = persistCopy(title, "SERVICE-REGISTER-UNPAIDFINE");
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_HAS_UNPAID_FINE",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanWithFiveOpenLoansIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-maxloans@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        for (int i = 0; i < 5; i++) {
            Copy openCopy = persistCopy(title, "SERVICE-REGISTER-MAXLOANS-" + i);
            persistLoan(borrower, openCopy, LoanStatus.ACTIVE, Instant.now());
        }
        Copy copy = persistCopy(title, "SERVICE-REGISTER-MAXLOANS-NEW");
        entityManager.flush();

        assertBusinessRuleCode("MAX_ACTIVE_LOANS_REACHED",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanWithUnknownCopyThrowsResourceNotFound() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-nocopy@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        entityManager.flush();

        assertThrows(ResourceNotFoundException.class,
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), -1L)));
    }

    @Test
    void registerLoanOnLoanCopyIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-onloan@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-ONLOAN");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        AppUser otherBorrower = persistMember("service-register-onloan-other@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        persistLoan(otherBorrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("COPY_ALREADY_ON_LOAN",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanOnUnavailableCopyIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-unavailable@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-UNAVAILABLE");
        copy.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        entityManager.flush();

        assertBusinessRuleCode("COPY_NOT_LENDABLE",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanOnCopyReservedForAnotherMemberIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-reserved-other@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser beneficiary = persistMember("service-register-reserved-beneficiary@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-RESERVED-OTHER");
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        persistReadyReservation(beneficiary, title, copy);
        entityManager.flush();

        assertBusinessRuleCode("COPY_RESERVED_FOR_ANOTHER_MEMBER",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanOnReservedCopyWithoutMatchingReadyReservationIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-reserved-nomatch@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-RESERVED-NOMATCH");
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        entityManager.flush();

        assertBusinessRuleCode("COPY_RESERVED_FOR_ANOTHER_MEMBER",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanWithAnotherOpenLoanOnCopyIsRejectedEvenIfAvailabilityStatusSaysAvailable() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-dataincoherence@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser otherBorrower = persistMember("service-register-dataincoherence-other@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-DATAINCOHERENCE");
        // Incohérence de données volontaire : availabilityStatus = AVAILABLE alors qu'un Loan ouvert
        // existe déjà — prouve que la revalidation post-lock ne se fie jamais uniquement à
        
        persistLoan(otherBorrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("COPY_ALREADY_ON_LOAN",
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    @Test
    void registerLoanFailsExplicitlyWhenLoanDurationDaysSettingIsMissing() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-register-nosetting@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-REGISTER-NOSETTING");
        entityManager.createQuery("DELETE FROM ApplicationSetting s WHERE s.settingKey = :key")
                .setParameter("key", "LOAN_DURATION_DAYS")
                .executeUpdate();
        entityManager.flush();

        assertThrows(IllegalStateException.class,
                () -> loanService.registerLoan(new CreateLoanRequest(borrower.getId(), copy.getId())));
    }

    private static void assertBusinessRuleCode(String expectedCode, org.junit.jupiter.api.function.Executable executable) {
        BusinessRuleException exception = (BusinessRuleException) assertThrows(BusinessRuleException.class, executable);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
    }

    
    
    

    @Test
    void registerReturnDeniedWithoutLoanManagePermission() {
        authenticateAsAnonymous();
        AppUser borrower = persistMember("service-return-denied@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-DENIED");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        assertThrows(AccessDeniedException.class, () -> loanService.registerReturn(loan.getId()));
    }

    @Test
    void registerReturnOfActiveLoanSucceeds() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-active@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-ACTIVE");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now().minusSeconds(3600));
        entityManager.flush();

        LoanResponse response = loanService.registerReturn(loan.getId());

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.RETURNED);
        // LocalDate.now(clock) — pas LocalDate.now() brut : registerReturn calcule
        
        assertThat(response.returnDate()).isEqualTo(LocalDate.now(clock));
    }

    @Test
    void registerReturnOfOverdueLoanSucceeds() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-overdue@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-OVERDUE");
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.OVERDUE, LocalDate.now().minusDays(10));
        entityManager.flush();

        LoanResponse response = loanService.registerReturn(loan.getId());

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.RETURNED);
        // LocalDate.now(clock) — pas LocalDate.now() brut : registerReturn calcule
        
        assertThat(response.returnDate()).isEqualTo(LocalDate.now(clock));
    }

    @Test
    void registerReturnOnGoodCopyWithoutReservationMakesCopyAvailable() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-good@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-GOOD");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void registerReturnOnDamagedCopyWithoutReservationMakesCopyAvailable() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-damaged@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-DAMAGED");
        copy.setCopyCondition(CopyCondition.DAMAGED);
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    
    @Test
    void registerReturnOnLostCopyMakesCopyUnavailable() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-lost@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopyWithCondition(title, "SERVICE-RETURN-LOST", CopyCondition.LOST, AvailabilityStatus.UNAVAILABLE);
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        LoanResponse response = loanService.registerReturn(loan.getId());

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.RETURNED);
        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void registerReturnOnOutOfServiceCopyMakesCopyUnavailable() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-oos@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopyWithCondition(title, "SERVICE-RETURN-OOS", CopyCondition.OUT_OF_SERVICE, AvailabilityStatus.UNAVAILABLE);
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        LoanResponse response = loanService.registerReturn(loan.getId());

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.RETURNED);
        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void registerReturnDoesNotPromoteReservationWhenCopyIsLost() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-lost-reservation@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser waitingUser = persistMember("service-return-lost-waiting@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopyWithCondition(title, "SERVICE-RETURN-LOST-RESERVATION", CopyCondition.LOST, AvailabilityStatus.UNAVAILABLE);
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        Reservation waiting = persistWaitingReservation(waitingUser, title, Instant.now());
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Reservation reloadedReservation = entityManager.find(Reservation.class, waiting.getId());
        assertThat(reloadedReservation.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(reloadedReservation.getAssignedCopy()).isNull();
    }

    @Test
    void registerReturnWithWaitingReservationPromotesItToReadyAndCopyToReserved() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-promote@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser waitingUser = persistMember("service-return-promote-waiting@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-PROMOTE");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        Reservation waiting = persistWaitingReservation(waitingUser, title, Instant.now());
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Copy reloadedCopy = entityManager.find(Copy.class, copy.getId());
        Reservation reloadedReservation = entityManager.find(Reservation.class, waiting.getId());
        assertThat(reloadedCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
        assertThat(reloadedReservation.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedReservation.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(reloadedReservation.getExpirationDate()).isNotNull();
    }

    @Test
    void registerReturnComputesReadyExpirationFromHoldHoursSetting() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-hold@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser waitingUser = persistMember("service-return-hold-waiting@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-HOLD");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        Reservation waiting = persistWaitingReservation(waitingUser, title, Instant.now());
        entityManager.flush();
        Instant beforeReturn = Instant.now();

        loanService.registerReturn(loan.getId());

        Reservation reloadedReservation = entityManager.find(Reservation.class, waiting.getId());
        Instant expectedExpiration = beforeReturn.plus(java.time.Duration.ofHours(48));
        assertThat(reloadedReservation.getExpirationDate())
                .isBetween(expectedExpiration.minusSeconds(5), expectedExpiration.plusSeconds(5));
    }

    @Test
    void registerReturnDetectsLatenessEvenWhenLoanWasStillActive() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-late-active@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-LATE-ACTIVE");
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, LocalDate.now().minusDays(5));
        entityManager.flush();

        LoanResponse response = loanService.registerReturn(loan.getId());

        assertThat(response.loanStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(response.returnDate()).isAfter(response.dueDate());
    }

    
    @Test
    void registerReturnOnTimeCreatesNoFine() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-no-fine@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-NO-FINE");
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, LocalDate.now(clock).plusDays(5));
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        assertThat(fineRepository.findByLoanId(loan.getId())).isEmpty();
    }

    
    @Test
    void registerReturnOfALateLoanCreatesAFineLinkedToIt() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-with-fine@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-WITH-FINE");
        Loan loan = persistLoanWithDueDate(borrower, copy, LoanStatus.ACTIVE, LocalDate.now(clock).minusDays(5));
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Fine fine = fineRepository.findByLoanId(loan.getId()).orElseThrow();
        assertThat(fine.getFineStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(fine.getLoan().getId()).isEqualTo(loan.getId());
        // 5 jours de retard -> 1 semaine entamée x FINE_WEEKLY_RATE (0.80, V006)
        assertThat(fine.getAmount()).isEqualByComparingTo(new BigDecimal("0.80"));
    }

    @Test
    void registerReturnWithUnknownLoanThrowsResourceNotFound() {
        authenticateWithLoanManage();

        assertThrows(ResourceNotFoundException.class, () -> loanService.registerReturn(999999999L));
    }

    @Test
    void registerReturnOfAlreadyReturnedLoanIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-already@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-RETURN-ALREADY");
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        Loan loan = persistLoan(borrower, copy, LoanStatus.RETURNED, Instant.now());
        loan.setReturnDate(LocalDate.now());
        entityManager.flush();

        assertBusinessRuleCode("LOAN_ALREADY_RETURNED", () -> loanService.registerReturn(loan.getId()));
    }

    @Test
    void registerReturnWithInconsistentCopyStateIsRejected() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-inconsistent@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        // Incohérence de données volontaire : Loan ACTIVE (donc ouvert) mais Copy
        // laissé AVAILABLE au lieu d'ON_LOAN — preuve que l'anomalie est signalée,
        
        // silencieusement l'anomalie »).
        Copy copy = persistCopy(title, "SERVICE-RETURN-INCONSISTENT");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("LOAN_COPY_STATE_INCONSISTENT", () -> loanService.registerReturn(loan.getId()));
    }

    @Test
    void registerReturnPromotesOldestWaitingReservationFirst() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-fifo@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser olderUser = persistMember("service-return-fifo-older@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser newerUser = persistMember("service-return-fifo-newer@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-FIFO");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        Reservation older = persistWaitingReservation(olderUser, title, Instant.now().minusSeconds(3600));
        Reservation newer = persistWaitingReservation(newerUser, title, Instant.now());
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Reservation reloadedOlder = entityManager.find(Reservation.class, older.getId());
        Reservation reloadedNewer = entityManager.find(Reservation.class, newer.getId());
        assertThat(reloadedOlder.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedNewer.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void registerReturnBreaksTiesByIdWhenReservationDatesAreEqual() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-tie@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser userOne = persistMember("service-return-tie-1@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser userTwo = persistMember("service-return-tie-2@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-TIE");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        Instant sameInstant = Instant.now();
        Reservation first = persistWaitingReservation(userOne, title, sameInstant);
        Reservation second = persistWaitingReservation(userTwo, title, sameInstant);
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        Long promotedId = Math.min(first.getId(), second.getId());
        Long stillWaitingId = Math.max(first.getId(), second.getId());
        assertThat(entityManager.find(Reservation.class, promotedId).getReservationStatus())
                .isEqualTo(ReservationStatus.READY);
        assertThat(entityManager.find(Reservation.class, stillWaitingId).getReservationStatus())
                .isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void registerReturnPromotesOnlyOneWaitingReservation() {
        authenticateWithLoanManage();
        AppUser borrower = persistMember("service-return-onlyone@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser userOne = persistMember("service-return-onlyone-1@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser userTwo = persistMember("service-return-onlyone-2@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        AppUser userThree = persistMember("service-return-onlyone-3@primatis.test", MemberStatus.ACTIVE, LocalDate.now().plusYears(1));
        Title title = persistTitle();
        Copy copy = persistOnLoanCopy(title, "SERVICE-RETURN-ONLYONE");
        Loan loan = persistLoan(borrower, copy, LoanStatus.ACTIVE, Instant.now());
        persistWaitingReservation(userOne, title, Instant.now().minusSeconds(300));
        persistWaitingReservation(userTwo, title, Instant.now().minusSeconds(200));
        persistWaitingReservation(userThree, title, Instant.now().minusSeconds(100));
        entityManager.flush();

        loanService.registerReturn(loan.getId());

        List<Reservation> reservations = entityManager
                .createQuery("SELECT r FROM Reservation r WHERE r.title.id = :titleId", Reservation.class)
                .setParameter("titleId", title.getId())
                .getResultList();
        long readyReservations = reservations.stream()
                .filter(r -> r.getReservationStatus() == ReservationStatus.READY).count();
        long waitingReservations = reservations.stream()
                .filter(r -> r.getReservationStatus() == ReservationStatus.WAITING).count();
        assertThat(readyReservations).isEqualTo(1);
        assertThat(waitingReservations).isEqualTo(2);
    }

    
    
    // LoanResponse.from → loan.getUser()/loan.getCopy()/copy.getTitle()).
    // Ni listLoans ni listOwnLoans n'introduisent de Repository/requête
    // supplémentaire pour compenser : mapping direct sur le résultat de la
    
    

    @Test
    void listLoansMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/loan/LoanService.java",
                "public Page<LoanResponse> listLoans(Pageable pageable) {");

        assertThat(methodBody)
                .as("listLoans se limite à loanRepository.findAll : aucune Repository additionnelle introduite")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("fineRepository")
                .doesNotContain("reservationRepository")
                .contains("loanRepository.findAll(pageable)");
    }

    @Test
    void listOwnLoansMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/loan/LoanService.java",
                "public Page<LoanResponse> listOwnLoans(Long selfUserId, Pageable pageable) {");

        assertThat(methodBody)
                .as("listOwnLoans se limite à loanRepository.findByUserId : aucune Repository additionnelle")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("fineRepository")
                .doesNotContain("reservationRepository")
                .contains("loanRepository.findByUserId(selfUserId, pageable)");
    }

    private static String extractMethodBody(String sourceFilePath, String signature) throws IOException {
        String source = Files.readString(Path.of(sourceFilePath));
        int signatureIndex = source.indexOf(signature);
        assertThat(signatureIndex).as("signature attendue introuvable dans " + sourceFilePath).isNotEqualTo(-1);

        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("Corps de méthode non fermé pour la signature : " + signature);
    }

    private static int indexOfLoanId(List<LoanResponse> content, Long loanId) {
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).id().equals(loanId)) {
                return i;
            }
        }
        throw new IllegalStateException("Loan " + loanId + " absent du contenu de la page.");
    }

    
    
    

    private static final Sort LOAN_SORT = Sort.by(Sort.Direction.DESC, "loanDate", "id");
    private static final Instant SEP_1_START = LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

    private AppUser persistBorrower(String email, String firstName, String lastName, String memberNumber) {
        AppUser user = persistUser(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMemberNumber(memberNumber);
        return user;
    }

    private Loan persistFilterLoan(AppUser user, String code, LoanStatus status, Instant loanDate, LocalDate dueDate) {
        Copy copy = persistCopy(persistTitle(), code);
        Loan loan = persistLoan(user, copy, status, loanDate);
        loan.setDueDate(dueDate);
        return loan;
    }

    private List<Long> ids(Page<LoanResponse> page) {
        return page.getContent().stream().map(LoanResponse::id).toList();
    }

    private Page<LoanResponse> search(String q, LoanStatus status, LocalDate ldFrom, LocalDate ldTo,
            LocalDate dFrom, LocalDate dTo) {
        return loanService.listLoans(PageRequest.of(0, 100, LOAN_SORT), q, status, ldFrom, ldTo, dFrom, dTo);
    }

    @Test
    void listLoansSearchMatchesFirstNameLastNameFullNameAndMemberNumber() {
        authenticateWithLoanRead();
        AppUser target = persistBorrower("f05-q1@primatis.test", "Fixprenom", "Fixnomunique", "M997000001");
        AppUser other = persistBorrower("f05-q2@primatis.test", "Autre", "Personne", "M997000002");
        Loan targetLoan = persistFilterLoan(target, "F05-Q1", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 22));
        persistFilterLoan(other, "F05-Q2", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 22));
        entityManager.flush();

        for (String q : List.of("fixprenom", "FIXNOMUNIQUE", "  Fixprenom Fixnomunique ", "fixnomunique fixprenom",
                "M997000001", "m997000001", "nomuniq")) {
            assertThat(ids(search(q, null, null, null, null, null))).as(q).containsExactly(targetLoan.getId());
        }
        assertThat(search("zzzxyzzy-f05", null, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void listLoansSearchTreatsLikeWildcardsAsLiterals() {
        authenticateWithLoanRead();
        AppUser user = persistBorrower("f05-wild@primatis.test", "Wildcard", "Nom", "M997000003");
        persistFilterLoan(user, "F05-WILD", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 22));
        entityManager.flush();

        assertThat(search("%", null, null, null, null, null).getContent())
                .extracting(l -> l.borrower().lastName()).doesNotContain("Nom");
        assertThat(search("Wild_ard", null, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void listLoansFiltersByEachStatus() {
        authenticateWithLoanRead();
        AppUser user = persistBorrower("f05-st@primatis.test", "Fixstatus", "Emprunteur", "M997000004");
        Loan active = persistFilterLoan(user, "F05-ST-A", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 22));
        Loan overdue = persistFilterLoan(user, "F05-ST-O", LoanStatus.OVERDUE, SEP_1_START, LocalDate.of(2026, 9, 22));
        Loan returned = persistFilterLoan(user, "F05-ST-R", LoanStatus.RETURNED, SEP_1_START, LocalDate.of(2026, 9, 22));
        entityManager.flush();

        assertThat(ids(search("Fixstatus", LoanStatus.ACTIVE, null, null, null, null))).containsExactly(active.getId());
        assertThat(ids(search("Fixstatus", LoanStatus.OVERDUE, null, null, null, null))).containsExactly(overdue.getId());
        assertThat(ids(search("Fixstatus", LoanStatus.RETURNED, null, null, null, null))).containsExactly(returned.getId());
        assertThat(search("Fixstatus", null, null, null, null, null).getTotalElements()).isEqualTo(3);
    }

    /** Bornes inclusives sur des Instants UTC : 00:00:00Z du jour « from », 23:59:59Z du jour « to ». */
    @Test
    void listLoansLoanDateRangeIsInclusiveOnUtcDays() {
        authenticateWithLoanRead();
        AppUser user = persistBorrower("f05-ld@primatis.test", "Fixloandate", "Emprunteur", "M997000005");
        LocalDate due = LocalDate.of(2026, 9, 30);
        Loan aug31Late = persistFilterLoan(user, "F05-LD-1", LoanStatus.ACTIVE, SEP_1_START.minusSeconds(1), due);
        Loan sep1Start = persistFilterLoan(user, "F05-LD-2", LoanStatus.ACTIVE, SEP_1_START, due);
        Loan sep2Late = persistFilterLoan(user, "F05-LD-3", LoanStatus.ACTIVE, SEP_1_START.plusSeconds(2 * 86400 - 1), due);
        Loan sep3Start = persistFilterLoan(user, "F05-LD-4", LoanStatus.ACTIVE, SEP_1_START.plusSeconds(2 * 86400), due);
        entityManager.flush();
        LocalDate sep1 = LocalDate.of(2026, 9, 1);
        LocalDate sep2 = LocalDate.of(2026, 9, 2);

        assertThat(ids(search("Fixloandate", null, sep1, null, null, null)))
                .containsExactlyInAnyOrder(sep1Start.getId(), sep2Late.getId(), sep3Start.getId());
        assertThat(ids(search("Fixloandate", null, null, sep2, null, null)))
                .containsExactlyInAnyOrder(aug31Late.getId(), sep1Start.getId(), sep2Late.getId());
        assertThat(ids(search("Fixloandate", null, sep1, sep2, null, null)))
                .containsExactlyInAnyOrder(sep1Start.getId(), sep2Late.getId());
        assertThat(ids(search("Fixloandate", null, sep1, sep1, null, null))).containsExactly(sep1Start.getId());
    }

    @Test
    void listLoansDueDateRangeIsInclusive() {
        authenticateWithLoanRead();
        AppUser user = persistBorrower("f05-dd@primatis.test", "Fixdue", "Emprunteur", "M997000006");
        Loan d19 = persistFilterLoan(user, "F05-DD-1", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 19));
        Loan d20 = persistFilterLoan(user, "F05-DD-2", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 20));
        Loan d21 = persistFilterLoan(user, "F05-DD-3", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 21));
        Loan d22 = persistFilterLoan(user, "F05-DD-4", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 22));
        entityManager.flush();
        LocalDate from = LocalDate.of(2026, 9, 20);
        LocalDate to = LocalDate.of(2026, 9, 21);

        assertThat(ids(search("Fixdue", null, null, null, from, null)))
                .containsExactlyInAnyOrder(d20.getId(), d21.getId(), d22.getId());
        assertThat(ids(search("Fixdue", null, null, null, null, to)))
                .containsExactlyInAnyOrder(d19.getId(), d20.getId(), d21.getId());
        assertThat(ids(search("Fixdue", null, null, null, from, to)))
                .containsExactlyInAnyOrder(d20.getId(), d21.getId());
    }

    @Test
    void listLoansRejectsInvertedRanges() {
        authenticateWithLoanRead();
        LocalDate later = LocalDate.of(2026, 9, 2);
        LocalDate earlier = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> search(null, null, later, earlier, null, null))
                .isInstanceOf(be.primatis.exception.InvalidDateRangeException.class)
                .extracting("parameterName").isEqualTo("loanDateFrom");
        assertThatThrownBy(() -> search(null, null, null, null, later, earlier))
                .isInstanceOf(be.primatis.exception.InvalidDateRangeException.class)
                .extracting("parameterName").isEqualTo("dueDateFrom");
        // from == to reste valide
        assertThat(search("zzzxyzzy-f05", null, earlier, earlier, earlier, earlier).getTotalElements()).isZero();
    }

    @Test
    void listLoansCombinesAllCriteriaWithAnd() {
        authenticateWithLoanRead();
        AppUser match = persistBorrower("f05-c1@primatis.test", "Fixcombo", "Cible", "M997000007");
        AppUser otherUser = persistBorrower("f05-c2@primatis.test", "Fixcombo", "Autre", "M997000008");
        LocalDate due = LocalDate.of(2026, 9, 22);
        Loan hit = persistFilterLoan(match, "F05-C-HIT", LoanStatus.OVERDUE, SEP_1_START.plusSeconds(3600), due);
        persistFilterLoan(match, "F05-C-STATUS", LoanStatus.ACTIVE, SEP_1_START.plusSeconds(3600), due);
        persistFilterLoan(match, "F05-C-LOANDATE", LoanStatus.OVERDUE, SEP_1_START.minusSeconds(3600), due);
        persistFilterLoan(match, "F05-C-DUE", LoanStatus.OVERDUE, SEP_1_START.plusSeconds(3600), due.plusDays(10));
        persistFilterLoan(otherUser, "F05-C-USER", LoanStatus.OVERDUE, SEP_1_START.plusSeconds(3600), due);
        entityManager.flush();

        Page<LoanResponse> page = search("cible", LoanStatus.OVERDUE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25));

        assertThat(ids(page)).containsExactly(hit.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(ids(search("cible", LoanStatus.OVERDUE, null, null, null, null))).hasSize(3);
        assertThat(ids(search("fixcombo", null, LocalDate.of(2026, 9, 1), null, null, null))).hasSize(4);
    }

    @Test
    void listLoansFilteredTotalAndPagesAreComputedAfterFiltering() {
        authenticateWithLoanRead();
        AppUser user = persistBorrower("f05-pg@primatis.test", "Fixpaging", "Emprunteur", "M997000009");
        for (int i = 1; i <= 5; i++) {
            persistFilterLoan(user, "F05-PG-" + i, LoanStatus.RETURNED, SEP_1_START.plusSeconds(i), LocalDate.of(2026, 9, 22));
        }
        persistFilterLoan(user, "F05-PG-X", LoanStatus.ACTIVE, SEP_1_START, LocalDate.of(2026, 9, 22));
        entityManager.flush();

        Page<LoanResponse> first = loanService.listLoans(PageRequest.of(0, 2, LOAN_SORT), "Fixpaging",
                LoanStatus.RETURNED, null, null, null, null);
        Page<LoanResponse> third = loanService.listLoans(PageRequest.of(2, 2, LOAN_SORT), "Fixpaging",
                LoanStatus.RETURNED, null, null, null, null);

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(third.getContent()).hasSize(1);
    }

    /**
     * Le graphe user/copy/copy.title doit rester appliqué à la variante Specification
     * (sinon N+1 réintroduit) : nombre d'ordres SQL constant, indépendant du nombre de lignes.
     */
    @Test
    void listLoansFilteredDoesNotIntroduceNPlusOne() {
        authenticateWithLoanRead();
        AppUser user = persistBorrower("f05-n1@primatis.test", "Fixnplus", "Emprunteur", "M997000010");
        for (int i = 1; i <= 8; i++) {
            persistFilterLoan(user, "F05-N1-" + i, LoanStatus.ACTIVE, SEP_1_START.plusSeconds(i), LocalDate.of(2026, 9, 22));
        }
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(org.hibernate.Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Page<LoanResponse> page = search("Fixnplus", LoanStatus.ACTIVE, null, null, null, null);
        page.getContent().forEach(l -> {
            assertThat(l.borrower().lastName()).isEqualTo("Emprunteur");
            assertThat(l.copy().inventoryCode()).startsWith("F05-N1-");
        });

        assertThat(page.getContent()).hasSize(8);
        // page + count ; jamais une requête par ligne
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2L);
        statistics.setStatisticsEnabled(false);
    }

    @Test
    void listLoansWithFiltersDeniedWithoutLoanReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> search("x", LoanStatus.ACTIVE, null, null, null, null));
    }

    
    // Fixtures minimales
    

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

    private Loan persistLoan(AppUser user, Copy copy, LoanStatus status, Instant loanDate) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(loanDate);
        loan.setDueDate(LocalDate.now().plusDays(21));
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }

    private Loan persistLoanWithDueDate(AppUser user, Copy copy, LoanStatus status, LocalDate dueDate) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(dueDate.minusDays(21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        loan.setDueDate(dueDate);
        loan.setLoanStatus(status);
        loan.setCreatedAt(Instant.now());
        loan.setUpdatedAt(Instant.now());
        entityManager.persist(loan);
        return loan;
    }

    private Copy persistOnLoanCopy(Title title, String inventoryCode) {
        Copy copy = persistCopy(title, inventoryCode);
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        return copy;
    }

    /**
     * Persiste directement la combinaison condition/disponibilité voulue
     * (jamais via une mutation en deux temps sur un Copy déjà persisté) :
     * {@code ck_copy_condition_availability} (V001) est vérifiée à chaque
     * flush, une combinaison transitoirement invalide échouerait même si
     * l'état final visé est cohérent.
     */
    private Copy persistCopyWithCondition(
            Title title, String inventoryCode, CopyCondition condition, AvailabilityStatus availability) {
        Copy copy = new Copy();
        copy.setTitle(title);
        copy.setInventoryCode(inventoryCode);
        copy.setCopyCondition(condition);
        copy.setAvailabilityStatus(availability);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private AppUser persistMember(String email, MemberStatus memberStatus, LocalDate memberExpirationDate) {
        AppUser user = persistUser(email);
        user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
        user.setMemberStatus(memberStatus);
        user.setRegistrationDate(LocalDate.now().minusYears(1));
        user.setMemberExpirationDate(memberExpirationDate);
        return user;
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.TEN);
        fine.setReason("Retard de test");
        fine.setIssuedAt(Instant.now());
        fine.setFineStatus(status);
        entityManager.persist(fine);
        return fine;
    }

    private Reservation persistReadyReservation(AppUser user, Title title, Copy assignedCopy) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setAssignedCopy(assignedCopy);
        reservation.setReservationDate(Instant.now());
        reservation.setExpirationDate(Instant.now().plusSeconds(3600));
        reservation.setReservationStatus(ReservationStatus.READY);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistWaitingReservation(AppUser user, Title title, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.WAITING);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }
}
