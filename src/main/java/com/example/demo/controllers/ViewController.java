// src/main/java/com/example/demo/controllers/ViewController.java
package com.example.demo.controllers;

import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.enums.Priority;
import com.example.demo.enums.TaskStatus;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.ProjectService;
import com.example.demo.services.TaskService;
import com.example.demo.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller  // НЕ @RestController — возвращает название шаблона
@RequiredArgsConstructor
public class ViewController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // ── Auth ────────────────────────────────────────────

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // → templates/login.html
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    // ── Главная: список проектов ─────────────────────────

//    @GetMapping({"/", "/projects"})
//    public String projectsPage(Model model,
//                               @AuthenticationPrincipal UserDetails userDetails) {
//
//        List<Project> projects = projectService.getAll();
//        var user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
//        long unreadCount = notificationService.countUnread(user.getId());
//
//        model.addAttribute("projects", projects);
//        model.addAttribute("currentUser", user);
//        model.addAttribute("unreadCount", unreadCount);
//
//        return "projects"; // → templates/projects.html
//    }

    // ── Kanban доска ──────────────────────────────────────

//    @GetMapping("/projects/{id}/board")
//    public String kanbanPage(@PathVariable Long id,
//                             Model model,
//                             @AuthenticationPrincipal UserDetails userDetails) {
//
//        Project project = projectService.getById(id);
//        var user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
//        long unreadCount = notificationService.countUnread(user.getId());
//
//        List<Task> newTasks      = taskService.filterByStatus(id, TaskStatus.NEW);
//        List<Task> inProgress    = taskService.filterByStatus(id, TaskStatus.IN_PROGRESS);
//        List<Task> review        = taskService.filterByStatus(id, TaskStatus.REVIEW);
//        List<Task> done          = taskService.filterByStatus(id, TaskStatus.DONE);
//
//        model.addAttribute("project", project);
//        model.addAttribute("newTasks", newTasks);
//        model.addAttribute("inProgress", inProgress);
//        model.addAttribute("review", review);
//        model.addAttribute("done", done);
//        model.addAttribute("currentUser", user);
//        model.addAttribute("unreadCount", unreadCount);
//        model.addAttribute("priorities", Priority.values());
//        model.addAttribute("statuses", TaskStatus.values());
//
//        return "kanban"; // → templates/kanban.html
//    }

    // ── Детальная задача ──────────────────────────────────

//    @GetMapping("/tasks/{id}")
//    public String taskDetailPage(@PathVariable Long id,
//                                 Model model,
//                                 @AuthenticationPrincipal UserDetails userDetails) {
//
//        Task task = taskService.getTaskById(id);
//        var user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
//        long unreadCount = notificationService.countUnread(user.getId());
//
//        model.addAttribute("task", task);
//        model.addAttribute("currentUser", user);
//        model.addAttribute("unreadCount", unreadCount);
//        model.addAttribute("priorities", Priority.values());
//        model.addAttribute("statuses", TaskStatus.values());
//
//        return "task-detail";
//    }
}
