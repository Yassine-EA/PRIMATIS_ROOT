package be.primatis.security;

import be.primatis.exception.AccountTemporarilyLockedException;
import be.primatis.exception.InvalidCredentialsException;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
/**
 * Service chargé de l'authentification et du verrouillage temporaire des comptes.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final int MAX_FAILED_ATTEMPTS = 3;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository appUserRepository;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final String unknownUserPasswordHash;

    public AuthService(
            AppUserRepository appUserRepository,
            AuthenticationManager authenticationManager,
            Clock clock,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.unknownUserPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountTemporarilyLockedException.class})
    /**
     * Tente d'authentifier un utilisateur avec son adresse e-mail et son mot de passe.
     *
     * Les échecs d'authentification ne révèlent pas si le compte existe ou s'il est désactivé.
     *
     * @param email adresse e-mail
     * @param rawPassword mot de passe fourni
     * @return authentification validée
     */
    public Authentication login(String email, String rawPassword) {
        Instant now = clock.instant();

        // Chargement verrouillé (FOR UPDATE) : nécessaire pour vérifier le
        // verrouillage temporaire et protéger la mise à jour du compteur
        // d'échecs contre une tentative concurrente sur le même compte.
        AppUser user = appUserRepository.findByEmailForAuthentication(email).orElse(null);
        if (user == null) {
            // Email inconnu : aucune donnée créée, aucun compteur modifié,
            // même code public que les autres échecs (non-divulgation).
            // Un travail BCrypt équivalent à celui d'un compte réel est
            // effectué (résultat ignoré) pour ne pas révéler l'inexistence
            passwordEncoder.matches(rawPassword, unknownUserPasswordHash);
            // Aucun identifiant journalisé : l'e-mail saisi est une donnée personnelle
            // (et peut contenir un mot de passe tapé dans le mauvais champ).
            log.info("Authentification refusée : identifiant inconnu");
            throw new InvalidCredentialsException();
        }

        if (user.getLockedUntil() != null) {
            if (now.isBefore(user.getLockedUntil())) {
                // Verrouillage en cours : rejet AVANT toute vérification du
                // mot de passe, compteur et lockedUntil inchangés.
                log.info("Authentification refusée : compte verrouillé (userId={}, jusqu'à {})",
                        user.getId(), user.getLockedUntil());
                throw new AccountTemporarilyLockedException();
            }
            // Verrouillage expiré : nouvelle séquence, compteur nettoyé
            // avant de poursuivre normalement l'authentification.
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (BadCredentialsException ex) {
            // Seul cas où une comparaison de mot de passe a réellement eu
            // lieu et a échoué : c'est la seule situation qui incrémente le
            // compteur d'échecs.
            registerFailedAttempt(user, now);
            log.info("Authentification refusée : identifiants invalides (userId={}, échecs consécutifs={})",
                    user.getId(), user.getFailedLoginCount());
            throw new InvalidCredentialsException();
        } catch (AuthenticationException ex) {
            // AccountStatus.DISABLED (DisabledException) et tout autre échec
            // Spring Security non lié à un mot de passe incorrect :
            // DaoAuthenticationProvider vérifie isEnabled() AVANT de
            // comparer le mot de passe, donc aucune comparaison n'a eu lieu
            // ici — le compteur d'échecs n'est délibérément pas modifié.
            // compte, même code public INVALID_CREDENTIALS que les autres
            // échecs plutôt qu'un code révélant que le compte est désactivé.
            // (Le journal serveur, lui, peut préciser la cause : il n'est pas exposé au client.)
            log.info("Authentification refusée : compte non actif ou refus Spring Security (userId={}, cause={})",
                    user.getId(), ex.getClass().getSimpleName());
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);

        log.info("Authentification réussie (userId={})", user.getId());
        return authentication;
    }

    private void registerFailedAttempt(AppUser user, Instant now) {
        int updatedCount = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(updatedCount);
        if (updatedCount == MAX_FAILED_ATTEMPTS) {
            // La tentative qui déclenche le verrouillage reste elle-même
            // INVALID_CREDENTIALS (levée par l'appelant) ; seules les
            // tentatives suivantes, pendant la fenêtre de verrouillage,
            // recevront ACCOUNT_TEMPORARILY_LOCKED.
            user.setLockedUntil(now.plus(LOCK_DURATION));
            log.warn("Compte verrouillé après {} échecs consécutifs (userId={}, jusqu'à {})",
                    updatedCount, user.getId(), user.getLockedUntil());
        }
    }
}
