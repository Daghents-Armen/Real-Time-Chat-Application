package com.example.chat.room.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomDeletedEvent {
    private UUID roomId;
    private String deletedBy;
    private LocalDateTime deletedAt;
}