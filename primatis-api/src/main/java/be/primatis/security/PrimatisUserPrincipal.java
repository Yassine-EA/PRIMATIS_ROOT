package be.primatis.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
/**
 * Représentation Spring Security d'un utilisateur PRIMATIS.
 *
 * Les rôles et permissions sont conservés séparément puis réunis dans les
 * autorités utilisées par Spring Security. Le verrouillage temporaire est
 * géré par le service d'authentification.
 */
public class PrimatisUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final List<String> roles;
    private final List<String> permissions;
    private final Set<GrantedAuthority> authorities;

    public PrimatisUserPrincipal(
            Long userId,
            String email,
            String passwordHash,
            boolean enabled,
            Set<String> roleCodes,
            Set<String> permissionCodes) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = List.copyOf(new TreeSet<>(roleCodes));
        this.permissions = List.copyOf(new TreeSet<>(permissionCodes));

        Set<GrantedAuthority> union = new TreeSet<>(Comparator.comparing(GrantedAuthority::getAuthority));
        this.roles.forEach(code -> union.add(new SimpleGrantedAuthority(code)));
        this.permissions.forEach(code -> union.add(new SimpleGrantedAuthority(code)));
        this.authorities = union;
    }
    /**
     * Retourne les codes de rôle de l'utilisateur.
     *
     * @return rôles triés sans doublon
     */
    public List<String> getRoles() {
        return roles;
    }
    /**
     * Retourne les codes de permission de l'utilisateur.
     *
     * @return permissions triées sans doublon
     */
    public List<String> getPermissions() {
        return permissions;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
