package com.fileprocessor.application.controller;

import com.fileprocessor.service.AsyncFileProcessor;
import com.fileprocessor.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final AsyncFileProcessor asyncFileProcessor;


    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable("taskId") String taskId) {
        AsyncFileProcessor.TaskInfo info = asyncFileProcessor.getTaskInfo(taskId);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found with ID: " + taskId));
        }
        return ResponseEntity.ok(info);
    }


    @GetMapping("/download/{filename}")
    public ResponseEntity<?> downloadFile(
            @PathVariable("filename") String filename,
            @RequestParam(value = "maxLimit", required = false) Long maxDownloadBytes) {
        log.info("API Download - Filename: {}, maxLimit: {}", filename, maxDownloadBytes);
        return null;
    }

}
