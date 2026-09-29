package be.primatis.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des notifications.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    /**
     * Retourne les notifications d'un utilisateur sous forme paginée.
     *
     * @param recipientUserId identifiant du destinataire
     * @param pageable paramètres de pagination
     * @return page des notifications du destinataire
     */
    Page<Notification> findByRecipientUserId(Long recipientUserId, Pageable pageable);
    /**
     * Recherche une notification appartenant à un utilisateur.
     *
     * @param id identifiant de la notification
     * @param recipientUserId identifiant du destinataire
     * @return notification correspondante, si elle existe
     */
    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);
    /**
     * Retourne les notifications d'un utilisateur correspondant au statut demandé.
     *
     * @param recipientUserId identifiant du destinataire
     * @param notificationStatus statut recherché
     * @return notifications correspondantes
     */
    List<Notification> findByRecipientUserIdAndNotificationStatus(
            Long recipientUserId, NotificationStatus notificationStatus);
    /**
     * Compte les notifications d'un utilisateur pour un statut donné.
     *
     * @param recipientUserId identifiant du destinataire
     * @param notificationStatus statut recherché
     * @return nombre de notifications correspondantes
     */
    long countByRecipientUserIdAndNotificationStatus(Long recipientUserId, NotificationStatus notificationStatus);
    /**
     * Vérifie si une notification d'un type donné existe déjà pour un prêt.
     *
     * @param loanId identifiant du prêt
     * @param notificationType type de notification
     * @return true si une notification correspondante existe
     */
    boolean existsByLoanIdAndNotificationType(Long loanId, NotificationType notificationType);
}
