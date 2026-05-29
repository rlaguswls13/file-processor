package com.fileprocessor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync      // 비동기 처리 활성화
@EnableScheduling // 스케줄링 활성화 (병목 분산 스케줄링 트리거 기능용)
public class AsyncConfig {

    /**
     * 파일 파싱 및 대용량 DB 저장 전용 비동기 스레드 풀
     */
    @Bean(name = "fileTaskExecutor")
    public Executor fileTaskExecutor() {
        log.info("Configuring ThreadPoolTaskExecutor for file processing tasks...");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 1. 코어 스레드 수
        executor.setCorePoolSize(5);
        // 2. 최대 스레드 수 (대용량 연산 시 확장)
        executor.setMaxPoolSize(10);
        // 3. 작업 큐 용량
        executor.setQueueCapacity(100);
        // 4. 스레드 이름 접두사
        executor.setThreadNamePrefix("FileProcExecutor-");
        
        // 5. 큐가 포화 상태일 때 호출자 스레드(Request-handling thread)에서 작업을 수행하게 하여 시스템의 부하를 역압박(Backpressure)으로 제어함
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 6. 애플리케이션 종료 시 진행 중인 비동기 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
