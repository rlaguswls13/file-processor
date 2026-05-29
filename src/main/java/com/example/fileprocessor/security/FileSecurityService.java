package com.example.fileprocessor.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FileSecurityService {

    private final Pattern whitelistPattern;
    private final Pattern blacklistPattern;

    public FileSecurityService(
            @Value("${app.security.file.whitelist}") String whitelistRegex,
            @Value("${app.security.file.blacklist}") String blacklistRegex) {
        
        log.info("Loading File Security Configurations...");
        log.info("Whitelist regex: {}", whitelistRegex);
        log.info("Blacklist regex: {}", blacklistRegex);
        
        this.whitelistPattern = Pattern.compile(whitelistRegex, Pattern.CASE_INSENSITIVE);
        this.blacklistPattern = Pattern.compile(blacklistRegex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * 파일명 및 경로 보안 검증 (Path Traversal 및 Regex 매칭)
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

        // 2. Blacklist 정규식 매칭 검사
        if (blacklistPattern.matcher(originalFilename).matches()) {
            log.error("Blacklisted file pattern matched for filename: {}", originalFilename);
            throw new SecurityException("Upload of this file type is prohibited (Blacklisted).");
        }

        // 3. Whitelist 정규식 매칭 검사
        if (!whitelistPattern.matcher(originalFilename).matches()) {
            log.error("File name does not match the whitelist: {}", originalFilename);
            throw new SecurityException("Only allowed file types can be uploaded (Whitelist constraint).");
        }

        log.debug("Filename '{}' passed security checks.", originalFilename);
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
