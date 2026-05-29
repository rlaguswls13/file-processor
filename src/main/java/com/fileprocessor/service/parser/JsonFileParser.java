package com.fileprocessor.service.parser;

import com.fileprocessor.model.AddressBook;
import com.fileprocessor.model.TargetData;
import com.fileprocessor.repository.BatchInsertRepository;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonFileParser implements FileParser {

    private final BatchInsertRepository batchInsertRepository;
    private final ObjectMapper objectMapper; // Spring에 기본 등록된 ObjectMapper 재사용
    private static final int CHUNK_SIZE = 1000;

    @Override
    public void parseAndSave(File file, String taskType) throws Exception {
        log.info("Starting JSON streaming parse: {} for task: {}", file.getName(), taskType);

        JsonFactory jsonFactory = new JsonFactory();
        
        // Jackson JsonParser를 활용한 이벤트/토큰 기반 스트리밍 처리
        try (JsonParser jsonParser = jsonFactory.createParser(file)) {
            // ObjectMapper 설정 동기화
            jsonParser.setCodec(objectMapper);

            // JSON 구조가 Array [ {..}, {..} ] 형태일 경우를 타겟팅
            JsonToken token = jsonParser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                // 배열로 시작하지 않으면 단일 객체이거나 다른 포맷임
                if (token == JsonToken.START_OBJECT) {
                    processSingleObject(jsonParser, taskType);
                    return;
                }
                throw new IllegalArgumentException("Expected JSON to start with an Array or Object.");
            }

            if ("ADDRESS_BOOK".equalsIgnoreCase(taskType)) {
                parseAddressBookArray(jsonParser);
            } else if ("TARGET_DATA".equalsIgnoreCase(taskType)) {
                parseTargetDataArray(jsonParser);
            } else {
                throw new IllegalArgumentException("Unknown task type for JSON parsing: " + taskType);
            }
        }
    }

    private void parseAddressBookArray(JsonParser parser) throws Exception {
        List<AddressBook> chunk = new ArrayList<>(CHUNK_SIZE);
        long count = 0;

        while (parser.nextToken() == JsonToken.START_OBJECT) {
            // JsonParser가 단일 객체 단위만 ObjectMapper를 이용해 바인드하므로, 메모리 절약
            AddressBook item = parser.readValueAs(AddressBook.class);
            item.setCreatedAt(LocalDateTime.now());
            chunk.add(item);
            count++;

            if (chunk.size() >= CHUNK_SIZE) {
                batchInsertRepository.saveAddressBooksInBatch(chunk);
                chunk.clear();
            }
        }

        if (!chunk.isEmpty()) {
            batchInsertRepository.saveAddressBooksInBatch(chunk);
        }
        log.info("Finished parsing and saving {} AddressBook objects from JSON", count);
    }

    private void parseTargetDataArray(JsonParser parser) throws Exception {
        List<TargetData> chunk = new ArrayList<>(CHUNK_SIZE);
        long count = 0;

        while (parser.nextToken() == JsonToken.START_OBJECT) {
            TargetData item = parser.readValueAs(TargetData.class);
            item.setCreatedAt(LocalDateTime.now());
            chunk.add(item);
            count++;

            if (chunk.size() >= CHUNK_SIZE) {
                batchInsertRepository.saveTargetDataInBatch(chunk);
                chunk.clear();
            }
        }

        if (!chunk.isEmpty()) {
            batchInsertRepository.saveTargetDataInBatch(chunk);
        }
        log.info("Finished parsing and saving {} TargetData objects from JSON", count);
    }

    private void processSingleObject(JsonParser parser, String taskType) throws Exception {
        log.info("Processing single JSON object...");
        if ("ADDRESS_BOOK".equalsIgnoreCase(taskType)) {
            AddressBook item = parser.readValueAs(AddressBook.class);
            item.setCreatedAt(LocalDateTime.now());
            batchInsertRepository.saveAddressBooksInBatch(List.of(item));
        } else if ("TARGET_DATA".equalsIgnoreCase(taskType)) {
            TargetData item = parser.readValueAs(TargetData.class);
            item.setCreatedAt(LocalDateTime.now());
            batchInsertRepository.saveTargetDataInBatch(List.of(item));
        }
    }
}
