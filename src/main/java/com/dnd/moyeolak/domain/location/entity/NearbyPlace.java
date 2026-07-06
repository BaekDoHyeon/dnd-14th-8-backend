package com.dnd.moyeolak.domain.location.entity;

import com.dnd.moyeolak.domain.location.entity.enums.PlaceCategory;
import com.dnd.moyeolak.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        indexes = @Index(name = "idx_base_coords", columnList = "baseLatitude, baseLongitude"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_base_place",
                columnNames = {"baseLatitude", "baseLongitude", "providerPlaceId"}
        )
)
public class NearbyPlace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nearby_place_id")
    private Long id;

    @Column(comment = "검색 기준점 위도 (지하철역 좌표)", nullable = false, precision = 10, scale = 7)
    private BigDecimal baseLatitude;

    @Column(comment = "검색 기준점 경도 (지하철역 좌표)", nullable = false, precision = 10, scale = 7)
    private BigDecimal baseLongitude;

    @Column(comment = "장소 카테고리", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlaceCategory category;

    @Column(comment = "외부 장소 제공자 ID", nullable = false, length = 300)
    private String providerPlaceId;

    @Column(comment = "장소명", nullable = false)
    private String name;

    @Column(comment = "주소", length = 500)
    private String formattedAddress;

    @Column(comment = "장소 위도", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(comment = "장소 경도", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(comment = "카카오맵 URL", length = 500)
    private String kakaoPlaceUrl;

    @Column(comment = "기준점으로부터의 거리 (미터)")
    private Integer distanceFromBase;

    @Builder.Default
    @OneToMany(mappedBy = "nearbyPlace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NearbyPlaceHours> nearbyPlaceHours = new ArrayList<>();

    public void addHours(NearbyPlaceHours hours) {
        this.nearbyPlaceHours.add(hours);
    }

    public static NearbyPlace of(
            BigDecimal baseLatitude,
            BigDecimal baseLongitude,
            PlaceCategory category,
            String providerPlaceId,
            String name,
            String formattedAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String kakaoPlaceUrl,
            Integer distanceFromBase
    ) {
        return NearbyPlace.builder()
                .baseLatitude(baseLatitude)
                .baseLongitude(baseLongitude)
                .category(category)
                .providerPlaceId(providerPlaceId)
                .name(name)
                .formattedAddress(formattedAddress)
                .latitude(latitude)
                .longitude(longitude)
                .kakaoPlaceUrl(kakaoPlaceUrl)
                .distanceFromBase(distanceFromBase)
                .build();
    }
}
