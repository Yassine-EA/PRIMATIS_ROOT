package be.primatis.user;

import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.MeProfileResponse;
import be.primatis.user.web.UpdateMeProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
/**
 * Service de consultation et de modification du profil personnel.
 */
@Service
public class MeProfileService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";

    private final AppUserRepository appUserRepository;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final Clock clock;

    public MeProfileService(
            AppUserRepository appUserRepository,
            MemberExpirationPolicy memberExpirationPolicy,
            PhoneNumberNormalizer phoneNumberNormalizer,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.clock = clock;
    }
    @Transactional
    /** Retourne le profil de l'utilisateur authentifié. */
    public MeProfileResponse getOwnProfile(Long selfUserId) {
        AppUser user = getRequiredUser(selfUserId);
        memberExpirationPolicy.syncIfNeeded(user);
        return MeProfileResponse.from(user);
    }
    @Transactional
    /** Modifie les informations personnelles autorisées du profil authentifié. */
    public MeProfileResponse updateOwnProfile(Long selfUserId, UpdateMeProfileRequest request) {
        AppUser user = getRequiredUser(selfUserId);

        String rawPhoneNumber = request.phoneNumber();
        if (rawPhoneNumber != null && !rawPhoneNumber.isBlank()) {
            String normalized = phoneNumberNormalizer.normalizeToE164(rawPhoneNumber)
                    .orElseThrow(() -> new IllegalStateException(
                            "phoneNumber invalide malgré la validation @ValidPhoneNumber préalable."));
            if (!normalized.equals(user.getPhoneNumber())) {
                user.setPhoneNumber(normalized);
                user.setUpdatedAt(clock.instant());
            }
        }

        memberExpirationPolicy.syncIfNeeded(user);
        return MeProfileResponse.from(user);
    }

    private AppUser getRequiredUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + userId + "."));
    }
}
