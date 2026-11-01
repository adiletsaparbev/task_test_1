package com.example.demo.repositories;

import com.example.demo.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByManagerId(Long managerId);

    Optional<Department> findFirstByManagerId(Long managerId);

    List<Department> findAllByManagerId(Long managerId);

    // Получить id проектов, где PM — указанный пользователь
    @Query("""
                SELECT p.id
                FROM Project p
                WHERE p.pm.id = :pmId
            """)
    List<Long> findProjectIdsByPmId(@Param("pmId") Long pmId);
}