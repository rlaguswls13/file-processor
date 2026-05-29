package com.fileprocessor.service;

import com.fileprocessor.service.parser.FileParser;
import com.fileprocessor.service.parser.FileParserFactory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncFileProcessor {

    private final FileParserFactory parserFactory;
    private final FileStorageService storageService;

    // 인메모리 비동기 태스크 상태 추적용 맵 (실무에서는 Redis 등으로 분산 구성 가능)
    private final Map<String, TaskInfo> taskStatusRegistry = new ConcurrentHashMap<>();

    @Data
    @Builder
    @AllArgsConstructor
    public static class TaskInfo {
        private String taskId;
        private String status; // SUBMITTED, PROCESSING, COMPLETED, FAILED
        private String taskType; // ADDRESS_BOOK, TARGET_DATA
        private String fileName;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /**
     * 비동기 작업 등록 및 초기 상태 세팅
     */
    public String registerTask(String fileName, String taskType) {
        String taskId = java.util.UUID.randomUUID().toString();
        TaskInfo info = TaskInfo.builder()
                .taskId(taskId)
                .status("SUBMITTED")
                .taskType(taskType)
                .fileName(fileName)
                .startTime(LocalDateTime.now())
                .build();
        taskStatusRegistry.put(taskId, info);
        return taskId;
    }

    /**
     * 특정 Task ID의 현재 상태 정보를 조회합니다.
     */
    public TaskInfo getTaskInfo(String taskId) {
        return taskStatusRegistry.get(taskId);
    }

    /**
     * 비동기 멀티스레드 기반 파일 파싱 및 DB 배치 삽입 트리거
     * AsyncConfig에 설정한 'fileTaskExecutor' 스레드 풀을 활용합니다.
     */
    @Async("fileTaskExecutor")
    public void processFileAsync(String taskId, File file, String taskType) {
        TaskInfo info = taskStatusRegistry.get(taskId);
        if (info == null) {
            log.error("Task info not found for taskId: {}", taskId);
            return;
        }

        info.setStatus("PROCESSING");
        log.info("[Async Task Started] Task ID: {}, File: {}, Thread: {}", taskId, file.getName(), Thread.currentThread().getName());

        try {
            // 1. 확장자에 상응하는 스트리밍 파서 획득
            FileParser parser = parserFactory.getParser(file.getName());

            // 2. 파싱 및 벌크 삽입 실행
            parser.parseAndSave(file, taskType);

            // 3. 작업 완수 기록
            info.setStatus("COMPLETED");
            info.setEndTime(LocalDateTime.now());
            log.info("[Async Task Success] Task ID: {} successfully completed.", taskId);

        } catch (Exception e) {
            log.error("[Async Task Failed] Task ID: {} failed due to: {}", taskId, e.getMessage(), e);
            info.setStatus("FAILED");
            info.setErrorMessage(e.getMessage());
            info.setEndTime(LocalDateTime.now());
        } finally {
            // 4. 로컬 디스크에 임시 생성했던 파일 안전하게 삭제 처리 (리소스 리크 방지)
            storageService.deleteFile(file);
        }
    }
}
