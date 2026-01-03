package com.uit.userservice.service;

import com.uit.userservice.dto.request.AdminCreateUserRequest;
import com.uit.userservice.dto.request.AdminUpdateUserRequest;
import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.AdminCreateUserResponse;
import com.uit.userservice.dto.response.AdminUserResponse;
import com.uit.userservice.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {

    UserResponse getCurrentUser(Jwt jwt);
    UserResponse updateCurrentUser(Jwt jwt, UpdateUserRequest request);
    UserResponse getUserById(String id);

    Page<AdminUserResponse> searchUsers(String keyword, int page, int size);
    void lockUser(String userId);
    void unlockUser(String userId);
    AdminUserResponse getUserDetailForAdmin(String userId);

    // Admin create and update user
    AdminCreateUserResponse createUserByAdmin(AdminCreateUserRequest request);
    AdminUserResponse updateUserByAdmin(String userId, AdminUpdateUserRequest request);
}
