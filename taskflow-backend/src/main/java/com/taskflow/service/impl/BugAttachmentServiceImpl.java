package com.taskflow.service.impl;

import com.taskflow.config.StorageProperties;
import com.taskflow.dto.bug.BugAttachmentResponse;
import com.taskflow.entity.Bug;
import com.taskflow.entity.BugAttachment;
import com.taskflow.entity.User;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.BugAttachmentRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.service.BugAttachmentService;
import com.taskflow.util.AttachmentSupport;
import com.taskflow.util.SecurityUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class BugAttachmentServiceImpl implements BugAttachmentService {

    private final BugRepository bugRepository;
    private final BugAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityEvaluator projectSecurityEvaluator;
    private final long maxFileSizeBytes;
    private final Path storageRoot;

    public BugAttachmentServiceImpl(
            BugRepository bugRepository,
            BugAttachmentRepository attachmentRepository,
            UserRepository userRepository,
            ProjectSecurityEvaluator projectSecurityEvaluator,
            StorageProperties storageProperties
    ) {
        this.bugRepository = bugRepository;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.projectSecurityEvaluator = projectSecurityEvaluator;
        this.maxFileSizeBytes = storageProperties.maxFileSizeBytes();
        this.storageRoot = Paths.get(storageProperties.localDir()).toAbsolutePath().normalize();
    }

    private Bug findBug(Long bugId) {
        return bugRepository.findById(bugId)
                .orElseThrow(() -> new ResourceNotFoundException("Bug not found: " + bugId));
    }

    /** Every attachment operation must pass the same project/organization visibility check. */
    private BugAttachment findAttachment(Long bugId, Long attachmentId) {
        BugAttachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (!a.getBug().getId().equals(bugId)) {
            throw new ResourceNotFoundException("Attachment not found: " + attachmentId);
        }
        var actor = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(a.getBug().getProject().getId(), actor);
        return a;
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover file is preferable to reporting a successful deletion that failed.
        }
    }

    /** If the transaction rolls back after the file was written, remove the orphan. */
    private void cleanupFileOnRollback(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(file);
            }
        });
    }

    /** Commit the metadata deletion first; only then remove the stored file. */
    private void deleteFileAfterCommit(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(file);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(file);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<BugAttachmentResponse> list(Long bugId) {
        Bug bug = findBug(bugId);
        var actor = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(bug.getProject().getId(), actor);
        return attachmentRepository.findByBugIdOrderByCreatedAtDesc(bugId).stream()
                .map(a -> new BugAttachmentResponse(
                        a.getId(), AttachmentSupport.sanitizeFilename(a.getOriginalFilename()),
                        a.getContentType(), a.getFileSizeBytes(),
                        a.getUploadedBy().getFullName(), a.getCreatedAt(),
                        "/api/v1/bugs/" + bugId + "/attachments/" + a.getId() + "/download"))
                .toList();
    }

    @Override
    @Transactional
    public BugAttachmentResponse upload(Long bugId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("File is required.");
        }

        Bug bug = findBug(bugId);
        var actor = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(bug.getProject().getId(), actor);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("Failed to read file.");
        }
        if (bytes.length > maxFileSizeBytes) {
            throw new BusinessRuleException("File exceeds maximum size of " + maxFileSizeBytes + " bytes.");
        }
        String contentType = AttachmentSupport.resolveContentType(bytes, file.getContentType());

        String safeOriginal = AttachmentSupport.sanitizeFilename(file.getOriginalFilename());
        String stored = UUID.randomUUID() + "_" + safeOriginal.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = storageRoot.resolve("bugs").resolve(String.valueOf(bugId)).normalize();
        Path target = dir.resolve(stored).normalize();
        if (!target.startsWith(dir)) {
            throw new BusinessRuleException("Invalid file path.");
        }
        try {
            Files.createDirectories(dir);
            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            deleteQuietly(target);
            throw new BusinessRuleException("Failed to store file.");
        }

        User uploader = userRepository.getReferenceById(actor.getId());
        BugAttachment attachment = new BugAttachment(
                bug, uploader, safeOriginal, stored, dir.toString(), contentType, (long) bytes.length
        );
        try {
            attachmentRepository.save(attachment);
        } catch (RuntimeException ex) {
            deleteQuietly(target);
            throw ex;
        }
        cleanupFileOnRollback(target);

        return new BugAttachmentResponse(
                attachment.getId(), attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getFileSizeBytes(), uploader.getFullName(), attachment.getCreatedAt(),
                "/api/v1/bugs/" + bugId + "/attachments/" + attachment.getId() + "/download"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(Long bugId, Long attachmentId) {
        BugAttachment a = findAttachment(bugId, attachmentId);
        Path file = Paths.get(a.getStoragePath()).resolve(a.getStoredFilename()).normalize();
        if (!file.startsWith(storageRoot)) {
            throw new UnauthorizedResourceAccessException("Invalid storage path.");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("File not found on disk.");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("File not found.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String contentType(Long bugId, Long attachmentId) {
        BugAttachment a = findAttachment(bugId, attachmentId);
        return a.getContentType();
    }

    @Override
    @Transactional(readOnly = true)
    public String filename(Long bugId, Long attachmentId) {
        BugAttachment a = findAttachment(bugId, attachmentId);
        return AttachmentSupport.sanitizeFilename(a.getOriginalFilename());
    }

    @Override
    @Transactional
    public void delete(Long bugId, Long attachmentId) {
        BugAttachment a = findAttachment(bugId, attachmentId);
        var actor = SecurityUtils.currentUser();
        boolean isUploader = a.getUploadedBy().getId().equals(actor.getId());
        boolean isManager = actor.isAdmin() || actor.getPrimaryRole() == com.taskflow.enums.RoleName.PROJECT_MANAGER;
        if (!isUploader && !isManager) {
            throw new UnauthorizedResourceAccessException("You cannot delete this attachment.");
        }
        Path file = Paths.get(a.getStoragePath()).resolve(a.getStoredFilename()).normalize();
        boolean safePath = file.startsWith(storageRoot);

        // Database deletion is the source of truth; remove the file only after commit.
        attachmentRepository.delete(a);
        if (safePath) {
            deleteFileAfterCommit(file);
        }
    }
}
