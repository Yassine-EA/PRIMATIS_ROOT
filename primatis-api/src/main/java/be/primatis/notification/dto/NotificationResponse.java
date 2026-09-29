package be.primatis.notification.dto;

import be.primatis.notification.Notification;
import be.primatis.notification.NotificationStatus;
import be.primatis.notification.NotificationType;

import java.time.Instant;
import java.util.Objects;
/**
 * Représentation d'une notification exposée par l'API.
 */
public record NotificationResponse(
        Long id,
        NotificationType notificationType,
        NotificationStatus notificationStatus,
        String title,
        String message,
        Long originId,
        Instant createdAt,
        Instant readAt) {

    /**
     * Crée la représentation d'une notification.
     *
     * @param notification notification à représenter
     * @return représentation de la notification
     */
    public static NotificationResponse from(Notification notification) {
        Objects.requireNonNull(notification, "notification");

        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getNotificationStatus(),
                notification.getTitle(),
                notification.getMessage(),
                originId(notification),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
    private static Long originId(Notification notification) {
        if (notification.getLoan() != null) {
            return notification.getLoan().getId();
        }
        if (notification.getReservation() != null) {
            return notification.getReservation().getId();
        }
        if (notification.getFine() != null) {
            return notification.getFine().getId();
        }
        if (notification.getArticle() != null) {
            return notification.getArticle().getId();
        }
        throw new IllegalStateException(
                "Incohérence de données : Notification " + notification.getId() + " sans origine "
                        + "(ck_notification_exactly_one_origin garantit normalement au moins une origine).");
    }
}
