package com.fileprocessor.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileMetadata {
    private final String originalName;
    private final String secureStoredName; // flat 고유 파일명
    private final String extension;
    private final String uuid;
    private final long fileSize;
    private final String uploadUser;
    private final String datePartition;    // "yyyy-MM-dd" 디렉토리명

    /**
     * DB 적재용 접두사(날짜 파티션)가 포함된 최종 저장 경로 반환
     */
    public String getRelativeStoredPath() {
        return datePartition + "/" + secureStoredName;
    }
}
