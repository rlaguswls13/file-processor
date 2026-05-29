package com.example.fileprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetData {
    private Long id;
    private String userId;
    private String actionPattern; // 행동패턴/이벤트
    private String targetGroup;   // 타겟그룹코드
    private Double score;         // 스코어
    private LocalDateTime createdAt;
}
