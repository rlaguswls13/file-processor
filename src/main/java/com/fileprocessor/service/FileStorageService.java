package com.fileprocessor.service;

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

    private final Path fileUploadLocation;
    private final Path fileDownloadLocation;

    public FileStorageService(
            @Value("${app.file.upload-dir}") String uploadDir,
            @Value("${app.file.download-dir}") String downloadDir) {
        this.fileUploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fileDownloadLocation = Paths.get(downloadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initDirectories() {
        try {
            Files.createDirectories(this.fileUploadLocation);
            Files.createDirectories(this.fileDownloadLocation);
            log.info("Initialized File Storage directories successfully:");
            log.info(" - Upload dir: {}", this.fileUploadLocation);
            log.info(" - Download dir: {}", this.fileDownloadLocation);
        } catch (IOException e) {
            log.error("Could not create directories for uploading/downloading files", e);
            throw new RuntimeException("Failed to initialize file storage directories.", e);
        }
    }

    /**
     * MultipartFile을 안전하게 업로드 디렉토리에 저장하고 생성된 로컬 파일 객체를 반환합니다.
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
        
        Path targetLocation = this.fileUploadLocation.resolve(storedFilename);
        
        // 임시 디스크 쓰기 수행
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        log.info("Stored file locally: {} -> {}", originalFilename, targetLocation);
        return targetLocation.toFile();
    }

    /**
     * 특정 로컬 가상 파일을 다운로드 폴더에서 읽어서 Resource 형태로 반환합니다.
     */
    public Resource loadFileAsResource(String filename) throws FileNotFoundException {
        try {
            Path filePath = this.fileDownloadLocation.resolve(filename).normalize();
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
}
