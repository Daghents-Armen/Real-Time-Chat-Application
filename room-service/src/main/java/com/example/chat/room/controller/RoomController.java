package com.example.chat.room.controller;

import com.example.chat.room.dto.RoomRequest;
import com.example.chat.room.dto.RoomResponse;
import com.example.chat.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request, Authentication authentication) {
        String ownerUsername = authentication.getName();
        log.info("API Request: {} is creating a room named '{}'", ownerUsername, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request, ownerUsername));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        log.info("API Request: Fetching all rooms");
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<String> joinRoom(@PathVariable UUID roomId, Authentication authentication) {
        String username = authentication.getName();
        log.info("API Request: {} is joining room {}", username, roomId);
        roomService.joinRoom(roomId, username);
        return ResponseEntity.ok("Successfully joined the room.");
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<String> leaveRoom(@PathVariable UUID roomId, Authentication authentication) {
        String username = authentication.getName();
        log.info("API Request: {} is leaving room {}", username, roomId);
        roomService.leaveRoom(roomId, username);
        return ResponseEntity.ok("Successfully left the room.");
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<String>> getRoomMembers(@PathVariable UUID roomId) {
        log.info("API Request: Fetching members for room {}", roomId);
        return ResponseEntity.ok(roomService.getRoomMembers(roomId));
    }

    @PostMapping("/{roomId}/members/{usernameToAdd}")
    public ResponseEntity<String> adminAddUser(@PathVariable UUID roomId, @PathVariable String usernameToAdd, Authentication authentication) {
        String adminUsername = authentication.getName();
        log.info("API Request: Admin {} is adding user {} to room {}", adminUsername, usernameToAdd, roomId);
        roomService.addUser(roomId, adminUsername, usernameToAdd);
        return ResponseEntity.ok("User successfully added by Admin.");
    }

    @DeleteMapping("/{roomId}/members/{usernameToKick}")
    public ResponseEntity<String> adminKickUser(@PathVariable UUID roomId, @PathVariable String usernameToKick, Authentication authentication) {
        String adminUsername = authentication.getName();
        log.info("API Request: Admin {} is kicking user {} from room {}", adminUsername, usernameToKick, roomId);
        roomService.kickUser(roomId, adminUsername, usernameToKick);
        return ResponseEntity.ok("User successfully kicked by Admin.");
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<String> deleteRoom(@PathVariable UUID roomId, Authentication authentication) {
        String adminUsername = authentication.getName();
        log.info("API Request: Admin {} is deleting room {}", adminUsername, roomId);
        roomService.deleteRoom(roomId, adminUsername);
        return ResponseEntity.ok("Room successfully deleted.");
    }
}