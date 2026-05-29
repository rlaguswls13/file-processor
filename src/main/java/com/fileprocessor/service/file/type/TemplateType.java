package com.fileprocessor.service.file.type;

import lombok.Getter;

@Getter
public enum TemplateType implements FileType {
    HTML("html", new byte[][]{}),
    HTM("htm", new byte[][]{}),
    EMAIL("email", new byte[][]{}),
    TPL("tpl", new byte[][]{});

    private final String extension;
    private final byte[][] signatures;

    TemplateType(String extension, byte[][] signatures) {
        this.extension = extension;
        this.signatures = signatures;
    }
}
