package com.example.chat.message.service;

import com.example.chat.message.model.ChatMessage;
import com.example.chat.message.repository.ChatMessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter messageCounter;

    public ChatService(ChatMessageRepository repository, KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;

        this.messageCounter = Counter.builder("chat.messages.sent.total")
                .description("Total number of messages sent through the Chat Service")
                .register(meterRegistry);
    }

    public ChatMessage saveAndSendMessage(UUID roomId, String senderUsername, String content) {
        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .senderUsername(senderUsername)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        ChatMessage saved = repository.save(message);
        kafkaTemplate.send("chat-messages-topic", roomId.toString(), saved);

        messageCounter.increment();

        return saved;
    }

    public Page<ChatMessage> getChatHistory(UUID roomId, Pageable pageable) {
        return repository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
    }
}