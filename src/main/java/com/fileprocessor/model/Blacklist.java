package com.fileprocessor.model;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class Blacklist {
    private final Long id;
    private final String userIdOrContact;
    private final String reason;
    private final LocalDateTime blockedAt;
}
