package com.fileprocessor.model;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class Testlist {
    private final Long id;
    private final String name;
    private final String testContact;
    private final LocalDateTime createdAt;
}
