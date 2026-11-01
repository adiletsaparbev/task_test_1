package com.example.demo.repositories;
// repositories/TaskAssigneeRepository.java

import com.example.demo.entity.TaskAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, Long> {
    List<TaskAssignee> findByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);

    @Query("""
            SELECT ta
            FROM TaskAssignee ta
            JOIN FETCH ta.user u
            JOIN FETCH ta.task t
            JOIN FETCH t.project p
            WHERE u.id IN :userIds
            """)
    List<TaskAssignee> findByUserIdsWithTaskAndProject(@Param("userIds") List<Long> userIds);

    @Query("""
            SELECT ta
            FROM TaskAssignee ta
            JOIN FETCH ta.user u
            JOIN FETCH ta.task t
            JOIN FETCH t.project p
            WHERE u.id = :userId
            """)
    List<TaskAssignee> findByUserIdWithTaskAndProject(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT u.id
            FROM TaskAssignee ta
            JOIN ta.user u
            JOIN ta.task t
            JOIN t.project p
            WHERE p.pm.id = :pmId
              AND u.isActive = true
            """)
    List<Long> findDistinctActiveUserIdsByPmId(@Param("pmId") Long pmId);
}
