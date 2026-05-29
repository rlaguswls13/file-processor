package com.fileprocessor.controller;

import com.fileprocessor.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileService fileService;

    @PostMapping(value = "/{category}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @PathVariable("category") String category,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "simple", defaultValue = "false") boolean simple,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String uploadUser) {
        
        String originalFilename = file.getOriginalFilename();
        log.info("API Integrated Upload - Category: {}, Filename: {}, SimpleMode: {}, User: {}", category, originalFilename, simple, uploadUser);
        try {
            if (simple) {
                // 단순 파일 업로드 흐름 (보안 검사 후 로컬 저장 + DB 저장)
                String storedFilename = fileService.uploadSimpleFile(category, file, uploadUser);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of(
                                "message", "Simple file uploaded and saved to DB successfully.",
                                "storedFilename", storedFilename,
                                "category", category
                        ));
            } else {
                // 타겟팅 파일 업로드 흐름 (비동기 파싱 실행 - 기존 흐름)
                String taskId = fileService.uploadMultipartFile(category, file, taskType);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of(
                                "message", "Upload process started successfully.",
                                "taskId", taskId,
                                "category", category
                        ));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("Upload constraint violated: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal upload failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed."));
        }
    }

    // ==========================================
    // [카테고리 2] Request Body 직접 유입 (PathVariable 기반 대칭 구조)
    // ==========================================

    @PostMapping(value = "/body/json/{target}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importJson(
            @PathVariable("target") String target,
            @RequestBody String jsonContent) {
        
        log.info("API Body Import [JSON -> {}] - Content Length: {}", target, jsonContent.length());
        try {
            fileService.importJsonBody(target, jsonContent);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "target", target));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid target path parameter: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Dynamic JSON deserialization & import failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to import JSON data."));
        }
    }

    @PostMapping(value = "/body/text/{target}", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> importRawText(
            @PathVariable("target") String target,
            @RequestBody String rawText,
            @RequestParam(value = "taskType", required = false) String taskType) {
        
        String effectiveTaskType = (taskType != null && !taskType.isBlank()) ? taskType : target.toUpperCase() + "_BODY_UPLOAD";
        log.info("API Body Import [TEXT -> {}] - Length: {}", target, rawText.length());
        
        try {
            String taskId = fileService.uploadRawText(rawText, effectiveTaskType);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "Raw text data submitted successfully.",
                            "taskId", taskId,
                            "target", target
                    ));
        } catch (Exception e) {
            log.error("Raw text upload processing failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
