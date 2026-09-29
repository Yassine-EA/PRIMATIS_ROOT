package be.primatis.e2e;

import be.primatis.e2e.web.E2eTimeResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service de contrôle du temps utilisé par les tests E2E.
 *
 * Le service agit uniquement sur l'horloge ajustable et reste disponible
 * sous le profil E2E.
 */
@Service
@Profile("e2e")
public class E2eTimeService {

    private final E2eAdjustableClock clock;

    public E2eTimeService(E2eAdjustableClock clock) {
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public E2eTimeResponse currentState() {
        return toResponse();
    }

    /**
     * Décale le temps perçu du nombre d'heures fourni.
     *
     * @param hours nombre d'heures à ajouter ou à retrancher
     * @return état courant de l'horloge E2E
     */
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public E2eTimeResponse advance(long hours) {
        clock.advance(Duration.ofHours(hours));
        return toResponse();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public E2eTimeResponse reset() {
        clock.reset();
        return toResponse();
    }

    private E2eTimeResponse toResponse() {
        return new E2eTimeResponse(clock.instant(), clock.currentOffset().getSeconds());
    }
}
