package com.fileprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class TargetData {
    private Long id;
    private String userId;
    private String targetGroup;   // 타겟그룹코드
    private String channel;       // 발송 채널 (SMS, EMAIL, PUSH)
    private String mappingInfo;   // 매핑 정보 (JSON String)
    private LocalDateTime createdAt;
}
