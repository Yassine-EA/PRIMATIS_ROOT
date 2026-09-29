package be.primatis.e2e;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Configure l'horloge ajustable utilisée sous le profil E2E.
 */
@Configuration
public class E2eClockConfig {

    @Bean
    @Profile("e2e")
    public E2eAdjustableClock clock() {
        return new E2eAdjustableClock(Clock.systemUTC());
    }
}
