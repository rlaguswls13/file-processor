package com.fileprocessor.service.parser;

import com.fileprocessor.service.task.TaskProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LineSeparatedFileParser implements FileParser {

    @Override
    public void parse(File file, TaskProcessor processor) throws Exception {
        parse(file, processor, null);
    }

    @Override
    public void parse(File file, TaskProcessor processor, String delimiter) throws Exception {
        log.info("Starting high-performance Line-Separated text parsing: {} for task: {}", file.getName(), processor.getSupportedType());

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            long lineCount = 0;
            boolean isFirstRow = true;
            String detectedDelimiter = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (detectedDelimiter == null) {
                    if (delimiter == null || delimiter.isEmpty()) {
                        if (line.contains("|")) {
                            detectedDelimiter = "\\|";
                        } else if (line.contains(",")) {
                            detectedDelimiter = ",";
                        } else {
                            detectedDelimiter = "\\s+";
                        }
                        log.info("Auto-detected delimiter for file {}: '{}'", file.getName(), detectedDelimiter);
                    } else {
                        if (delimiter.equals(" ") || delimiter.equals("\\s+")) {
                            detectedDelimiter = "\\s+";
                        } else {
                            detectedDelimiter = Pattern.quote(delimiter);
                        }
                        log.info("Using user-defined delimiter for file {}: '{}' (regex: '{}')", file.getName(), delimiter, detectedDelimiter);
                    }
                }

                String[] tokens = line.split(detectedDelimiter, -1);

                if (isFirstRow) {
                    isFirstRow = false;
                    Map<String, Integer> headerIndexMap = new HashMap<>();
                    for (int i = 0; i < tokens.length; i++) {
                        String colName = tokens[i].toLowerCase().trim();
                        if (!colName.isEmpty()) {
                            headerIndexMap.put(colName, i);
                        }
                    }
                    log.info("Initialized dynamic header index mapping for file {}: {}", file.getName(), headerIndexMap);
                    
                    processor.initializeHeaders(headerIndexMap);
                    continue;
                }

                lineCount++;
                try {
                    processor.processRow(tokens);
                } catch (Exception e) {
                    log.error("Error supplying row {} to strategy processor: {}", lineCount, e.getMessage());
                }
            }

            processor.flush();
            log.info("Finished Line-Separated parsing. Supplied {} records to processor.", lineCount);
        }
    }
}
