package be.primatis.user;

import be.primatis.access.RoleAndPermissionCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des utilisateurs.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
    Optional<AppUser> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.email = :email")
    Optional<AppUser> findByEmailForAuthentication(@Param("email") String email);
    @Query("""
            SELECT NEW be.primatis.access.RoleAndPermissionCode(r.code, p.code)
            FROM UserRole ur
            JOIN ur.role r
            LEFT JOIN RolePermission rp ON rp.role = r
            LEFT JOIN rp.permission p
            WHERE ur.id.userId = :userId
            """)
    List<RoleAndPermissionCode> findRoleAndPermissionCodesByUserId(@Param("userId") Long userId);
    boolean existsByEmail(String email);
    boolean existsByMemberNumber(String memberNumber);
    List<AppUser> findByMemberStatus(MemberStatus memberStatus);
}
