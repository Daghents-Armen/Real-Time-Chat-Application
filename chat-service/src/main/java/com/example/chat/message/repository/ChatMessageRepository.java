package com.example.chat.message.repository;

import com.example.chat.message.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Page<ChatMessage> findByRoomIdOrderByTimestampDesc(UUID roomId, Pageable pageable);
    void deleteByRoomId(UUID roomId);
}