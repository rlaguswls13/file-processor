package com.fileprocessor.config.properties;

import com.fileprocessor.model.FileCategory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "app.security.file")
@Getter
@Setter
public class FileSecurityProperties {
    private long maxLimitBytes;
    private Map<FileCategory, Pattern> whitelist;
    private Map<FileCategory, Pattern> blacklist;
    private Map<FileCategory, Pattern> tasklist;
}
