package be.primatis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Configure l'horloge utilisée par les traitements dépendant du temps.
 */
@Configuration
public class ClockConfig {

    @Bean
    @Profile("!e2e")
    public Clock clock() {
        return Clock.systemUTC();
    }
}
