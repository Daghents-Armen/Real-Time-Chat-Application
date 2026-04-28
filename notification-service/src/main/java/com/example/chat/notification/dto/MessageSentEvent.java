package com.example.chat.notification.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MessageSentEvent {
    private UUID id;
    private UUID roomId;
    private String senderUsername;
    private String content;
    private LocalDateTime timestamp;
}