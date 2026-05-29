package com.fileprocessor.service;

import com.fileprocessor.security.FileStagingProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final FileStagingProperties stagingProperties;
    private final Path tempLocation;
    private final Path downloadLocation;

    public FileStorageService(
            FileStagingProperties stagingProperties,
            @Value("${app.file.download-dir}") String downloadDir) {
        this.stagingProperties = stagingProperties;
        this.tempLocation = Paths.get(stagingProperties.getTempDir()).toAbsolutePath().normalize();
        this.downloadLocation = Paths.get(downloadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initDirectories() {
        try {
            // 1. 임시 Staging 디렉토리 생성
            Files.createDirectories(this.tempLocation);
            // 2. 다운로드 디렉토리 생성
            Files.createDirectories(this.downloadLocation);
            // 3. 각 카테고리별 영구 저장 디렉토리 자동 생성
            if (stagingProperties.getCategories() != null) {
                for (String categoryPath : stagingProperties.getCategories().values()) {
                    Files.createDirectories(Paths.get(categoryPath).toAbsolutePath().normalize());
                }
            }
            log.info("Initialized File Storage staging and category directories successfully:");
            log.info(" - Temp Staging dir: {}", this.tempLocation);
            log.info(" - Download dir: {}", this.downloadLocation);
        } catch (IOException e) {
            log.error("Could not create directories for staging files", e);
            throw new RuntimeException("Failed to initialize file storage directories.", e);
        }
    }

    /**
     * MultipartFile을 임시 격리저장소(tempLocation)에 저장하고 파일 객체를 반환합니다.
     */
    public File storeToTemp(MultipartFile file, String secureName) throws IOException {
        Path targetLocation = this.tempLocation.resolve(secureName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored file locally in temp staging: {}", targetLocation);
        return targetLocation.toFile();
    }

    /**
     * 임시 저장소의 파일을 카테고리별 실제 영구 저장소의 날짜 파티션 폴더로 안전하게 이동시킵니다.
     */
    public void moveToCategory(File tempFile, String category, String datePartition, String secureName) throws IOException {
        String categoryDir = stagingProperties.getCategories().get(category.toLowerCase().trim());
        if (categoryDir == null) {
            throw new IllegalArgumentException("Unsupported staging category: " + category);
        }
        
        // 날짜 파티션 서브디렉토리 바인딩 및 동적 폴더 생성
        Path targetDir = Paths.get(categoryDir).resolve(datePartition).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        Path targetLocation = targetDir.resolve(secureName);
        Files.move(tempFile.toPath(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved file to permanent category partition storage: {}", targetLocation);
    }

    /**
     * 임시 보관 폴더의 특정 안전명 파일 제거 (트랜잭션 롤백용)
     */
    public void deleteTempFile(String secureName) {
        try {
            Path targetLocation = this.tempLocation.resolve(secureName);
            Files.deleteIfExists(targetLocation);
            log.info("Rollback clean-up: Deleted temp file -> {}", targetLocation);
        } catch (IOException e) {
            log.error("Failed to delete temp file during rollback: {}", secureName, e);
        }
    }

    /**
     * 카테고리 영구 폴더의 특정 날짜 파티션 파일 제거 (트랜잭션 롤백용)
     */
    public void deleteCategoryFile(String category, String datePartition, String secureName) {
        try {
            String categoryDir = stagingProperties.getCategories().get(category.toLowerCase().trim());
            if (categoryDir != null) {
                Path targetLocation = Paths.get(categoryDir).resolve(datePartition).toAbsolutePath().normalize().resolve(secureName);
                Files.deleteIfExists(targetLocation);
                log.info("Rollback clean-up: Deleted category file -> {}", targetLocation);
            }
        } catch (IOException e) {
            log.error("Failed to delete category file during rollback: {}", secureName, e);
        }
    }

    /**
     * MultipartFile을 안전하게 임시 격리 디렉토리에 저장하고 생성된 로컬 파일 객체를 반환합니다. (기존 로직 지원용)
     */
    public File storeFile(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        
        // 고유 파일명 생성으로 파일명 충돌 및 하이재킹 방지
        String fileExtension = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx != -1) {
            fileExtension = originalFilename.substring(dotIdx);
        }
        String storedFilename = UUID.randomUUID().toString() + fileExtension;
        
        Path targetLocation = this.tempLocation.resolve(storedFilename);
        
        // 임시 디스크 쓰기 수행
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        log.info("Stored file locally (legacy): {} -> {}", originalFilename, targetLocation);
        return targetLocation.toFile();
    }

    /**
     * 특정 로컬 가상 파일을 다운로드 폴더에서 읽어서 Resource 형태로 반환합니다.
     */
    public Resource loadFileAsResource(String filename) throws FileNotFoundException {
        try {
            Path filePath = this.downloadLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found or not readable: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new FileNotFoundException("File path is invalid for filename: " + filename);
        }
    }

    /**
     * 임시 파싱이 끝난 파일의 삭제 처리
     */
    public void deleteFile(File file) {
        try {
            if (file != null && file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.debug("Temporary file deleted: {}", file.getAbsolutePath());
                } else {
                    log.warn("Failed to delete temporary file: {}", file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("Error occurred while deleting temporary file", e);
        }
    }

    /**
     * 로우 텍스트/CSV 본문을 임시 디렉토리에 파일 형태로 쓰고 로컬 파일 객체를 반환합니다.
     */
    public File storeRawText(String rawText, String filename) throws IOException {
        String storedFilename = UUID.randomUUID().toString() + "_" + filename;
        Path targetLocation = this.tempLocation.resolve(storedFilename);
        
        Files.writeString(targetLocation, rawText);
        
        log.info("Stored raw text body locally -> {}", targetLocation);
        return targetLocation.toFile();
    }
}
