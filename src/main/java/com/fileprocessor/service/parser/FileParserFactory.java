package com.fileprocessor.service.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FileParserFactory {

    private final CsvTxtFileParser csvTxtFileParser;
    private final ExcelFileParser excelFileParser;
    private final JsonFileParser jsonFileParser;

    /**
     * 파일 확장자에 매핑되는 최적의 FileParser 구현체를 반환합니다.
     */
    public FileParser getParser(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null.");
        }

        String extension = getFileExtension(filename).toLowerCase(Locale.ROOT);

        switch (extension) {
            case "csv":
            case "txt":
                return csvTxtFileParser;
            case "xls":
            case "xlsx":
                return excelFileParser;
            case "json":
                return jsonFileParser;
            default:
                throw new IllegalArgumentException("No suitable parser found for extension: " + extension);
        }
    }

    private String getFileExtension(String filename) {
        int lastIdx = filename.lastIndexOf('.');
        if (lastIdx == -1) {
            return "";
        }
        return filename.substring(lastIdx + 1);
    }
}
