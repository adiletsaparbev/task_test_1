package com.example.demo.repositories;

import com.example.demo.entity.TelegramLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkToken, Long> {

    Optional<TelegramLinkToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM TelegramLinkToken t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TelegramLinkToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
