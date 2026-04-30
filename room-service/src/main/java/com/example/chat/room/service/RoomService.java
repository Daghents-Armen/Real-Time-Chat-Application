package com.example.chat.room.service;

import com.example.chat.room.dto.RoomRequest;
import com.example.chat.room.dto.RoomResponse;
import com.example.chat.room.exception.BadRequestException;
import com.example.chat.room.exception.RoomNotFoundException;
import com.example.chat.room.exception.UnauthorizedRoomAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.chat.room.model.Room;
import com.example.chat.room.model.RoomMember;
import com.example.chat.room.repository.RoomMemberRepository;
import com.example.chat.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public RoomResponse createRoom(RoomRequest request, String ownerUsername) {
        log.debug("User {} is attempting to create a new room named: {}", ownerUsername, request.getName());

        if (roomRepository.existsByName(request.getName())) {
            log.warn("Room creation failed: Room name '{}' already exists.", request.getName());
            throw new BadRequestException("A room with this name already exists.");
        }

        Room room = new Room();
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setOwnerUsername(ownerUsername);
        room.setCreatedAt(LocalDateTime.now());

        Room savedRoom = roomRepository.save(room);
        log.debug("Room saved to database with ID: {}", savedRoom.getId());

        RoomMember member = new RoomMember();
        member.setRoomId(savedRoom.getId());
        member.setUsername(ownerUsername);

        member.setJoinedAt(LocalDateTime.now());
        roomMemberRepository.save(member);
        log.info("Successfully created room '{}' (ID: {}) with owner {}", savedRoom.getName(), savedRoom.getId(), ownerUsername);

        return mapToResponse(savedRoom);
    }

    public Page<RoomResponse> getAllRooms(Pageable pageable) {
        log.debug("Fetching a page of rooms from the database.");

        return roomRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public void joinRoom(UUID roomId, String username) {
        log.debug("User {} attempting to join room {}", username, roomId);

        if (!roomRepository.existsById(roomId)) {
            log.warn("Join failed: Room {} does not exist.", roomId);
            throw new RoomNotFoundException("Room with ID " + roomId + " does not exist.");
        }
        if (roomMemberRepository.existsByRoomIdAndUsername(roomId, username)) {
            log.warn("Join failed: User {} is already in room {}", username, roomId);
            throw new BadRequestException("User is already in this room.");
        }

        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUsername(username);
        roomMemberRepository.save(member);

        log.info("User {} successfully joined room {}", username, roomId);
    }

    @Transactional
    public void leaveRoom(UUID roomId, String username) {
        log.debug("User {} attempting to leave room {}", username, roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (room.getOwnerUsername().equals(username)) {
            log.debug("Owner {} is leaving room {}. Attempting to transfer ownership.", username, roomId);
            Optional<RoomMember> nextOwner = roomMemberRepository
                    .findFirstByRoomIdAndUsernameNotOrderByJoinedAtAsc(roomId, username);

            if (nextOwner.isPresent()) {
                room.setOwnerUsername(nextOwner.get().getUsername());
                roomRepository.save(room);
                log.info("Ownership of room {} transferred to {}", roomId, nextOwner.get().getUsername());
            } else {
                log.info("Owner left and room {} is empty. Deleting room.", roomId);
                roomRepository.delete(room);
                sendRoomDeletedEvent(roomId);
                return;
            }
        }
        roomMemberRepository.deleteByRoomIdAndUsername(roomId, username);
        log.info("User {} successfully left room {}", username, roomId);
    }

    @Transactional
    public void kickUser(UUID roomId, String adminUsername, String userToKick) {
        log.debug("Admin {} attempting to kick user {} from room {}", adminUsername, userToKick, roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (!room.getOwnerUsername().equals(adminUsername)) {
            log.warn("Kick failed: User {} is not the owner of room {}", adminUsername, roomId);
            throw new UnauthorizedRoomAccessException("Only the room owner can kick users.");
        }

        if (room.getOwnerUsername().equals(userToKick)) {
            log.warn("Kick failed: Admin {} attempted to kick themselves from room {}", adminUsername, roomId);
            throw new BadRequestException("Owner cannot be kicked.");
        }

        boolean exists = roomMemberRepository
                .existsByRoomIdAndUsername(roomId, userToKick);

        if (!exists) {
            log.warn("Kick failed: User {} is not in room {}", userToKick, roomId);
            throw new BadRequestException("User is not in this room.");
        }

        roomMemberRepository.deleteByRoomIdAndUsername(roomId, userToKick);
        log.info("User {} was kicked from room {} by admin {}", userToKick, roomId, adminUsername);
    }

    @Transactional
    public void addUser(UUID roomId, String adminUsername, String userToAdd) {
        log.debug("Admin {} attempting to add user {} to room {}", adminUsername, userToAdd, roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (!room.getOwnerUsername().equals(adminUsername)) {
            log.warn("Add user failed: User {} is not the owner of room {}", adminUsername, roomId);
            throw new UnauthorizedRoomAccessException("Only the room owner can kick users.");
        }

        if (roomMemberRepository.existsByRoomIdAndUsername(roomId, userToAdd)) {
            log.warn("Add user failed: User {} is already in room {}", userToAdd, roomId);
            throw new BadRequestException("User is already in this room.");
        }

        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUsername(userToAdd);
        member.setJoinedAt(LocalDateTime.now());
        roomMemberRepository.save(member);
        log.info("User {} was successfully added to room {} by admin {}", userToAdd, roomId, adminUsername);
    }

    public List<String> getRoomMembers(UUID roomId) {
        log.debug("Fetching members for room {}", roomId);
        return roomMemberRepository.findByRoomId(roomId).stream()
                .map(RoomMember::getUsername)
                .collect(Collectors.toList());
    }

    private RoomResponse mapToResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .ownerUsername(room.getOwnerUsername())
                .createdAt(room.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteRoom(UUID roomId, String adminUsername) {
        log.debug("Admin {} attempting to delete room {}", adminUsername, roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (!room.getOwnerUsername().equals(adminUsername)) {
            log.warn("Delete failed: User {} is not the owner of room {}", adminUsername, roomId);
            throw new UnauthorizedRoomAccessException("Only the room owner can delete this room.");
        }

        roomRepository.delete(room);
        log.info("Room {} was successfully deleted by admin {}", roomId, adminUsername);
        sendRoomDeletedEvent(roomId);
    }

    private void sendRoomDeletedEvent(UUID roomId) {
        log.debug("Publishing room-deleted event to Kafka for room {}", roomId);
        kafkaTemplate.send("room-deleted-topic", roomId.toString())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        var meta = result.getRecordMetadata();
                        System.out.println(
                                "Kafka SENT successfully: topic=" + meta.topic() +
                                        " partition=" + meta.partition() +
                                        " offset=" + meta.offset()
                        );
                    } else {
                        System.err.println("Kafka FAILED: " + ex.getMessage());
                    }
                });
    }
}