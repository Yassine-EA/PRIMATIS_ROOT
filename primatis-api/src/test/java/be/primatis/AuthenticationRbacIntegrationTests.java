package be.primatis;

import be.primatis.access.Permission;
import be.primatis.access.PermissionRepository;
import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.exception.InvalidCredentialsException;
import be.primatis.security.AccessToken;
import be.primatis.security.AuthService;
import be.primatis.security.JwtService;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration du flux d'authentification et des autorisations RBAC.
 *
 * Ils couvrent la chaîne complète depuis les rôles et permissions persistés
 * jusqu'au JWT, au Resource Server et aux contrôles d'autorisation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationRbacIntegrationTests {

    private static final Set<String> LIBRARIAN_PERMISSION_CODES = Set.of(
            "CATALOGUE_READ", "CATALOGUE_MANAGE",
            "COPY_READ", "COPY_MANAGE",
            "LOAN_READ", "LOAN_MANAGE",
            "RESERVATION_READ", "RESERVATION_MANAGE",
            "FINE_READ", "FINE_MANAGE",
            "ARTICLE_READ", "ARTICLE_MANAGE", "ARTICLE_PUBLISH",
            "USER_READ", "USER_PROFILE_MANAGE");

    private static final Set<String> MEMBER_PERMISSION_CODES = Set.of("CATALOGUE_READ", "ARTICLE_READ");

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTransactionTemplate() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void librarianEndToEndLoginProducesJwtAuthorizedForLoanManageButDeniedForRoleManage() throws Exception {
        String email = "e2e-librarian@primatis.test";
        String rawPassword = "Correct-Librarian-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_LIBRARIAN");

        try {
            Authentication authentication = authService.login(email, rawPassword);

            Set<String> expectedAuthorities = new HashSet<>(LIBRARIAN_PERMISSION_CODES);
            expectedAuthorities.add("ROLE_LIBRARIAN");
            assertThat(authorityStrings(authentication)).containsExactlyInAnyOrderElementsOf(expectedAuthorities);

            AccessToken accessToken = jwtService.generateAccessToken(authentication);
            assertThat(accessToken.tokenType()).isEqualTo("Bearer");
            assertThat(accessToken.expiresInSeconds()).isEqualTo(3600); // durée du jeton : 1 heure

            mockMvc.perform(get("/api/v1/protected/authorities")
                    .header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", containsInAnyOrder(expectedAuthorities.toArray())));

            mockMvc.perform(get("/api/v1/protected/loan-manage-only")
                    .header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/protected/role-manage-only")
                    .header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andExpect(jsonPath("$.fieldErrors").isEmpty());
        } finally {
            deleteUserAndRoles(email);
        }
    }

    @Test
    void memberEndToEndLoginIsRestrictedToCatalogueAndArticleReadOnly() throws Exception {
        String email = "e2e-member@primatis.test";
        String rawPassword = "Correct-Member-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_MEMBER");

        try {
            Authentication authentication = authService.login(email, rawPassword);

            Set<String> expectedAuthorities = new HashSet<>(MEMBER_PERMISSION_CODES);
            expectedAuthorities.add("ROLE_MEMBER");
            assertThat(authorityStrings(authentication)).containsExactlyInAnyOrderElementsOf(expectedAuthorities);
            assertThat(authorityStrings(authentication)).doesNotContain(
                    "LOAN_MANAGE", "ARTICLE_MANAGE", "USER_READ", "ROLE_MANAGE");

            AccessToken accessToken = jwtService.generateAccessToken(authentication);

            Jwt decoded = jwtDecoder.decode(accessToken.token());
            assertThat(decoded.<List<String>>getClaim("roles")).containsExactly("ROLE_MEMBER");
            assertThat(decoded.<List<String>>getClaim("permissions"))
                    .containsExactlyInAnyOrderElementsOf(MEMBER_PERMISSION_CODES);

            mockMvc.perform(get("/api/v1/protected/loan-manage-only")
                    .header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mockMvc.perform(get("/api/v1/protected/role-manage-only")
                    .header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isForbidden());
        } finally {
            deleteUserAndRoles(email);
        }
    }

    /**
     * Vérifie que l'administrateur obtient l'autorité ROLE_MANAGE via les
     * permissions
     * réellement chargées depuis le modèle RBAC, sans contournement spécifique au
     * rôle.
     */
    @Test
    void adminEndToEndLoginIsAuthorizedForRoleManageThroughRealPermissionNotBypass() throws Exception {
        String email = "e2e-admin@primatis.test";
        String rawPassword = "Correct-Admin-Password-2026!";
        persistActiveUserWithRole(email, rawPassword, "ROLE_ADMIN");

        try {
            Authentication authentication = authService.login(email, rawPassword);

            Set<String> allPermissionCodes = permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
            Set<String> expectedAuthorities = new HashSet<>(allPermissionCodes);
            expectedAuthorities.add("ROLE_ADMIN");
            assertThat(authorityStrings(authentication)).containsExactlyInAnyOrderElementsOf(expectedAuthorities);
            assertThat(authorityStrings(authentication)).contains("ROLE_MANAGE");

            AccessToken accessToken = jwtService.generateAccessToken(authentication);

            Jwt decoded = jwtDecoder.decode(accessToken.token());
            assertThat(decoded.<List<String>>getClaim("roles")).containsExactly("ROLE_ADMIN");
            assertThat(decoded.<List<String>>getClaim("permissions"))
                    .containsExactlyInAnyOrderElementsOf(allPermissionCodes)
                    .contains("ROLE_MANAGE", "ROLE_READ");

            mockMvc.perform(get("/api/v1/protected/role-manage-only")
                    .header("Authorization", "Bearer " + accessToken.token()))
                    .andExpect(status().isOk());
        } finally {
            deleteUserAndRoles(email);
        }
    }

    /**
     * Vérifie qu'un compte désactivé échoue pendant l'authentification,
     * avant toute génération possible de JWT.
     */
    @Test
    void disabledAccountLoginFailsBeforeAnyJwtCouldBeGenerated() {
        String email = "e2e-disabled@primatis.test";
        String rawPassword = "Correct-Disabled-Password-2026!";
        persistUser(email, rawPassword, AccountStatus.DISABLED);

        try {
            assertThatExceptionOfType(InvalidCredentialsException.class)
                    .isThrownBy(() -> authService.login(email, rawPassword));
        } finally {
            deleteUserAndRoles(email);
        }
    }

    /**
     * Vérifie qu'un mot de passe incorrect empêche l'authentification
     * avant toute génération possible de JWT.
     */
    @Test
    void wrongPasswordLoginFailsBeforeAnyJwtCouldBeGenerated() {
        String email = "e2e-wrongpassword@primatis.test";
        String rawPassword = "Correct-Password-2026!";
        persistUser(email, rawPassword, AccountStatus.ACTIVE);

        try {
            assertThatExceptionOfType(InvalidCredentialsException.class)
                    .isThrownBy(() -> authService.login(email, "Totally-Wrong-Password-2026!"));
        } finally {
            deleteUserAndRoles(email);
        }
    }

    private Set<String> authorityStrings(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private void persistUser(String email, String rawPassword, AccountStatus accountStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(accountStatus);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);
        });
    }

    /**
     * Crée un utilisateur actif et lui associe un rôle déjà présent dans le
     * référentiel RBAC.
     */
    private void persistActiveUserWithRole(String email, String rawPassword, String roleCode) {
        transactionTemplate.executeWithoutResult(status -> {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Bootstrap RBAC manquant : " + roleCode));

            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setFirstName("Prénom");
            user.setLastName("Nom");
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setFailedLoginCount(0);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            appUserRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setId(new UserRoleId(user.getId(), role.getId()));
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setAssignedAt(Instant.now());
            entityManager.persist(userRole);
        });
    }

    private void deleteUserAndRoles(String email) {
        transactionTemplate.executeWithoutResult(status -> appUserRepository.findByEmail(email).ifPresent(user -> {
            entityManager.createQuery("DELETE FROM UserRole ur WHERE ur.id.userId = :userId")
                    .setParameter("userId", user.getId())
                    .executeUpdate();
            appUserRepository.delete(user);
        }));
    }
}