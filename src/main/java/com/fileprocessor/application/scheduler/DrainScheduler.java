package com.fileprocessor.application.scheduler;

import com.fileprocessor.service.AsyncTaskScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrainScheduler {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final AsyncTaskScheduler asyncTaskScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void startSchedulerOnApplicationReady() {
        log.info("Application is completely started and ready. Initiating task drainage scheduler...");
        Instant startTime = Instant.now().plusSeconds(3);
        taskScheduler.scheduleWithFixedDelay(
                asyncTaskScheduler::drainQueuedTasks,
                startTime,
                Duration.ofSeconds(10)
        );
    }
}
