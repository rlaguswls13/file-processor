package com.fileprocessor.config;

import com.fileprocessor.config.properties.FileSecurityProperties;
import com.fileprocessor.model.FileCategory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileSecurityConfig {

    private final FileSecurityProperties fileSecurityProperties;

    @PostConstruct
    public void init() {
        log.info("Initializing File Security Patterns from Config class...");
        if (fileSecurityProperties.getWhitelist() != null) {
            fileSecurityProperties.getWhitelist().forEach(FileCategory::setRegex);
        }
        if (fileSecurityProperties.getBlacklist() != null) {
            fileSecurityProperties.getBlacklist().forEach(FileCategory::setRegex);
        }
        if (fileSecurityProperties.getTasklist() != null) {
            fileSecurityProperties.getTasklist().forEach(FileCategory::setRegex);
        }
        log.info("Successfully injected whitelist & blacklist regex patterns into FileCategory constants.");
    }
}
