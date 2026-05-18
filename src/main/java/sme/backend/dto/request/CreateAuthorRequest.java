package sme.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAuthorRequest {
    @NotBlank
    @Size(max = 200)
    private String name;

    private String avatarUrl;
    private String biography;
    private Boolean isFeatured;
}
