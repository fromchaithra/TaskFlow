package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_jti", nullable = false, unique = true, length = 64)
    private String tokenJti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt = Instant.now();

    protected RevokedToken() {
    }

    public RevokedToken(String tokenJti, User user, Instant expiresAt) {
        this.tokenJti = tokenJti;
        this.user = user;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTokenJti() {
        return tokenJti;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
