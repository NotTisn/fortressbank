package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.request.AdminCreateUserRequest;
import com.uit.userservice.dto.request.AdminUpdateUserRequest;
import com.uit.userservice.dto.response.AdminCreateUserResponse;
import com.uit.userservice.dto.response.AdminUserResponse;
import com.uit.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminController {

    private final UserService userService;

    // API search user: GET /admin/users?keyword=...&page=0&size=10
    @GetMapping
    public ApiResponse<Page<AdminUserResponse>> getUsers(
            @RequestParam(name = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.success(userService.searchUsers(keyword, page, size));
    }

    // API xem chi tiết user: GET /admin/users/{userId}
    @GetMapping("/{userId}")
    public ApiResponse<AdminUserResponse> getUserDetail(@PathVariable("userId") String userId) {
        return ApiResponse.success(userService.getUserDetailForAdmin(userId));
    }

    // API khóa tài khoản: PUT /admin/users/{userId}/lock
    @PutMapping("/{userId}/lock")
    public ApiResponse<String> lockUser(@PathVariable("userId") String userId) {
        userService.lockUser(userId);
        return ApiResponse.success("User account has been locked successfully.");
    }

    // API mở khóa: PUT /admin/users/{userId}/unlock
    @PutMapping("/{userId}/unlock")
    public ApiResponse<String> unlockUser(@PathVariable("userId") String userId) {
        userService.unlockUser(userId);
        return ApiResponse.success("User account has been unlocked successfully.");
    }

    // API tạo user (bao gồm account và card): POST /admin/users
    @PostMapping
    public ApiResponse<AdminCreateUserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ApiResponse.success(userService.createUserByAdmin(request));
    }

    // API cập nhật user: PUT /admin/users/{userId}
    @PutMapping("/{userId}")
    public ApiResponse<AdminUserResponse> updateUser(
            @PathVariable("userId") String userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return ApiResponse.success(userService.updateUserByAdmin(userId, request));
    }
}