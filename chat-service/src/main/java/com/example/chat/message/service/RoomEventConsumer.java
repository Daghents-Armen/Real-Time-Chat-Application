package com.example.chat.message.service;

import com.example.chat.message.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomEventConsumer {

    private final ChatMessageRepository chatMessageRepository;

    @SuppressWarnings("unused")
    @KafkaListener(topics = "room-deleted-topic", groupId = "chat-service-group-v2")
    @Transactional
    public void handleRoomDeleted(String roomIdStr) {
        try {
            UUID roomId = UUID.fromString(roomIdStr);

            chatMessageRepository.deleteByRoomId(roomId);

            log.info("SUCCESS: Deleted messages for room {}", roomId);
        } catch (Exception e) {
            log.error("FAILED: Processing room deletion event for roomId={}. Will be retried by Kafka.", roomIdStr, e);
            throw e;
        }
    }
}