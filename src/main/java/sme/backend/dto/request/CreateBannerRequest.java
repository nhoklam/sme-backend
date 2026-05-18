package sme.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import sme.backend.entity.HomeBanner;

import java.time.LocalDateTime;

@Data
public class CreateBannerRequest {

    private String title;

    @NotBlank
    private String imageUrl;

    private String linkUrl;

    private String buttonText;

    private Integer sortOrder;

    @NotNull
    private HomeBanner.BannerType bannerType;

    private Boolean isActive;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
}
