package com.example.chat.message.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomDeletedEvent {
    private UUID roomId;
    private String deletedBy;
    private LocalDateTime deletedAt;
}