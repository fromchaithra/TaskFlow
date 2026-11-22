package com.taskflow.service;

import com.taskflow.dto.bug.AssignBugRequest;
import com.taskflow.dto.bug.BugDetailResponse;
import com.taskflow.dto.bug.BugSummaryResponse;
import com.taskflow.dto.bug.CreateBugRequest;
import com.taskflow.dto.bug.ReopenBugRequest;
import com.taskflow.dto.bug.UpdateBugRequest;
import com.taskflow.dto.bug.UpdateBugSeverityRequest;
import com.taskflow.dto.bug.UpdateBugStatusRequest;
import com.taskflow.dto.bug.VerifyBugRequest;
import com.taskflow.dto.common.PagedResponse;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.BugStatus;
import org.springframework.data.domain.Pageable;

public interface BugService {

    BugDetailResponse createBug(CreateBugRequest request);

    PagedResponse<BugSummaryResponse> listBugs(
            Long projectId,
            Long assigneeId,
            BugSeverity severity,
            BugStatus status,
            String search,
            Pageable pageable
    );

    BugDetailResponse getBugById(Long bugId);

    BugDetailResponse updateBug(Long bugId, UpdateBugRequest request);

    BugDetailResponse updateBugStatus(Long bugId, UpdateBugStatusRequest request);

    BugDetailResponse assignBug(Long bugId, AssignBugRequest request);

    BugDetailResponse updateBugSeverity(Long bugId, UpdateBugSeverityRequest request);

    BugDetailResponse verifyBug(Long bugId, VerifyBugRequest request);

    BugDetailResponse reopenBug(Long bugId, ReopenBugRequest request);

    void deleteBug(Long bugId);

    java.util.List<com.taskflow.dto.bug.BugCommentResponse> listComments(Long bugId);

    com.taskflow.dto.bug.BugCommentResponse addComment(Long bugId, String content);

    com.taskflow.dto.bug.BugCommentResponse editComment(Long bugId, Long commentId, String content);

    void deleteComment(Long bugId, Long commentId);
}
