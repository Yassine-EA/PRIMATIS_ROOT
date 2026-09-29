package be.primatis.user;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
/**
 * Applique la politique d'expiration de l'adhésion.
 */
@Component
public class MemberExpirationPolicy {

    private final Clock clock;

    public MemberExpirationPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean isExpired(AppUser user) {
        LocalDate expirationDate = user.getMemberExpirationDate();
        return expirationDate != null && expirationDate.isBefore(LocalDate.now(clock));
    }
    public boolean syncIfNeeded(AppUser user) {
        if (user.getMemberStatus() == MemberStatus.ACTIVE && isExpired(user)) {
            user.setMemberStatus(MemberStatus.EXPIRED);
            return true;
        }
        return false;
    }
}
