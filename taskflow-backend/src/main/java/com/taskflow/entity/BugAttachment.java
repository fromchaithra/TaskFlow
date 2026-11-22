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
@Table(name = "bug_attachments")
public class BugAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bug_id", nullable = false)
    private Bug bug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true, length = 255)
    private String storedFilename;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected BugAttachment() {
    }

    public BugAttachment(
            Bug bug,
            User uploadedBy,
            String originalFilename,
            String storedFilename,
            String storagePath,
            String contentType,
            Long fileSizeBytes
    ) {
        this.bug = bug;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Bug getBug() {
        return bug;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
