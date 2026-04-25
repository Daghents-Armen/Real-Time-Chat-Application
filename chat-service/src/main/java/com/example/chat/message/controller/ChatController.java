package com.example.chat.message.controller;

import com.example.chat.message.dto.MessageRequest;
import com.example.chat.message.model.ChatMessage;
import com.example.chat.message.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final Map<UUID, Map<String, SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    @PostMapping("/{roomId}/send")
    public ChatMessage sendMessage(@PathVariable UUID roomId, @RequestBody MessageRequest request, Principal principal) {

        ChatMessage saved = chatService.saveAndSendMessage(roomId, principal.getName(), request.getContent());

        broadcastMessage(roomId, saved);

        return saved;
    }

    @GetMapping(value = "/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(@PathVariable UUID roomId, Principal principal) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String username = principal.getName();

        roomEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(username, emitter);

        emitter.onCompletion(() -> roomEmitters.get(roomId).remove(username));
        emitter.onTimeout(() -> roomEmitters.get(roomId).remove(username));
        emitter.onError((e) -> roomEmitters.get(roomId).remove(username));

        return emitter;
    }

    @GetMapping("/{roomId}/history")
    public Page<ChatMessage> getChatHistory(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return chatService.getChatHistory(roomId, PageRequest.of(page, size));
    }

    private void broadcastMessage(UUID roomId, ChatMessage message) {
        Map<String, SseEmitter> emitters = roomEmitters.get(roomId);
        if (emitters != null) {
            emitters.forEach((user, emitter) -> {
                try {
                    emitter.send(SseEmitter.event().name("message").data(message));
                } catch (Exception e) {
                    emitter.complete();
                    roomEmitters.get(roomId).remove(user);
                }
            });
        }
    }
}