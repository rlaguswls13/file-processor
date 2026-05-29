package com.example.fileprocessor.service.parser;

import java.io.File;

public interface FileParser {
    
    /**
     * 특정 파일을 파싱하여 DB에 벌크 삽입 처리합니다.
     * 
     * @param file     파싱할 실제 파일 객체
     * @param taskType 작업의 종류 ("ADDRESS_BOOK" 또는 "TARGET_DATA")
     * @throws Exception 파싱 및 파일 로드 중 발생 가능한 모든 오류
     */
    void parseAndSave(File file, String taskType) throws Exception;
}
