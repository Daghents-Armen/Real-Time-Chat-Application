package com.example.chat.message.service;

import com.example.chat.room.dto.RoomDeletedEvent;
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
    @KafkaListener(topics = "room-deleted-topic")
    @Transactional
    public void handleRoomDeleted(RoomDeletedEvent event) {
        try {
            UUID roomId = event.getRoomId();
            log.info("Room {} was deleted by {} at {}", roomId, event.getDeletedBy(), event.getDeletedAt());

            chatMessageRepository.deleteByRoomId(roomId);

            log.info("SUCCESS: Deleted messages for room {}", roomId);
        } catch (Exception e) {
            log.error("FAILED: Processing room deletion event for roomId={}. Will be retried by Kafka.", event.getRoomId(), e);
            throw e;
        }
    }
}