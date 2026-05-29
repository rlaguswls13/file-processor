package com.fileprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskType {
    ADDRESS_BOOK("마스터 정보", "ID, 이메일, Push 토큰, 가입일"),
    TARGETING("발송 대상 수신자 그룹", "그룹ID, 매핑 정보(JSON), 발송 채널"),
    BLACKLIST("수신 차단 블랙리스트", "ID/연락처, 차단 사유, 차단 일시"),
    WHITELIST("수신 동의 화이트리스트", "ID, 동의 일시, 유효 기간"),
    TESTLIST("내부 발송 테스트 명단", "이름, 테스트용 연락처");

    private final String role;
    private final String majorFields;
}
