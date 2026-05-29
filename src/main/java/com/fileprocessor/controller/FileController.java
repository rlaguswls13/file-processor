package com.fileprocessor.controller;

import com.fileprocessor.service.AsyncFileProcessor;
import com.fileprocessor.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final AsyncFileProcessor asyncFileProcessor;

    /**
     * 1) 파일 다운로드 API (크기 제한 옵션 지원 및 검증 FileService 위임)
     * 
     * @param filename          다운로드 대상 로컬 파일명
     * @param maxDownloadBytes  선택적 다운로드 크기 제한 제한 옵션 (bytes)
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<?> downloadFile(
            @PathVariable("filename") String filename,
            @RequestParam(value = "maxLimit", required = false) Long maxDownloadBytes) {
        
        log.info("API Download - Filename: {}, maxLimit: {}", filename, maxDownloadBytes);

        try {
            Resource resource = fileService.loadSecureFileForDownload(filename, maxDownloadBytes);

            // HTTP Content-Disposition 헤더 및 MIME 설정으로 안전한 스트림 송출
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Requested file does not exist or is not readable."));
        } catch (Exception e) {
            log.error("Secure file download processing failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Secure download failed."));
        }
    }

    /**
     * 2) 비동기 파싱 작업 진행 상태 및 결과 조회 API
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
}
