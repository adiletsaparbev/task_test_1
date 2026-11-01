package com.example.demo.repositories;

import com.example.demo.entity.Subtask;
import com.example.demo.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubtaskRepository extends JpaRepository<Subtask, Long> {
    List<Subtask> findByTaskId(Long taskId);
    long countByTaskId(Long taskId);
    long countByTaskIdAndStatus(Long taskId, TaskStatus status);
}