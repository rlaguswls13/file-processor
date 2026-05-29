package com.fileprocessor.model;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class Whitelist {
    private final Long id;
    private final String userId;
    private final LocalDateTime consentedAt;
    private final LocalDateTime validUntil;
}
