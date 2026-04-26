package com.example.chat.room.exception;

public class UnauthorizedRoomAccessException extends RuntimeException {
    public UnauthorizedRoomAccessException(String message) {
        super(message);
    }
}