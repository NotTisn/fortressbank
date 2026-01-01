package com.uit.userservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class FaceRequest {
    @NotNull(message = "FILES_REQUIRED")
    private List<MultipartFile> files;
}
