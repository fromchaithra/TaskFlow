package com.taskflow.service;

import com.taskflow.dto.common.PagedResponse;
import com.taskflow.dto.user.CreateUserRequest;
import com.taskflow.dto.user.InviteUserRequest;
import com.taskflow.dto.user.InvitationResponse;
import com.taskflow.dto.user.UpdateUserRequest;
import com.taskflow.dto.user.UpdateUserRoleRequest;
import com.taskflow.dto.user.UpdateUserStatusRequest;
import com.taskflow.dto.user.UserResponse;
import com.taskflow.enums.RoleName;
import org.springframework.data.domain.Pageable;

public interface UserService {

    PagedResponse<UserResponse> listUsers(String search, RoleName role, Boolean active, Pageable pageable);

    UserResponse getUserById(Long userId);

    UserResponse createUser(CreateUserRequest request);

    InvitationResponse inviteUser(InviteUserRequest request);

    UserResponse updateUser(Long userId, UpdateUserRequest request);

    UserResponse updateUserStatus(Long userId, UpdateUserStatusRequest request);

    UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request);
}
