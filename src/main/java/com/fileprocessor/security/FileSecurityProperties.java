package com.fileprocessor.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.file")
@Getter
public class FileSecurityProperties {

    @Setter
    private long maxLimitBytes;

    private final Whitelist whitelist = new Whitelist();
    private final Blacklist blacklist = new Blacklist();

    @Getter
    @Setter
    public static class Whitelist {
        private String safeDocumentsRegex;
        private String imagesRegex;
        private String textsRegex;
        private String templatesRegex;
    }

    @Getter
    @Setter
    public static class Blacklist {
        private String binariesRegex;
        private String dangerousScriptsRegex;
    }
}
