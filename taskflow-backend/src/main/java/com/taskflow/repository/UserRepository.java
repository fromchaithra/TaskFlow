package com.taskflow.repository;

import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByOrganizationIdAndEmployeeCode(Long organizationId, String employeeCode);

    @Query("""
            SELECT COUNT(DISTINCT u.id)
            FROM User u
            JOIN u.roles r
            WHERE u.organization.id = :orgId
              AND u.active = true
              AND r.name = :roleName
            """)
    long countActiveByRole(@Param("orgId") Long organizationId, @Param("roleName") RoleName roleName);

    @Query("""
            SELECT COUNT(DISTINCT u.id)
            FROM User u
            JOIN u.roles r
            WHERE r.name = :roleName
            """)
    long countByRole(@Param("roleName") RoleName roleName);

    @Query("""
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN u.roles r
            WHERE u.organization.id = :orgId
              AND (:active IS NULL OR u.active = :active)
              AND (:roleName IS NULL OR r.name = :roleName)
              AND (:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> searchUsers(
            @Param("orgId") Long organizationId,
            @Param("search") String search,
            @Param("roleName") RoleName roleName,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
