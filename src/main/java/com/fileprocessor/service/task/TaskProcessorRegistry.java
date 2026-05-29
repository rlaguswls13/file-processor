package com.fileprocessor.service.task;

import com.fileprocessor.model.TaskType;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskProcessorRegistry {
    private final Map<TaskType, TaskProcessor> processorMap = new EnumMap<>(TaskType.class);

    public TaskProcessorRegistry(List<TaskProcessor> processors) {
        for (TaskProcessor processor : processors) {
            processorMap.put(processor.getSupportedType(), processor);
        }
    }

    public TaskProcessor getProcessor(TaskType taskType) {
        TaskProcessor processor = processorMap.get(taskType);
        if (processor == null) {
            throw new IllegalArgumentException("No TaskProcessor registered for type: " + taskType);
        }
        return processor;
    }
}
