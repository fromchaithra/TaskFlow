package com.taskflow.service.impl;

import com.taskflow.entity.AuditLog;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.event.LiveChangeEvent;
import com.taskflow.repository.AuditLogRepository;
import com.taskflow.service.AuditLogWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditLogWriterImpl implements AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriterImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AuditLogWriterImpl(AuditLogRepository auditLogRepository, JsonMapper jsonMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.auditLogRepository = auditLogRepository;
        this.jsonMapper = jsonMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(
            Organization organization,
            User actor,
            Project project,
            AuditAction action,
            String entityType,
            Long entityId,
            Object previousValue,
            Object newValue
    ) {
        Map<String, Object> prevSnapshot = toJsonMap(previousValue);
        Map<String, Object> newSnapshot = toJsonMap(newValue);
        String ipAddress = resolveClientIp();

        AuditLog entry = new AuditLog(
                organization,
                actor,
                project,
                action,
                entityType,
                entityId,
                prevSnapshot,
                newSnapshot,
                ipAddress
        );
        auditLogRepository.save(entry);

        // The relay listens AFTER_COMMIT, so a rolled-back mutation never
        // reaches the live-update pipeline. Only opaque identifiers are sent.
        eventPublisher.publishEvent(new LiveChangeEvent(
                entry.getOrganization().getId(),
                project == null ? null : project.getId(),
                entityType,
                entityId,
                action.name(),
                entry.getCreatedAt()
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            mapValue.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            return normalized;
        }
        try {
            return jsonMapper.convertValue(value, Map.class);
        } catch (RuntimeException ex) {
            log.warn("Failed to convert audit snapshot to JSON map: {}", ex.getMessage());
            return Map.of("value", String.valueOf(value));
        }
    }

    private String resolveClientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
