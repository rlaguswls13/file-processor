package com.fileprocessor.service.parser;

import com.fileprocessor.service.task.TaskProcessor;
import java.io.File;

public interface FileParser {
    
    /**
     * 특정 파일을 스트리밍 파싱하여 TaskProcessor에 한 줄씩 공급합니다.
     * 
     * @param file      파싱할 실제 파일 객체
     * @param processor 주입받아 공급을 수행할 TaskProcessor 컴포넌트
     * @throws Exception 파싱 및 파일 로드 중 발생 가능한 모든 오류
     */
    void parse(File file, TaskProcessor processor) throws Exception;
    void parse(File file, TaskProcessor processor, String delimiter) throws Exception;
}
