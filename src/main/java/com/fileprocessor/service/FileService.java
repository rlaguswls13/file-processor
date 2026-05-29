package com.fileprocessor.service;

import com.fileprocessor.model.*;
import com.fileprocessor.repository.BatchInsertRepository;
import com.fileprocessor.config.properties.FileSecurityProperties;
import com.fileprocessor.security.FileSecurityService;
import com.fileprocessor.service.file.FileMetadataGenerator;
import com.fileprocessor.service.file.FileStorageService;
import com.fileprocessor.service.transaction.FileTransactionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileSecurityProperties securityProperties;
    private final FileSecurityService securityService;
    private final FileStorageService storageService;
    private final AsyncFileProcessor asyncFileProcessor;
    private final BatchInsertRepository batchInsertRepository;
    private final FileMetadataGenerator metadataGenerator;
    private final FileTransactionHelper transactionHelper;
    private final TransactionTemplate transactionTemplate;

    public String uploadSimpleFile(FileCategory category, MultipartFile file, String uploadUser) throws IOException {
        String originalFilename = file.getOriginalFilename();
        log.info("Processing simple file upload in service (Transactional Staging). Category: {}, Filename: {}, User: {}", category, originalFilename, uploadUser);

        securityService.validateFileSize(file.getSize(), securityProperties.getMaxLimitBytes());
        securityService.validateFileNameByCategory(originalFilename, category);

        FileMetadata metadata = metadataGenerator.generate(originalFilename, file.getSize(), uploadUser);
        String secureName = metadata.getSecureStoredName();

        File tempFile = storageService.storeToTemp(file, secureName);
        try {
            securityService.validateFileSignature(tempFile);
            transactionTemplate.executeWithoutResult(status -> {
                batchInsertRepository.saveUploadedFileInfo(metadata);

                String datePartition = metadata.getDatePartition();
                transactionHelper.registerFileRollback(() -> {
                    log.warn("[Rollback] Transaction failed. Cleaning up files for secureName: {}", secureName);
                    storageService.deleteTempFile(secureName);
                    storageService.deleteCategoryFile(category, datePartition, secureName);
                });
                try {
                    storageService.moveToCategory(tempFile, category, datePartition, secureName);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to move file to permanent category folder", e);
                }
            });
        } catch (Exception e) {
            if(Files.exists(tempFile.toPath())) {
                storageService.deleteTempFile(secureName);
            }
            log.error("Transaction failed during simple file database mapping and movement: {}", e.getMessage(), e);
            throw new IOException("Staging file persistence failed. DB and File transactions rolled back.", e);
        }
        return secureName;
    }

    public String uploadTaskTypeFile(FileCategory category, MultipartFile file, TaskType taskType, String delimiter, String uploadUser) throws IOException {
        String originalFilename = file.getOriginalFilename();

        category.isOnlyForTaskType();

        securityService.validateFileSize(file.getSize(), securityProperties.getMaxLimitBytes());
        securityService.validateFileNameByCategory(originalFilename, category);

        FileMetadata metadata = metadataGenerator.generate(originalFilename, file.getSize(), uploadUser);
        String secureName = metadata.getSecureStoredName();

        File tempFile = storageService.storeToTemp(file, secureName);
        try {
            securityService.validateFileSignature(tempFile);
        } catch (Exception e) {
            storageService.deleteFile(tempFile);
            throw e;
        }


        String taskId;
        try {
            // [비동기] 1차 즉시 제출 시도 (SUBMITTED)
            taskId = asyncFileProcessor.registerTask(originalFilename, taskType, tempFile.getAbsolutePath(), delimiter, file.getSize(), TaskStatus.SUBMITTED);
            asyncFileProcessor.processFileAsync(taskId, tempFile, taskType, delimiter);
            log.info("Directly submitted task {} to executor (SUBMITTED).", taskId);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            log.warn("ThreadPoolTaskExecutor is FULL! Sheltering file to temp/task: {}", originalFilename);

            // 1. temp/task 격리 서브경로로 파일 대피
            File fallbackFile = storageService.moveTempToTask(tempFile);
            taskId = asyncFileProcessor.registerTask(originalFilename, taskType, fallbackFile.getAbsolutePath(), delimiter, file.getSize(), TaskStatus.QUEUED);
            log.info("Sheltered task {} successfully queued (QUEUED).", taskId);
        }

        return taskId;
    }

}
