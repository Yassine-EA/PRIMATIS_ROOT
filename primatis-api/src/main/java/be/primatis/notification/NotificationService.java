package be.primatis.notification;

import be.primatis.article.Article;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.fine.Fine;
import be.primatis.loan.Loan;
import be.primatis.notification.dto.NotificationMarkAllAsReadResponse;
import be.primatis.notification.dto.NotificationResponse;
import be.primatis.reservation.Reservation;
import be.primatis.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
/**
 * Service de création, de consultation et de gestion des notifications.
 */
@Service
public class NotificationService {

    private static final String NOTIFICATION_NOT_FOUND_CODE = "NOTIFICATION_NOT_FOUND";

    private static final Set<NotificationType> LOAN_NOTIFICATION_TYPES =
            Set.of(NotificationType.LOAN_DUE_SOON, NotificationType.LOAN_OVERDUE, NotificationType.LOAN_RETURNED);

    private static final Set<NotificationType> RESERVATION_NOTIFICATION_TYPES = Set.of(
            NotificationType.RESERVATION_CREATED, NotificationType.RESERVATION_READY,
            NotificationType.RESERVATION_EXPIRED, NotificationType.RESERVATION_CANCELLED);

    private static final Set<NotificationType> FINE_NOTIFICATION_TYPES =
            Set.of(NotificationType.FINE_ISSUED, NotificationType.FINE_PAID, NotificationType.FINE_CANCELLED);

    private static final Set<NotificationType> ARTICLE_NOTIFICATION_TYPES = Set.of(NotificationType.ARTICLE_PUBLISHED);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    // Création
    /**
     * Crée une notification liée à un prêt.
     *
     * @param loan prêt d'origine
     * @param type type de notification
     * @param title titre de la notification
     * @param message contenu de la notification
     * @return notification créée
     */
    public NotificationResponse createForLoan(Loan loan, NotificationType type, String title, String message) {
        Objects.requireNonNull(loan, "loan");
        requireOrigin(LOAN_NOTIFICATION_TYPES, type, "Loan");
        return create(loan.getUser(), type, loan, null, null, null, title, message);
    }
    /**
     * Crée une notification d'échéance proche si aucune n'existe déjà pour le prêt.
     *
     * @param loan prêt concerné
     * @param title titre de la notification
     * @param message contenu de la notification
     * @return notification créée, ou une valeur vide si elle existait déjà
     */
    public Optional<NotificationResponse> createLoanDueSoonIfAbsent(Loan loan, String title, String message) {
        Objects.requireNonNull(loan, "loan");
        if (notificationRepository.existsByLoanIdAndNotificationType(loan.getId(), NotificationType.LOAN_DUE_SOON)) {
            return Optional.empty();
        }
        return Optional.of(create(loan.getUser(), NotificationType.LOAN_DUE_SOON, loan, null, null, null, title, message));
    }
    /**
     * Crée une notification liée à une réservation.
     *
     * @param reservation réservation d'origine
     * @param type type de notification
     * @param title titre de la notification
     * @param message contenu de la notification
     * @return notification créée
     */
    public NotificationResponse createForReservation(Reservation reservation, NotificationType type, String title, String message) {
        Objects.requireNonNull(reservation, "reservation");
        requireOrigin(RESERVATION_NOTIFICATION_TYPES, type, "Reservation");
        return create(reservation.getUser(), type, null, reservation, null, null, title, message);
    }
    /**
     * Crée une notification liée à une amende.
     *
     * @param fine amende d'origine
     * @param type type de notification
     * @param title titre de la notification
     * @param message contenu de la notification
     * @return notification créée
     */
    public NotificationResponse createForFine(Fine fine, NotificationType type, String title, String message) {
        Objects.requireNonNull(fine, "fine");
        requireOrigin(FINE_NOTIFICATION_TYPES, type, "Fine");
        return create(fine.getLoan().getUser(), type, null, null, fine, null, title, message);
    }
    /**
     * Crée une notification de publication d'article pour un destinataire.
     *
     * @param article article publié
     * @param recipient destinataire
     * @param title titre de la notification
     * @param message contenu de la notification
     * @return notification créée
     */
    public NotificationResponse createForArticle(Article article, AppUser recipient, String title, String message) {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(recipient, "recipient");
        return create(recipient, NotificationType.ARTICLE_PUBLISHED, null, null, null, article, title, message);
    }
    private NotificationResponse create(
            AppUser recipient, NotificationType type, Loan loan, Reservation reservation, Fine fine, Article article,
            String title, String message) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(type, "notificationType");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title ne peut pas être vide.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message ne peut pas être vide.");
        }

        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setLoan(loan);
        notification.setReservation(reservation);
        notification.setFine(fine);
        notification.setArticle(article);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setCreatedAt(clock.instant());
        notification.setReadAt(null);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    private void requireOrigin(Set<NotificationType> allowedTypes, NotificationType type, String originName) {
        Objects.requireNonNull(type, "notificationType");
        if (!allowedTypes.contains(type)) {
            throw new IllegalArgumentException(
                    "NotificationType " + type + " incompatible avec l'origine " + originName + ".");
        }
    }

    // Self-service
    /**
     * Retourne les notifications d'un utilisateur sous forme paginée.
     *
     * @param selfUserId identifiant de l'utilisateur
     * @param pageable paramètres de pagination
     * @return page des notifications de l'utilisateur
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listOwnNotifications(Long selfUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserId(selfUserId, pageable).map(NotificationResponse::from);
    }
    /**
     * Compte les notifications non lues d'un utilisateur.
     *
     * @param selfUserId identifiant de l'utilisateur
     * @return nombre de notifications non lues
     */
    @Transactional(readOnly = true)
    public long countUnread(Long selfUserId) {
        return notificationRepository.countByRecipientUserIdAndNotificationStatus(selfUserId, NotificationStatus.UNREAD);
    }
    /**
     * Marque une notification comme lue pour son destinataire.
     *
     * Un nouvel appel sur une notification déjà lue conserve son premier horodatage de lecture.
     *
     * @param selfUserId identifiant de l'utilisateur
     * @param notificationId identifiant de la notification
     * @return notification mise à jour
     */
    @Transactional
    public NotificationResponse markAsRead(Long selfUserId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, selfUserId)
                .orElseThrow(() -> new ResourceNotFoundException(NOTIFICATION_NOT_FOUND_CODE,
                        "Aucune notification pour l'identifiant " + notificationId + "."));

        if (notification.getNotificationStatus() == NotificationStatus.UNREAD) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(clock.instant());
        }

        return NotificationResponse.from(notification);
    }
    /**
     * Marque comme lues toutes les notifications non lues d'un utilisateur.
     *
     * @param selfUserId identifiant de l'utilisateur
     * @return nombre de notifications mises à jour
     */
    @Transactional
    public NotificationMarkAllAsReadResponse markAllAsRead(Long selfUserId) {
        List<Notification> unreadNotifications =
                notificationRepository.findByRecipientUserIdAndNotificationStatus(selfUserId, NotificationStatus.UNREAD);

        if (unreadNotifications.isEmpty()) {
            return new NotificationMarkAllAsReadResponse(0);
        }

        Instant now = clock.instant();
        for (Notification notification : unreadNotifications) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(now);
        }

        return new NotificationMarkAllAsReadResponse(unreadNotifications.size());
    }
}
