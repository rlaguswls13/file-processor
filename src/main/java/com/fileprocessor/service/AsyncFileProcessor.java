package com.fileprocessor.service;

import com.fileprocessor.model.TaskType;
import com.fileprocessor.model.TaskStatus;
import com.fileprocessor.repository.BatchInsertRepository;
import com.fileprocessor.service.file.FileStorageService;
import com.fileprocessor.service.parser.FileParser;
import com.fileprocessor.service.parser.FileParserFactory;
import com.fileprocessor.service.task.TaskProcessor;
import com.fileprocessor.service.task.TaskProcessorRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncFileProcessor {

    private final FileParserFactory parserFactory;
    private final FileStorageService storageService;
    private final TaskProcessorRegistry processorRegistry;
    private final BatchInsertRepository batchInsertRepository;
    private final JdbcTemplate jdbcTemplate;

    @Data
    @Builder
    @AllArgsConstructor
    public static class TaskInfo {
        private String taskId;
        private TaskStatus status; 
        private TaskType taskType; 
        private String fileName;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /**
     * 비동기 작업 등록 및 초기 DB 상태 세팅
     */
    public String registerTask(String fileName, TaskType taskType, String storedPath, String delimiter, long fileSize, TaskStatus initialStatus) {
        String taskId = java.util.UUID.randomUUID().toString();
        
        // 1. 상태 등록 (async_task)
        batchInsertRepository.saveAsyncTask(taskId, taskType, initialStatus);
        
        // 2. 파일 메타데이터 등록 (async_task_file - Logical FK)
        batchInsertRepository.saveAsyncTaskFile(taskId, fileName, storedPath, delimiter, fileSize);
        
        return taskId;
    }

    /**
     * 특정 Task ID의 현재 상태 정보를 DB에서 실시간으로 조회합니다. (서버 리부팅 등에도 추적 유지)
     */
    public TaskInfo getTaskInfo(String taskId) {
        String taskSql = "SELECT task_id, task_type, status, error_message, created_at, updated_at FROM async_task WHERE task_id = ?";
        String fileSql = "SELECT original_name FROM async_task_file WHERE task_id = ?";
        
        try {
            return jdbcTemplate.queryForObject(taskSql, (rs, rowNum) -> {
                String originalName = "unknown";
                try {
                    originalName = jdbcTemplate.queryForObject(fileSql, String.class, taskId);
                } catch (Exception ignored) {}
                
                return TaskInfo.builder()
                        .taskId(rs.getString("task_id"))
                        .status(TaskStatus.valueOf(rs.getString("status")))
                        .taskType(TaskType.valueOf(rs.getString("task_type")))
                        .fileName(originalName)
                        .errorMessage(rs.getString("error_message"))
                        .startTime(rs.getTimestamp("created_at").toLocalDateTime())
                        .endTime(rs.getTimestamp("updated_at").toLocalDateTime())
                        .build();
            }, taskId);
        } catch (Exception e) {
            log.warn("Task not found in DB for taskId: {}", taskId);
            return null;
        }
    }

    /**
     * 비동기 멀티스레드 기반 파일 파싱 및 DB 배치 삽입 트리거 (사용자 정의 구분자 인자 추가)
     */
    @Async("fileTaskExecutor")
    public void processFileAsync(String taskId, File file, TaskType taskType, String delimiter) {
        log.info("[Async Task Started] Task ID: {}, File: {}, Type: {}, Delimiter: {}, Thread: {}", 
                taskId, file.getName(), taskType, delimiter, Thread.currentThread().getName());

        batchInsertRepository.updateTaskStatus(taskId, TaskStatus.PROCESSING, null);

        try {
            FileParser parser = parserFactory.getParser(file.getName());
            TaskProcessor processor = processorRegistry.getProcessor(taskType);

            parser.parse(file, processor, delimiter);

            batchInsertRepository.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);
            log.info("[Async Task Success] Task ID: {} successfully completed.", taskId);

        } catch (Exception e) {
            log.error("[Async Task Failed] Task ID: {} failed due to: {}", taskId, e.getMessage(), e);
            batchInsertRepository.updateTaskStatus(taskId, TaskStatus.FAILED, e.getMessage());
        } finally {
            storageService.deleteFile(file);
        }
    }
}
