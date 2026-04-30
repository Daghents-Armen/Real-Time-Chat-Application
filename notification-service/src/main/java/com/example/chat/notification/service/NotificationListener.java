package com.example.chat.notification.service;

import com.example.chat.notification.dto.MessageSentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationListener {
    @KafkaListener(topics = "chat-messages-topic")
    public void handleNewMessage(MessageSentEvent event) {
        log.info("Activity in Room: {}", event.getRoomId());
        log.info("User [{}] sent a message.", event.getSenderUsername());
        log.info("Message preview: '{}'", event.getContent());
        log.info("Action: Simulating push notification to offline members...");
    }
}