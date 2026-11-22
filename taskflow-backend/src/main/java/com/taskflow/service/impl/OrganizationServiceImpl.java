package com.taskflow.service.impl;

import com.taskflow.dto.organization.OrganizationResponse;
import com.taskflow.dto.organization.UpdateOrganizationRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.OrganizationService;
import com.taskflow.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditLogWriter auditLogWriter;

    public OrganizationServiceImpl(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            AuditLogWriter auditLogWriter
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getCurrentOrganization() {
        CustomUserDetails principal = SecurityUtils.currentUser();
        Organization org = organizationRepository.findById(principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        return OrganizationResponse.from(org);
    }

    @Override
    @Transactional
    public OrganizationResponse updateCurrentOrganization(UpdateOrganizationRequest request) {
        validateTimezone(request.timezone());
        String workingDays = validateWorkingDays(request.workingDays());

        CustomUserDetails principal = SecurityUtils.currentUser();
        Organization org = organizationRepository.findById(principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        User actor = userRepository.getReferenceById(principal.getId());

        Map<String, Object> previous = Map.of(
                "name", org.getName(),
                "industry", org.getIndustry() != null ? org.getIndustry() : "",
                "timezone", org.getTimezone(),
                "workingDays", org.getWorkingDays()
        );

        org.setName(request.name().trim());
        org.setIndustry(request.industry() != null ? request.industry().trim() : null);
        org.setTimezone(request.timezone().trim());
        org.setWorkingDays(workingDays);

        Organization saved = organizationRepository.save(org);

        Map<String, Object> updated = Map.of(
                "name", saved.getName(),
                "industry", saved.getIndustry() != null ? saved.getIndustry() : "",
                "timezone", saved.getTimezone(),
                "workingDays", saved.getWorkingDays()
        );
        auditLogWriter.record(saved, actor, null, AuditAction.ORGANIZATION_UPDATED, "ORGANIZATION", saved.getId(), previous, updated);

        return OrganizationResponse.from(saved);
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            throw new BusinessRuleException("Invalid IANA timezone: " + timezone);
        }
    }

    /** Accepts 1-7 distinct weekday tokens in the existing MON,SAT-style wire format. */
    private String validateWorkingDays(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("Working days must include at least one weekday.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        Set<String> seen = new LinkedHashSet<>();
        for (String token : normalized.split(",", -1)) {
            String day = token.trim();
            if (!allowed.contains(day)) {
                throw new BusinessRuleException("Invalid working day: " + day);
            }
            if (!seen.add(day)) {
                throw new BusinessRuleException("Duplicate working day: " + day);
            }
        }
        if (seen.isEmpty()) {
            throw new BusinessRuleException("Working days must include at least one weekday.");
        }
        return String.join(",", seen);
    }
}
