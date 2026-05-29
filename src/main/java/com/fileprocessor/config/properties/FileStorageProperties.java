package com.fileprocessor.config.properties;

import com.fileprocessor.model.FileCategory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.file")
@Getter
@Setter
public class FileStorageProperties {
    private Path tempDir;
    private Path tempTaskDir;
    private Map<FileCategory, Path> categories;
}
