package com.fileprocessor.service.task.processor;

import com.fileprocessor.model.TaskType;
import com.fileprocessor.model.Whitelist;
import com.fileprocessor.repository.BatchInsertRepository;
import com.fileprocessor.service.task.TaskProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WhitelistTaskProcessor implements TaskProcessor {
    private final BatchInsertRepository repository;
    private final List<Whitelist> chunk = new ArrayList<>(1000);

    private int userIdIdx = -1;
    private int consentedIdx = -1;
    private int validIdx = -1;

    @Override
    public TaskType getSupportedType() {
        return TaskType.WHITELIST;
    }

    @Override
    public void initializeHeaders(Map<String, Integer> headerIndexMap) {
        if (headerIndexMap == null) return;
        this.userIdIdx = headerIndexMap.getOrDefault("user_id", -1);
        if (this.userIdIdx == -1) {
            this.userIdIdx = headerIndexMap.getOrDefault("id", -1);
        }
        this.consentedIdx = headerIndexMap.getOrDefault("consented_at", -1);
        this.validIdx = headerIndexMap.getOrDefault("valid_until", -1);
    }

    @Override
    public synchronized void processRow(String[] tokens) throws Exception {
        if (tokens == null || tokens.length == 0) return;

        String userId = getSafeToken(tokens, userIdIdx);
        String consentedAtStr = getSafeToken(tokens, consentedIdx);
        String validUntilStr = getSafeToken(tokens, validIdx);

        if (userId.isEmpty()) return;

        LocalDateTime consentedAt = LocalDateTime.now();
        if (!consentedAtStr.isEmpty()) {
            try {
                consentedAt = LocalDateTime.parse(consentedAtStr);
            } catch (Exception ignored) {}
        }

        LocalDateTime validUntil = consentedAt.plusYears(1);
        if (!validUntilStr.isEmpty()) {
            try {
                validUntil = LocalDateTime.parse(validUntilStr);
            } catch (Exception ignored) {}
        }

        Whitelist item = Whitelist.builder()
                .userId(userId)
                .consentedAt(consentedAt)
                .validUntil(validUntil)
                .build();
        chunk.add(item);
        if (chunk.size() >= 1000) {
            repository.saveWhitelistInBatch(chunk);
            chunk.clear();
        }
    }

    @Override
    public synchronized void flush() throws Exception {
        if (!chunk.isEmpty()) {
            repository.saveWhitelistInBatch(chunk);
            chunk.clear();
        }
    }

    private String getSafeToken(String[] tokens, int index) {
        if (index < 0 || index >= tokens.length) return "";
        String val = tokens[index];
        return val != null ? val.trim() : "";
    }
}
