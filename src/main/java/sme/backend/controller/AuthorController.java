package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.CreateAuthorRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.AuthorResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.service.AuthorService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuthorResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name"));
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(authorService.getAllAuthors(keyword, pageable))));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<AuthorResponse>>> getFeatured() {
        return ResponseEntity.ok(ApiResponse.ok(authorService.getFeaturedAuthors()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthorResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(authorService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<AuthorResponse>> create(@Valid @RequestBody CreateAuthorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(authorService.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<AuthorResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody CreateAuthorRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authorService.update(id, req)));
    }

    @PatchMapping("/{id}/toggle-featured")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<AuthorResponse>> toggleFeatured(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(authorService.toggleFeatured(id)));
    }
}
