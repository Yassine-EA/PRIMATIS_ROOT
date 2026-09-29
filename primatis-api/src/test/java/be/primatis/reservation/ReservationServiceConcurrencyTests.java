package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyCondition;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.catalogue.TitleStatus;
import be.primatis.exception.BusinessRuleException;
import be.primatis.loan.Loan;
import be.primatis.loan.LoanRepository;
import be.primatis.loan.LoanService;
import be.primatis.loan.LoanStatus;
import be.primatis.loan.dto.CreateLoanRequest;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceConcurrencyTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TitleRepository titleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private ReservationExpirationService reservationExpirationService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private static void authenticateWithLoanManage() {
        List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("LOAN_MANAGE"));
        Authentication authentication = new TestingAuthenticationToken("1", null, grantedAuthorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void concurrentReservationCreationForSameMemberAndTitleYieldsExactlyOneSuccessAndOneCleanConflict()
            throws InterruptedException, ExecutionException {
        String email = "reservation-concurrency@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long userId = transactionTemplate.execute(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        reservationService.createOwnReservation(userId, new CreateOwnReservationRequest(titleId));
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    }
                }));
            }

            ready.await();
            go.countDown();
            Map<String, Long> outcomeCounts = new HashMap<>();
            for (Future<String> future : futures) {
                outcomeCounts.merge(future.get(), 1L, Long::sum);
            }
            executor.shutdown();

            assertThat(outcomeCounts.getOrDefault("SUCCESS", 0L)).isEqualTo(1L);
            assertThat(outcomeCounts.getOrDefault("RESERVATION_ALREADY_ACTIVE", 0L)).isEqualTo(1L);

            List<Reservation> finalReservations = transactionTemplate.execute(status ->
                    reservationRepository.findByUserIdAndTitleIdAndReservationStatusIn(
                            userId, titleId, List.of(ReservationStatus.WAITING, ReservationStatus.READY)));
            assertThat(finalReservations).hasSize(1);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                
                // (fk_notification_reservation_id, ON DELETE RESTRICT) — supprimée avant
                // la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.user.id = :userId")
                        .setParameter("userId", userId).executeUpdate();
                reservationRepository.findByUserIdAndTitleIdAndReservationStatusIn(
                                userId, titleId, List.of(
                                        ReservationStatus.WAITING, ReservationStatus.READY,
                                        ReservationStatus.FULFILLED, ReservationStatus.CANCELLED,
                                        ReservationStatus.EXPIRED))
                        .forEach(reservationRepository::delete);
                reservationRepository.flush();
                appUserRepository.deleteById(userId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    
    @Test
    void concurrentCancellationOfTheSameReadyReservationYieldsExactlyOneSuccessAndOneCleanConflict()
            throws InterruptedException, ExecutionException {
        String email = "reservation-cancel-concurrency@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 2;

        Long userId = transactionTemplate.execute(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Cancel Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("CANCEL-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(userId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now());
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copyRepository.getReferenceById(copyId));
            reservation.setExpirationDate(Instant.now().plusSeconds(3600));
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        reservationService.cancelOwnReservation(userId, reservationId);
                        return "SUCCESS";
                    } catch (BusinessRuleException ex) {
                        return ex.getCode();
                    }
                }));
            }

            ready.await();
            go.countDown();
            Map<String, Long> outcomeCounts = new HashMap<>();
            for (Future<String> future : futures) {
                outcomeCounts.merge(future.get(), 1L, Long::sum);
            }
            executor.shutdown();

            assertThat(outcomeCounts.getOrDefault("SUCCESS", 0L)).isEqualTo(1L);
            assertThat(outcomeCounts.getOrDefault("RESERVATION_NOT_CANCELLABLE", 0L)).isEqualTo(1L);

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());
            assertThat(finalReservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                
                // RESERVATION_CANCELLED/RESERVATION_EXPIRED (fk_notification_reservation_id,
                // ON DELETE RESTRICT) — supprimée avant la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                appUserRepository.deleteById(userId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    
    @Test
    void concurrentExpirationAndCancellationOfTheSameReadyReservationYieldsExactlyOneTerminalTransition()
            throws InterruptedException, ExecutionException {
        String email = "reservation-expire-cancel-concurrency@primatis.test";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long userId = transactionTemplate.execute(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash("hash");
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
            user.setMemberStatus(MemberStatus.ACTIVE);
            user.setRegistrationDate(LocalDate.now().minusYears(1));
            user.setMemberExpirationDate(LocalDate.now().plusYears(1));
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
            return user.getId();
        });

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Expire/Cancel Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("EXPIRE-CANCEL-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Instant expiredExpirationDate = Instant.now().minusSeconds(3600);
        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(userId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(7200));
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copyRepository.getReferenceById(copyId));
            reservation.setExpirationDate(expiredExpirationDate);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Future<String> expireOutcome = executor.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    reservationExpirationService.expireReservationIfStillDue(reservationId, Instant.now());
                    return "COMPLETED";
                } catch (RuntimeException ex) {
                    return "ERROR:" + ex.getClass().getSimpleName();
                }
            });
            Future<String> cancelOutcome = executor.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    reservationService.cancelOwnReservation(userId, reservationId);
                    return "SUCCESS";
                } catch (BusinessRuleException ex) {
                    return ex.getCode();
                }
            });

            ready.await();
            go.countDown();
            String expireResult = expireOutcome.get();
            String cancelResult = cancelOutcome.get();
            executor.shutdown();

            assertThat(expireResult).isEqualTo("COMPLETED");

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());

            if (finalReservation.getReservationStatus() == ReservationStatus.CANCELLED) {
                assertThat(cancelResult).isEqualTo("SUCCESS");
            } else if (finalReservation.getReservationStatus() == ReservationStatus.EXPIRED) {
                assertThat(cancelResult).isEqualTo("RESERVATION_NOT_CANCELLABLE");
            } else {
                throw new AssertionError("Statut final inattendu : " + finalReservation.getReservationStatus());
            }

            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                
                // RESERVATION_CANCELLED/RESERVATION_EXPIRED (fk_notification_reservation_id,
                // ON DELETE RESTRICT) — supprimée avant la Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                appUserRepository.deleteById(userId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    
    @Test
    void concurrentExpirationAndFulfillmentOfTheSameReadyReservationNeverProducesBothTerminalStates()
            throws InterruptedException, ExecutionException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long beneficiaryId = transactionTemplate.execute(status -> persistMember("expire-fulfill-beneficiary").getId());
        Long waitingCandidateId = transactionTemplate.execute(status -> persistMember("expire-fulfill-candidate").getId());

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Expire/Fulfill Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("EXPIRE-FULFILL-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.RESERVED);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(beneficiaryId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(7200));
            reservation.setReservationStatus(ReservationStatus.READY);
            reservation.setAssignedCopy(copyRepository.getReferenceById(copyId));
            reservation.setExpirationDate(Instant.now().minusSeconds(3600));
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        Long waitingReservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(waitingCandidateId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(60));
            reservation.setReservationStatus(ReservationStatus.WAITING);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        try {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Future<String> expireOutcome = executor.submit(() -> {
                ready.countDown();
                go.await();
                reservationExpirationService.expireReservationIfStillDue(reservationId, Instant.now());
                return "COMPLETED";
            });
            Future<String> loanOutcome = executor.submit(() -> {
                authenticateWithLoanManage();
                ready.countDown();
                go.await();
                try {
                    loanService.registerLoan(new CreateLoanRequest(beneficiaryId, copyId));
                    return "SUCCESS";
                } catch (BusinessRuleException ex) {
                    return ex.getCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            ready.await();
            go.countDown();
            String expireResult = expireOutcome.get();
            String loanResult = loanOutcome.get();
            executor.shutdown();

            assertThat(expireResult).isEqualTo("COMPLETED");

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());
            Reservation finalWaitingReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(waitingReservationId).orElseThrow());
            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());

            if (finalReservation.getReservationStatus() == ReservationStatus.FULFILLED) {
                assertThat(loanResult).isEqualTo("SUCCESS");
                assertThat(finalReservation.getFulfilledByLoan()).isNotNull();
                assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_LOAN);
                assertThat(finalWaitingReservation.getReservationStatus()).isEqualTo(ReservationStatus.WAITING);
            } else if (finalReservation.getReservationStatus() == ReservationStatus.EXPIRED) {
                assertThat(loanResult).isEqualTo("COPY_RESERVED_FOR_ANOTHER_MEMBER");
                assertThat(finalCopy.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.RESERVED);
                assertThat(finalWaitingReservation.getReservationStatus()).isEqualTo(ReservationStatus.READY);
                assertThat(finalWaitingReservation.getAssignedCopy().getId()).isEqualTo(copyId);
            } else {
                throw new AssertionError("Statut final inattendu, invariant violé : "
                        + finalReservation.getReservationStatus());
            }
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                
                // RESERVATION_READY (fk_notification_reservation_id, ON DELETE RESTRICT) —
                // supprimées avant Loan/Reservation.
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id IN :ids")
                        .setParameter("ids", List.of(waitingReservationId, reservationId)).executeUpdate();
                loanRepository.findByCopyIdAndLoanStatusIn(
                                finalCopyId, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.RETURNED))
                        .forEach(loanRepository::delete);
                loanRepository.flush();
                reservationRepository.deleteById(waitingReservationId);
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                appUserRepository.deleteById(beneficiaryId);
                appUserRepository.deleteById(waitingCandidateId);
                titleRepository.deleteById(titleId);
            });
        }
    }

    
    @Test
    void cancelOfAWaitingReservationDiscoversItWasPromotedToReadyViaTheStaleSnapshotBranchAndCancelsIt()
            throws InterruptedException, ExecutionException, TimeoutException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Long userId = transactionTemplate.execute(status -> persistMember("stale-snapshot-canceller").getId());
        Long borrowerId = transactionTemplate.execute(status -> persistMember("stale-snapshot-borrower").getId());

        Long titleId = transactionTemplate.execute(status -> {
            Title title = new Title();
            title.setTitle("Reservation Stale Snapshot Concurrency Title");
            title.setLanguage(Language.FR);
            title.setTitleStatus(TitleStatus.ACTIVE);
            title.setCreatedAt(Instant.now());
            title.setUpdatedAt(Instant.now());
            titleRepository.save(title);
            return title.getId();
        });

        Long copyId = transactionTemplate.execute(status -> {
            Copy copy = new Copy();
            copy.setTitle(titleRepository.getReferenceById(titleId));
            copy.setInventoryCode("STALE-SNAPSHOT-CONCURRENCY-" + System.nanoTime());
            copy.setCopyCondition(CopyCondition.GOOD);
            copy.setAvailabilityStatus(AvailabilityStatus.ON_LOAN);
            copy.setCreatedAt(Instant.now());
            copy.setUpdatedAt(Instant.now());
            copyRepository.save(copy);
            return copy.getId();
        });

        Long loanId = transactionTemplate.execute(status -> {
            Loan loan = new Loan();
            loan.setUser(appUserRepository.getReferenceById(borrowerId));
            loan.setCopy(copyRepository.getReferenceById(copyId));
            loan.setLoanDate(Instant.now().minusSeconds(3600));
            loan.setDueDate(LocalDate.now().plusDays(21));
            loan.setLoanStatus(LoanStatus.ACTIVE);
            loan.setCreatedAt(Instant.now());
            loan.setUpdatedAt(Instant.now());
            loanRepository.save(loan);
            return loan.getId();
        });

        Long reservationId = transactionTemplate.execute(status -> {
            Reservation reservation = new Reservation();
            reservation.setUser(appUserRepository.getReferenceById(userId));
            reservation.setTitle(titleRepository.getReferenceById(titleId));
            reservation.setReservationDate(Instant.now().minusSeconds(60));
            reservation.setReservationStatus(ReservationStatus.WAITING);
            reservation.setCreatedAt(Instant.now());
            reservation.setUpdatedAt(Instant.now());
            reservationRepository.save(reservation);
            return reservation.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch blockerLocked = new CountDownLatch(1);
            CountDownLatch blockerRelease = new CountDownLatch(1);
            Future<?> blockerFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                reservationRepository.findByIdForUpdate(reservationId).orElseThrow();
                blockerLocked.countDown();
                try {
                    blockerRelease.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(blockerLocked.await(10, TimeUnit.SECONDS))
                    .as("le blocker doit réellement détenir le verrou Reservation avant la suite")
                    .isTrue();

            CountDownLatch returnDone = new CountDownLatch(1);
            List<String> returnOutcome = Collections.synchronizedList(new ArrayList<>());
            executor.submit(() -> {
                authenticateWithLoanManage();
                try {
                    loanService.registerReturn(loanId);
                    returnOutcome.add("SUCCESS");
                } catch (RuntimeException ex) {
                    returnOutcome.add(ex.getClass().getSimpleName() + ":" + ex.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                    returnDone.countDown();
                }
            });
            assertThat(returnDone.await(500, TimeUnit.MILLISECONDS))
                    .as("registerReturn doit être réellement bloqué sur le verrou Reservation "
                            + "(déjà en file d'attente, après Loan+Copy+FIFO) avant le démarrage de l'annulation")
                    .isFalse();

            CountDownLatch cancelDone = new CountDownLatch(1);
            List<String> cancelOutcome = Collections.synchronizedList(new ArrayList<>());
            executor.submit(() -> {
                try {
                    reservationService.cancelOwnReservation(userId, reservationId);
                    cancelOutcome.add("SUCCESS");
                } catch (RuntimeException ex) {
                    cancelOutcome.add(ex.getClass().getSimpleName() + ":" + ex.getMessage());
                } finally {
                    cancelDone.countDown();
                }
            });
            assertThat(cancelDone.await(500, TimeUnit.MILLISECONDS))
                    .as("cancelOwnReservation doit lui aussi être réellement bloqué sur le verrou Reservation, "
                            + "en file derrière registerReturn — preuve que son snapshot non verrouillé a bien "
                            + "été lu avant toute promotion")
                    .isFalse();

            blockerRelease.countDown();
            blockerFuture.get(10, TimeUnit.SECONDS);

            assertThat(returnDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cancelDone.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            assertThat(returnOutcome).containsExactly("SUCCESS");
            assertThat(cancelOutcome).containsExactly("SUCCESS");

            Reservation finalReservation = transactionTemplate.execute(status ->
                    reservationRepository.findById(reservationId).orElseThrow());
            assertThat(finalReservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(finalReservation.getAssignedCopy())
                    .as("assignedCopy renseigné prouve que la Reservation est bien passée par READY "
                            + "avant d'être annulée — la branche WAITING directe ne le renseigne jamais")
                    .isNotNull();
            assertThat(finalReservation.getAssignedCopy().getId()).isEqualTo(copyId);
            assertThat(finalReservation.getExpirationDate()).isNotNull();

            Loan finalLoan = transactionTemplate.execute(status -> loanRepository.findById(loanId).orElseThrow());
            assertThat(finalLoan.getLoanStatus()).isEqualTo(LoanStatus.RETURNED);

            Copy finalCopy = transactionTemplate.execute(status -> copyRepository.findById(copyId).orElseThrow());
            assertThat(finalCopy.getAvailabilityStatus())
                    .as("aucun autre candidat WAITING pour ce Title : le Copy doit redevenir AVAILABLE "
                            + "après l'annulation de la Reservation READY qui l'occupait")
                    .isEqualTo(AvailabilityStatus.AVAILABLE);
        } finally {
            Long finalCopyId = copyId;
            transactionTemplate.executeWithoutResult(status -> {
                
                // l'annulation/promotion crée RESERVATION_CANCELLED/RESERVATION_READY —
                // supprimées avant Loan/Reservation (fk_notification_loan_id/
                // fk_notification_reservation_id, ON DELETE RESTRICT).
                entityManager.createQuery("DELETE FROM Notification n WHERE n.loan.copy.id = :copyId")
                        .setParameter("copyId", finalCopyId).executeUpdate();
                entityManager.createQuery("DELETE FROM Notification n WHERE n.reservation.id = :id")
                        .setParameter("id", reservationId).executeUpdate();
                loanRepository.findByCopyIdAndLoanStatusIn(
                                finalCopyId, List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.RETURNED))
                        .forEach(loanRepository::delete);
                loanRepository.flush();
                reservationRepository.deleteById(reservationId);
                reservationRepository.flush();
                copyRepository.deleteById(finalCopyId);
                titleRepository.deleteById(titleId);
                appUserRepository.deleteById(userId);
                appUserRepository.deleteById(borrowerId);
            });
        }
    }

    private AppUser persistMember(String emailPrefix) {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "-" + System.nanoTime() + "@primatis.test");
        user.setPasswordHash("hash");
        user.setFirstName("Prénom");
        user.setLastName("Nom");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setMemberNumber(String.format("M%09d", System.nanoTime() % 1_000_000_000L));
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setRegistrationDate(LocalDate.now().minusYears(1));
        user.setMemberExpirationDate(LocalDate.now().plusYears(1));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        appUserRepository.save(user);
        return user;
    }
}
