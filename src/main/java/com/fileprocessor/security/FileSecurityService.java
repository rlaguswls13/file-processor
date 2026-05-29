package com.fileprocessor.security;

import com.fileprocessor.security.type.FileType;
import jakarta.annotation.PostConstruct;
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

    private Pattern safeDocumentsPattern;
    private Pattern imagesPattern;
    private Pattern textsPattern;
    private Pattern templatesPattern;
    private Pattern binariesPattern;
    private Pattern dangerousScriptsPattern;

    @PostConstruct
    public void init() {
        log.info("Loading Segmented File Security Configurations via DI Properties...");
        log.info("Whitelist - Docs Regex: {}", properties.getWhitelist().getSafeDocumentsRegex());
        log.info("Whitelist - Images Regex: {}", properties.getWhitelist().getImagesRegex());
        log.info("Whitelist - Texts Regex: {}", properties.getWhitelist().getTextsRegex());
        log.info("Whitelist - Templates Regex: {}", properties.getWhitelist().getTemplatesRegex());
        log.info("Blacklist - Binaries Regex: {}", properties.getBlacklist().getBinariesRegex());
        log.info("Blacklist - Scripts Regex: {}", properties.getBlacklist().getDangerousScriptsRegex());
        
        this.safeDocumentsPattern = Pattern.compile(properties.getWhitelist().getSafeDocumentsRegex(), Pattern.CASE_INSENSITIVE);
        this.imagesPattern = Pattern.compile(properties.getWhitelist().getImagesRegex(), Pattern.CASE_INSENSITIVE);
        this.textsPattern = Pattern.compile(properties.getWhitelist().getTextsRegex(), Pattern.CASE_INSENSITIVE);
        this.templatesPattern = Pattern.compile(properties.getWhitelist().getTemplatesRegex(), Pattern.CASE_INSENSITIVE);
        this.binariesPattern = Pattern.compile(properties.getBlacklist().getBinariesRegex(), Pattern.CASE_INSENSITIVE);
        this.dangerousScriptsPattern = Pattern.compile(properties.getBlacklist().getDangerousScriptsRegex(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * 파일명 및 경로 보안 검증 (Path Traversal 및 Regex 확장자 매칭)
     */
    public void validateFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name is empty or invalid.");
        }

        // 1. Path Traversal 차단 (경로 이탈 탐지)
        if (originalFilename.contains("../") || originalFilename.contains("..\\")) {
            log.error("Path traversal attempt detected in filename: {}", originalFilename);
            throw new SecurityException("Potential Path Traversal attack detected.");
        }

        // 2. 확장자 추출 및 매칭 검사
        String extension = getFileExtension(originalFilename).toLowerCase().trim();

        // 2-1. Blacklist 정규식 매칭 검사 (확장자만 검사)
        if (binariesPattern.matcher(extension).matches()) {
            log.error("Executable binary file blocked: {}. Extension: .{}", originalFilename, extension);
            throw new SecurityException("Binary files (.jar, .exe, etc.) are strictly prohibited.");
        }
        if (dangerousScriptsPattern.matcher(extension).matches()) {
            log.error("Dangerous script/system file blocked: {}. Extension: .{}", originalFilename, extension);
            throw new SecurityException("Dangerous files that can harm the system are strictly prohibited.");
        }

        // 2-2. Whitelist 정규식 매칭 검사 (확장자만 검사)
        boolean isWhitelisted = safeDocumentsPattern.matcher(extension).matches() ||
                                imagesPattern.matcher(extension).matches() ||
                                textsPattern.matcher(extension).matches() ||
                                templatesPattern.matcher(extension).matches();

        if (!isWhitelisted) {
            log.error("File type not in whitelist: {}. Extension: .{}", originalFilename, extension);
            throw new SecurityException("Only allowed file types can be uploaded (Whitelist constraint).");
        }

        log.debug("Filename '{}' passed security checks (Extension: .{}).", originalFilename, extension);
    }

    /**
     * 특정 카테고리별 확장자 정밀 검증 (Path Traversal 방지 및 특정 Whitelist 카테고리 매칭)
     */
    public void validateFileNameByCategory(String originalFilename, String categoryName) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name is empty or invalid.");
        }

        // 1. Path Traversal 차단 (경로 이탈 탐지)
        if (originalFilename.contains("../") || originalFilename.contains("..\\")) {
            log.error("Path traversal attempt detected in filename: {}", originalFilename);
            throw new SecurityException("Potential Path Traversal attack detected.");
        }

        // 2. 확장자 추출 및 매칭 검사
        String extension = getFileExtension(originalFilename).toLowerCase().trim();

        // 2-1. Blacklist 정규식 매칭 검사 (확장자만 검사)
        if (binariesPattern.matcher(extension).matches()) {
            log.error("Executable binary file blocked: {}. Extension: .{}", originalFilename, extension);
            throw new SecurityException("Binary files (.jar, .exe, etc.) are strictly prohibited.");
        }
        if (dangerousScriptsPattern.matcher(extension).matches()) {
            log.error("Dangerous script/system file blocked: {}. Extension: .{}", originalFilename, extension);
            throw new SecurityException("Dangerous files that can harm the system are strictly prohibited.");
        }

        // 2-2. 카테고리별 Whitelist 정밀 매칭
        Pattern targetPattern;
        switch (categoryName.toLowerCase().trim()) {
            case "images":
                targetPattern = imagesPattern;
                break;
            case "templates":
                targetPattern = templatesPattern;
                break;
            case "texts":
                targetPattern = textsPattern;
                break;
            case "safe-documents":
                targetPattern = safeDocumentsPattern;
                break;
            default:
                throw new IllegalArgumentException("Unknown security validation category: " + categoryName);
        }

        if (!targetPattern.matcher(extension).matches()) {
            log.error("File type not allowed for category '{}': {}. Extension: .{}", categoryName, originalFilename, extension);
            throw new SecurityException("Upload of this file type is not allowed for category: " + categoryName);
        }

        log.debug("Filename '{}' passed security checks for category '{}' (Extension: .{}).", originalFilename, categoryName, extension);
    }

    /**
     * 파일 크기 검증 (업로드/다운로드 한계 체크)
     * @param size 현재 파일 크기 (bytes)
     * @param maxLimitByte 제한할 최대 크기 (bytes)
     */
    public void validateFileSize(long size, long maxLimitByte) {
        if (size > maxLimitByte) {
            log.error("File size {} bytes exceeds maximum limit {} bytes.", size, maxLimitByte);
            throw new SecurityException("File size exceeds the allowed limit.");
        }
    }

    /**
     * 파일 Magic Number(시그니처) 정합성 검증
     * 껍데기 확장자만 바꾼 악성 파일을 차단합니다.
     */
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

    private boolean matchSignature(byte[] fileHeader, byte[] signature) {
        if (fileHeader.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (fileHeader[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private String getFileExtension(String filename) {
        int lastIdx = filename.lastIndexOf('.');
        if (lastIdx == -1) return "";
        return filename.substring(lastIdx + 1);
    }
}
