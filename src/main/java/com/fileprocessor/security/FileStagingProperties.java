package com.fileprocessor.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.file")
@Getter
@Setter
public class FileStagingProperties {
    private String tempDir;
    private String downloadDir;
    private Map<String, String> categories;
}
