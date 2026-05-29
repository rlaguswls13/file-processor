package com.fileprocessor.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FileSecurityService {

    private final Pattern safeDocumentsPattern;
    private final Pattern imagesPattern;
    private final Pattern textsPattern;
    private final Pattern templatesPattern;
    private final Pattern binariesPattern;
    private final Pattern dangerousScriptsPattern;

    public FileSecurityService(
            @Value("${app.security.file.whitelist.safe-documents-regex}") String safeDocsRegex,
            @Value("${app.security.file.whitelist.images-regex}") String imagesRegex,
            @Value("${app.security.file.whitelist.texts-regex}") String textsRegex,
            @Value("${app.security.file.whitelist.templates-regex}") String templatesRegex,
            @Value("${app.security.file.blacklist.binaries-regex}") String binariesRegex,
            @Value("${app.security.file.blacklist.dangerous-scripts-regex}") String scriptsRegex) {
        
        log.info("Loading Segmented File Security Configurations...");
        log.info("Whitelist - Docs Regex: {}", safeDocsRegex);
        log.info("Whitelist - Images Regex: {}", imagesRegex);
        log.info("Whitelist - Texts Regex: {}", textsRegex);
        log.info("Whitelist - Templates Regex: {}", templatesRegex);
        log.info("Blacklist - Binaries Regex: {}", binariesRegex);
        log.info("Blacklist - Scripts Regex: {}", scriptsRegex);
        
        this.safeDocumentsPattern = Pattern.compile(safeDocsRegex, Pattern.CASE_INSENSITIVE);
        this.imagesPattern = Pattern.compile(imagesRegex, Pattern.CASE_INSENSITIVE);
        this.textsPattern = Pattern.compile(textsRegex, Pattern.CASE_INSENSITIVE);
        this.templatesPattern = Pattern.compile(templatesRegex, Pattern.CASE_INSENSITIVE);
        this.binariesPattern = Pattern.compile(binariesRegex, Pattern.CASE_INSENSITIVE);
        this.dangerousScriptsPattern = Pattern.compile(scriptsRegex, Pattern.CASE_INSENSITIVE);
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
