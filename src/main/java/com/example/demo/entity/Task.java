package com.example.demo.entity;

import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;


    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.NEW;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;

    private LocalDateTime startDate;
    private LocalDateTime dueDate;
    private LocalDateTime completedAt;
    private boolean isOverdue = false;

    @JsonIgnore
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskAssignee> assignees;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<Subtask> subtasks;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<Comment> comments;

    @ManyToMany
    @JoinTable(
            name = "task_tags_relation",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;



    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Автопроверка просрочки
        if (dueDate != null && dueDate.isBefore(LocalDateTime.now()) && status != TaskStatus.DONE) {
            isOverdue = true;
        }
    }
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // ← добавить:
        if (dueDate != null && dueDate.isBefore(LocalDateTime.now()) && status != TaskStatus.DONE) {
            isOverdue = true;
        }
    }
}
