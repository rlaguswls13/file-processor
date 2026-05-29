package com.fileprocessor.service.file;

import com.fileprocessor.model.FileMetadata;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class FileMetadataGenerator {

    public FileMetadata generate(String originalName, long fileSize, String uploadUser) {
        String extension = getFileExtension(originalName);
        String uuid = UUID.randomUUID().toString();
        String secureStoredName = generateSecureName(originalName, uuid, extension);

        String datePartition = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        return FileMetadata.builder()
                .originalName(originalName)
                .secureStoredName(secureStoredName)
                .extension(extension)
                .uuid(uuid)
                .fileSize(fileSize)
                .uploadUser(uploadUser)
                .datePartition(datePartition)
                .build();
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastIdx = filename.lastIndexOf('.');
        if (lastIdx == -1) return "";
        return filename.substring(lastIdx + 1);
    }

    private String generateSecureName(String originalName, String uuid, String extension) {
        try {
            String input = originalName + uuid + System.nanoTime();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString() + (extension.isEmpty() ? "" : "." + extension);
        } catch (NoSuchAlgorithmException e) {
            return uuid + (extension.isEmpty() ? "" : "." + extension);
        }
    }
}
