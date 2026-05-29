package com.fileprocessor.security;

import lombok.Getter;

@Getter
public enum ImageType implements FileType {
    JPG("jpg", new byte[][]{
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
    }),
    JPEG("jpeg", new byte[][]{
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
    }),
    PNG("png", new byte[][]{
        {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    }),
    GIF("gif", new byte[][]{
        {0x47, 0x49, 0x46, 0x38, 0x39, 0x61},
        {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}
    }),
    BMP("bmp", new byte[][]{
        {0x42, 0x4D}
    }),
    WEBP("webp", new byte[][]{
        {0x52, 0x49, 0x46, 0x46} // RIFF
    }),
    SVG("svg", new byte[][]{});

    private final String extension;
    private final byte[][] signatures;

    ImageType(String extension, byte[][] signatures) {
        this.extension = extension;
        this.signatures = signatures;
    }
}
