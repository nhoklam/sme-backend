package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "home_banners")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class HomeBanner extends BaseEntity {

    @Column(length = 255)
    private String title;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "link_url", columnDefinition = "TEXT")
    private String linkUrl;

    @Column(name = "button_text", length = 100)
    private String buttonText;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "banner_type", length = 50)
    private BannerType bannerType;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    public enum BannerType {
        HERO_SLIDER, PROMOTION_BANNER, CATEGORY_BANNER
    }
}
