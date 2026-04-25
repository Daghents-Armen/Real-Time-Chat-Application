package com.example.chat.message.repository;

import com.example.chat.message.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Page<ChatMessage> findByRoomIdOrderByTimestampDesc(UUID roomId, Pageable pageable);
    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.roomId = :roomId")
    void deleteByRoomId(UUID roomId);
}