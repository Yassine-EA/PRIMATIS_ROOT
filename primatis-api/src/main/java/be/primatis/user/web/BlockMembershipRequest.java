package be.primatis.user.web;

import jakarta.validation.constraints.NotBlank;
/**
 * Requête de blocage d'une adhésion.
 */
public record BlockMembershipRequest(@NotBlank String blockedReason) {
}
