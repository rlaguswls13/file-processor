package com.fileprocessor.service.file.type;

import lombok.Getter;

@Getter
public enum DocumentType implements FileType {
    CSV("csv", new byte[][]{}),
    JSON("json", new byte[][]{}),
    PDF("pdf", new byte[][]{
        {0x25, 0x50, 0x44, 0x46} // %PDF
    }),
    ZIP("zip", new byte[][]{
        {0x50, 0x4B, 0x03, 0x04} // PK..
    }),
    XLS("xls", new byte[][]{
        {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1}
    }),
    XLSX("xlsx", new byte[][]{
        {0x50, 0x4B, 0x03, 0x04} // PK..
    }),
    DOCX("docx", new byte[][]{
        {0x50, 0x4B, 0x03, 0x04}
    }),
    PPTX("pptx", new byte[][]{
        {0x50, 0x4B, 0x03, 0x04}
    }),
    DOC("doc", new byte[][]{
        {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1}
    }),
    PPT("ppt", new byte[][]{
        {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1}
    });

    private final String extension;
    private final byte[][] signatures;

    DocumentType(String extension, byte[][] signatures) {
        this.extension = extension;
        this.signatures = signatures;
    }
}
