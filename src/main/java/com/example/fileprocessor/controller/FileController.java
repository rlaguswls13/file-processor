package com.example.fileprocessor.controller;

import com.example.fileprocessor.model.AddressBook;
import com.example.fileprocessor.model.TargetData;
import com.example.fileprocessor.repository.BatchInsertRepository;
import com.example.fileprocessor.security.FileSecurityService;
import com.example.fileprocessor.service.AsyncFileProcessor;
import com.example.fileprocessor.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileSecurityService securityService;
    private final FileStorageService storageService;
    private final AsyncFileProcessor asyncFileProcessor;
    private final BatchInsertRepository batchInsertRepository;

    private static final long DEFAULT_MAX_LIMIT_BYTES = 50 * 1024 * 1024; // 기본 50MB 제한

    /**
     * 1) 파일 업로드 API (비동기 처리 트리거 및 Task ID 즉시 반환)
     * 
     * @param file     업로드 대상 파일
     * @param taskType 작업 구분 ("ADDRESS_BOOK" or "TARGET_DATA")
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("taskType") String taskType) {
        
        String originalFilename = file.getOriginalFilename();
        log.info("Received upload request for file: {}, Task Type: {}", originalFilename, taskType);

        try {
            // [보안 1 단계] 업로드 크기 제한 검증
            securityService.validateFileSize(file.getSize(), DEFAULT_MAX_LIMIT_BYTES);

            // [보안 2 단계] 파일명 및 확장자 (Whitelist / Blacklist) 정규식 검사 + Path Traversal 방지
            securityService.validateFileName(originalFilename);

            // [저장 단계] 업로드 임시 스토리지에 UUID 파일로 안전하게 복제
            File tempFile = storageService.storeFile(file);

            // [보안 3 단계] Magic Number 정합성 검사 (바이너리 위조 여부)
            securityService.validateFileSignature(tempFile);

            // [비동기 등록] Task 상태 등록 및 스레드 풀 트리거
            String taskId = asyncFileProcessor.registerTask(originalFilename, taskType);
            asyncFileProcessor.processFileAsync(taskId, tempFile, taskType);

            // 202 Accepted 상태로 Task ID를 즉시 리턴하여 병목 완화
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "File upload successful. Processing started asynchronously.",
                            "taskId", taskId,
                            "status", "SUBMITTED"
                    ));

        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("Security or validation constraint violated: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("IO Exception occurred during file processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File I/O failure during processing."));
        }
    }

    /**
     * 2) 파일 다운로드 API (크기 제한 옵션 지원)
     * 
     * @param filename          다운로드 대상 로컬 파일명
     * @param maxDownloadBytes  선택적 다운로드 크기 제한 제한 옵션 (bytes)
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<?> downloadFile(
            @PathVariable("filename") String filename,
            @RequestParam(value = "maxLimit", required = false) Long maxDownloadBytes) {
        
        log.info("Received download request for file: {}, size limit: {}", filename, maxDownloadBytes);

        try {
            // [보안 1 단계] 파일명 및 경로 이탈 체크
            securityService.validateFileName(filename);

            // 스토리지로부터 파일 로드
            Resource resource = storageService.loadFileAsResource(filename);
            long fileLength = resource.contentLength();

            // [보안 2 단계] 다운로드 크기 제한 옵션 적용 (요청 시 제한값이 인입된 경우)
            if (maxDownloadBytes != null) {
                securityService.validateFileSize(fileLength, maxDownloadBytes);
            }

            // HTTP Content-Disposition 헤더 및 MIME 설정으로 안전한 스트림 송출
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Requested file does not exist or is not readable."));
        }
    }

    /**
     * 3) 비동기 파싱 작업 진행 상태 및 결과 조회 API
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable("taskId") String taskId) {
        AsyncFileProcessor.TaskInfo info = asyncFileProcessor.getTaskInfo(taskId);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found with ID: " + taskId));
        }
        return ResponseEntity.ok(info);
    }

    /**
     * 4) API를 통한 직접 대량 JSON 데이터 수신 처리 (주소록 bulk 저장용)
     */
    @PostMapping("/json-data/address-book")
    public ResponseEntity<?> importAddressBookJson(@RequestBody List<AddressBook> list) {
        log.info("Received direct API request to import {} AddressBook records", list.size());
        try {
            list.forEach(item -> item.setCreatedAt(java.time.LocalDateTime.now()));
            // bulk insert 수행
            batchInsertRepository.saveAddressBooksInBatch(list);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully imported data via direct API call.",
                    "recordsImported", list.size()
            ));
        } catch (Exception e) {
            log.error("Failed to import address book via API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 4-2) API를 통한 직접 대량 JSON 데이터 수신 처리 (타겟팅 데이터 bulk 저장용)
     */
    @PostMapping("/json-data/target-data")
    public ResponseEntity<?> importTargetDataJson(@RequestBody List<TargetData> list) {
        log.info("Received direct API request to import {} TargetData records", list.size());
        try {
            list.forEach(item -> item.setCreatedAt(java.time.LocalDateTime.now()));
            // bulk insert 수행
            batchInsertRepository.saveTargetDataInBatch(list);
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully imported data via direct API call.",
                    "recordsImported", list.size()
            ));
        } catch (Exception e) {
            log.error("Failed to import target data via API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
