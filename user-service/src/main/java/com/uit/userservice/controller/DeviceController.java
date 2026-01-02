package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.device.*;
import com.uit.userservice.entity.Device;
import com.uit.userservice.repository.DeviceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceRepository deviceRepository;

    @PostMapping("/register")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<DeviceRegisterResponse>> registerDevice(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeviceRegisterRequest request) {

        String userId = jwt.getSubject();

        // Save device
        Device device = Device.builder()
                .deviceId(request.getDeviceId())
                .userId(userId)
                .name(request.getName())
                .publicKeyPem(request.getPublicKeyPem())
                .registeredAt(LocalDateTime.now())
                .isActive(true)
                .build();

        deviceRepository.save(device);

        DeviceRegisterResponse resp = new DeviceRegisterResponse(device.getDeviceId(), device.getRegisteredAt());
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @GetMapping("")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<List<DeviceRegisterResponse>>> listDevices(
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<DeviceRegisterResponse> devices = deviceRepository.findByUserId(userId)
                .stream()
                .map(d -> new DeviceRegisterResponse(d.getDeviceId(), d.getRegisteredAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<String>> revokeDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("deviceId") String deviceId) {
        String userId = jwt.getSubject();
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        if (!device.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, "Forbidden", null));
        }
        device.setIsActive(false);
        deviceRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success("Device revoked"));
    }

    /**
     * Internal endpoint: verify a challenge signature sent by device.
     * This is intended to be called by transaction-service or other internal services.
     */
    @PostMapping("/internal/verify-signature")
    public ResponseEntity<ApiResponse<DeviceVerifySignatureResponse>> internalVerifySignature(
            @RequestBody DeviceVerifySignatureRequest request) {

        log.info("Internal verify signature for user={}, device={}", request.getUserId(), request.getDeviceId());

        Device device = deviceRepository.findById(request.getDeviceId()).orElse(null);
        if (device == null || !device.getUserId().equals(request.getUserId()) || !Boolean.TRUE.equals(device.getIsActive())) {
            return ResponseEntity.ok(ApiResponse.success(new DeviceVerifySignatureResponse(false, "Device not found or inactive")));
        }

        boolean valid;
        try {
            valid = verifySignature(device.getPublicKeyPem(), request.getChallenge(), request.getSignatureBase64());
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return ResponseEntity.ok(ApiResponse.success(new DeviceVerifySignatureResponse(false, "Verification failed")));
        }

        if (valid) {
            device.setLastSeenAt(LocalDateTime.now());
            deviceRepository.save(device);
            return ResponseEntity.ok(ApiResponse.success(new DeviceVerifySignatureResponse(true, "Verified")));
        } else {
            return ResponseEntity.ok(ApiResponse.success(new DeviceVerifySignatureResponse(false, "Invalid signature")));
        }
    }

    // ==================== Helpers ====================
    private boolean verifySignature(String publicKeyPem, String challenge, String signatureBase64) throws Exception {
        byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
        PublicKey pk = parsePublicKeyPem(publicKeyPem);

        // Try common algorithms based on key type
        String algorithm = pk.getAlgorithm();
        Signature sig;
        if ("RSA".equalsIgnoreCase(algorithm)) {
            sig = Signature.getInstance("SHA256withRSA");
        } else if ("EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm)) {
            sig = Signature.getInstance("SHA256withECDSA");
        } else {
            // Default to RSA
            sig = Signature.getInstance("SHA256withRSA");
        }

        sig.initVerify(pk);
        sig.update(challenge.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return sig.verify(sigBytes);
    }

    private PublicKey parsePublicKeyPem(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);

        // Try RSA first
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception ignored) {
        }
        // Try EC
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(spec);
    }
}
