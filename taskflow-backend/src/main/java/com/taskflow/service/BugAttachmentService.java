package com.taskflow.service;

import com.taskflow.dto.bug.BugAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BugAttachmentService {
    List<BugAttachmentResponse> list(Long bugId);
    BugAttachmentResponse upload(Long bugId, MultipartFile file);
    org.springframework.core.io.Resource download(Long bugId, Long attachmentId);
    String contentType(Long bugId, Long attachmentId);
    String filename(Long bugId, Long attachmentId);
    void delete(Long bugId, Long attachmentId);
}
