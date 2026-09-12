package com.demo.upimesh.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllBySessionId(String sessionId);

    Optional<Account> findBySessionIdAndVpa(String sessionId, String vpa);

    boolean existsBySessionIdAndVpa(String sessionId, String vpa);
}