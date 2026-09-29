package be.primatis.notification;

/**
 * Types de notifications générées par l'application.
 */
public enum NotificationType {
    LOAN_DUE_SOON,
    LOAN_OVERDUE,
    LOAN_RETURNED,

    RESERVATION_CREATED,
    RESERVATION_READY,
    RESERVATION_EXPIRED,
    RESERVATION_CANCELLED,

    FINE_ISSUED,
    FINE_PAID,
    FINE_CANCELLED,

    ARTICLE_PUBLISHED
}
