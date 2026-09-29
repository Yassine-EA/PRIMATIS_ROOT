package be.primatis.security;

import be.primatis.access.RoleAndPermissionCode;
import be.primatis.user.AccountStatus;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
@Service
/**
 * Charge les informations d'un utilisateur nécessaires à Spring Security.
 */
public class PrimatisUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public PrimatisUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable."));

        List<RoleAndPermissionCode> rows = appUserRepository.findRoleAndPermissionCodesByUserId(user.getId());

        Set<String> roleCodes = new TreeSet<>();
        Set<String> permissionCodes = new TreeSet<>();
        for (RoleAndPermissionCode row : rows) {
            roleCodes.add(row.roleCode());
            if (row.permissionCode() != null) {
                permissionCodes.add(row.permissionCode());
            }
        }

        return new PrimatisUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getAccountStatus() == AccountStatus.ACTIVE,
                roleCodes,
                permissionCodes);
    }
}
