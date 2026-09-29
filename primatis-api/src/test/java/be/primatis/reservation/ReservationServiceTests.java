package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.Fine;
import be.primatis.fine.FineStatus;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanStatus;
import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.reservation.dto.CreateReservationRequest;
import be.primatis.reservation.dto.ReservationResponse;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationServiceTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithReservationRead() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("RESERVATION_READ"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateWithReservationManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("RESERVATION_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void authenticateAsAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
    }

    
    // listReservations — staff, RESERVATION_READ
    

    @Test
    void listReservationsDeniedWithoutReservationReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> reservationService.listReservations(PageRequest.of(0, 20)));
    }

    @Test
    void listReservationsReturnsPaginatedAndMappedResultsAcrossMembers() {
        authenticateWithReservationRead();
        AppUser memberOne = persistUser("service-staff-list-1@primatis.test");
        AppUser memberTwo = persistUser("service-staff-list-2@primatis.test");
        Reservation older = persistReservation(
                memberOne, persistTitle(), ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation newer = persistReservation(
                memberTwo, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listReservations(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "reservationDate", "id")));

        assertThat(page.getContent()).extracting(ReservationResponse::id)
                .contains(newer.getId(), older.getId());
        int newerIndex = indexOfReservationId(page.getContent(), newer.getId());
        int olderIndex = indexOfReservationId(page.getContent(), older.getId());
        assertThat(newerIndex).isLessThan(olderIndex);
    }

    @Test
    void listReservationsExposesBothMembersReservationsNotJustOne() {
        authenticateWithReservationRead();
        AppUser memberOne = persistUser("service-staff-multi-1@primatis.test");
        AppUser memberTwo = persistUser("service-staff-multi-2@primatis.test");
        Reservation reservationOne = persistReservation(memberOne, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Reservation reservationTwo = persistReservation(memberTwo, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listReservations(PageRequest.of(0, 100));

        assertThat(page.getContent()).extracting(ReservationResponse::id)
                .contains(reservationOne.getId(), reservationTwo.getId());
    }

    
    // listOwnReservations — self, ownership structurelle
    

    /**
     * Aucun {@code @PreAuthorize} sur {@code listOwnReservations} : fonctionne
     * même sans authentification RBAC (anonyme), l'ownership reposant
     * uniquement sur le {@code selfUserId} transmis par le Controller.
     */
    @Test
    void listOwnReservationsWorksWithoutReservationReadPermission() {
        authenticateAsAnonymous();
        AppUser self = persistUser("service-self-no-permission@primatis.test");
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listOwnReservations(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ReservationResponse::id).containsExactly(reservation.getId());
    }

    @Test
    void listOwnReservationsIsolatesReservationsBetweenUsers() {
        AppUser self = persistUser("service-self-isolation-1@primatis.test");
        AppUser other = persistUser("service-self-isolation-2@primatis.test");
        Reservation selfReservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(other, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listOwnReservations(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ReservationResponse::id).containsExactly(selfReservation.getId());
        assertThat(page.getContent()).allSatisfy(
                response -> assertThat(response.member().id()).isEqualTo(self.getId()));
    }

    @Test
    void listOwnReservationsReturnsEmptyPageRatherThanErrorWhenNoReservation() {
        AppUser self = persistUser("service-self-empty@primatis.test");
        entityManager.flush();

        Page<ReservationResponse> page = reservationService.listOwnReservations(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    
    
    

    @Test
    void createOwnReservationCreatesWaitingReservationForTheAuthenticatedUser() {
        AppUser self = persistMember("service-create-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(response.member().id()).isEqualTo(self.getId());
        assertThat(response.title().id()).isEqualTo(title.getId());
        assertThat(response.assignedCopy()).isNull();
        assertThat(response.expirationDate()).isNull();
        assertThat(response.fulfilledByLoanId()).isNull();

        
        // origine Reservation, UNREAD.
        Notification notification = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.reservation.id = :id", Notification.class)
                .setParameter("id", response.id())
                .getSingleResult();
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RESERVATION_CREATED);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getRecipientUser().getId()).isEqualTo(self.getId());
        assertThat(notification.getReservation().getId()).isEqualTo(response.id());
    }

    @Test
    void createReservationCreatesWaitingReservationForTheDesignatedMember() {
        authenticateWithReservationManage();
        AppUser member = persistMember("service-create-staff@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response = reservationService.createReservation(
                new CreateReservationRequest(member.getId(), title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(response.member().id()).isEqualTo(member.getId());
        assertThat(response.assignedCopy()).isNull();
        assertThat(response.expirationDate()).isNull();
        assertThat(response.fulfilledByLoanId()).isNull();
    }

    @Test
    void createReservationDeniedWithoutReservationManagePermission() {
        authenticateAsAnonymous();
        AppUser member = persistMember("service-create-noperm@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        assertThrows(AccessDeniedException.class, () -> reservationService.createReservation(
                new CreateReservationRequest(member.getId(), title.getId())));
    }

    @Test
    void createOwnReservationWorksWithoutAnyPermission() {
        authenticateAsAnonymous();
        AppUser self = persistMember("service-create-self-noperm@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationSetsReservationDateCreatedAtAndUpdatedAtFromTheInjectedClock() {
        AppUser self = persistMember("service-create-timestamps@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();
        Instant before = clock.instant();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        Instant after = clock.instant();
        assertThat(response.reservationDate()).isBetween(before, after);
        assertThat(response.createdAt()).isEqualTo(response.reservationDate());
        assertThat(response.updatedAt()).isEqualTo(response.reservationDate());
    }

    
    
    

    @Test
    void createReservationRejectsNonMember() {
        AppUser nonMember = persistUser("service-create-nonmember@primatis.test");
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("NOT_A_MEMBER", () ->
                reservationService.createOwnReservation(nonMember.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationRejectsBlockedMember() {
        AppUser blocked = persistMember("service-create-blocked@primatis.test", MemberStatus.BLOCKED, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_BLOCKED", () ->
                reservationService.createOwnReservation(blocked.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationRejectsAlreadyExpiredMember() {
        AppUser expired = persistMember("service-create-expired@primatis.test", MemberStatus.EXPIRED, LocalDate.now(clock).minusDays(1));
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_EXPIRED", () ->
                reservationService.createOwnReservation(expired.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    /**
     * {@code memberStatus} vaut encore {@code ACTIVE} en base mais
     * {@code memberExpirationDate} est dans le passé : {@code
     * MemberExpirationPolicy.syncIfNeeded} doit transitionner vers {@code
     * EXPIRED} avant le refus — même précédent exact que
     * {@code LoanService.requireEligibleBorrower}.
     */
    @Test
    void createReservationSynchronizesExpiredMembershipBeforeRejecting() {
        AppUser staleActive = persistMember(
                "service-create-stale-active@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        Title title = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("MEMBER_EXPIRED", () -> reservationService.createOwnReservation(
                staleActive.getId(), new CreateOwnReservationRequest(title.getId())));

        AppUser reloaded = entityManager.find(AppUser.class, staleActive.getId());
        assertThat(reloaded.getMemberStatus()).isEqualTo(MemberStatus.EXPIRED);
    }

    
    @Test
    void createReservationAllowedDespiteMemberHavingAnUnpaidFine() {
        AppUser self = persistMember("service-create-unpaid-fine@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title loanTitle = persistTitle();
        Copy loanCopy = persistCopy(loanTitle, "SERVICE-CREATE-FINE-LOAN-COPY");
        loanCopy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(self, loanCopy);
        persistFine(loan, FineStatus.UNPAID);
        Title reservationTitle = persistTitle();
        entityManager.flush();

        ReservationResponse response = reservationService.createOwnReservation(
                self.getId(), new CreateOwnReservationRequest(reservationTitle.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    
    
    

    @Test
    void createReservationRejectsUnknownTitle() {
        AppUser self = persistMember("service-create-unknown-title@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        entityManager.flush();

        ResourceNotFoundException exception = (ResourceNotFoundException) assertThrows(
                ResourceNotFoundException.class, () -> reservationService.createOwnReservation(
                        self.getId(), new CreateOwnReservationRequest(-1L)));
        assertThat(exception.getCode()).isEqualTo("TITLE_NOT_FOUND");
    }

    
    
    

    @Test
    void createReservationRejectsWhenAnAvailableCopyExistsForTheTitle() {
        AppUser self = persistMember("service-create-copy-available@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-COPY-AVAILABLE-1");
        copy.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_COPY_AVAILABLE", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationAllowedWhenNoCopyIsAvailableForTheTitle() {
        AppUser self = persistMember("service-create-copy-onloan@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-COPY-ONLOAN-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationAllowedWhenTitleHasNoCopyAtAll() {
        AppUser self = persistMember("service-create-copy-none@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    
    
    

    @Test
    void createReservationRejectsWhenAWaitingReservationIsAlreadyActiveForTheSameTitle() {
        AppUser self = persistMember("service-create-dup-waiting@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        persistReservation(self, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_ALREADY_ACTIVE", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationRejectsWhenAReadyReservationIsAlreadyActiveForTheSameTitle() {
        AppUser self = persistMember("service-create-dup-ready@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-DUP-READY-1");
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        persistReadyReservation(self, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_ALREADY_ACTIVE", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId())));
    }

    @Test
    void createReservationAllowedWhenOnlyAFulfilledReservationExistsForTheSameTitle() {
        AppUser self = persistMember("service-create-terminal-fulfilled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-TERMINAL-FULFILLED-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(self, copy);
        persistFulfilledReservation(self, title, copy, loan, Instant.now());
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationAllowedWhenOnlyACancelledReservationExistsForTheSameTitle() {
        AppUser self = persistMember("service-create-terminal-cancelled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        persistReservation(self, title, ReservationStatus.CANCELLED, Instant.now());
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationAllowedWhenOnlyAnExpiredReservationExistsForTheSameTitle() {
        AppUser self = persistMember("service-create-terminal-expired@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        persistReservation(self, title, ReservationStatus.EXPIRED, Instant.now());
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    
    @Test
    void createReservationAllowedWhenMemberHasAnActiveLoanOnTheSameTitle() {
        AppUser self = persistMember("service-create-active-loan@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CREATE-ACTIVE-LOAN-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        persistLoan(self, copy);
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(title.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    
    
    

    @Test
    void createReservationAllowedWhenUnderTheActiveLimit() {
        AppUser self = persistMember("service-create-under-limit@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(2);
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    @Test
    void createReservationRejectsWhenTheActiveLimitIsReached() {
        AppUser self = persistMember("service-create-limit-reached@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(2);
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_LIMIT_REACHED", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId())));
    }

    /**
     * Confirme que la limite est réellement lue dynamiquement depuis
     * {@code application_setting} et non codée en dur : la valeur bootstrap
     * (10) est modifiée en base pour ce test, jamais supposée fixe.
     */
    @Test
    void createReservationLimitIsReadDynamicallyFromApplicationSetting() {
        AppUser self = persistMember("service-create-limit-dynamic@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(1);
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_LIMIT_REACHED", () ->
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId())));
    }

    @Test
    void createReservationLimitDoesNotCountTerminalReservations() {
        AppUser self = persistMember("service-create-limit-terminal@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        updateMaxActiveReservationsSetting(1);
        persistReservation(self, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        persistReservation(self, persistTitle(), ReservationStatus.EXPIRED, Instant.now());
        Title newTitle = persistTitle();
        entityManager.flush();

        ReservationResponse response =
                reservationService.createOwnReservation(self.getId(), new CreateOwnReservationRequest(newTitle.getId()));

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
    }

    private static void assertBusinessRuleCode(String expectedCode, Executable executable) {
        BusinessRuleException exception = (BusinessRuleException) assertThrows(BusinessRuleException.class, executable);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
    }

    private void updateMaxActiveReservationsSetting(int value) {
        entityManager.createQuery("UPDATE ApplicationSetting s SET s.settingValue = :value WHERE s.settingKey = :key")
                .setParameter("value", String.valueOf(value))
                .setParameter("key", "MAX_ACTIVE_RESERVATIONS_PER_MEMBER")
                .executeUpdate();
    }

    private void updateReservationReadyHoldHoursSetting(int value) {
        entityManager.createQuery("UPDATE ApplicationSetting s SET s.settingValue = :value WHERE s.settingKey = :key")
                .setParameter("value", String.valueOf(value))
                .setParameter("key", "RESERVATION_READY_HOLD_HOURS")
                .executeUpdate();
    }

    private static void assertResourceNotFoundCode(String expectedCode, Executable executable) {
        ResourceNotFoundException exception =
                (ResourceNotFoundException) assertThrows(ResourceNotFoundException.class, executable);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
    }

    
    
    

    @Test
    void cancelOwnReservationCancelsAWaitingReservationOwnedBySelf() {
        AppUser self = persistMember("service-cancel-waiting-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        ReservationResponse response = reservationService.cancelOwnReservation(self.getId(), reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.assignedCopy()).isNull();

        
        // (aucun Copy impliqué pour une WAITING).
        Notification notification = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.reservation.id = :id", Notification.class)
                .setParameter("id", reservation.getId())
                .getSingleResult();
        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getRecipientUser().getId()).isEqualTo(self.getId());
    }

    @Test
    void cancelReservationCancelsAWaitingReservationAsStaff() {
        authenticateWithReservationManage();
        AppUser member = persistMember("service-cancel-waiting-staff@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(member, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        ReservationResponse response = reservationService.cancelReservation(reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelOwnReservationRejectsAnotherMembersWaitingReservation() {
        AppUser self = persistMember("service-cancel-other-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser other = persistMember("service-cancel-other-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(other, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertResourceNotFoundCode("RESERVATION_NOT_FOUND",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelOwnReservationOnUnknownReservationReturnsNotFound() {
        AppUser self = persistMember("service-cancel-unknown@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        entityManager.flush();

        assertResourceNotFoundCode("RESERVATION_NOT_FOUND",
                () -> reservationService.cancelOwnReservation(self.getId(), 999999999L));
    }

    @Test
    void cancelOwnReservationRejectsAnAlreadyCancelledReservation() {
        AppUser self = persistMember("service-cancel-already-cancelled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.CANCELLED, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_NOT_CANCELLABLE",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelOwnReservationRejectsAnExpiredReservation() {
        AppUser self = persistMember("service-cancel-expired-reservation@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(self, persistTitle(), ReservationStatus.EXPIRED, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_NOT_CANCELLABLE",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelOwnReservationRejectsAFulfilledReservation() {
        AppUser self = persistMember("service-cancel-fulfilled@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FULFILLED-1");
        copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(self, copy);
        Reservation reservation = persistFulfilledReservation(self, title, copy, loan, Instant.now());
        entityManager.flush();

        assertBusinessRuleCode("RESERVATION_NOT_CANCELLABLE",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelReservationDeniedWithoutReservationManagePermission() {
        authenticateAsAnonymous();
        AppUser member = persistMember("service-cancel-staff-noperm@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Reservation reservation = persistReservation(member, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        assertThrows(AccessDeniedException.class, () -> reservationService.cancelReservation(reservation.getId()));
    }

    
    
    

    @Test
    void cancelOwnReservationCancelsAReadyReservationPreservingAssignedCopyAndExpirationDate() {
        AppUser self = persistMember("service-cancel-ready-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-1");
        Instant expirationDate = Instant.now().plusSeconds(3600);
        Reservation reservation = persistReadyReservation(self, title, copy, Instant.now(), expirationDate);
        entityManager.flush();

        ReservationResponse response = reservationService.cancelOwnReservation(self.getId(), reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.assignedCopy()).isNotNull();
        assertThat(response.assignedCopy().id()).isEqualTo(copy.getId());
        assertThat(response.expirationDate()).isEqualTo(expirationDate);
    }

    @Test
    void cancelReservationCancelsAReadyReservationAsStaff() {
        authenticateWithReservationManage();
        AppUser member = persistMember("service-cancel-ready-staff@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-STAFF-1");
        Reservation reservation = persistReadyReservation(member, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        ReservationResponse response = reservationService.cancelReservation(reservation.getId());

        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelOwnReservationRejectsAnotherMembersReadyReservation() {
        AppUser self = persistMember("service-cancel-ready-other-self@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser other = persistMember("service-cancel-ready-other-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-OTHER-1");
        Reservation reservation = persistReadyReservation(other, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        assertResourceNotFoundCode("RESERVATION_NOT_FOUND",
                () -> reservationService.cancelOwnReservation(self.getId(), reservation.getId()));
    }

    @Test
    void cancelReadyReservationSetsCopyAvailableWhenNoWaitingReservationExists() {
        AppUser self = persistMember("service-cancel-ready-available@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-AVAILABLE-1");
        Reservation reservation = persistReadyReservation(self, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();

        reservationService.cancelOwnReservation(self.getId(), reservation.getId());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void cancelReadyReservationPromotesNextAdmissibleWaitingReservationAndKeepsCopyReserved() {
        AppUser readyOwner = persistMember("service-cancel-ready-promote-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser nextMember = persistMember("service-cancel-ready-promote-next@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-PROMOTE-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation waitingReservation = persistReservation(nextMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedWaiting.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(reloadedWaiting.getExpirationDate()).isNotNull();

        
        // RESERVATION_CANCELLED (propriétaire de la READY annulée) et
        // RESERVATION_READY (bénéficiaire de la promotion FIFO suivante), toutes
        // deux persistées, UNREAD, recipients corrects.
        List<Notification> notifications = entityManager
                .createQuery("SELECT n FROM Notification n WHERE n.reservation.id IN :ids", Notification.class)
                .setParameter("ids", List.of(readyReservation.getId(), waitingReservation.getId()))
                .getResultList();
        assertThat(notifications).hasSize(2);
        assertThat(notifications).allMatch(n -> n.getNotificationStatus() == NotificationStatus.UNREAD);
        assertThat(notifications).filteredOn(n -> n.getNotificationType() == NotificationType.RESERVATION_CANCELLED)
                .extracting(n -> n.getRecipientUser().getId()).containsExactly(readyOwner.getId());
        assertThat(notifications).filteredOn(n -> n.getNotificationType() == NotificationType.RESERVATION_READY)
                .extracting(n -> n.getRecipientUser().getId()).containsExactly(nextMember.getId());
    }

    @Test
    void cancelReadyReservationReadsHoldHoursDynamicallyWhenPromotingNextReservation() {
        AppUser readyOwner = persistMember("service-cancel-ready-hold-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser nextMember = persistMember("service-cancel-ready-hold-next@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-READY-HOLD-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation waitingReservation = persistReservation(nextMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        updateReservationReadyHoldHoursSetting(1);
        entityManager.flush();
        Instant before = clock.instant();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Instant after = clock.instant();
        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getExpirationDate()).isBetween(before.plusSeconds(3600), after.plusSeconds(3600));
    }

    
    
    

    @Test
    void cancelReadyReservationSkipsABlockedFirstCandidateAndPromotesTheNextOne() {
        AppUser readyOwner = persistMember("service-cancel-fifo-blocked-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser blockedMember = persistMember("service-cancel-fifo-blocked-first@primatis.test", MemberStatus.BLOCKED, LocalDate.now(clock).plusYears(1));
        AppUser admissibleMember = persistMember("service-cancel-fifo-blocked-second@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-BLOCKED-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation blockedWaiting = persistReservation(blockedMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedBlocked = entityManager.find(Reservation.class, blockedWaiting.getId());
        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedBlocked.getReservationStatus())
                .as("le candidat non admissible n'est jamais modifié, jamais bloquant")
                .isEqualTo(ReservationStatus.WAITING);
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reloadedAdmissible.getAssignedCopy().getId()).isEqualTo(copy.getId());
        assertThat(copy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
    }

    @Test
    void cancelReadyReservationSkipsAnExpiredMembershipFirstCandidate() {
        AppUser readyOwner = persistMember("service-cancel-fifo-expired-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser expiredMember = persistMember("service-cancel-fifo-expired-first@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        AppUser admissibleMember = persistMember("service-cancel-fifo-expired-second@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-EXPIRED-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation expiredWaiting = persistReservation(expiredMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedExpired = entityManager.find(Reservation.class, expiredWaiting.getId());
        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedExpired.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
        // Synchronisation paresseuse déclenchée par la revalidation d'admissibilité :
        // le membre expiré est désormais MemberStatus.EXPIRED en base.
        AppUser reloadedExpiredMember = entityManager.find(AppUser.class, expiredMember.getId());
        assertThat(reloadedExpiredMember.getMemberStatus()).isEqualTo(MemberStatus.EXPIRED);
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    @Test
    void cancelReadyReservationSkipsSeveralNonAdmissibleCandidatesBeforeAdmissibleOne() {
        AppUser readyOwner = persistMember("service-cancel-fifo-multi-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser blockedMember = persistMember("service-cancel-fifo-multi-blocked@primatis.test", MemberStatus.BLOCKED, LocalDate.now(clock).plusYears(1));
        AppUser expiredMember = persistMember("service-cancel-fifo-multi-expired@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).minusDays(1));
        AppUser admissibleMember = persistMember("service-cancel-fifo-multi-admissible@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-MULTI-1");
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        persistReservation(blockedMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(180));
        persistReservation(expiredMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(120));
        Reservation admissibleWaiting = persistReservation(admissibleMember, title, ReservationStatus.WAITING, Instant.now().minusSeconds(60));
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedAdmissible = entityManager.find(Reservation.class, admissibleWaiting.getId());
        assertThat(reloadedAdmissible.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    
    @Test
    void cancelReadyReservationPromotesACandidateDespiteAnUnpaidFine() {
        AppUser readyOwner = persistMember("service-cancel-fifo-fine-owner@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        AppUser candidateWithFine = persistMember("service-cancel-fifo-fine-candidate@primatis.test", MemberStatus.ACTIVE, LocalDate.now(clock).plusYears(1));
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-CANCEL-FIFO-FINE-1");
        Copy otherCopy = persistCopy(persistTitle(), "SERVICE-CANCEL-FIFO-FINE-LOAN-1");
        otherCopy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
        Loan loan = persistLoan(candidateWithFine, otherCopy);
        persistFine(loan, FineStatus.UNPAID);
        Reservation readyReservation = persistReadyReservation(readyOwner, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        Reservation waitingReservation = persistReservation(candidateWithFine, title, ReservationStatus.WAITING, Instant.now());
        entityManager.flush();

        reservationService.cancelOwnReservation(readyOwner.getId(), readyReservation.getId());

        Reservation reloadedWaiting = entityManager.find(Reservation.class, waitingReservation.getId());
        assertThat(reloadedWaiting.getReservationStatus()).isEqualTo(ReservationStatus.READY);
    }

    
    
    
    // user/title (LAZY) et conditionnellement à assignedCopy (LAZY) :
    // vérifie que le @EntityGraph de ReservationRepository.findAll/
    // findByUserId charge bien ces relations en une seule requête (déjà
    // initialisées au retour, sans accès supplémentaire déclencheur).
    

    @Test
    void listReservationsEagerlyInitializesUserTitleAndAssignedCopyButNotFulfilledByLoan() {
        authenticateWithReservationRead();
        AppUser member = persistUser("service-entitygraph-staff@primatis.test");
        Title title = persistTitle();
        Copy copy = persistCopy(title, "SERVICE-ENTITYGRAPH-1");
        persistReadyReservation(member, title, copy, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.flush();
        entityManager.clear();

        Page<Reservation> page = reservationRepository.findAll(PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
        for (Reservation reservation : page.getContent()) {
            assertThat(Hibernate.isInitialized(reservation.getUser()))
                    .as("user doit être chargé par l'EntityGraph, pas paresseusement")
                    .isTrue();
            assertThat(Hibernate.isInitialized(reservation.getTitle()))
                    .as("title doit être chargé par l'EntityGraph, pas paresseusement")
                    .isTrue();
            if (reservation.getAssignedCopy() != null) {
                assertThat(Hibernate.isInitialized(reservation.getAssignedCopy()))
                        .as("assignedCopy doit être chargé par l'EntityGraph quand non-null")
                        .isTrue();
            }
        }
    }

    @Test
    void listOwnReservationsEagerlyInitializesUserAndTitle() {
        AppUser self = persistUser("service-entitygraph-self@primatis.test");
        persistReservation(self, persistTitle(), ReservationStatus.WAITING, Instant.now());
        entityManager.flush();
        entityManager.clear();

        Page<Reservation> page = reservationRepository.findByUserId(self.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
        for (Reservation reservation : page.getContent()) {
            assertThat(Hibernate.isInitialized(reservation.getUser())).isTrue();
            assertThat(Hibernate.isInitialized(reservation.getTitle())).isTrue();
        }
    }

    
    // Confirmation structurelle N+1 (même précédent que
    // LoanServiceTests.listLoansMethodBodyNeverReferencesAnAdditionalRepository,
    
    // de Repository/requête supplémentaire pour compenser un N+1 — la
    // compensation passe exclusivement par le @EntityGraph du Repository
    // (confirmé ci-dessus), jamais par une Repository additionnelle dans
    // le Service.
    

    @Test
    void listReservationsMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/reservation/ReservationService.java",
                "public Page<ReservationResponse> listReservations(Pageable pageable) {");

        assertThat(methodBody)
                .as("listReservations se limite à reservationRepository.findAll : aucune Repository additionnelle")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("titleRepository")
                .doesNotContain("loanRepository")
                .doesNotContain("applicationSettingService")
                .contains("reservationRepository.findAll(pageable)");
    }

    @Test
    void listOwnReservationsMethodBodyNeverReferencesAnAdditionalRepository() throws IOException {
        String methodBody = extractMethodBody(
                "src/main/java/be/primatis/reservation/ReservationService.java",
                "public Page<ReservationResponse> listOwnReservations(Long selfUserId, Pageable pageable) {");

        assertThat(methodBody)
                .as("listOwnReservations se limite à reservationRepository.findByUserId : "
                        + "aucune Repository additionnelle")
                .doesNotContain("appUserRepository")
                .doesNotContain("copyRepository")
                .doesNotContain("titleRepository")
                .doesNotContain("loanRepository")
                .doesNotContain("applicationSettingService")
                .contains("reservationRepository.findByUserId(selfUserId, pageable)");
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

    private static int indexOfReservationId(List<ReservationResponse> content, Long reservationId) {
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).id().equals(reservationId)) {
                return i;
            }
        }
        throw new IllegalStateException("Reservation " + reservationId + " absente du contenu de la page.");
    }

    
    // Fixtures minimales
    

    
    
    

    private static final Sort RES_SORT = Sort.by(Sort.Direction.DESC, "reservationDate", "id");
    private static final Instant SEP_1 = LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

    private AppUser persistMember(String email, String firstName, String lastName, String memberNumber) {
        AppUser user = persistUser(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setMemberNumber(memberNumber);
        return user;
    }

    private Title persistTitleNamed(String name) {
        Title title = persistTitle();
        title.setTitle(name);
        return title;
    }

    private List<Long> resIds(Page<ReservationResponse> page) {
        return page.getContent().stream().map(ReservationResponse::id).toList();
    }

    private Page<ReservationResponse> searchRes(String q, ReservationStatus status, LocalDate rFrom, LocalDate rTo,
            LocalDate eFrom, LocalDate eTo) {
        return reservationService.listReservations(PageRequest.of(0, 100, RES_SORT), q, status, rFrom, rTo, eFrom, eTo);
    }

    @Test
    void listReservationsSearchMatchesMemberNumberNamesAndTitle() {
        authenticateWithReservationRead();
        AppUser target = persistMember("f07-q1@primatis.test", "Fixprenom", "Fixnomunique", "M996000001");
        AppUser other = persistMember("f07-q2@primatis.test", "Autre", "Personne", "M996000002");
        Title asimov = persistTitleNamed("Fondation Fixasimov");
        Title dune = persistTitleNamed("Dune");
        Reservation byMember = persistReservation(target, dune, ReservationStatus.WAITING, SEP_1);
        Reservation byTitle = persistReservation(other, asimov, ReservationStatus.WAITING, SEP_1.plusSeconds(1));
        entityManager.flush();

        for (String q : List.of("fixprenom", "FIXNOMUNIQUE", "  Fixprenom Fixnomunique ", "fixnomunique fixprenom",
                "M996000001", "m996000001", "nomuniq")) {
            assertThat(resIds(searchRes(q, null, null, null, null, null))).as(q).containsExactly(byMember.getId());
        }
        assertThat(resIds(searchRes("  fixasimov ", null, null, null, null, null))).containsExactly(byTitle.getId());
        assertThat(searchRes("zzzxyzzy-f07", null, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void listReservationsSearchTreatsLikeWildcardsAsLiterals() {
        authenticateWithReservationRead();
        persistReservation(persistMember("f07-wild@primatis.test", "Wildcard", "Nom", "M996000003"),
                persistTitleNamed("Titre Wildtitle"), ReservationStatus.WAITING, SEP_1);
        entityManager.flush();

        assertThat(searchRes("%", null, null, null, null, null).getContent())
                .extracting(r -> r.member().lastName()).doesNotContain("Nom");
        assertThat(searchRes("Wild_ard", null, null, null, null, null).getTotalElements()).isZero();
        assertThat(searchRes("Wild_title", null, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void listReservationsFiltersByEachStatus() {
        authenticateWithReservationRead();
        AppUser user = persistMember("f07-st@primatis.test", "Fixstatus", "Membre", "M996000004");
        Title title = persistTitle();
        Instant exp = SEP_1.plusSeconds(86400);
        Reservation waiting = persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1);
        Reservation ready = persistReadyReservation(user, persistTitle(), persistCopy(persistTitle(), "F07-ST-R"), SEP_1, exp);
        Reservation cancelled = persistReservation(user, persistTitle(), ReservationStatus.CANCELLED, SEP_1);
        Reservation expired = persistReservation(user, persistTitle(), ReservationStatus.EXPIRED, SEP_1);
        Copy copy = persistCopy(title, "F07-ST-F");
        Reservation fulfilled = persistFulfilledReservation(user, title, copy, persistLoan(user, copy), SEP_1);
        entityManager.flush();

        assertThat(resIds(searchRes("Fixstatus", ReservationStatus.WAITING, null, null, null, null))).containsExactly(waiting.getId());
        assertThat(resIds(searchRes("Fixstatus", ReservationStatus.READY, null, null, null, null))).containsExactly(ready.getId());
        assertThat(resIds(searchRes("Fixstatus", ReservationStatus.FULFILLED, null, null, null, null))).containsExactly(fulfilled.getId());
        assertThat(resIds(searchRes("Fixstatus", ReservationStatus.CANCELLED, null, null, null, null))).containsExactly(cancelled.getId());
        assertThat(resIds(searchRes("Fixstatus", ReservationStatus.EXPIRED, null, null, null, null))).containsExactly(expired.getId());
        assertThat(searchRes("Fixstatus", null, null, null, null, null).getTotalElements()).isEqualTo(5);
    }

    /** Bornes inclusives sur des Instants UTC : 00:00:00Z du jour « from », 23:59:59Z du jour « to ». */
    @Test
    void listReservationsReservationDateRangeIsInclusiveOnUtcDays() {
        authenticateWithReservationRead();
        AppUser user = persistMember("f07-rd@primatis.test", "Fixresdate", "Membre", "M996000005");
        Reservation aug31 = persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1.minusSeconds(1));
        Reservation sep1 = persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1);
        Reservation sep2Late = persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1.plusSeconds(2 * 86400 - 1));
        Reservation sep3 = persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1.plusSeconds(2 * 86400));
        entityManager.flush();
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 2);

        assertThat(resIds(searchRes("Fixresdate", null, d1, null, null, null)))
                .containsExactlyInAnyOrder(sep1.getId(), sep2Late.getId(), sep3.getId());
        assertThat(resIds(searchRes("Fixresdate", null, null, d2, null, null)))
                .containsExactlyInAnyOrder(aug31.getId(), sep1.getId(), sep2Late.getId());
        assertThat(resIds(searchRes("Fixresdate", null, d1, d2, null, null)))
                .containsExactlyInAnyOrder(sep1.getId(), sep2Late.getId());
        assertThat(resIds(searchRes("Fixresdate", null, d1, d1, null, null))).containsExactly(sep1.getId());
    }

    @Test
    void listReservationsExpirationRangeIsInclusiveAndExcludesNull() {
        authenticateWithReservationRead();
        AppUser user = persistMember("f07-ex@primatis.test", "Fixexpire", "Membre", "M996000006");
        Instant day20 = LocalDate.of(2026, 9, 20).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Reservation before = persistReadyReservation(user, persistTitle(), persistCopy(persistTitle(), "F07-EX-1"), SEP_1, day20.minusSeconds(1));
        Reservation start = persistReadyReservation(user, persistTitle(), persistCopy(persistTitle(), "F07-EX-2"), SEP_1, day20);
        Reservation endOfDay = persistReadyReservation(user, persistTitle(), persistCopy(persistTitle(), "F07-EX-3"), SEP_1, day20.plusSeconds(86400 - 1));
        Reservation next = persistReadyReservation(user, persistTitle(), persistCopy(persistTitle(), "F07-EX-4"), SEP_1, day20.plusSeconds(86400));
        Reservation noExpiration = persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1);
        entityManager.flush();
        LocalDate d20 = LocalDate.of(2026, 9, 20);

        assertThat(resIds(searchRes("Fixexpire", null, null, null, d20, null)))
                .containsExactlyInAnyOrder(start.getId(), endOfDay.getId(), next.getId());
        assertThat(resIds(searchRes("Fixexpire", null, null, null, null, d20)))
                .containsExactlyInAnyOrder(before.getId(), start.getId(), endOfDay.getId());
        assertThat(resIds(searchRes("Fixexpire", null, null, null, d20, d20)))
                .containsExactlyInAnyOrder(start.getId(), endOfDay.getId());
        // aucune borne : la réservation sans expiration reste visible ; avec une borne : jamais
        assertThat(resIds(searchRes("Fixexpire", null, null, null, null, null))).contains(noExpiration.getId());
        assertThat(resIds(searchRes("Fixexpire", null, null, null, d20.minusYears(10), d20.plusYears(10))))
                .doesNotContain(noExpiration.getId());
    }

    @Test
    void listReservationsRejectsInvertedRanges() {
        authenticateWithReservationRead();
        LocalDate later = LocalDate.of(2026, 9, 2);
        LocalDate earlier = LocalDate.of(2026, 9, 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> searchRes(null, null, later, earlier, null, null))
                .isInstanceOf(be.primatis.exception.InvalidDateRangeException.class)
                .extracting("parameterName").isEqualTo("reservationDateFrom");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> searchRes(null, null, null, null, later, earlier))
                .isInstanceOf(be.primatis.exception.InvalidDateRangeException.class)
                .extracting("parameterName").isEqualTo("expirationDateFrom");
        assertThat(searchRes("zzzxyzzy-f07", null, earlier, earlier, earlier, earlier).getTotalElements()).isZero();
    }

    @Test
    void listReservationsCombinesAllCriteriaWithAnd() {
        authenticateWithReservationRead();
        AppUser match = persistMember("f07-c1@primatis.test", "Fixcombo", "Cible", "M996000007");
        AppUser otherUser = persistMember("f07-c2@primatis.test", "Fixcombo", "Autre", "M996000008");
        Instant inDay = SEP_1.plusSeconds(3600);
        Instant exp = LocalDate.of(2026, 9, 22).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Reservation hit = persistReadyReservation(match, persistTitle(), persistCopy(persistTitle(), "F07-C-HIT"), inDay, exp);
        persistReservation(match, persistTitle(), ReservationStatus.WAITING, inDay);
        persistReadyReservation(match, persistTitle(), persistCopy(persistTitle(), "F07-C-DATE"), SEP_1.minusSeconds(3600), exp);
        persistReadyReservation(match, persistTitle(), persistCopy(persistTitle(), "F07-C-EXP"), inDay, exp.plusSeconds(10 * 86400));
        persistReadyReservation(otherUser, persistTitle(), persistCopy(persistTitle(), "F07-C-USER"), inDay, exp);
        entityManager.flush();

        Page<ReservationResponse> page = searchRes("cible", ReservationStatus.READY,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25));

        assertThat(resIds(page)).containsExactly(hit.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(resIds(searchRes("cible", ReservationStatus.READY, null, null, null, null))).hasSize(3);
        assertThat(resIds(searchRes("fixcombo", null, LocalDate.of(2026, 9, 1), null, null, null))).hasSize(4);
    }

    @Test
    void listReservationsFilteredTotalAndPagesAreComputedAfterFiltering() {
        authenticateWithReservationRead();
        AppUser user = persistMember("f07-pg@primatis.test", "Fixpaging", "Membre", "M996000009");
        for (int i = 1; i <= 5; i++) {
            persistReservation(user, persistTitle(), ReservationStatus.CANCELLED, SEP_1.plusSeconds(i));
        }
        persistReservation(user, persistTitle(), ReservationStatus.WAITING, SEP_1);
        entityManager.flush();

        Page<ReservationResponse> first = reservationService.listReservations(PageRequest.of(0, 2, RES_SORT), "Fixpaging",
                ReservationStatus.CANCELLED, null, null, null, null);
        Page<ReservationResponse> third = reservationService.listReservations(PageRequest.of(2, 2, RES_SORT), "Fixpaging",
                ReservationStatus.CANCELLED, null, null, null, null);

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(third.getContent()).hasSize(1);
    }

    /** Le graphe user/title/assignedCopy doit rester appliqué à la variante Specification (sinon N+1). */
    @Test
    void listReservationsFilteredDoesNotIntroduceNPlusOne() {
        authenticateWithReservationRead();
        AppUser user = persistMember("f07-n1@primatis.test", "Fixnplus", "Membre", "M996000010");
        Instant exp = SEP_1.plusSeconds(86400);
        for (int i = 1; i <= 8; i++) {
            persistReadyReservation(user, persistTitleNamed("Titre nplus " + i),
                    persistCopy(persistTitle(), "F07-N1-" + i), SEP_1.plusSeconds(i), exp);
        }
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.unwrap(org.hibernate.Session.class).getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Page<ReservationResponse> page = searchRes("Fixnplus", ReservationStatus.READY, null, null, null, null);
        page.getContent().forEach(r -> {
            assertThat(r.member().lastName()).isEqualTo("Membre");
            assertThat(r.title().title()).startsWith("Titre nplus");
            assertThat(r.assignedCopy().inventoryCode()).startsWith("F07-N1-");
        });

        assertThat(page.getContent()).hasSize(8);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2L);
        statistics.setStatisticsEnabled(false);
    }

    @Test
    void listReservationsWithFiltersDeniedWithoutReservationReadPermission() {
        authenticateAsAnonymous();

        assertThrows(AccessDeniedException.class,
                () -> searchRes("x", ReservationStatus.WAITING, null, null, null, null));
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
        copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
        copy.setCreatedAt(Instant.now());
        copy.setUpdatedAt(Instant.now());
        entityManager.persist(copy);
        return copy;
    }

    private Reservation persistReservation(AppUser user, Title title, ReservationStatus status, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(status);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistReadyReservation(
            AppUser user, Title title, Copy copy, Instant reservationDate, Instant expirationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.READY);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(expirationDate);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private Reservation persistFulfilledReservation(
            AppUser user, Title title, Copy copy, Loan loan, Instant reservationDate) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setTitle(title);
        reservation.setReservationDate(reservationDate);
        reservation.setReservationStatus(ReservationStatus.FULFILLED);
        reservation.setAssignedCopy(copy);
        reservation.setExpirationDate(reservationDate.plusSeconds(3600));
        reservation.setFulfilledByLoan(loan);
        reservation.setCreatedAt(Instant.now());
        reservation.setUpdatedAt(Instant.now());
        entityManager.persist(reservation);
        return reservation;
    }

    private AppUser persistMember(String email, MemberStatus memberStatus, LocalDate memberExpirationDate) {
        AppUser user = persistUser(email);
        user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
        user.setMemberStatus(memberStatus);
        user.setRegistrationDate(LocalDate.now(clock).minusYears(1));
        user.setMemberExpirationDate(memberExpirationDate);
        return user;
    }

    private Loan persistLoan(AppUser user, Copy copy) {
        Loan loan = new Loan();
        loan.setUser(user);
        loan.setCopy(copy);
        loan.setLoanDate(clock.instant());
        loan.setDueDate(LocalDate.now(clock).plusDays(21));
        loan.setLoanStatus(LoanStatus.ACTIVE);
        loan.setCreatedAt(clock.instant());
        loan.setUpdatedAt(clock.instant());
        entityManager.persist(loan);
        return loan;
    }

    private Fine persistFine(Loan loan, FineStatus status) {
        Fine fine = new Fine();
        fine.setLoan(loan);
        fine.setAmount(BigDecimal.TEN);
        fine.setReason("Retard de test");
        fine.setIssuedAt(clock.instant());
        fine.setFineStatus(status);
        entityManager.persist(fine);
        return fine;
    }
}
