package com.fileprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressBook {
    private Long id;
    private String name;
    private String phoneNumber;
    private String email;
    private String groupName;
    private LocalDateTime createdAt;
}
