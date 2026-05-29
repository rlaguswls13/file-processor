package com.fileprocessor.service.task.processor;

import com.fileprocessor.model.TaskType;
import com.fileprocessor.model.Testlist;
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
public class TestlistTaskProcessor implements TaskProcessor {
    private final BatchInsertRepository repository;
    private final List<Testlist> chunk = new ArrayList<>(1000);

    private int nameIdx = -1;
    private int contactIdx = -1;

    @Override
    public TaskType getSupportedType() {
        return TaskType.TESTLIST;
    }

    @Override
    public void initializeHeaders(Map<String, Integer> headerIndexMap) {
        if (headerIndexMap == null) return;
        this.nameIdx = headerIndexMap.getOrDefault("name", -1);
        this.contactIdx = headerIndexMap.getOrDefault("test_contact", -1);
        if (this.contactIdx == -1) {
            this.contactIdx = headerIndexMap.getOrDefault("contact", -1);
        }
        if (this.contactIdx == -1) {
            this.contactIdx = headerIndexMap.getOrDefault("phone", -1);
        }
    }

    @Override
    public synchronized void processRow(String[] tokens) throws Exception {
        if (tokens == null || tokens.length == 0) return;

        String name = getSafeToken(tokens, nameIdx);
        if (name.isEmpty()) {
            name = "Tester";
        }
        String testContact = getSafeToken(tokens, contactIdx);

        if (testContact.isEmpty()) return;

        Testlist item = Testlist.builder()
                .name(name)
                .testContact(testContact)
                .createdAt(LocalDateTime.now())
                .build();
        chunk.add(item);
        if (chunk.size() >= 1000) {
            repository.saveTestlistInBatch(chunk);
            chunk.clear();
        }
    }

    @Override
    public synchronized void flush() throws Exception {
        if (!chunk.isEmpty()) {
            repository.saveTestlistInBatch(chunk);
            chunk.clear();
        }
    }

    private String getSafeToken(String[] tokens, int index) {
        if (index < 0 || index >= tokens.length) return "";
        String val = tokens[index];
        return val != null ? val.trim() : "";
    }
}
