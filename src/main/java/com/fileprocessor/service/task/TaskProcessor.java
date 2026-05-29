package com.fileprocessor.service.task;

import com.fileprocessor.model.TaskType;
import java.util.Map;

public interface TaskProcessor {

    TaskType getSupportedType();

    void initializeHeaders(Map<String, Integer> headerIndexMap);

    void processRow(String[] tokens) throws Exception;

    void flush() throws Exception;
}
