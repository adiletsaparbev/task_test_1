package com.example.demo.repositories;

import com.example.demo.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    // Поиск по названию и/или тегам
    @Query("""
                SELECT DISTINCT t FROM Task t
                LEFT JOIN t.tags tag
                WHERE (:title IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :title, '%')))
                  AND (:tagIds IS NULL OR tag.id IN :tagIds)
            """)
    List<Task> searchTasks(
            @Param("title") String title,
            @Param("tagIds") List<Long> tagIds);

    // Задачи по исполнителю
    @Query("SELECT DISTINCT t FROM Task t JOIN t.assignees a WHERE a.user.id = :userId")
    List<Task> findByAssigneeUserId(@Param("userId") Long userId);

    // Все задачи по отделу (через проект → department)
    @Query("""
                SELECT t FROM Task t
                WHERE t.project.department.id = :departmentId
            """)
    List<Task> findByDepartmentId(@Param("departmentId") Long departmentId);

    // Просроченные задачи для шедулера
    @Query("""
                SELECT t FROM Task t
                WHERE t.dueDate < :today
                  AND t.status <> com.example.demo.enums.TaskStatus.DONE
            """)
    List<Task> findOverdueTasks(@Param("today") LocalDate today);


    // TaskRepository.java — добавьте этот метод
    @Query("""
    SELECT DISTINCT t FROM Task t
    LEFT JOIN FETCH t.project p
    LEFT JOIN FETCH p.department d
    LEFT JOIN FETCH d.manager
    LEFT JOIN FETCH p.pm
    LEFT JOIN FETCH t.creator
    LEFT JOIN FETCH t.assignees a
    LEFT JOIN FETCH a.user
    LEFT JOIN FETCH t.tags
    """)
    List<Task> findAllWithRelations();
}