package com.example.chat.room.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoomRequest {
    @NotBlank(message = "Room name cannot be empty")
    private String name;
    private String description;
}