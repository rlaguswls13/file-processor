package com.example.fileprocessor.security;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum FileType {
    CSV("csv", new byte[][]{}), // 텍스트 기반 포맷 (일반 시그니처 없음)
    TXT("txt", new byte[][]{}), // 텍스트 기반 포맷 (일반 시그니처 없음)
    
    // MS Excel 97-2003 (.xls) 파일 시그니처 (D0 CF 11 E0 A1 B1 1A E1)
    XLS("xls", new byte[][]{
        {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1}
    }),
    
    // MS Office Open XML (.xlsx) 파일 시그니처 (PK.. ZIP 포맷: 50 4B 03 04)
    XLSX("xlsx", new byte[][]{
        {0x50, 0x4B, 0x03, 0x04}
    }),
    
    // JSON 파일 시그니처 ({ 또는 [ 로 시작 가능하므로 바이트화 가능하지만, 텍스트 포맷으로 간주 가능)
    JSON("json", new byte[][]{});

    private final String extension;
    private final byte[][] signatures;

    FileType(String extension, byte[][] signatures) {
        this.extension = extension;
        this.signatures = signatures;
    }

    /**
     * 확장자명으로 FileType Enum 조회
     */
    public static FileType fromExtension(String ext) {
        if (ext == null) return null;
        String normalizedExt = ext.toLowerCase().trim();
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(normalizedExt))
                .findFirst()
                .orElse(null);
    }
}
