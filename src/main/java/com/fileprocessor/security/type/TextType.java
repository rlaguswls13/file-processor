package com.fileprocessor.security.type;

import lombok.Getter;

@Getter
public enum TextType implements FileType {
    TXT("txt", new byte[][]{}),
    LOG("log", new byte[][]{});

    private final String extension;
    private final byte[][] signatures;

    TextType(String extension, byte[][] signatures) {
        this.extension = extension;
        this.signatures = signatures;
    }
}
