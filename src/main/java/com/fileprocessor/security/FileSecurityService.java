package com.fileprocessor.security;

import com.fileprocessor.config.properties.FileSecurityProperties;
import com.fileprocessor.model.FileCategory;
import com.fileprocessor.service.file.type.FileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileSecurityService {

    private final FileSecurityProperties properties;

    // 파일 크기 검사
    public void validateFileSize(long size, long maxLimitByte) {
        if (size > maxLimitByte) {
            log.error("File size {} bytes exceeds maximum limit {} bytes.", size, maxLimitByte);
            throw new SecurityException("File size exceeds the allowed limit.");
        } else if (size == 0) {
            log.error("File size 0 bytes.");
            throw new SecurityException("File size zero.");
        }
    }

    // 파일 카테고리 별 확장자 검사
    public void validateFileNameByCategory(String originalFilename, FileCategory fileCategory) {
        validateBasicAndPath(originalFilename);
        String extension = getFileExtension(originalFilename);
        checkBlacklist(extension, originalFilename);
        checkCategoryWhitelist(extension, originalFilename, fileCategory);
        log.debug("Filename '{}' passed security checks for category '{}' (Extension: .{}).", originalFilename, fileCategory, extension);
    }

    // 파일 경로 이탈 및 공백 파일명 확인
    private void validateBasicAndPath(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name is empty or invalid.");
        }
        if (originalFilename.contains("../") || originalFilename.contains("..\\")) {
            log.error("Path traversal attempt detected in filename: {}", originalFilename);
            throw new SecurityException("Potential Path Traversal attack detected.");
        }
    }

    // 확장자 블랙리스트 확인
    private void checkBlacklist(String extension, String originalFilename) {
        if (properties.getBlacklist() == null) return;
        for (FileCategory category : properties.getBlacklist().keySet()) {
            Pattern pattern = category.getRegex();
            if (pattern != null && pattern.matcher(extension).matches()) {
                log.error("File upload blocked by blacklist category '{}': {}. Extension: .{}", category, originalFilename, extension);
                throw new SecurityException("Files of category " + category + " are strictly prohibited.");
            }
        }
    }

    // 파일 카테고리별 화이트리스트 확인
    private void checkCategoryWhitelist(String extension, String originalFilename, FileCategory fileCategory) {
        Pattern targetPattern = fileCategory.getRegex();
        if (targetPattern == null || !targetPattern.matcher(extension).matches()) {
            log.error("File type not allowed for category '{}': {}. Extension: .{}", fileCategory, originalFilename, extension);
            throw new SecurityException("Upload of this file type is not allowed for category: " + fileCategory);
        }
    }

    // 파일임시 저장후, 매직넘버 확인 (변조파일검사)
    public void validateFileSignature(File file) {
        String fileName = file.getName();
        String extension = getFileExtension(fileName);
        
        FileType fileType = FileType.fromExtension(extension);
        if (fileType == null) {
            throw new SecurityException("Unsupported file extension inside system registry.");
        }

        // 텍스트 계열(CSV, TXT, JSON)은 고유 바이너리 시그니처가 없으므로 건너뜀
        if (fileType.getSignatures().length == 0) {
            return;
        }

        // 바이너리 파일 (XLS, XLSX) 시그니처 확인
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[8];
            int bytesRead = is.read(header);
            if (bytesRead < 8) {
                throw new SecurityException("File is too small to verify magic bytes.");
            }

            boolean signatureMatched = false;
            for (byte[] signature : fileType.getSignatures()) {
                if (matchSignature(header, signature)) {
                    signatureMatched = true;
                    break;
                }
            }

            if (!signatureMatched) {
                // 확장자가 xlsx인데 실제 헤더가 xls이거나 zip이 아니면 오염된 파일로 판단
                log.error("Magic bytes mismatch for file: {}. Expected signature for extension: {}", fileName, extension);
                throw new SecurityException("File content signature does not match its declared extension.");
            }

            log.debug("Magic bytes verified for file: {}", fileName);

        } catch (IOException e) {
            log.error("Failed to read file for signature verification", e);
            throw new SecurityException("Error occurred during file signature validation.");
        }
    }

    // 시그니쳐 매칭
    private boolean matchSignature(byte[] fileHeader, byte[] signature) {
        if (fileHeader.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (fileHeader[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    // 확장자 추출
    private String getFileExtension(String filename) {
        int lastIndexOf = filename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // 확장자가 없는 경우
        }
        return filename.substring(lastIndexOf + 1).toLowerCase().trim();
    }
}
