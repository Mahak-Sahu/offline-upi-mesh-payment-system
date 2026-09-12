package com.demo.upimesh.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findTop20BySessionIdOrderByIdDesc(String sessionId);

    boolean existsByPacketHash(String packetHash);

    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.sessionId = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);
}