package com.dnd.moyeolak.domain.location.service.impl;

import com.dnd.moyeolak.domain.location.dto.NearbyPlaceSearchResponse;
import com.dnd.moyeolak.domain.location.entity.NearbyPlace;
import com.dnd.moyeolak.domain.location.entity.enums.PlaceCategory;
import com.dnd.moyeolak.domain.location.repository.NearbyPlaceRepository;
import com.dnd.moyeolak.domain.location.service.BusinessHoursCalculator;
import com.dnd.moyeolak.domain.location.service.BusinessHoursCalculator.BusinessStatus;
import com.dnd.moyeolak.domain.location.service.NearbyPlaceSearchService;
import com.dnd.moyeolak.global.client.kakao.KakaoLocalClient;
import com.dnd.moyeolak.global.client.kakao.dto.CategorySearchResponse;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoKeywordSearchRequest;
import com.dnd.moyeolak.global.utils.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyPlaceSearchServiceImpl implements NearbyPlaceSearchService {

    private static final int CACHE_TTL_DAYS = 30;

    private final KakaoLocalClient kakaoLocalClient;
    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final BusinessHoursCalculator businessHoursCalculator;

    @Override
    public NearbyPlaceSearchResponse nearbyPlaceSearch(String latitude, String longitude) {
        BigDecimal baseLat = new BigDecimal(latitude);
        BigDecimal baseLng = new BigDecimal(longitude);
        double latDouble = baseLat.doubleValue();
        double lngDouble = baseLng.doubleValue();

        List<NearbyPlace> cached = nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(baseLat, baseLng);

        if (!cached.isEmpty()) {
            NearbyPlace sample = cached.getFirst();
            LocalDateTime updatedAt = sample.getUpdatedAt();
            if (updatedAt != null && updatedAt.plusDays(CACHE_TTL_DAYS).isAfter(LocalDateTime.now())) {
                return buildResponse(cached);
            }
            nearbyPlaceRepository.deleteByBaseLatitudeAndBaseLongitude(baseLat, baseLng);
        }

        Map<PlaceCategory, List<CategorySearchResponse.Place>> searchResults =
                fetchFromKakao(baseLat.toPlainString(), baseLng.toPlainString());
        List<NearbyPlace> allPlaces = toNearbyPlaces(baseLat, baseLng, latDouble, lngDouble, searchResults);

        nearbyPlaceRepository.saveAll(allPlaces);
        return buildResponse(allPlaces);
    }

    private Map<PlaceCategory, List<CategorySearchResponse.Place>> fetchFromKakao(String latitude, String longitude) {
        Map<PlaceCategory, List<CategorySearchResponse.Place>> result = new LinkedHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<SearchResult>> futures = new ArrayList<>();

            for (PlaceCategory category : PlaceCategory.values()) {
                for (String keyword : category.getSearchKeywords()) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> searchKeyword(category, keyword, latitude, longitude),
                            executor
                    ));
                }
            }

            for (CompletableFuture<SearchResult> future : futures) {
                SearchResult searchResult = future.join();
                result.computeIfAbsent(searchResult.category(), ignored -> new ArrayList<>())
                        .addAll(searchResult.places());
            }
        }

        for (PlaceCategory category : PlaceCategory.values()) {
            result.putIfAbsent(category, List.of());
        }

        return result;
    }

    private SearchResult searchKeyword(PlaceCategory category, String keyword, String latitude, String longitude) {
        KakaoKeywordSearchRequest request = KakaoKeywordSearchRequest.ofNearby(
                keyword,
                longitude,
                latitude
        );

        try {
            CategorySearchResponse response = kakaoLocalClient.searchByKeyword(request);
            List<CategorySearchResponse.Place> places = response != null && response.documents() != null
                    ? response.documents()
                    : List.of();
            return new SearchResult(category, places);
        } catch (Exception e) {
            log.warn("Kakao Local 장소 검색에 실패했습니다. keyword='{}', error={}", keyword, e.getMessage());
            return new SearchResult(category, List.of());
        }
    }

    private List<NearbyPlace> toNearbyPlaces(
            BigDecimal baseLat,
            BigDecimal baseLng,
            double latDouble,
            double lngDouble,
            Map<PlaceCategory, List<CategorySearchResponse.Place>> searchResults
    ) {
        List<NearbyPlace> allPlaces = new ArrayList<>();
        Set<String> seenProviderPlaceIds = new HashSet<>();

        for (Map.Entry<PlaceCategory, List<CategorySearchResponse.Place>> entry : searchResults.entrySet()) {
            PlaceCategory category = entry.getKey();

            for (CategorySearchResponse.Place kakaoPlace : entry.getValue()) {
                if (kakaoPlace.id() == null || !seenProviderPlaceIds.add(kakaoPlace.id())) {
                    continue;
                }

                NearbyPlace nearbyPlace = toNearbyPlace(
                        baseLat,
                        baseLng,
                        latDouble,
                        lngDouble,
                        category,
                        kakaoPlace
                );

                if (nearbyPlace != null) {
                    allPlaces.add(nearbyPlace);
                }
            }
        }

        return allPlaces;
    }

    private NearbyPlace toNearbyPlace(
            BigDecimal baseLat,
            BigDecimal baseLng,
            double latDouble,
            double lngDouble,
            PlaceCategory category,
            CategorySearchResponse.Place kakaoPlace
    ) {
        Double placeLat = parseDouble(kakaoPlace.y());
        Double placeLng = parseDouble(kakaoPlace.x());
        if (placeLat == null || placeLng == null) {
            log.warn("Kakao Local 장소 좌표 파싱 실패. id={}, name={}", kakaoPlace.id(), kakaoPlace.placeName());
            return null;
        }

        return NearbyPlace.of(
                baseLat,
                baseLng,
                category,
                kakaoPlace.id(),
                kakaoPlace.placeName(),
                resolveAddress(kakaoPlace),
                BigDecimal.valueOf(placeLat),
                BigDecimal.valueOf(placeLng),
                kakaoPlace.placeUrl(),
                resolveDistance(kakaoPlace.distance(), latDouble, lngDouble, placeLat, placeLng)
        );
    }

    private String resolveAddress(CategorySearchResponse.Place kakaoPlace) {
        if (hasText(kakaoPlace.roadAddressName())) {
            return kakaoPlace.roadAddressName();
        }
        return kakaoPlace.addressName();
    }

    private Integer resolveDistance(
            String kakaoDistance,
            double baseLat,
            double baseLng,
            double placeLat,
            double placeLng
    ) {
        try {
            if (hasText(kakaoDistance)) {
                return Integer.parseInt(kakaoDistance);
            }
        } catch (NumberFormatException e) {
            log.warn("Kakao Local distance 파싱 실패. distance={}", kakaoDistance);
        }

        return (int) Math.round(GeoUtils.haversine(baseLat, baseLng, placeLat, placeLng));
    }

    private Double parseDouble(String value) {
        try {
            if (hasText(value)) {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private NearbyPlaceSearchResponse buildResponse(List<NearbyPlace> places) {
        Map<PlaceCategory, List<NearbyPlace>> grouped = new LinkedHashMap<>();
        for (PlaceCategory cat : PlaceCategory.values()) {
            grouped.put(cat, new ArrayList<>());
        }
        for (NearbyPlace place : places) {
            grouped.get(place.getCategory()).add(place);
        }

        List<NearbyPlaceSearchResponse.CategoryPlaces> categories = new ArrayList<>();

        for (Map.Entry<PlaceCategory, List<NearbyPlace>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;

            List<NearbyPlaceSearchResponse.PlaceDetail> placeDetails = entry.getValue().stream()
                    .map(place -> {
                        BusinessStatus status = businessHoursCalculator.calculateBusinessStatus(place.getNearbyPlaceHours());
                        Boolean isOpen = status != null ? status.isOpen() : null;
                        String message = status != null ? status.message() : null;
                        return new NearbyPlaceSearchResponse.PlaceDetail(
                                place.getId(),
                                place.getName(),
                                place.getFormattedAddress(),
                                place.getLatitude() != null ? place.getLatitude().doubleValue() : null,
                                place.getLongitude() != null ? place.getLongitude().doubleValue() : null,
                                place.getKakaoPlaceUrl(),
                                place.getDistanceFromBase(),
                                isOpen,
                                message
                        );
                    })
                    .toList();

            categories.add(new NearbyPlaceSearchResponse.CategoryPlaces(
                    entry.getKey().getDisplayName(), placeDetails));
        }

        return new NearbyPlaceSearchResponse(categories);
    }

    private record SearchResult(PlaceCategory category, List<CategorySearchResponse.Place> places) {}
}
