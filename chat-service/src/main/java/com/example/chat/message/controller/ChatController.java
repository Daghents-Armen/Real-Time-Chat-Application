package com.example.chat.message.controller;

import com.example.chat.message.dto.MessageRequest;
import com.example.chat.message.model.ChatMessage;
import com.example.chat.message.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final Map<UUID, Map<String, SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    @PostMapping("/{roomId}/send")
    public ChatMessage sendMessage(@PathVariable UUID roomId, @RequestBody MessageRequest request, Principal principal) {
        String username = principal.getName();
        log.info("API Request: User {} is sending a message to room {}", username, roomId);

        ChatMessage saved = chatService.saveAndSendMessage(roomId, username, request.getContent());

        log.debug("Broadcasting message ID {} to active listeners in room {}", saved.getId(), roomId);
        broadcastMessage(roomId, saved);

        return saved;
    }

    @GetMapping(value = "/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(@PathVariable UUID roomId, Principal principal) {
        String username = principal.getName();
        log.info("API Request: User {} is subscribing to the SSE stream for room {}", username, roomId);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        chatService.verifyUserInRoom(roomId, username);

        roomEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(username, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE stream completed gracefully for user {} in room {}", username, roomId);
            roomEmitters.get(roomId).remove(username);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE stream timed out for user {} in room {}", username, roomId);
            roomEmitters.get(roomId).remove(username);
        });
        emitter.onError((e) -> {
            log.debug("SSE stream error for user {} in room {}: {}", username, roomId, e.getMessage());
            roomEmitters.get(roomId).remove(username);
        });

        return emitter;
    }

    @GetMapping("/{roomId}/history")
    public Page<ChatMessage> getChatHistory(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        String username = principal.getName();
        log.info("API Request: User {} is fetching chat history for room {} (Page: {}, Size: {})", username, roomId, page, size);

        return chatService.getChatHistory(roomId, username, PageRequest.of(page, size));
    }

    private void broadcastMessage(UUID roomId, ChatMessage message) {
        Map<String, SseEmitter> emitters = roomEmitters.get(roomId);
        if (emitters != null) {
            emitters.forEach((user, emitter) -> {
                try {
                    emitter.send(SseEmitter.event().name("message").data(message));
                } catch (Exception e) {
                    log.warn("Failed to push message to user {} in room {}. Removing stale SSE connection.", user, roomId);
                    emitter.complete();
                    roomEmitters.get(roomId).remove(user);
                }
            });
        } else {
            log.debug("No active SSE listeners found for room {}", roomId);
        }
    }
}