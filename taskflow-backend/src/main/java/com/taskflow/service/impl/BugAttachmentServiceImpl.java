package com.taskflow.service.impl;

import com.taskflow.config.StorageProperties;
import com.taskflow.dto.bug.BugAttachmentResponse;
import com.taskflow.entity.Bug;
import com.taskflow.entity.BugAttachment;
import com.taskflow.entity.User;
import com.taskflow.repository.BugAttachmentRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.BugAttachmentService;
import com.taskflow.security.ProjectSecurityEvaluator;
import com.taskflow.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BugAttachmentServiceImpl implements BugAttachmentService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf", "text/plain", "text/csv",
            "application/zip", "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

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
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Bug not found: " + bugId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BugAttachmentResponse> list(Long bugId) {
        Bug bug = findBug(bugId);
        var actor = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(bug.getProject().getId(), actor);
        return attachmentRepository.findByBugIdOrderByCreatedAtDesc(bugId).stream()
                .map(a -> new BugAttachmentResponse(
                        a.getId(), a.getOriginalFilename(), a.getContentType(), a.getFileSizeBytes(),
                        a.getUploadedBy().getFullName(), a.getCreatedAt(),
                        "/api/v1/bugs/" + bugId + "/attachments/" + a.getId() + "/download"))
                .toList();
    }

    @Override
    @Transactional
    public BugAttachmentResponse upload(Long bugId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.taskflow.exception.BusinessRuleException("File is required.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new com.taskflow.exception.BusinessRuleException("File exceeds maximum size of " + maxFileSizeBytes + " bytes.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new com.taskflow.exception.BusinessRuleException("File type not allowed: " + contentType);
        }

        Bug bug = findBug(bugId);
        var actor = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(bug.getProject().getId(), actor);

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeOriginal = Paths.get(original).getFileName().toString();
        String stored = UUID.randomUUID() + "_" + safeOriginal.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = storageRoot.resolve("bugs").resolve(String.valueOf(bugId)).normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(stored).normalize();
            if (!target.startsWith(dir)) {
                throw new com.taskflow.exception.BusinessRuleException("Invalid file path.");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new com.taskflow.exception.BusinessRuleException("Failed to store file.");
        }

        User uploader = userRepository.getReferenceById(actor.getId());
        BugAttachment attachment = new BugAttachment(
                bug, uploader, safeOriginal, stored, dir.toString(), contentType, file.getSize()
        );
        attachmentRepository.save(attachment);

        return new BugAttachmentResponse(
                attachment.getId(), attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getFileSizeBytes(), uploader.getFullName(), attachment.getCreatedAt(),
                "/api/v1/bugs/" + bugId + "/attachments/" + attachment.getId() + "/download"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(Long bugId, Long attachmentId) {
        BugAttachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (!a.getBug().getId().equals(bugId)) {
            throw new com.taskflow.exception.ResourceNotFoundException("Attachment not found: " + attachmentId);
        }
        var actor = SecurityUtils.currentUser();
        projectSecurityEvaluator.assertCanViewProject(a.getBug().getProject().getId(), actor);

        Path file = Paths.get(a.getStoragePath()).resolve(a.getStoredFilename()).normalize();
        if (!file.startsWith(storageRoot)) {
            throw new com.taskflow.exception.UnauthorizedResourceAccessException("Invalid storage path.");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists()) {
                throw new com.taskflow.exception.ResourceNotFoundException("File not found on disk.");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new com.taskflow.exception.ResourceNotFoundException("File not found.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String contentType(Long bugId, Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Attachment not found: " + attachmentId))
                .getContentType();
    }

    @Override
    @Transactional(readOnly = true)
    public String filename(Long bugId, Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Attachment not found: " + attachmentId))
                .getOriginalFilename();
    }

    @Override
    @Transactional
    public void delete(Long bugId, Long attachmentId) {
        BugAttachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new com.taskflow.exception.ResourceNotFoundException("Attachment not found: " + attachmentId));
        if (!a.getBug().getId().equals(bugId)) {
            throw new com.taskflow.exception.ResourceNotFoundException("Attachment not found: " + attachmentId);
        }
        var actor = SecurityUtils.currentUser();
        boolean isUploader = a.getUploadedBy().getId().equals(actor.getId());
        boolean isManager = actor.isAdmin() || actor.getPrimaryRole() == com.taskflow.enums.RoleName.PROJECT_MANAGER;
        if (!isUploader && !isManager) {
            throw new com.taskflow.exception.UnauthorizedResourceAccessException("You cannot delete this attachment.");
        }
        Path file = Paths.get(a.getStoragePath()).resolve(a.getStoredFilename()).normalize();
        if (file.startsWith(storageRoot)) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        }
        attachmentRepository.delete(a);
    }
}
