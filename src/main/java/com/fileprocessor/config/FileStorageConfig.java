package com.fileprocessor.config;

import com.fileprocessor.config.properties.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Getter
public class FileStorageConfig {

    private final FileStorageProperties fileStorageProperties;

    private Path tempLocation;
    private Path taskLocation;

    @PostConstruct
    public void initDirectories() {
        log.info("Initialized File Storage staging and category directories successfully from Config Class:");
        this.tempLocation = fileStorageProperties.getTempDir();
        this.taskLocation = fileStorageProperties.getTempTaskDir();

        try {
            Files.createDirectories(this.tempLocation);
            log.info(" - Temp Staging dir: {}", this.tempLocation);
            Files.createDirectories(this.taskLocation);
            log.info(" - Task Fallback dir: {}", this.taskLocation);
            if (fileStorageProperties.getCategories() != null) {
                for (Path categoryPath : fileStorageProperties.getCategories().values()) {
                    Files.createDirectories(categoryPath.toAbsolutePath().normalize());
                    log.info(" - File Category dir: {}", categoryPath);
                }
            }
        } catch (IOException e) {
            log.error("Could not create directories for staging files from Config Class", e);
            throw new RuntimeException("Failed to initialize file storage directories.", e);
        }
    }
}
