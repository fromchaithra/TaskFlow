package com.taskflow.controller;

import com.taskflow.dto.setup.FirstAdminRegistrationRequest;
import com.taskflow.dto.setup.SetupStatusResponse;
import com.taskflow.dto.user.UserResponse;
import com.taskflow.service.FirstAdminProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public registration endpoints; each signup creates its own organization and ADMIN. */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final FirstAdminProvisioningService provisioningService;

    public SetupController(FirstAdminProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> status() {
        return ResponseEntity.ok(provisioningService.getSetupStatus());
    }

    @PostMapping("/admin")
    public ResponseEntity<UserResponse> registerFirstAdmin(
            @Valid @RequestBody FirstAdminRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(provisioningService.registerFirstAdmin(request));
    }
}
