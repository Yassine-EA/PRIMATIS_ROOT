package be.primatis.e2e.web;

import java.time.Instant;

/**
 * Représente l'état courant de l'horloge E2E.
 */
public record E2eTimeResponse(Instant currentTime, long offsetSeconds) {
}
