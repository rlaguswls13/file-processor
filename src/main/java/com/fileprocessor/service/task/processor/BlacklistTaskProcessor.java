package com.fileprocessor.service.task.processor;

import com.fileprocessor.model.Blacklist;
import com.fileprocessor.model.TaskType;
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
public class BlacklistTaskProcessor implements TaskProcessor {
    private final BatchInsertRepository repository;
    private final List<Blacklist> chunk = new ArrayList<>(1000);

    private int contactIdx = -1;
    private int reasonIdx = -1;

    @Override
    public TaskType getSupportedType() {
        return TaskType.BLACKLIST;
    }

    @Override
    public void initializeHeaders(Map<String, Integer> headerIndexMap) {
        if (headerIndexMap == null) return;
        this.contactIdx = headerIndexMap.getOrDefault("user_id_or_contact", -1);
        if (this.contactIdx == -1) {
            this.contactIdx = headerIndexMap.getOrDefault("id", -1);
        }
        if (this.contactIdx == -1) {
            this.contactIdx = headerIndexMap.getOrDefault("contact", -1);
        }
        this.reasonIdx = headerIndexMap.getOrDefault("reason", -1);
    }

    @Override
    public synchronized void processRow(String[] tokens) throws Exception {
        if (tokens == null || tokens.length == 0) return;

        String userIdOrContact = getSafeToken(tokens, contactIdx);
        String reason = getSafeToken(tokens, reasonIdx);
        if (reason.isEmpty()) {
            reason = "No reason provided";
        }

        if (userIdOrContact.isEmpty()) return;

        Blacklist item = Blacklist.builder()
                .userIdOrContact(userIdOrContact)
                .reason(reason)
                .blockedAt(LocalDateTime.now())
                .build();
        chunk.add(item);
        if (chunk.size() >= 1000) {
            repository.saveBlacklistInBatch(chunk);
            chunk.clear();
        }
    }

    @Override
    public synchronized void flush() throws Exception {
        if (!chunk.isEmpty()) {
            repository.saveBlacklistInBatch(chunk);
            chunk.clear();
        }
    }

    private String getSafeToken(String[] tokens, int index) {
        if (index < 0 || index >= tokens.length) return "";
        String val = tokens[index];
        return val != null ? val.trim() : "";
    }
}
