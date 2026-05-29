package com.fileprocessor.repository;

import com.fileprocessor.model.AddressBook;
import com.fileprocessor.model.TargetData;
import com.fileprocessor.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BatchInsertRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 애플리케이션 구동 완료 후, H2 데이터베이스 내 테스트 테이블을 안전하게 생성합니다. (스키마 초기화)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initSchema() {
        log.info("Initializing H2 database schemas for AddressBook and TargetData after application is ready...");
        
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS address_book (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(100)," +
                "phone_number VARCHAR(50)," +
                "email VARCHAR(100)," +
                "group_name VARCHAR(100)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
                
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS target_data (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "user_id VARCHAR(100)," +
                "action_pattern VARCHAR(255)," +
                "target_group VARCHAR(100)," +
                "score DOUBLE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
        
        jdbcTemplate.execute("DROP TABLE IF EXISTS uploaded_file");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS uploaded_file (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "original_name VARCHAR(255)," +
                "stored_name VARCHAR(255)," +
                "extension VARCHAR(50)," +
                "uuid VARCHAR(100) UNIQUE," +
                "file_size BIGINT," +
                "upload_user VARCHAR(100)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
        
        log.info("Database schemas initialized successfully.");
    }

    /**
     * 주소록 대량 벌크 인서트 (JdbcTemplate batchUpdate 활용)
     */
    @Transactional
    public void saveAddressBooksInBatch(List<AddressBook> addressBooks) {
        if (addressBooks.isEmpty()) return;
        
        String sql = "INSERT INTO address_book (name, phone_number, email, group_name, created_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
                     
        long startTime = System.currentTimeMillis();
        
        jdbcTemplate.batchUpdate(
            sql,
            addressBooks,
            addressBooks.size(), // 청크 사이즈 통째로 전달
            (ps, addressBook) -> {
                ps.setString(1, addressBook.getName());
                ps.setString(2, addressBook.getPhoneNumber());
                ps.setString(3, addressBook.getEmail());
                ps.setString(4, addressBook.getGroupName());
                ps.setTimestamp(5, Timestamp.valueOf(addressBook.getCreatedAt()));
            }
        );
        
        long endTime = System.currentTimeMillis();
        log.info("Batch inserted {} AddressBook records in {} ms", addressBooks.size(), (endTime - startTime));
    }

    /**
     * 타겟팅 데이터 대량 벌크 인서트 (JdbcTemplate batchUpdate 활용)
     */
    @Transactional
    public void saveTargetDataInBatch(List<TargetData> targetDataList) {
        if (targetDataList.isEmpty()) return;
        
        String sql = "INSERT INTO target_data (user_id, action_pattern, target_group, score, created_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
                     
        long startTime = System.currentTimeMillis();
        
        jdbcTemplate.batchUpdate(
            sql,
            targetDataList,
            targetDataList.size(),
            (ps, targetData) -> {
                ps.setString(1, targetData.getUserId());
                ps.setString(2, targetData.getActionPattern());
                ps.setString(3, targetData.getTargetGroup());
                ps.setDouble(4, targetData.getScore());
                ps.setTimestamp(5, Timestamp.valueOf(targetData.getCreatedAt()));
            }
        );
        
        long endTime = System.currentTimeMillis();
        log.info("Batch inserted {} TargetData records in {} ms", targetDataList.size(), (endTime - startTime));
    }

    /**
     * 단순 업로드된 파일 정보 DB 저장 (FileMetadata 기반)
     */
    @Transactional
    public void saveUploadedFileInfo(FileMetadata metadata) {
        String sql = "INSERT INTO uploaded_file (original_name, stored_name, extension, uuid, file_size, upload_user, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        jdbcTemplate.update(
            sql, 
            metadata.getOriginalName(), 
            metadata.getRelativeStoredPath(), 
            metadata.getExtension(), 
            metadata.getUuid(), 
            metadata.getFileSize(), 
            metadata.getUploadUser()
        );
        log.info("Saved uploaded file metadata to DB [UUID: {}]", metadata.getUuid());
    }
}
