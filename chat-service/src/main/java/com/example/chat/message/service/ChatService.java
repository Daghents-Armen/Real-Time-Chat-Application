package com.example.chat.message.service;

import com.example.chat.message.exception.UnauthorizedChatAccessException;
import com.example.chat.message.model.ChatMessage;
import com.example.chat.message.repository.ChatMessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter messageCounter;
    private final RestTemplate restTemplate;

    public ChatService(ChatMessageRepository repository, KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry, RestTemplate restTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;

        this.messageCounter = Counter.builder("chat.messages.sent.total")
                .description("Total number of messages sent through the Chat Service")
                .register(meterRegistry);
        this.restTemplate = restTemplate;
    }

    public void verifyUserInRoom(UUID roomId, String username) {
        String url = "http://room-service:8081/api/rooms/" + roomId + "/members";

        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String authHeader = request.getHeader("Authorization");

            HttpHeaders headers = new HttpHeaders();
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            List<?> members = response.getBody();

            if (members == null || !members.contains(username)) {
                throw new UnauthorizedChatAccessException("You are not a member of this room.");
            }
        } catch (UnauthorizedChatAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedChatAccessException("Failed to verify room membership with Room Service.");
        }
    }

    public ChatMessage saveAndSendMessage(UUID roomId, String senderUsername, String content) {
        verifyUserInRoom(roomId, senderUsername);

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

    public Page<ChatMessage> getChatHistory(UUID roomId, String username, Pageable pageable) {
        verifyUserInRoom(roomId, username);

        return repository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
    }
}