package com.uit.userservice.client;

import com.uit.userservice.dto.response.ai.FaceRegisterResponse;
import com.uit.userservice.dto.response.ai.FaceVerifyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(name = "ai-service", url = "${fortress-bank.ai-service.url}")
public interface FaceIdClient {

    @PostMapping(value = "/api/v1/register-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FaceRegisterResponse registerFace(
            @RequestPart("user_id") String userId,
            @RequestPart("files") List<MultipartFile> files
    );

    @PostMapping(value = "/api/v1/verify-transaction", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FaceVerifyResponse verifyTransaction(
            @RequestPart("user_id") String userId,
            @RequestPart("files") List<MultipartFile> files
    );
}