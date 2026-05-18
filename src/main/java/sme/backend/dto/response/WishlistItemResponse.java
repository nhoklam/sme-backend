package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WishlistItemResponse {
    private UUID wishlistId;
    private ProductResponse product;
    private Instant addedAt;
}
