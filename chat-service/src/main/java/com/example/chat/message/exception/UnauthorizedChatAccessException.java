package com.example.chat.message.exception;

public class UnauthorizedChatAccessException extends RuntimeException {
    public UnauthorizedChatAccessException(String message) {
        super(message);
    }
}