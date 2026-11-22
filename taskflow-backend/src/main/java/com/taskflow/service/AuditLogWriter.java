package com.taskflow.service;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;

public interface AuditLogWriter {

    void record(
            Organization organization,
            User actor,
            Project project,
            AuditAction action,
            String entityType,
            Long entityId,
            Object previousValue,
            Object newValue
    );
}
