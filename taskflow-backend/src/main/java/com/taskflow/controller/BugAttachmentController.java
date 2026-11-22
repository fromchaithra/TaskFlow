package com.taskflow.controller;

import com.taskflow.dto.bug.BugAttachmentResponse;
import com.taskflow.service.BugAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bugs/{id}/attachments")
public class BugAttachmentController {

    private final BugAttachmentService bugAttachmentService;

    public BugAttachmentController(BugAttachmentService bugAttachmentService) {
        this.bugAttachmentService = bugAttachmentService;
    }

    @GetMapping
    public ResponseEntity<List<BugAttachmentResponse>> list(@PathVariable Long id) {
        return ResponseEntity.ok(bugAttachmentService.list(id));
    }

    @PostMapping
    public ResponseEntity<BugAttachmentResponse> upload(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(bugAttachmentService.upload(id, file));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        Resource resource = bugAttachmentService.download(id, attachmentId);
        String filename = bugAttachmentService.filename(id, attachmentId);
        String contentType = bugAttachmentService.contentType(id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @DeleteMapping("/{attachmentId}")
    // The service layer decides uploader/manager permissions; DEVELOPER must be allowed
    // through here so the actual uploader can remove their own attachment.
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'QA_TESTER', 'DEVELOPER')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long attachmentId) {
        bugAttachmentService.delete(id, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
