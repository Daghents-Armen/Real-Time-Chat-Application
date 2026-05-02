package com.example.chat.message.exception;

public class RoomServiceUnavailableException extends RuntimeException {
    public RoomServiceUnavailableException(String message) {
        super(message);
    }
}