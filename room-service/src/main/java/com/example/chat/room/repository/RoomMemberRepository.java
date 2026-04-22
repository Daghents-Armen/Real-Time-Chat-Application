package com.example.chat.room.repository;

import com.example.chat.room.model.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
    boolean existsByRoomIdAndUsername(UUID roomId, String username);
    List<RoomMember> findByRoomId(UUID roomId);
    void deleteByRoomIdAndUsername(UUID roomId, String username);
    Optional<RoomMember> findFirstByRoomIdAndUsernameNotOrderByJoinedAtAsc(UUID roomId, String username);
    void deleteByRoomId(UUID roomId);
}