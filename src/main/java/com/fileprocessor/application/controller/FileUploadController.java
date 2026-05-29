package com.fileprocessor.application.controller;

import com.fileprocessor.model.FileCategory;
import com.fileprocessor.model.TaskType;
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

    // ==========================================
    // [API 1] 단순 파일 업로드
    // ==========================================
    @PostMapping(value = "/simple/{category}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSimpleFile(
            @PathVariable("category") FileCategory category,
            @RequestParam("file") MultipartFile file) {

        log.info("API Simple Upload - Category: {}, Filename: {}", category, file.getOriginalFilename());
        try {

            String storedFilename = fileService.uploadSimpleFile(category, file, null);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Simple file uploaded and saved to DB successfully.",
                            "storedFilename", storedFilename,
                            "category", category
                    ));
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("Simple upload constraint violated: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal simple upload failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Simple file processing failed."));
        }
    }

    // ==========================================
    // [API 2] 비동기 대용량 파싱 및 벌크 적재 파이프라인 (스레드 풀 포화 시 대피소 연동)
    // ==========================================
    @PostMapping(value = "/task/{category}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTaskFile(
            @PathVariable("category") FileCategory category,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskType") TaskType taskType,
            @RequestParam(value = "delimiter") String delimiter) {
        
        String originalFilename = file.getOriginalFilename();
        log.info("API Async Task Upload - Category: {}, Filename: {}, TaskType: {}, Delimiter: {}", category, originalFilename, taskType, delimiter);
        try {
            String taskId = fileService.uploadTaskTypeFile(category, file, taskType, delimiter, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "Upload and parsing process started successfully.",
                            "taskId", taskId,
                            "category", category
                    ));
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("Task upload constraint violated: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal async task upload failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Async task processing failed."));
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
        return null;
    }

    @PostMapping(value = "/body/text/{target}", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> importRawText(
            @PathVariable("target") String target,
            @RequestBody String rawText,
            @RequestParam(value = "taskType", required = false) TaskType taskType,
            @RequestParam(value = "delimiter", required = false) String delimiter) {
        
        log.info("API Body Import [TEXT -> {}] - Length: {}, TaskType: {}, Delimiter: {}", target, rawText.length(), taskType, delimiter);
        return null;
    }
}
