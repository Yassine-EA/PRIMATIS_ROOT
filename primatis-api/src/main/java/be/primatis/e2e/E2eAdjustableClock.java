package be.primatis.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Horloge ajustable utilisée par les tests E2E.
 *
 * Le décalage est appliqué relativement à une horloge de base et partagé
 * entre les différentes vues de zone.
 */
public final class E2eAdjustableClock extends Clock {

    private final Clock baseClock;
    private final AtomicReference<Duration> offset;

    public E2eAdjustableClock(Clock baseClock) {
        this(baseClock, new AtomicReference<>(Duration.ZERO));
    }

    private E2eAdjustableClock(Clock baseClock, AtomicReference<Duration> offset) {
        this.baseClock = Objects.requireNonNull(baseClock, "baseClock");
        this.offset = offset;
    }

    @Override
    public ZoneId getZone() {
        return baseClock.getZone();
    }

    /**
     * Retourne une horloge utilisant la zone demandée tout en conservant le décalage courant.
     *
     * @param zone zone horaire à utiliser
     * @return horloge ajustable utilisant la zone demandée
     */
    @Override
    public Clock withZone(ZoneId zone) {
        return new E2eAdjustableClock(baseClock.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return baseClock.instant().plus(offset.get());
    }

    /** Avance le temps perçu de la durée fournie. */
    void advance(Duration duration) {
        offset.updateAndGet(current -> current.plus(duration));
    }

    /** Réinitialise le décalage temporel. */
    void reset() {
        offset.set(Duration.ZERO);
    }

    Duration currentOffset() {
        return offset.get();
    }
}
