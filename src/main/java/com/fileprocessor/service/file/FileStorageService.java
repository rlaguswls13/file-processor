package com.fileprocessor.service.file;

import com.fileprocessor.config.FileStorageConfig;
import com.fileprocessor.config.properties.FileStorageProperties;
import com.fileprocessor.model.FileCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class FileStorageService {

    private final FileStorageProperties stagingProperties;
    private final Path tempLocation;
    private final Path taskLocation;

    public FileStorageService(FileStorageConfig storageConfig) {
        this.stagingProperties = storageConfig.getFileStorageProperties();
        this.tempLocation = storageConfig.getTempLocation();
        this.taskLocation = storageConfig.getTaskLocation();
    }

    public File storeToTemp(MultipartFile file, String secureName) throws IOException {
        Path targetLocation = this.tempLocation.resolve(secureName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored file locally in temp staging: {}", targetLocation);
        return targetLocation.toFile();
    }

    public void moveToCategory(File tempFile, FileCategory category, String datePartition, String secureName) throws IOException {
        Path categoryDir = stagingProperties.getCategories().get(category);
        if (categoryDir == null) {
            throw new IllegalArgumentException("Unsupported staging category: " + category);
        }

        Path targetDir = categoryDir.resolve(datePartition).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        Path targetLocation = targetDir.resolve(secureName);
        Files.move(tempFile.toPath(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved file to permanent category partition storage: {}", targetLocation);
    }

    public void deleteTempFile(String secureName) {
        try {
            Path targetLocation = this.tempLocation.resolve(secureName);
            Files.deleteIfExists(targetLocation);
            log.info("Rollback clean-up: Deleted temp file -> {}", targetLocation);
        } catch (IOException e) {
            log.error("Failed to delete temp file during rollback: {}", secureName, e);
        }
    }

    public void deleteCategoryFile(FileCategory category, String datePartition, String secureName) {
        try {
            Path categoryDir = stagingProperties.getCategories().get(category);
            if (categoryDir != null) {
                Path targetLocation = categoryDir.resolve(datePartition).toAbsolutePath().normalize().resolve(secureName);
                Files.deleteIfExists(targetLocation);
                log.info("Rollback clean-up: Deleted category file -> {}", targetLocation);
            }
        } catch (IOException e) {
            log.error("Failed to delete category file during rollback: {}", secureName, e);
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
     * 임시 파일을 비동기 대피소(temp/task) 디렉토리로 이동시키고 새 File 객체를 반환합니다.
     */
    public File moveTempToTask(File tempFile) throws IOException {
        if (tempFile == null || !tempFile.exists()) {
            throw new FileNotFoundException("Source temp file does not exist.");
        }
        Path targetPath = this.taskLocation.resolve(tempFile.getName());
        Files.move(tempFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved file to async fallback task staging: {}", targetPath);
        return targetPath.toFile();
    }
}
