package com.fileprocessor.repository;

import com.fileprocessor.model.AddressBook;
import com.fileprocessor.model.TargetData;
import com.fileprocessor.model.FileMetadata;
import com.fileprocessor.model.Blacklist;
import com.fileprocessor.model.Whitelist;
import com.fileprocessor.model.Testlist;
import com.fileprocessor.model.TaskType;
import com.fileprocessor.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
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
        log.info("Initializing H2 database schemas for AddressBook, TargetData, Blacklist, Whitelist, Testlist and Async Queue tables...");
        
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS address_book (" +
                 "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                 "name VARCHAR(100)," +
                 "phone_number VARCHAR(50)," +
                 "email VARCHAR(100)," +
                 "group_name VARCHAR(100)," +
                 "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                 ")");
                 
        // target_data 테이블을 JSON mapping_info 구조로 리포맷하기 위해 DROP 후 신규 컬럼셋 기동
        jdbcTemplate.execute("DROP TABLE IF EXISTS target_data");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS target_data (" +
                 "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                 "user_id VARCHAR(100)," +
                 "target_group VARCHAR(100)," +
                 "channel VARCHAR(50)," +
                 "mapping_info VARCHAR(4000)," +
                 "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                 ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS blacklist (" +
                 "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                 "user_id_or_contact VARCHAR(255)," +
                 "reason VARCHAR(255)," +
                 "blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                 ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS whitelist (" +
                 "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                 "user_id VARCHAR(255)," +
                 "consented_at TIMESTAMP," +
                 "valid_until TIMESTAMP" +
                 ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS testlist (" +
                 "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                 "name VARCHAR(100)," +
                 "test_contact VARCHAR(100)," +
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

        // 비동기 작업 대피(QUEUED) 트랙커용 테이블 생성
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS async_task (" +
                 "task_id VARCHAR(100) PRIMARY KEY," +
                 "task_type VARCHAR(50)," +
                 "status VARCHAR(50)," + // QUEUED, SUBMITTED, PROCESSING, COMPLETED, FAILED
                 "error_message VARCHAR(500)," +
                 "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                 "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                 ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS async_task_file (" +
                 "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                 "task_id VARCHAR(100)," +
                 "original_name VARCHAR(255)," +
                 "stored_path VARCHAR(255)," +
                 "delimiter VARCHAR(50)," +
                 "file_size BIGINT," +
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
        
        String sql = "INSERT INTO target_data (user_id, target_group, channel, mapping_info, created_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
                     
        long startTime = System.currentTimeMillis();
        
        jdbcTemplate.batchUpdate(
            sql,
            targetDataList,
            targetDataList.size(),
            (ps, targetData) -> {
                ps.setString(1, targetData.getUserId());
                ps.setString(2, targetData.getTargetGroup());
                ps.setString(3, targetData.getChannel());
                ps.setString(4, targetData.getMappingInfo());
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

    /**
     * 블랙리스트 대량 벌크 인서트
     */
    @Transactional
    public void saveBlacklistInBatch(List<Blacklist> blacklistList) {
        if (blacklistList.isEmpty()) return;
        String sql = "INSERT INTO blacklist (user_id_or_contact, reason, blocked_at) VALUES (?, ?, ?)";
        long startTime = System.currentTimeMillis();
        jdbcTemplate.batchUpdate(
            sql,
            blacklistList,
            blacklistList.size(),
            (ps, item) -> {
                ps.setString(1, item.getUserIdOrContact());
                ps.setString(2, item.getReason());
                ps.setTimestamp(3, Timestamp.valueOf(item.getBlockedAt()));
            }
        );
        long endTime = System.currentTimeMillis();
        log.info("Batch inserted {} Blacklist records in {} ms", blacklistList.size(), (endTime - startTime));
    }

    /**
     * 화이트리스트 대량 벌크 인서트
     */
    @Transactional
    public void saveWhitelistInBatch(List<Whitelist> whitelistList) {
        if (whitelistList.isEmpty()) return;
        String sql = "INSERT INTO whitelist (user_id, consented_at, valid_until) VALUES (?, ?, ?)";
        long startTime = System.currentTimeMillis();
        jdbcTemplate.batchUpdate(
            sql,
            whitelistList,
            whitelistList.size(),
            (ps, item) -> {
                ps.setString(1, item.getUserId());
                ps.setTimestamp(2, Timestamp.valueOf(item.getConsentedAt()));
                ps.setTimestamp(3, Timestamp.valueOf(item.getValidUntil()));
            }
        );
        long endTime = System.currentTimeMillis();
        log.info("Batch inserted {} Whitelist records in {} ms", whitelistList.size(), (endTime - startTime));
    }

    /**
     * 테스트 대상자 대량 벌크 인서트
     */
    @Transactional
    public void saveTestlistInBatch(List<Testlist> testlistList) {
        if (testlistList.isEmpty()) return;
        String sql = "INSERT INTO testlist (name, test_contact, created_at) VALUES (?, ?, ?)";
        long startTime = System.currentTimeMillis();
        jdbcTemplate.batchUpdate(
            sql,
            testlistList,
            testlistList.size(),
            (ps, item) -> {
                ps.setString(1, item.getName());
                ps.setString(2, item.getTestContact());
                ps.setTimestamp(3, Timestamp.valueOf(item.getCreatedAt()));
            }
        );
        long endTime = System.currentTimeMillis();
        log.info("Batch inserted {} Testlist records in {} ms", testlistList.size(), (endTime - startTime));
    }

    // ==========================================
    // 비동기 작업 대피(QUEUE) 및 스케줄러 연동용 레포지토리 헬퍼
    // ==========================================

    @Transactional
    public void saveAsyncTask(String taskId, TaskType taskType, TaskStatus status) {
        String sql = "INSERT INTO async_task (task_id, task_type, status, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        jdbcTemplate.update(sql, taskId, taskType.name(), status.name());
        log.info("Saved async task registry to DB: {} [Status: {}]", taskId, status);
    }

    @Transactional
    public void saveAsyncTaskFile(String taskId, String originalName, String storedPath, String delimiter, long fileSize) {
        String sql = "INSERT INTO async_task_file (task_id, original_name, stored_path, delimiter, file_size, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        jdbcTemplate.update(sql, taskId, originalName, storedPath, delimiter, fileSize);
        log.info("Saved async task file metadata to DB for task: {}", taskId);
    }

    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus status, String errorMessage) {
        String sql = "UPDATE async_task SET status = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP WHERE task_id = ?";
        jdbcTemplate.update(sql, status.name(), errorMessage, taskId);
        log.info("Updated async task {} status to {}", taskId, status);
    }

    @Transactional(readOnly = true)
    public QueuedTask fetchOldestQueuedTask() {
        String sql = "SELECT t.task_id, t.task_type, f.stored_path, f.delimiter " +
                     "FROM async_task t " +
                     "JOIN async_task_file f ON t.task_id = f.task_id " +
                     "WHERE t.status = 'QUEUED' " +
                     "ORDER BY t.created_at ASC LIMIT 1";
        
        List<QueuedTask> list = jdbcTemplate.query(sql, (rs, rowNum) -> new QueuedTask(
                rs.getString("task_id"),
                rs.getString("stored_path"),
                rs.getString("delimiter"),
                TaskType.valueOf(rs.getString("task_type"))
        ));
        return list.isEmpty() ? null : list.get(0);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class QueuedTask {
        private String taskId;
        private String storedPath;
        private String delimiter;
        private TaskType taskType;
    }
}
