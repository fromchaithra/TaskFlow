package com.taskflow.controller;

import com.taskflow.dto.organization.OrganizationResponse;
import com.taskflow.dto.organization.UpdateOrganizationRequest;
import com.taskflow.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<OrganizationResponse> getOrganization() {
        return ResponseEntity.ok(organizationService.getCurrentOrganization());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @Valid @RequestBody UpdateOrganizationRequest request
    ) {
        return ResponseEntity.ok(organizationService.updateCurrentOrganization(request));
    }
}
