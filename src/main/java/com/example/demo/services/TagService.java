package com.example.demo.services;

import com.example.demo.dto.request.TagCreateRequest;
import com.example.demo.dto.response.TagResponse;
import com.example.demo.entity.Tag;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repositories.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final TagRepository tagRepository;

    public TagResponse createTag(TagCreateRequest request) {
        if (tagRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Тег с таким именем уже существует");
        }

        Tag tag = Tag.builder()
                .name(request.getName())
                .color(request.getColor() != null ? request.getColor() : "#4472C4")
                .build();

        return toResponse(tagRepository.save(tag));
    }

    public List<TagResponse> getAllTags() {
        return tagRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteTag(Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Тег не найден"));
        tagRepository.delete(tag);
    }

    private TagResponse toResponse(Tag tag) {
        TagResponse res = new TagResponse();
        res.setId(tag.getId());
        res.setName(tag.getName());
        res.setColor(tag.getColor());
        return res;
    }
}
