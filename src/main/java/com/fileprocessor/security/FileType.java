package com.fileprocessor.security;

import java.util.Arrays;
import java.util.stream.Stream;

public interface FileType {
    String getExtension();
    byte[][] getSignatures();

    default boolean hasSignature() {
        return getSignatures() != null && getSignatures().length > 0;
    }

    static FileType fromExtension(String ext) {
        if (ext == null) return null;
        String normalizedExt = ext.toLowerCase().trim();

        return Stream.concat(
            Stream.concat(Arrays.stream(DocumentType.values()), Arrays.stream(ImageType.values())),
            Stream.concat(Arrays.stream(TextType.values()), Arrays.stream(TemplateType.values()))
        )
        .filter(type -> type.getExtension().equals(normalizedExt))
        .findFirst()
        .orElse(null);
    }
}
