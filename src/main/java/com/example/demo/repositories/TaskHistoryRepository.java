package com.example.demo.repositories;

import com.example.demo.entity.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

    // История изменений конкретной задачи, сначала старые
    List<TaskHistory> findByTaskIdOrderByChangedAtAsc(Long taskId);
}