package com.example.chat.room.service;

import com.example.chat.room.dto.RoomRequest;
import com.example.chat.room.dto.RoomResponse;
import com.example.chat.room.exception.BadRequestException;
import com.example.chat.room.exception.RoomNotFoundException;
import com.example.chat.room.exception.UnauthorizedRoomAccessException;
import com.example.chat.room.model.Room;
import com.example.chat.room.model.RoomMember;
import com.example.chat.room.repository.RoomMemberRepository;
import com.example.chat.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public RoomResponse createRoom(RoomRequest request, String ownerUsername) {
        if (roomRepository.existsByName(request.getName())) {
            throw new BadRequestException("A room with this name already exists.");
        }

        Room room = new Room();
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setOwnerUsername(ownerUsername);
        room.setCreatedAt(LocalDateTime.now());

        Room savedRoom = roomRepository.save(room);

        RoomMember member = new RoomMember();
        member.setRoomId(savedRoom.getId());
        member.setUsername(ownerUsername);

        member.setJoinedAt(java.time.LocalDateTime.now());
        roomMemberRepository.save(member);

        return mapToResponse(savedRoom);
    }

    public List<RoomResponse> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinRoom(UUID roomId, String username) {
        if (!roomRepository.existsById(roomId)) {
            throw new RoomNotFoundException("Room with ID " + roomId + " does not exist.");
        }
        if (roomMemberRepository.existsByRoomIdAndUsername(roomId, username)) {
            throw new BadRequestException("User is already in this room.");
        }

        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUsername(username);
        roomMemberRepository.save(member);
    }

    @Transactional
    public void leaveRoom(UUID roomId, String username) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (room.getOwnerUsername().equals(username)) {
            Optional<RoomMember> nextOwner = roomMemberRepository
                    .findFirstByRoomIdAndUsernameNotOrderByJoinedAtAsc(roomId, username);

            if (nextOwner.isPresent()) {
                room.setOwnerUsername(nextOwner.get().getUsername());
                roomRepository.save(room);
            } else {
                roomRepository.delete(room);
                sendRoomDeletedEvent(roomId);
                return;
            }
        }
        roomMemberRepository.deleteByRoomIdAndUsername(roomId, username);
    }

    @Transactional
    public void kickUser(UUID roomId, String adminUsername, String userToKick) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (!room.getOwnerUsername().equals(adminUsername)) {
            throw new UnauthorizedRoomAccessException("Only the room owner can kick users.");
        }

        if (room.getOwnerUsername().equals(userToKick)) {
            throw new BadRequestException("Owner cannot be kicked.");
        }

        boolean exists = roomMemberRepository
                .existsByRoomIdAndUsername(roomId, userToKick);

        if (!exists) {
            throw new BadRequestException("User is not in this room.");
        }

        roomMemberRepository.deleteByRoomIdAndUsername(roomId, userToKick);
    }

    @Transactional
    public void addUser(UUID roomId, String adminUsername, String userToAdd) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));

        if (!room.getOwnerUsername().equals(adminUsername)) {
            throw new UnauthorizedRoomAccessException("Only the room owner can kick users.");
        }

        if (roomMemberRepository.existsByRoomIdAndUsername(roomId, userToAdd)) {
            throw new BadRequestException("User is already in this room.");
        }

        RoomMember member = new RoomMember();
        member.setRoomId(roomId);
        member.setUsername(userToAdd);
        member.setJoinedAt(java.time.LocalDateTime.now());
        roomMemberRepository.save(member);
    }

    public List<String> getRoomMembers(UUID roomId) {
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
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room with ID " + roomId + " does not exist."));
        if (!room.getOwnerUsername().equals(adminUsername)) {
            throw new UnauthorizedRoomAccessException("Only the room owner can delete this room.");
        }

        roomRepository.delete(room);
        sendRoomDeletedEvent(roomId);
    }

    private void sendRoomDeletedEvent(UUID roomId) {
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