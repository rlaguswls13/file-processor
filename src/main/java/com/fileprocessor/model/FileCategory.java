package com.fileprocessor.model;

import lombok.Getter;
import lombok.Setter;

import java.util.regex.Pattern;


public enum FileCategory {
    IMAGE,
    TEMPLATE,
    TEXT,
    DOCUMENT,
    BINARY,
    SCRIPT,
    LINE_DEMETER,
    JSON_PARSER;

    @Setter
    @Getter
    private Pattern regex;

    public void isOnlyForTaskType() {
        if(!(this == FileCategory.LINE_DEMETER || this == JSON_PARSER)) {
            throw new SecurityException("File category not implemented yet.");
        }
    }
}
