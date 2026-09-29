package be.primatis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active l'exécution des tâches planifiées de l'application.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
