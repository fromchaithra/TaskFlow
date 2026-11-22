package com.taskflow.repository;

import com.taskflow.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    @Query("SELECT at FROM ActivationToken at JOIN FETCH at.user WHERE at.tokenHash = :tokenHash")
    Optional<ActivationToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE ActivationToken at SET at.used = true WHERE at.user.id = :userId AND at.used = false")
    int invalidatePendingForUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ActivationToken at WHERE at.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
