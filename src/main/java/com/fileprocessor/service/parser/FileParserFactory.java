package com.fileprocessor.service.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FileParserFactory {

    private final LineSeparatedFileParser lineSeparatedFileParser;

    /**
     * 파일 확장자에 매핑되는 최적의 FileParser 구현체를 반환합니다. (JSON/Excel 제외, 오로지 고성능 라인 텍스트 지원)
     */
    public FileParser getParser(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null.");
        }

        String extension = getFileExtension(filename).toLowerCase(Locale.ROOT);

        switch (extension) {
            case "csv":
            case "txt":
                return lineSeparatedFileParser;
            default:
                throw new IllegalArgumentException("Unsupported file type. Only line-separated CSV/TXT are supported. Ext: " + extension);
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
