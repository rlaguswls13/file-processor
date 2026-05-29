package com.fileprocessor.service.task.processor;

import com.fileprocessor.model.TargetData;
import com.fileprocessor.model.TaskType;
import com.fileprocessor.repository.BatchInsertRepository;
import com.fileprocessor.service.task.TaskProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TargetingTaskProcessor implements TaskProcessor {
    private final BatchInsertRepository repository;
    private final List<TargetData> chunk = new ArrayList<>(1000);

    private Map<String, Integer> headersMap = null;
    private int userIdIdx = -1;
    private int groupIdx = -1;
    private int channelIdx = -1;

    @Override
    public TaskType getSupportedType() {
        return TaskType.TARGETING;
    }

    @Override
    public void initializeHeaders(Map<String, Integer> headerIndexMap) {
        if (headerIndexMap == null) return;
        this.headersMap = headerIndexMap;
        this.userIdIdx = headerIndexMap.getOrDefault("user_id", -1);
        this.groupIdx = headerIndexMap.containsKey("target_group") ? 
                headerIndexMap.get("target_group") : headerIndexMap.getOrDefault("group_id", -1);
        this.channelIdx = headerIndexMap.containsKey("channel") ? 
                headerIndexMap.get("channel") : headerIndexMap.getOrDefault("send_channel", -1);
    }

    @Override
    public synchronized void processRow(String[] tokens) throws Exception {
        if (tokens == null || tokens.length == 0) return;

        String userId = getSafeToken(tokens, userIdIdx);
        String targetGroup = getSafeToken(tokens, groupIdx);
        String channel = getSafeToken(tokens, channelIdx);

        if (userId.isEmpty()) return;

        // Build mapping_info as JSON string in O(N) using StringBuilder with zero heavy object/map allocations
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        if (headersMap != null) {
            for (Map.Entry<String, Integer> entry : headersMap.entrySet()) {
                String col = entry.getKey();
                int idx = entry.getValue();
                
                // Skip the core columns as they are saved in explicit DB fields
                if (col.equals("user_id") || col.equals("target_group") || col.equals("group_id") || col.equals("channel") || col.equals("send_channel")) {
                    continue;
                }
                
                String val = getSafeToken(tokens, idx);
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(escapeJson(col)).append("\":\"").append(escapeJson(val)).append("\"");
                first = false;
            }
        }
        sb.append("}");

        TargetData td = TargetData.builder()
                .userId(userId)
                .targetGroup(targetGroup)
                .channel(channel.isEmpty() ? "EMAIL" : channel)
                .mappingInfo(sb.toString())
                .createdAt(LocalDateTime.now())
                .build();
        chunk.add(td);
        if (chunk.size() >= 1000) {
            repository.saveTargetDataInBatch(chunk);
            chunk.clear();
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public synchronized void flush() throws Exception {
        if (!chunk.isEmpty()) {
            repository.saveTargetDataInBatch(chunk);
            chunk.clear();
        }
    }

    private String getSafeToken(String[] tokens, int index) {
        if (index < 0 || index >= tokens.length) return "";
        String val = tokens[index];
        return val != null ? val.trim() : "";
    }
}
