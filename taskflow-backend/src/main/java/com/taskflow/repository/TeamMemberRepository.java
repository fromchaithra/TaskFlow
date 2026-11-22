package com.taskflow.repository;

import com.taskflow.entity.TeamMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);

    @EntityGraph(attributePaths = {"team"})
    List<TeamMember> findByUserId(Long userId);

    void deleteByTeamId(Long teamId);

    long countByTeamId(Long teamId);

    @Query("SELECT tm.team.id, COUNT(tm.id) FROM TeamMember tm WHERE tm.team.id IN :teamIds GROUP BY tm.team.id")
    List<Object[]> countMembersByTeamIds(@Param("teamIds") Collection<Long> teamIds);

    @Query("SELECT tm.user.id FROM TeamMember tm WHERE tm.team.id IN :teamIds")
    List<Long> findAnalyticsUserIdsByTeamIds(@Param("teamIds") Collection<Long> teamIds);

    @Query("SELECT new com.taskflow.dto.analytics.projections.TeamMemberProjection(tm.team.id, tm.user.id) "
            + "FROM TeamMember tm WHERE tm.team.id IN :teamIds")
    List<com.taskflow.dto.analytics.projections.TeamMemberProjection> findAnalyticsTeamMembers(
            @Param("teamIds") Collection<Long> teamIds);
}
