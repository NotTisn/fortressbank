package com.uit.userservice.service;

import com.uit.userservice.dto.response.FaceRegistrationResult;
import com.uit.userservice.dto.response.FaceVerificationResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FaceIdService {
    FaceRegistrationResult registerFace(String userId, List<MultipartFile> files);
    FaceRegistrationResult updateFace(String userId, List<MultipartFile> files);
    FaceVerificationResult verifyFace(String userId, List<MultipartFile> files, String transactionId);
}