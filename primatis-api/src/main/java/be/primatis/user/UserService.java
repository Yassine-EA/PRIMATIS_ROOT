package be.primatis.user;

import be.primatis.access.Role;
import be.primatis.access.RoleRepository;
import be.primatis.access.UserRole;
import be.primatis.access.UserRoleId;
import be.primatis.access.UserRoleRepository;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.InvalidDateRangeException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.user.web.BlockMembershipRequest;
import be.primatis.user.web.CreateUserRequest;
import be.primatis.user.web.CreateUserResponse;
import be.primatis.user.web.UpdateAccountStatusRequest;
import be.primatis.user.web.UpdateUserRequest;
import be.primatis.user.web.UserDetailResponse;
import be.primatis.user.web.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
/**
 * Service de consultation et de gestion des utilisateurs.
 */
@Service
public class UserService {

    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String ROLE_MEMBER_CODE = "ROLE_MEMBER";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberNumberGenerator memberNumberGenerator;
    private final MemberExpirationPolicy memberExpirationPolicy;
    private final Clock clock;

    public UserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            MemberNumberGenerator memberNumberGenerator,
            MemberExpirationPolicy memberExpirationPolicy,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberNumberGenerator = memberNumberGenerator;
        this.memberExpirationPolicy = memberExpirationPolicy;
        this.clock = clock;
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    /** Retourne l'ensemble des utilisateurs sous forme paginée. */
    public Page<UserResponse> listUsers(Pageable pageable) {
        return listUsers(pageable, null);
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    public Page<UserResponse> listUsers(Pageable pageable, String q) {
        return listUsers(pageable, q, null, null);
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    public Page<UserResponse> listUsers(
            Pageable pageable, String q, AccountStatus accountStatus, MemberStatus memberStatus) {
        return listUsers(pageable, q, accountStatus, memberStatus, null, null, null, null);
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    public Page<UserResponse> listUsers(
            Pageable pageable,
            String q,
            AccountStatus accountStatus,
            MemberStatus memberStatus,
            LocalDate createdDateFrom,
            LocalDate createdDateTo,
            LocalDate memberExpirationDateFrom,
            LocalDate memberExpirationDateTo) {
        requireOrderedRange("createdDateFrom", createdDateFrom, createdDateTo);
        requireOrderedRange("memberExpirationDateFrom", memberExpirationDateFrom, memberExpirationDateTo);
        Page<AppUser> page = appUserRepository.findAll(
                UserSpecifications.matching(
                        q, accountStatus, memberStatus,
                        createdDateFrom, createdDateTo,
                        memberExpirationDateFrom, memberExpirationDateTo,
                        LocalDate.now(clock)),
                pageable);
        page.forEach(memberExpirationPolicy::syncIfNeeded);
        return page.map(UserResponse::from);
    }

    private static void requireOrderedRange(String fromParameter, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(fromParameter,
                    "La date de début doit être antérieure ou égale à la date de fin.");
        }
    }
    @PreAuthorize("hasAuthority('USER_READ')")
    @Transactional
    public UserDetailResponse getUserById(Long id) {
        AppUser user = getRequiredUser(id);
        memberExpirationPolicy.syncIfNeeded(user);
        List<String> roles = userRoleRepository.findByIdUserId(user.getId()).stream()
                .map(userRole -> userRole.getRole().getCode())
                .sorted()
                .toList();
        return new UserDetailResponse(UserResponse.from(user), roles);
    }
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    /** Crée un utilisateur et génère son mot de passe initial. */
    public CreateUserResponse createUser(CreateUserRequest request, Long adminUserId) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    "USER_EMAIL_ALREADY_EXISTS", "Un utilisateur existe déjà avec cet email.");
        }

        boolean memberRoleSelected = request.roles().contains(ROLE_MEMBER_CODE);
        validateMembershipCoherence(memberRoleSelected, request);

        Set<Role> roles = new LinkedHashSet<>();
        for (String code : request.roles()) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException(
                            "UNKNOWN_ROLE_CODE", "Le rôle demandé n'existe pas : " + code + "."));
            roles.add(role);
        }

        String initialPassword = PasswordGenerator.generate();
        String passwordHash = passwordEncoder.encode(initialPassword);
        String memberNumber = memberRoleSelected ? memberNumberGenerator.generateNext() : null;

        Instant now = clock.instant();
        AppUser user = new AppUser();
        user.setEmail(request.email());
        user.setPasswordHash(passwordHash);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setMemberNumber(memberNumber);
        user.setMemberStatus(memberRoleSelected ? request.memberStatus() : null);
        user.setRegistrationDate(memberRoleSelected ? request.registrationDate() : null);
        user.setMemberExpirationDate(memberRoleSelected ? request.memberExpirationDate() : null);
        user.setBlockedReason(memberRoleSelected ? request.blockedReason() : null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserRepository.save(user);

        AppUser adminReference = appUserRepository.getReferenceById(adminUserId);
        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setId(new UserRoleId(user.getId(), role.getId()));
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setAssignedAt(now);
            userRole.setAssignedBy(adminReference);
            userRoleRepository.save(userRole);
        }

        return new CreateUserResponse(UserResponse.from(user), initialPassword);
    }
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, Long adminUserId) {
        AppUser user = getRequiredUser(id);

        if (request.isFirstNamePresent()) {
            if (request.getFirstName() == null || request.getFirstName().isBlank()) {
                throw new BusinessRuleException(
                        "FIRST_NAME_MUST_NOT_BE_BLANK", "firstName ne peut pas être vide.");
            }
            user.setFirstName(request.getFirstName());
        }
        if (request.isLastNamePresent()) {
            if (request.getLastName() == null || request.getLastName().isBlank()) {
                throw new BusinessRuleException(
                        "LAST_NAME_MUST_NOT_BE_BLANK", "lastName ne peut pas être vide.");
            }
            user.setLastName(request.getLastName());
        }
        if (request.isPhoneNumberPresent()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        applyMembershipUpdate(user, request);

        if (request.getRoles() != null) {
            updateRoles(user, request.getRoles(), adminUserId);
        }

        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse updateAccountStatus(Long id, UpdateAccountStatusRequest request) {
        AppUser user = getRequiredUser(id);
        if (user.getAccountStatus() != request.status()) {
            user.setAccountStatus(request.status());
            user.setUpdatedAt(clock.instant());
        }
        return UserResponse.from(user);
    }
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse blockMember(Long id, BlockMembershipRequest request) {
        AppUser user = getRequiredUser(id);
        requireExistingMembership(user);
        memberExpirationPolicy.syncIfNeeded(user);
        user.setMemberStatus(MemberStatus.BLOCKED);
        user.setBlockedReason(request.blockedReason());
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse unblockMember(Long id) {
        AppUser user = getRequiredUser(id);
        requireExistingMembership(user);
        if (user.getMemberStatus() != MemberStatus.BLOCKED) {
            throw new BusinessRuleException("MEMBER_NOT_BLOCKED", "Cet adhérent n'est pas bloqué.");
        }
        user.setBlockedReason(null);
        user.setMemberStatus(
                memberExpirationPolicy.isExpired(user) ? MemberStatus.EXPIRED : MemberStatus.ACTIVE);
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Transactional
    public UserResponse reactivateMembership(Long id) {
        AppUser user = getRequiredUser(id);
        requireExistingMembership(user);
        if (user.getMemberStatus() != MemberStatus.EXPIRED) {
            throw new BusinessRuleException("MEMBER_NOT_EXPIRED", "Cet adhérent n'est pas expiré.");
        }
        if (memberExpirationPolicy.isExpired(user)) {
            throw new BusinessRuleException("CANNOT_REACTIVATE_EXPIRED_MEMBERSHIP",
                    "memberExpirationDate est toujours dans le passé : prolonger la date avant réactivation.");
        }
        user.setMemberStatus(MemberStatus.ACTIVE);
        user.setUpdatedAt(clock.instant());
        return UserResponse.from(user);
    }

    private AppUser getRequiredUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + id + "."));
    }

    private void requireExistingMembership(AppUser user) {
        if (user.getMemberNumber() == null) {
            throw new BusinessRuleException("NOT_A_MEMBER", "Cet utilisateur n'a jamais été adhérent.");
        }
    }
    private void applyMembershipUpdate(AppUser user, UpdateUserRequest request) {
        boolean hasMembership = user.getMemberNumber() != null;
        boolean membershipFieldsProvided = request.isRegistrationDatePresent()
                || request.isMemberExpirationDatePresent()
                || request.isBlockedReasonPresent();

        if (!hasMembership) {
            if (membershipFieldsProvided) {
                throw new BusinessRuleException("MEMBERSHIP_DATA_REQUIRES_EXISTING_MEMBERSHIP",
                        "Les données d'adhésion ne peuvent être modifiées que pour un utilisateur "
                                + "ayant déjà été adhérent.");
            }
            return;
        }

        if (request.isRegistrationDatePresent() && request.getRegistrationDate() == null) {
            throw new BusinessRuleException("REGISTRATION_DATE_REQUIRED",
                    "registrationDate ne peut pas être effacée pour un adhérent existant.");
        }

        LocalDate effectiveRegistrationDate = request.isRegistrationDatePresent()
                ? request.getRegistrationDate() : user.getRegistrationDate();
        LocalDate effectiveExpirationDate = request.isMemberExpirationDatePresent()
                ? request.getMemberExpirationDate() : user.getMemberExpirationDate();
        String effectiveBlockedReason = request.isBlockedReasonPresent()
                ? request.getBlockedReason() : user.getBlockedReason();

        if (effectiveBlockedReason != null && user.getMemberStatus() != MemberStatus.BLOCKED) {
            throw new BusinessRuleException("BLOCKED_REASON_REQUIRES_BLOCKED_STATUS",
                    "blockedReason ne peut être renseigné que si memberStatus vaut BLOCKED.");
        }
        if (effectiveExpirationDate != null && effectiveExpirationDate.isBefore(effectiveRegistrationDate)) {
            throw new BusinessRuleException("MEMBER_EXPIRATION_BEFORE_REGISTRATION",
                    "memberExpirationDate ne peut pas être antérieure à registrationDate.");
        }

        user.setRegistrationDate(effectiveRegistrationDate);
        user.setMemberExpirationDate(effectiveExpirationDate);
        user.setBlockedReason(effectiveBlockedReason);
    }
    private void updateRoles(AppUser user, Set<String> requestedRoleCodes, Long adminUserId) {
        if (requestedRoleCodes.isEmpty()) {
            throw new BusinessRuleException("ROLES_MUST_NOT_BE_EMPTY",
                    "roles ne peut pas être vide : au moins un rôle est requis.");
        }
        if (requestedRoleCodes.contains(ROLE_MEMBER_CODE) && user.getMemberNumber() == null) {
            throw new BusinessRuleException("NEW_MEMBERSHIP_NOT_SUPPORTED_VIA_UPDATE",
                    "L'attribution de ROLE_MEMBER à un utilisateur n'ayant jamais été adhérent "
                            + "n'est pas prise en charge par cet endpoint.");
        }

        Set<Role> targetRoles = new LinkedHashSet<>();
        for (String code : requestedRoleCodes) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessRuleException(
                            "UNKNOWN_ROLE_CODE", "Le rôle demandé n'existe pas : " + code + "."));
            targetRoles.add(role);
        }

        List<UserRole> currentUserRoles = userRoleRepository.findByIdUserId(user.getId());
        Set<Long> currentRoleIds = currentUserRoles.stream()
                .map(userRole -> userRole.getRole().getId()).collect(Collectors.toSet());
        Set<Long> targetRoleIds = targetRoles.stream().map(Role::getId).collect(Collectors.toSet());

        for (UserRole currentUserRole : currentUserRoles) {
            if (!targetRoleIds.contains(currentUserRole.getRole().getId())) {
                userRoleRepository.delete(currentUserRole);
            }
        }

        Instant now = clock.instant();
        AppUser adminReference = appUserRepository.getReferenceById(adminUserId);
        for (Role role : targetRoles) {
            if (!currentRoleIds.contains(role.getId())) {
                UserRole newUserRole = new UserRole();
                newUserRole.setId(new UserRoleId(user.getId(), role.getId()));
                newUserRole.setUser(user);
                newUserRole.setRole(role);
                newUserRole.setAssignedAt(now);
                newUserRole.setAssignedBy(adminReference);
                userRoleRepository.save(newUserRole);
            }
        }
    }
    private void validateMembershipCoherence(boolean memberRoleSelected, CreateUserRequest request) {
        boolean hasMembershipData = request.memberStatus() != null
                || request.registrationDate() != null
                || request.memberExpirationDate() != null
                || request.blockedReason() != null;

        if (memberRoleSelected) {
            if (request.memberStatus() == null) {
                throw new BusinessRuleException("MEMBER_STATUS_REQUIRED",
                        "memberStatus est obligatoire lorsque ROLE_MEMBER est sélectionné.");
            }
            if (request.registrationDate() == null) {
                throw new BusinessRuleException("REGISTRATION_DATE_REQUIRED",
                        "registrationDate est obligatoire lorsque ROLE_MEMBER est sélectionné.");
            }
        } else if (hasMembershipData) {
            throw new BusinessRuleException("MEMBERSHIP_DATA_REQUIRES_MEMBER_ROLE",
                    "Les données d'adhésion ne peuvent être fournies que si ROLE_MEMBER est sélectionné.");
        }

        if (request.blockedReason() != null && request.memberStatus() != MemberStatus.BLOCKED) {
            throw new BusinessRuleException("BLOCKED_REASON_REQUIRES_BLOCKED_STATUS",
                    "blockedReason ne peut être renseigné que si memberStatus vaut BLOCKED.");
        }

        if (request.memberExpirationDate() != null && request.registrationDate() != null
                && request.memberExpirationDate().isBefore(request.registrationDate())) {
            throw new BusinessRuleException("MEMBER_EXPIRATION_BEFORE_REGISTRATION",
                    "memberExpirationDate ne peut pas être antérieure à registrationDate.");
        }
    }
}
