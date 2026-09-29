package be.primatis.user.web;

import be.primatis.user.AccountStatus;
import jakarta.validation.constraints.NotNull;
/**
 * Requête de modification du statut d'un compte.
 */
public record UpdateAccountStatusRequest(@NotNull AccountStatus status) {
}
