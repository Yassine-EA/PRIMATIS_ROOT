package be.primatis.reservation;

import be.primatis.catalogue.AvailabilityStatus;
import be.primatis.catalogue.Copy;
import be.primatis.catalogue.CopyRepository;
import be.primatis.catalogue.Title;
import be.primatis.catalogue.TitleRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.InvalidDateRangeException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.reservation.dto.CreateOwnReservationRequest;
import be.primatis.reservation.dto.CreateReservationRequest;
import be.primatis.reservation.dto.ReservationResponse;
import be.primatis.setting.ApplicationSettingService;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberExpirationPolicy;
import be.primatis.user.MemberStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Service de consultation, de création et d'annulation des réservations.
 */
@Service
public class ReservationService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String NOT_A_MEMBER_CODE = "NOT_A_MEMBER";
    private static final String MEMBER_BLOCKED_CODE = "MEMBER_BLOCKED";
    private static final String MEMBER_EXPIRED_CODE = "MEMBER_EXPIRED";
    private static final String TITLE_NOT_FOUND_CODE = "TITLE_NOT_FOUND";
    private static final String RESERVATION_COPY_AVAILABLE_CODE = "RESERVATION_COPY_AVAILABLE";
    private static final String RESERVATION_ALREADY_ACTIVE_CODE = "RESERVATION_ALREADY_ACTIVE";
    private static final String RESERVATION_LIMIT_REACHED_CODE = "RESERVATION_LIMIT_REACHED";
    private static final String RESERVATION_NOT_FOUND_CODE = "RESERVATION_NOT_FOUND";
    private static final String RESERVATION_NOT_CANCELLABLE_CODE = "RESERVATION_NOT_CANCELLABLE";
    private static final String RESERVATION_CANCELLATION_CONTENTION_CODE = "RESERVATION_CANCELLATION_CONTENTION";

    private static final String MAX_ACTIVE_RESERVATIONS_PER_MEMBER_KEY = "MAX_ACTIVE_RESERVATIONS_PER_MEMBER";
    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(ReservationStatus.WAITING,
            ReservationStatus.READY);
    private static final int MAX_CANCELLATION_REASSESSMENT_ATTEMPTS = 5;
    private static final Set<String> RESERVATION_DUPLICATE_CONSTRAINTS = Set.of("ux_reservation_active_user_title");

    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final TitleRepository titleRepository;
    private final CopyRepository copyRepository;
    private final ApplicationSettingService applicationSettingService;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final ReservationAssignmentService reservationAssignmentService;
    private final NotificationService notificationService;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            AppUserRepository appUserRepository,
            TitleRepository titleRepository,
            CopyRepository copyRepository,
            ApplicationSettingService applicationSettingService,
            MemberExpirationPolicy memberExpirationPolicy,
            ReservationAssignmentService reservationAssignmentService,
            NotificationService notificationService,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.titleRepository = titleRepository;
        this.copyRepository = copyRepository;
        this.applicationSettingService = applicationSettingService;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.reservationAssignmentService = reservationAssignmentService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Transactional(readOnly = true)
    /** Retourne l'ensemble des réservations sous forme paginée. */
    public Page<ReservationResponse> listReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable).map(ReservationResponse::from);
    }

    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Transactional(readOnly = true)
    /** Recherche les réservations selon les critères fournis. */
    public Page<ReservationResponse> listReservations(
            Pageable pageable,
            String q,
            ReservationStatus reservationStatus,
            LocalDate reservationDateFrom,
            LocalDate reservationDateTo,
            LocalDate expirationDateFrom,
            LocalDate expirationDateTo) {
        requireOrderedRange("reservationDateFrom", reservationDateFrom, reservationDateTo);
        requireOrderedRange("expirationDateFrom", expirationDateFrom, expirationDateTo);
        return reservationRepository
                .findAll(ReservationSpecifications.matching(
                        q, reservationStatus, reservationDateFrom, reservationDateTo,
                        expirationDateFrom, expirationDateTo), pageable)
                .map(ReservationResponse::from);
    }

    private static void requireOrderedRange(String fromParameter, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(fromParameter,
                    "La date de début doit être antérieure ou égale à la date de fin.");
        }
    }

    @Transactional(readOnly = true)
    /** Retourne les réservations de l'utilisateur authentifié. */
    public Page<ReservationResponse> listOwnReservations(Long selfUserId, Pageable pageable) {
        return reservationRepository.findByUserId(selfUserId, pageable).map(ReservationResponse::from);
    }

    @Transactional
    /** Crée une réservation pour l'utilisateur authentifié. */
    public ReservationResponse createOwnReservation(Long selfUserId, CreateOwnReservationRequest request) {
        return createReservationForUser(selfUserId, request.titleId());
    }

    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Transactional
    /** Crée une réservation pour le membre indiqué. */
    public ReservationResponse createReservation(CreateReservationRequest request) {
        return createReservationForUser(request.userId(), request.titleId());
    }

    private ReservationResponse createReservationForUser(Long userId, Long titleId) {
        AppUser member = requireEligibleMember(userId);
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new ResourceNotFoundException(TITLE_NOT_FOUND_CODE,
                        "Aucun titre pour l'identifiant " + titleId + "."));

        if (copyRepository.existsByTitleIdAndAvailabilityStatus(titleId, AvailabilityStatus.AVAILABLE)) {
            throw new BusinessRuleException(RESERVATION_COPY_AVAILABLE_CODE,
                    "Un exemplaire de ce titre est immédiatement disponible.");
        }

        if (!reservationRepository
                .findByUserIdAndTitleIdAndReservationStatusIn(userId, titleId, ACTIVE_RESERVATION_STATUSES)
                .isEmpty()) {
            throw new BusinessRuleException(RESERVATION_ALREADY_ACTIVE_CODE,
                    "Cet adhérent a déjà une réservation active pour ce titre.");
        }

        int maxActiveReservations = applicationSettingService.getInteger(MAX_ACTIVE_RESERVATIONS_PER_MEMBER_KEY);
        if (reservationRepository.countByUserIdAndReservationStatusIn(userId,
                ACTIVE_RESERVATION_STATUSES) >= maxActiveReservations) {
            throw new BusinessRuleException(RESERVATION_LIMIT_REACHED_CODE,
                    "Cet adhérent a atteint le nombre maximal de réservations actives.");
        }

        Instant now = clock.instant();
        Reservation reservation = new Reservation();
        reservation.setUser(member);
        reservation.setTitle(title);
        reservation.setReservationDate(now);
        reservation.setReservationStatus(ReservationStatus.WAITING);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);

        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            if (isReservationDuplicateConstraint(ex)) {
                throw new BusinessRuleException(RESERVATION_ALREADY_ACTIVE_CODE,
                        "Cet adhérent a déjà une réservation active pour ce titre.");
            }
            throw ex;
        }

        notificationService.createForReservation(reservation, NotificationType.RESERVATION_CREATED,
                "Réservation créée", "Votre réservation a bien été enregistrée.");

        return ReservationResponse.from(reservation);
    }

    @Transactional
    /** Annule une réservation appartenant à l'utilisateur authentifié. */
    public ReservationResponse cancelOwnReservation(Long selfUserId, Long reservationId) {
        return cancelReservationForUser(reservationId, selfUserId);
    }

    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Transactional
    /** Annule une réservation. */
    public ReservationResponse cancelReservation(Long reservationId) {
        return cancelReservationForUser(reservationId, null);
    }

    private ReservationResponse cancelReservationForUser(Long reservationId, Long selfUserId) {
        for (int attempt = 0; attempt < MAX_CANCELLATION_REASSESSMENT_ATTEMPTS; attempt++) {
            ReservationCancellationSnapshot snapshot = reservationRepository
                    .findCancellationSnapshotById(reservationId)
                    .orElseThrow(() -> new ResourceNotFoundException(RESERVATION_NOT_FOUND_CODE,
                            "Aucune réservation pour l'identifiant " + reservationId + "."));
            requireOwnershipIfSelf(snapshot.getUserId(), selfUserId);

            Instant now = clock.instant();

            if (snapshot.getAssignedCopyId() != null) {
                Copy copy = copyRepository.findByIdForUpdate(snapshot.getAssignedCopyId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Incohérence de données : aucun exemplaire pour l'identifiant "
                                        + snapshot.getAssignedCopyId() + ", pourtant référencé par le snapshot "
                                        + "de la Reservation " + reservationId + "."));
                Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Incohérence de données : Reservation " + reservationId
                                        + " disparue entre son instantané et son verrouillage."));

                requireOwnershipIfSelf(reservation.getUser().getId(), selfUserId);

                if (reservation.getReservationStatus() != ReservationStatus.READY) {
                    throw notCancellableException(reservation.getReservationStatus());
                }
                if (reservation.getAssignedCopy() == null
                        || !reservation.getAssignedCopy().getId().equals(copy.getId())) {
                    throw new IllegalStateException("Incohérence de données : Reservation " + reservationId
                            + " READY sans correspondance avec le Copy verrouillé " + copy.getId() + ".");
                }

                reservation.setReservationStatus(ReservationStatus.CANCELLED);
                reservation.setUpdatedAt(now);
                notificationService.createForReservation(reservation, NotificationType.RESERVATION_CANCELLED,
                        "Réservation annulée", "Votre réservation a été annulée.");
                reservationAssignmentService.assignNextAdmissibleWaitingReservationOrMakeAvailable(copy, now);
                copy.setUpdatedAt(now);

                return ReservationResponse.from(reservation);
            }

            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Incohérence de données : Reservation " + reservationId
                                    + " disparue entre son instantané et son verrouillage."));

            requireOwnershipIfSelf(reservation.getUser().getId(), selfUserId);

            if (reservation.getReservationStatus() == ReservationStatus.WAITING) {
                reservation.setReservationStatus(ReservationStatus.CANCELLED);
                reservation.setUpdatedAt(now);
                notificationService.createForReservation(reservation, NotificationType.RESERVATION_CANCELLED,
                        "Réservation annulée", "Votre réservation a été annulée.");
                return ReservationResponse.from(reservation);
            }

            if (reservation.getReservationStatus() == ReservationStatus.READY) {
                // Snapshot périmé : promue WAITING→READY entre l'instantané et ce verrouillage,
                // aucun Copy verrouillé pour l'instant — impossible de continuer sans violer
                // nouveau snapshot, qui reflétera cette fois assignedCopyId.
                continue;
            }

            throw notCancellableException(reservation.getReservationStatus());
        }

        throw new ConflictException(RESERVATION_CANCELLATION_CONTENTION_CODE,
                "Impossible d'annuler la réservation " + reservationId + " après "
                        + MAX_CANCELLATION_REASSESSMENT_ATTEMPTS + " tentatives : contention concurrente trop forte.");
    }

    private void requireOwnershipIfSelf(Long ownerUserId, Long selfUserId) {
        if (selfUserId != null && !ownerUserId.equals(selfUserId)) {
            throw new ResourceNotFoundException(RESERVATION_NOT_FOUND_CODE,
                    "Aucune réservation accessible pour cet identifiant.");
        }
    }

    private BusinessRuleException notCancellableException(ReservationStatus currentStatus) {
        return new BusinessRuleException(RESERVATION_NOT_CANCELLABLE_CODE,
                "Cette réservation n'est plus annulable (statut actuel : " + currentStatus + ").");
    }

    private AppUser requireEligibleMember(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + userId + "."));

        if (user.getMemberNumber() == null) {
            throw new BusinessRuleException(NOT_A_MEMBER_CODE, "Cet utilisateur n'est pas adhérent.");
        }

        memberExpirationPolicy.syncIfNeeded(user);
        if (user.getMemberStatus() == MemberStatus.BLOCKED) {
            throw new BusinessRuleException(MEMBER_BLOCKED_CODE, "Cet adhérent est bloqué.");
        }
        if (user.getMemberStatus() == MemberStatus.EXPIRED) {
            throw new BusinessRuleException(MEMBER_EXPIRED_CODE, "L'adhésion de cet adhérent est expirée.");
        }

        return user;
    }

    private boolean isReservationDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return RESERVATION_DUPLICATE_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }
}
