package com.uit.userservice.service;

import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.AdminUserResponse;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.keycloak.KeycloakClient;
import com.uit.userservice.repository.UserRepository;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.sharedkernel.audit.AuditEventDto;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
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
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KeycloakClient keycloakClient;
    private final AuditEventPublisher auditEventPublisher;

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
                true,
                user.getCreatedAt()
        ));
    }

    @Override
    public void lockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        keycloakClient.updateUserStatus(user.getId(), false);
    }

    @Override
    public void unlockUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
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
}
