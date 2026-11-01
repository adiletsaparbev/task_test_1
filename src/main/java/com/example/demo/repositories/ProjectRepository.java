package com.example.demo.repositories;

import com.example.demo.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByDepartmentId(Long departmentId);
    List<Project> findByPmId(Long pmId);

    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.user.id = :userId")
    List<Project> findByMemberId(Long userId);
    // В ProjectRepository.java
    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.user.id = :userId")
    List<Project> findByMemberUserId(@Param("userId") Long userId);

}