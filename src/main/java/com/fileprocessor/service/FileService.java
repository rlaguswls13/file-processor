package com.fileprocessor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fileprocessor.model.AddressBook;
import com.fileprocessor.model.TargetData;
import com.fileprocessor.model.FileMetadata;
import com.fileprocessor.repository.BatchInsertRepository;
import com.fileprocessor.security.FileSecurityProperties;
import com.fileprocessor.security.FileSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileSecurityProperties securityProperties;
    private final FileSecurityService securityService;
    private final FileStorageService storageService;
    private final AsyncFileProcessor asyncFileProcessor;
    private final BatchInsertRepository batchInsertRepository;
    private final ObjectMapper objectMapper;
    
    // 신규 공통 헬퍼 빈 주입
    private final FileMetadataGenerator metadataGenerator;
    private final FileTransactionHelper transactionHelper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 단순 파일 업로드 처리 (보안 검증 + Staging 임시 저장 + Magic Number 검증 + DB 선적재 + 트랜잭션 동기화 롤백 + 실제 카테고리 영구 보관소 이관)
     */
    public String uploadSimpleFile(String category, MultipartFile file, String uploadUser) throws IOException {
        String originalFilename = file.getOriginalFilename();
        log.info("Processing simple file upload in service (Transactional Staging). Category: {}, Filename: {}, User: {}", category, originalFilename, uploadUser);

        securityService.validateFileSize(file.getSize(), securityProperties.getMaxLimitBytes());
        String expectedRegexpName = mapCategoryToRegexName(category);
        securityService.validateFileNameByCategory(originalFilename, expectedRegexpName);

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

    /**
     * 1) Multipart 파일 업로드 비즈니스 처리 (보안 3단계 검증 + 비동기 작업 등록)
     */
    public String uploadMultipartFile(String category, MultipartFile file, String taskType) throws IOException {
        String originalFilename = file.getOriginalFilename();
        log.info("Processing multipart file upload in service. Category: {}, Filename: {}", category, originalFilename);

        // [보안 1단계] 파일 크기 검사
        securityService.validateFileSize(file.getSize(), securityProperties.getMaxLimitBytes());

        // [보안 2단계] 카테고리별 확장자 정밀 검증
        String expectedRegexpName = mapCategoryToRegexName(category);
        securityService.validateFileNameByCategory(originalFilename, expectedRegexpName);

        // [저장] 로컬 임시 Staging에 격리 저장
        File tempFile = storageService.storeFile(file);

        // [보안 3단계] Magic Number 시그니처 위조 정합성 검사
        securityService.validateFileSignature(tempFile);

        // [비동기] 백그라운드 파싱 스레드 풀 등록 및 작업 위임
        String effectiveTaskType = (taskType != null && !taskType.isBlank()) ? taskType : category.toUpperCase() + "_UPLOAD";
        String taskId = asyncFileProcessor.registerTask(originalFilename, effectiveTaskType);
        asyncFileProcessor.processFileAsync(taskId, tempFile, effectiveTaskType);

        return taskId;
    }



    /**
     * 2) Raw Text 본문 직접 업로드 처리
     */
    public String uploadRawText(String rawText, String taskType) throws IOException {
        log.info("Processing raw text body upload in service. TaskType: {}", taskType);
        File tempFile = storageService.storeRawText(rawText, "raw-body-upload.txt");
        
        String taskId = asyncFileProcessor.registerTask("raw-body-upload.txt", taskType);
        asyncFileProcessor.processFileAsync(taskId, tempFile, taskType);
        
        return taskId;
    }

    /**
     * 3) 동적 타겟에 따른 JSON 역직렬화 및 벌크 인서트 일괄 처리
     */
    public void importJsonBody(String target, String jsonContent) throws IOException {
        log.info("Processing dynamic JSON import in service. Target: {}, size: {}", target, jsonContent.length());
        
        switch (target.toLowerCase().trim()) {
            case "address-book":
                List<AddressBook> addressList = objectMapper.readValue(
                        jsonContent, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, AddressBook.class)
                );
                addressList.forEach(item -> item.setCreatedAt(LocalDateTime.now()));
                batchInsertRepository.saveAddressBooksInBatch(addressList);
                log.info("AddressBook bulk insert completed successfully. Size: {}", addressList.size());
                break;
                
            case "target-data":
                List<TargetData> targetList = objectMapper.readValue(
                        jsonContent, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, TargetData.class)
                );
                targetList.forEach(item -> item.setCreatedAt(LocalDateTime.now()));
                batchInsertRepository.saveTargetDataInBatch(targetList);
                log.info("TargetData bulk insert completed successfully. Size: {}", targetList.size());
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported JSON import target: " + target);
        }
    }

    /**
     * 4) 다운로드 대상 파일 로드 및 다운로드 보안 검증 조율
     */
    public Resource loadSecureFileForDownload(String filename, Long maxDownloadBytes) throws FileNotFoundException, IOException {
        log.info("Processing secure file download in service. Filename: {}", filename);

        // [보안 1단계] 파일명 및 경로 이탈 체크
        securityService.validateFileName(filename);

        // 스토리지 파일 읽기
        Resource resource = storageService.loadFileAsResource(filename);
        long fileLength = resource.contentLength();

        // [보안 2단계] 파일 크기 검증
        long effectiveMaxLimit = (maxDownloadBytes != null) ? maxDownloadBytes : securityProperties.getMaxLimitBytes();
        securityService.validateFileSize(fileLength, effectiveMaxLimit);

        return resource;
    }

    private String mapCategoryToRegexName(String category) {
        switch (category.toLowerCase().trim()) {
            case "image": return "images";
            case "template": return "templates";
            case "text": return "texts";
            case "general": return "safe-documents";
            default: throw new IllegalArgumentException("Unsupported upload category: " + category);
        }
    }
}
