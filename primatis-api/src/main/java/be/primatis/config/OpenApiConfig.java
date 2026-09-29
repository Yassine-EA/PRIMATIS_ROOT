package be.primatis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure la documentation OpenAPI et l'authentification JWT Bearer.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI primatisOpenApi() {
        SecurityScheme bearerAuthScheme = new SecurityScheme()
                .name(BEARER_AUTH_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Authentification par JWT Bearer RS256 : "
                        + "en-tête Authorization: Bearer <token>.");

        return new OpenAPI()
                .info(new Info()
                        .title("PRIMATIS API")
                        .description("API REST de gestion de bibliothèque PRIMATIS.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME_NAME, bearerAuthScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME_NAME));
    }
}
