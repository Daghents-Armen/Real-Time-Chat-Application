package com.example.chat.message.service;

import com.example.chat.message.exception.UnauthorizedChatAccessException;
import com.example.chat.message.model.ChatMessage;
import com.example.chat.message.repository.ChatMessageRepository;
import io.micrometer.core.instrument.Counter;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter messageCounter;
    private final RestTemplate restTemplate;

    @Value("${ROOM_SERVICE_URL:http://localhost:8082}")
    private String roomServiceBaseUrl;

    public ChatService(ChatMessageRepository repository, KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry, RestTemplate restTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;

        this.messageCounter = Counter.builder("chat.messages.sent.total")
                .description("Total number of messages sent through the Chat Service")
                .register(meterRegistry);
        this.restTemplate = restTemplate;
    }

    public void verifyUserInRoom(UUID roomId, String username) {
        String url = roomServiceBaseUrl + "/api/rooms/" + roomId + "/members";
        log.debug("Starting room membership verification for user: {} in room: {}", username, roomId);

        try {
            HttpHeaders headers = new HttpHeaders();

            var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");

                if (authHeader != null) {
                    log.debug("Authorization header found, attaching to Room Service request.");
                    headers.set("Authorization", authHeader);
                } else {
                    log.debug("No Authorization header found in the current request.");
                }
            } else {
                log.debug("No active web request context found. Proceeding without Authorization header.");
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.debug("Calling Room Service at: {}", url);
            ParameterizedTypeReference<List<String>> responseType = new ParameterizedTypeReference<>() {};

            ResponseEntity<List<String>> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);

            List<String> members = response.getBody();

            if (members == null || !members.contains(username)) {
                log.warn("Verification failed: User {} is not a member of room {}", username, roomId);
                throw new UnauthorizedChatAccessException("You are not a member of this room.");
            }

            log.info("Successfully verified user {} is a member of room {}", username, roomId);

        } catch (UnauthorizedChatAccessException e) {
            throw e;
        } catch (Exception e) {
            log.error("System error while verifying membership for user {} in room {}. Error: {}", username, roomId, e.getMessage(), e);
            throw new UnauthorizedChatAccessException("Failed to verify room membership with Room Service.");
        }
    }

    @Transactional
    public ChatMessage saveAndSendMessage(UUID roomId, String senderUsername, String content) {
        verifyUserInRoom(roomId, senderUsername);

        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .senderUsername(senderUsername)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        log.debug("Saving message for user {} in room {}", senderUsername, roomId);
        ChatMessage saved = repository.save(message);
        log.info("Message saved by {} in room {}", senderUsername, roomId);
        log.debug("Sending message to Kafka topic chat-messages-topic for room {}", roomId);
        kafkaTemplate.send("chat-messages-topic", roomId.toString(), saved);

        messageCounter.increment();

        return saved;
    }

    public Page<ChatMessage> getChatHistory(UUID roomId, String username, Pageable pageable) {
        verifyUserInRoom(roomId, username);
        log.debug("Fetching chat history from database for room {}", roomId);

        return repository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
    }
}