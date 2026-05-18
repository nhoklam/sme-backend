package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import sme.backend.entity.Author;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AuthorResponse {
    private UUID id;
    private String name;
    private String avatarUrl;
    private String biography;
    private Boolean isFeatured;
    private Instant createdAt;

    public static AuthorResponse from(Author author) {
        return AuthorResponse.builder()
                .id(author.getId())
                .name(author.getName())
                .avatarUrl(author.getAvatarUrl())
                .biography(author.getBiography())
                .isFeatured(author.getIsFeatured())
                .createdAt(author.getCreatedAt())
                .build();
    }
}
