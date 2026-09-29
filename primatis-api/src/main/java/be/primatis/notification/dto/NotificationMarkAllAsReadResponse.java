package be.primatis.notification.dto;
/**
 * Résultat de l'opération permettant de marquer toutes les notifications comme lues.
 */
public record NotificationMarkAllAsReadResponse(int updatedCount) {
}
