package com.taskflow.service;

import com.taskflow.dto.auth.ActivateAccountRequest;
import com.taskflow.dto.auth.ActivationTokenValidationResponse;
import com.taskflow.dto.auth.AuthResult;
import com.taskflow.dto.auth.AuthUserDto;
import com.taskflow.dto.auth.LoginRequest;

public interface AuthService {

    AuthResult login(LoginRequest request);

    AuthResult refresh(String rawRefreshToken);

    ActivationTokenValidationResponse validateActivationToken(String rawToken);

    AuthUserDto activateAccount(ActivateAccountRequest request);

    void logout(String authorizationHeader, String rawRefreshToken);

    AuthUserDto getCurrentUserProfile();
}
