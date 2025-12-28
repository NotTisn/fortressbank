package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.request.ChangePasswordRequest;
import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.service.AuthService;
import com.uit.userservice.service.FaceIdService;
import com.uit.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final FaceIdService faceIdService;

    @GetMapping
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(userService.getCurrentUser(jwt));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateCurrentUser(jwt, request));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ChangePasswordRequest request) {

        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");

        authService.changePassword(request, userId, username);
        return ApiResponse.success(null);
    }


    @PostMapping(value = "/update-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<com.uit.userservice.dto.response.FaceRegistrationResult> updateFace(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("files") List<MultipartFile> files
    ) {
        String userId = jwt.getSubject();
        var result = faceIdService.updateFace(userId, files);

        if (!result.isSuccess()) {
            return ApiResponse.error(400, result.getMessage(), null);
        }

        return ApiResponse.success(result);
    }

    @PostMapping(value = "/verify-transaction", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<com.uit.userservice.dto.response.FaceVerificationResult> verifyTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "transactionId", required = false) String transactionId
    ) {
        String userId = jwt.getSubject();
        var result = faceIdService.verifyFace(userId, files, transactionId);

        return ApiResponse.success(result);
    }
}
