package be.primatis.config;

import be.primatis.security.PrimatisAccessDeniedHandler;
import be.primatis.security.PrimatisAuthenticationEntryPoint;
import be.primatis.security.PrimatisInvalidTokenAuthenticationEntryPoint;
import be.primatis.security.PrimatisUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Configure la sécurité HTTP, l'authentification JWT et les autorisations de
 * l'application.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

        /**
         * Construit la chaîne de filtres de sécurité HTTP.
         *
         * @param http                       configuration de sécurité HTTP
         * @param jwtDecoder                 décodeur des jetons JWT
         * @param jwtAuthenticationConverter convertisseur des autorités JWT
         * @param corsConfigurationSource    configuration CORS
         * @param objectMapper               sérialiseur JSON
         * @param clock                      horloge applicative
         * @return chaîne de filtres de sécurité
         * @throws Exception si la configuration de sécurité échoue
         */
        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtDecoder jwtDecoder,
                        JwtAuthenticationConverter jwtAuthenticationConverter,
                        CorsConfigurationSource corsConfigurationSource,
                        ObjectMapper objectMapper,
                        Clock clock) throws Exception {
                // Gestion des réponses 401 et 403 de Spring Security.
                PrimatisAuthenticationEntryPoint authenticationEntryPoint = new PrimatisAuthenticationEntryPoint(
                                objectMapper, clock);
                PrimatisInvalidTokenAuthenticationEntryPoint invalidTokenAuthenticationEntryPoint = new PrimatisInvalidTokenAuthenticationEntryPoint(
                                objectMapper, clock);
                PrimatisAccessDeniedHandler accessDeniedHandler = new PrimatisAccessDeniedHandler(objectMapper, clock);

                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                // Protection renforcée des fichiers exposés sous /media/**.
                                .headers(headers -> headers.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                                PathPatternRequestMatcher.withDefaults().matcher("/media/**"),
                                                new StaticHeadersWriter("Content-Security-Policy",
                                                                "default-src 'none'; sandbox"))))
                                .authorizeHttpRequests(authorize -> authorize
                                                // Préflight CORS : ne doit jamais être bloqué par l'authentification.
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/v1/titles/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/v1/genres/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/v1/articles/**").permitAll()
                                                // Les images intégrées aux articles publiés restent accessibles
                                                // publiquement.
                                                .requestMatchers(HttpMethod.GET, "/media/articles/**").permitAll()
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(authenticationEntryPoint)
                                                .accessDeniedHandler(accessDeniedHandler))
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .authenticationEntryPoint(invalidTokenAuthenticationEntryPoint)
                                                .jwt(jwt -> jwt
                                                                .decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter)));

                return http.build();
        }

        /**
         * Convertit les claims de rôles et de permissions du JWT en autorités Spring
         * Security.
         *
         * @return convertisseur des autorités JWT
         */
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
                rolesConverter.setAuthoritiesClaimName("roles");
                rolesConverter.setAuthorityPrefix("");

                JwtGrantedAuthoritiesConverter permissionsConverter = new JwtGrantedAuthoritiesConverter();
                permissionsConverter.setAuthoritiesClaimName("permissions");
                permissionsConverter.setAuthorityPrefix("");

                DelegatingJwtGrantedAuthoritiesConverter combined = new DelegatingJwtGrantedAuthoritiesConverter(
                                rolesConverter, permissionsConverter);

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(jwt -> new LinkedHashSet<>(combined.convert(jwt)));
                converter.setPrincipalClaimName(JwtClaimNames.SUB);
                return converter;
        }

        /**
         * Configure les règles CORS appliquées à l'API.
         *
         * @param corsProperties origines autorisées
         * @return source de configuration CORS
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(corsProperties.allowedOrigins());
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
                configuration.setAllowCredentials(false);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        /**
         * Fournit l'encodeur de mots de passe utilisé par l'application.
         *
         * @return encodeur de mots de passe
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        /**
         * Configure le fournisseur d'authentification basé sur les comptes
         * utilisateurs.
         *
         * @param userDetailsService service de chargement des utilisateurs
         * @param passwordEncoder    encodeur de mots de passe
         * @return fournisseur d'authentification
         */
        @Bean
        public DaoAuthenticationProvider daoAuthenticationProvider(
                        PrimatisUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);
                return provider;
        }

        /**
         * Fournit le gestionnaire d'authentification de l'application.
         *
         * @param daoAuthenticationProvider fournisseur d'authentification
         * @return gestionnaire d'authentification
         */
        @Bean
        public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
                return new ProviderManager(daoAuthenticationProvider);
        }
}
