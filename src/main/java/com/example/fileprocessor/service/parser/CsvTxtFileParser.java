package com.example.fileprocessor.service.parser;

import com.example.fileprocessor.model.AddressBook;
import com.example.fileprocessor.model.TargetData;
import com.example.fileprocessor.repository.BatchInsertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsvTxtFileParser implements FileParser {

    private final BatchInsertRepository batchInsertRepository;
    private static final int CHUNK_SIZE = 1000; // 메모리 절약을 위한 1회 DB 전송 벌크 단위

    @Override
    public void parseAndSave(File file, String taskType) throws Exception {
        log.info("Starting to parse CSV/TXT file: {} for task type: {}", file.getName(), taskType);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // Apache Commons CSV 를 사용한 안전한 라인 바이 라인 파싱 (TXT도 콤마나 탭 구분으로 사용 가능)
            CSVFormat format = CSVFormat.DEFAULT
                    .builder()
                    .setIgnoreSurroundingSpaces(true)
                    .setHeader() // 첫 행은 헤더로 처리하여 자동 스킵 및 매핑
                    .setSkipHeaderRecord(true)
                    .build();

            try (CSVParser parser = new CSVParser(br, format)) {
                if ("ADDRESS_BOOK".equalsIgnoreCase(taskType)) {
                    parseAddressBook(parser);
                } else if ("TARGET_DATA".equalsIgnoreCase(taskType)) {
                    parseTargetData(parser);
                } else {
                    throw new IllegalArgumentException("Unknown task type for file parser: " + taskType);
                }
            }
        }
    }

    private void parseAddressBook(CSVParser parser) {
        List<AddressBook> chunk = new ArrayList<>(CHUNK_SIZE);
        long lineCount = 0;

        for (CSVRecord record : parser) {
            lineCount++;
            try {
                // 헤더 기준 매핑 (이름, 전화번호, 이메일, 그룹)
                AddressBook addressBook = AddressBook.builder()
                        .name(record.get("name"))
                        .phoneNumber(record.get("phone_number"))
                        .email(record.get("email"))
                        .groupName(record.get("group_name"))
                        .createdAt(LocalDateTime.now())
                        .build();

                chunk.add(addressBook);

                if (chunk.size() >= CHUNK_SIZE) {
                    batchInsertRepository.saveAddressBooksInBatch(chunk);
                    chunk.clear();
                }
            } catch (Exception e) {
                log.error("Error parsing row {} in AddressBook CSV/TXT: {}", lineCount, e.getMessage());
            }
        }

        // 잔여 데이터 DB 저장
        if (!chunk.isEmpty()) {
            batchInsertRepository.saveAddressBooksInBatch(chunk);
        }
        log.info("Finished parsing and saving {} AddressBook rows from CSV/TXT", lineCount);
    }

    private void parseTargetData(CSVParser parser) {
        List<TargetData> chunk = new ArrayList<>(CHUNK_SIZE);
        long lineCount = 0;

        for (CSVRecord record : parser) {
            lineCount++;
            try {
                // 헤더 기준 매핑 (유저ID, 행동패턴, 타겟그룹, 스코어)
                TargetData targetData = TargetData.builder()
                        .userId(record.get("user_id"))
                        .actionPattern(record.get("action_pattern"))
                        .targetGroup(record.get("target_group"))
                        .score(Double.parseDouble(record.get("score")))
                        .createdAt(LocalDateTime.now())
                        .build();

                chunk.add(targetData);

                if (chunk.size() >= CHUNK_SIZE) {
                    batchInsertRepository.saveTargetDataInBatch(chunk);
                    chunk.clear();
                }
            } catch (Exception e) {
                log.error("Error parsing row {} in TargetData CSV/TXT: {}", lineCount, e.getMessage());
            }
        }

        // 잔여 데이터 DB 저장
        if (!chunk.isEmpty()) {
            batchInsertRepository.saveTargetDataInBatch(chunk);
        }
        log.info("Finished parsing and saving {} TargetData rows from CSV/TXT", lineCount);
    }
}
