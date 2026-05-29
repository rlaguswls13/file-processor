package com.fileprocessor.service.task.processor;

import com.fileprocessor.model.AddressBook;
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
public class AddressBookTaskProcessor implements TaskProcessor {
    private final BatchInsertRepository repository;
    private final List<AddressBook> chunk = new ArrayList<>(1000);

    private int nameIdx = -1;
    private int phoneIdx = -1;
    private int emailIdx = -1;
    private int groupIdx = -1;

    @Override
    public TaskType getSupportedType() {
        return TaskType.ADDRESS_BOOK;
    }

    @Override
    public void initializeHeaders(Map<String, Integer> headerIndexMap) {
        if (headerIndexMap == null) return;
        this.nameIdx = headerIndexMap.getOrDefault("name", -1);
        this.phoneIdx = headerIndexMap.getOrDefault("phone_number", -1);
        this.emailIdx = headerIndexMap.getOrDefault("email", -1);
        this.groupIdx = headerIndexMap.getOrDefault("group_name", -1);
    }

    @Override
    public synchronized void processRow(String[] tokens) throws Exception {
        if (tokens == null || tokens.length == 0) return;

        String name = getSafeToken(tokens, nameIdx);
        String phoneNumber = getSafeToken(tokens, phoneIdx);
        String email = getSafeToken(tokens, emailIdx);
        String groupName = getSafeToken(tokens, groupIdx);

        if (name.isEmpty() && phoneNumber.isEmpty()) return;

        AddressBook ab = AddressBook.builder()
                .name(name)
                .phoneNumber(phoneNumber)
                .email(email)
                .groupName(groupName)
                .createdAt(LocalDateTime.now())
                .build();
        chunk.add(ab);
        if (chunk.size() >= 1000) {
            repository.saveAddressBooksInBatch(chunk);
            chunk.clear();
        }
    }

    @Override
    public synchronized void flush() throws Exception {
        if (!chunk.isEmpty()) {
            repository.saveAddressBooksInBatch(chunk);
            chunk.clear();
        }
    }

    private String getSafeToken(String[] tokens, int index) {
        if (index < 0 || index >= tokens.length) return "";
        String val = tokens[index];
        return val != null ? val.trim() : "";
    }
}
