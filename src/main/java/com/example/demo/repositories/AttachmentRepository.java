package com.example.demo.repositories;

import com.example.demo.entity.Attachment;
import com.example.demo.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByEntityTypeAndEntityId(EntityType entityType, Long entityId);
    void deleteByEntityTypeAndEntityId(EntityType entityType, Long entityId);

    // Метод findAllById(Long id) мы удаляем,
    // так как JpaRepository уже предоставляет findAllById(Iterable<Long> ids)
}