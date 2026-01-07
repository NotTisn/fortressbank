package com.uit.userservice.service;

import com.uit.userservice.client.AccountClient;
import com.uit.userservice.client.CardClient;
import com.uit.userservice.client.CreateAccountInternalRequest;
import com.uit.userservice.dto.request.AdminCreateUserRequest;
import com.uit.userservice.dto.request.AdminUpdateUserRequest;
import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.AccountDto;
import com.uit.userservice.dto.response.AdminCreateUserResponse;
import com.uit.userservice.dto.response.AdminUserResponse;
import com.uit.userservice.dto.response.CardDto;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.keycloak.KeycloakClient;
import com.uit.userservice.repository.UserRepository;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.sharedkernel.audit.AuditEventDto;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KeycloakClient keycloakClient;
    private final AuditEventPublisher auditEventPublisher;
    private final AccountClient accountClient;
    private final CardClient cardClient;

    @Override
    public UserResponse getCurrentUser(Jwt jwt) {
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String name = jwt.hasClaim("name")
                ? jwt.getClaimAsString("name")
                : username;

        User user = userRepository.findById(userId)
                .orElseGet(() -> createFromToken(userId, username, email, name));

        return toResponse(user);
    }

    @Override
    public UserResponse updateCurrentUser(Jwt jwt, UpdateUserRequest request) {
        String userId = jwt.getSubject();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_ALREADY_EXISTS, "User not found"));

        // Capture old values for audit
        Map<String, Object> oldValues = Map.of(
            "fullName", user.getFullName() != null ? user.getFullName() : "",
            "dob", user.getDob() != null ? user.getDob().toString() : "",
            "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : ""
        );

        user.setFullName(request.getFullName());
        user.setDob(request.getDob());
        user.setPhoneNumber(request.getPhoneNumber());
        
        // Log update event
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("user-service")
                .entityType("User")
                .entityId(user.getId())
                .action("UPDATE_USER")
                .userId(userId)
                .oldValues(oldValues)
                .newValues(Map.of(
                    "fullName", request.getFullName(),
                    "dob", request.getDob() != null ? request.getDob().toString() : "",
                    "phoneNumber", request.getPhoneNumber()
                ))
                .changes("User updated profile information")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);

        return toResponse(user);
    }

    @Override
    public UserResponse getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return toResponse(user);
    }

    // HELPERS
    private User createFromToken(String id, String username, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(name);
        User savedUser = userRepository.save(user);

        // Log create event
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("user-service")
                .entityType("User")
                .entityId(savedUser.getId())
                .action("CREATE_USER")
                .userId(savedUser.getId())
                .newValues(Map.of(
                    "username", username,
                    "email", email,
                    "fullName", name
                ))
                .changes("New user registered via Keycloak")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);

        return savedUser;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getCitizenId(),
                user.getDob(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getIsFaceRegistered()
        );
    }

    // ADMIN SECTION
    @Override
    public Page<AdminUserResponse> searchUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> usersPage = userRepository.searchUsers(keyword, pageable);

        return usersPage.map(user -> new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getCitizenId(),
                user.getDob(),
                user.getPhoneNumber(),
                user.getIsEnable(),
                user.getCreatedAt()
        ));
    }

    @Override
    public void lockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setIsEnable(false);
        userRepository.save(user);
        keycloakClient.updateUserStatus(user.getId(), false);
    }

    @Override
    public void unlockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setIsEnable(true);
        userRepository.save(user);
        keycloakClient.updateUserStatus(user.getId(), true);
    }

    @Override
    public AdminUserResponse getUserDetailForAdmin(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Lấy status thực tế từ Keycloak
        Map<String, Object> keycloakInfo = keycloakClient.getUserFromKeycloak(userId);
        boolean isEnabled = (boolean) keycloakInfo.getOrDefault("enabled", true);

        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getCitizenId(),
                user.getDob(),
                user.getPhoneNumber(),
                isEnabled,
                user.getCreatedAt()
        );
    }

    @Override
    public AdminCreateUserResponse createUserByAdmin(AdminCreateUserRequest request) {
        log.info("Admin creating user with username: {}", request.getUsername());

        // Check uniqueness
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AppException(ErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_EXISTS);
        }
        if (userRepository.findByCitizenId(request.getCitizenId()).isPresent()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "CITIZEN_ID_ALREADY_EXISTS");
        }
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PHONE_NUMBER_ALREADY_EXISTS");
        }

        // 1. Create user in Keycloak
        String keycloakUserId = keycloakClient.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFullName(),
                request.getPassword(),
                request.getPhoneNumber()
        );

        // 1.1. Assign roles to user in Keycloak
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            keycloakClient.assignRealmRolesToUser(keycloakUserId, request.getRoles());
        }

        // 2. Create user profile in local DB
        User user = new User();
        user.setId(keycloakUserId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setDob(request.getDob());
        user.setCitizenId(request.getCitizenId());
        user.setPhoneNumber(request.getPhoneNumber());

        user = userRepository.save(user);

        // Log creation audit event
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("user-service")
                .entityType("User")
                .entityId(user.getId())
                .action("ADMIN_CREATE_USER")
                .userId("admin") // TODO: Get actual admin userId from context if available
                .newValues(Map.of(
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "citizenId", user.getCitizenId(),
                        "phoneNumber", user.getPhoneNumber()
                ))
                .changes("Admin created new user")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);

        // 3. Create account for user
        AccountDto account = null;
        try {
            log.info("Creating account for user {} with accountNumberType {}", user.getId(), request.getAccountNumberType());
            CreateAccountInternalRequest accountRequest = CreateAccountInternalRequest.builder()
                    .accountNumberType(request.getAccountNumberType())
                    .phoneNumber(request.getPhoneNumber())
                    .pin(request.getPin())
                    .build();

            account = accountClient.createAccountForUser(user.getId(), accountRequest, user.getFullName()).getData();
            log.info("Account created successfully for user {} with accountNumber {}", user.getId(), account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to create account for user {}: {}", user.getId(), e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Failed to create account for user");
        }

        // 4. Create card if requested
        CardDto card = null;
        if (Boolean.TRUE.equals(request.getCreateCard()) && account != null) {
            try {
                log.info("Issuing card for account {}", account.getAccountId());
                card = cardClient.issueCard(account.getAccountId(), user.getFullName()).getData();
                log.info("Card issued successfully for account {}", account.getAccountId());
            } catch (Exception e) {
                log.warn("Failed to issue card for account {}: {}", account.getAccountId(), e.getMessage());
                // Don't fail the whole operation if card creation fails
            }
        }

        return AdminCreateUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .citizenId(user.getCitizenId())
                .dob(user.getDob())
                .phoneNumber(user.getPhoneNumber())
                .createdAt(user.getCreatedAt())
                .account(account)
                .card(card)
                .build();
    }

    @Override
    public AdminUserResponse updateUserByAdmin(String userId, AdminUpdateUserRequest request) {
        log.info("Admin updating user {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Capture old values for audit
        Map<String, Object> oldValues = Map.of(
                "fullName", user.getFullName() != null ? user.getFullName() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "dob", user.getDob() != null ? user.getDob().toString() : "",
                "phoneNumber", user.getPhoneNumber() != null ? user.getPhoneNumber() : ""
        );

        // Update user fields
        user.setFullName(request.getFullName());

        // Check email uniqueness if changing
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new AppException(ErrorCode.EMAIL_EXISTS);
            }
            user.setEmail(request.getEmail());
        }

        user.setDob(request.getDob());

        // Check phone number uniqueness if changing
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "PHONE_NUMBER_ALREADY_EXISTS");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }

        user = userRepository.save(user);

        // Log update event
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("user-service")
                .entityType("User")
                .entityId(user.getId())
                .action("ADMIN_UPDATE_USER")
                .userId("admin") // TODO: Get actual admin userId from context if available
                .oldValues(oldValues)
                .newValues(Map.of(
                        "fullName", request.getFullName(),
                        "email", request.getEmail() != null ? request.getEmail() : "",
                        "dob", request.getDob() != null ? request.getDob().toString() : "",
                        "phoneNumber", request.getPhoneNumber() != null ? request.getPhoneNumber() : ""
                ))
                .changes("Admin updated user information")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);

        // Get enabled status from Keycloak
        Map<String, Object> keycloakInfo = keycloakClient.getUserFromKeycloak(userId);
        boolean isEnabled = (boolean) keycloakInfo.getOrDefault("enabled", true);

        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getCitizenId(),
                user.getDob(),
                user.getPhoneNumber(),
                isEnabled,
                user.getCreatedAt()
        );
    }
}
