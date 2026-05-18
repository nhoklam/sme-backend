package sme.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreateAuthorRequest;
import sme.backend.dto.response.AuthorResponse;
import sme.backend.entity.Author;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.AuthorRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;

    @Transactional(readOnly = true)
    public Page<AuthorResponse> getAllAuthors(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return authorRepository.findByNameContainingIgnoreCaseOrderByName(keyword, pageable)
                    .map(AuthorResponse::from);
        }
        return authorRepository.findAll(pageable).map(AuthorResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AuthorResponse> getFeaturedAuthors() {
        return authorRepository.findByIsFeaturedTrueOrderByName()
                .stream()
                .map(AuthorResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AuthorResponse getById(UUID id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
        return AuthorResponse.from(author);
    }

    @Transactional
    public AuthorResponse create(CreateAuthorRequest req) {
        Author author = Author.builder()
                .name(req.getName())
                .avatarUrl(req.getAvatarUrl())
                .biography(req.getBiography())
                .isFeatured(req.getIsFeatured() != null ? req.getIsFeatured() : false)
                .build();
        Author saved = authorRepository.save(author);
        return AuthorResponse.from(saved);
    }

    @Transactional
    public AuthorResponse update(UUID id, CreateAuthorRequest req) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
        if (req.getName() != null) author.setName(req.getName());
        if (req.getAvatarUrl() != null) author.setAvatarUrl(req.getAvatarUrl());
        if (req.getBiography() != null) author.setBiography(req.getBiography());
        if (req.getIsFeatured() != null) author.setIsFeatured(req.getIsFeatured());
        Author saved = authorRepository.save(author);
        return AuthorResponse.from(saved);
    }

    @Transactional
    public AuthorResponse toggleFeatured(UUID id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author", id));
        author.setIsFeatured(!author.getIsFeatured());
        Author saved = authorRepository.save(author);
        return AuthorResponse.from(saved);
    }
}
