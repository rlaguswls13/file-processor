package com.fileprocessor.service;

import com.fileprocessor.config.properties.AsyncProperties;
import com.fileprocessor.model.TaskStatus;
import com.fileprocessor.repository.BatchInsertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskScheduler {

    private final ThreadPoolTaskExecutor fileTaskExecutor;
    private final AsyncProperties asyncProperties;
    private final BatchInsertRepository repository;
    private final AsyncFileProcessor asyncFileProcessor;

    public void drainQueuedTasks() {
        int currentQueueSize = fileTaskExecutor.getQueueSize();
        int maxCapacity = asyncProperties.getQueueCapacity();
        
        if (currentQueueSize >= maxCapacity) {
            log.debug("Async task executor queue is currently full ({}/{}). Skipping drain cycle.", currentQueueSize, maxCapacity);
            return;
        }

        // DB 큐에서 Status가 QUEUED인 가장 오래된 태스크 1건 획득
        BatchInsertRepository.QueuedTask task = repository.fetchOldestQueuedTask();
        if (task == null) {
            return;
        }

        log.info("[Scheduler] Found queued fallback task: {} (storedPath: {}). Drainage initiated.", task.getTaskId(), task.getStoredPath());
        
        // 상태를 즉시 'SUBMITTED'로 업데이트하여 중복 스케줄링 제출 방지
        repository.updateTaskStatus(task.getTaskId(), TaskStatus.SUBMITTED, null);

        try {
            File file = new File(task.getStoredPath());
            if (!file.exists()) {
                throw new java.io.FileNotFoundException("Fallback task file not found at path: " + task.getStoredPath());
            }
            
            // 비동기 파싱 스레드풀로 전송
            asyncFileProcessor.processFileAsync(task.getTaskId(), file, task.getTaskType(), task.getDelimiter());
            log.info("[Scheduler] Successfully submitted task {} to ThreadPoolTaskExecutor.", task.getTaskId());
        } catch (Exception e) {
            log.error("[Scheduler] Failed to submit queued task {} to executor: {}", task.getTaskId(), e.getMessage());
            repository.updateTaskStatus(task.getTaskId(), TaskStatus.FAILED, e.getMessage());
        }
    }
}
