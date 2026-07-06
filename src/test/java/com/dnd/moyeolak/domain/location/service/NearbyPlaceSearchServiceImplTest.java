package com.dnd.moyeolak.domain.location.service;

import com.dnd.moyeolak.domain.location.dto.NearbyPlaceSearchResponse;
import com.dnd.moyeolak.domain.location.entity.NearbyPlace;
import com.dnd.moyeolak.domain.location.entity.enums.PlaceCategory;
import com.dnd.moyeolak.domain.location.repository.NearbyPlaceRepository;
import com.dnd.moyeolak.domain.location.service.BusinessHoursCalculator.BusinessStatus;
import com.dnd.moyeolak.domain.location.service.impl.NearbyPlaceSearchServiceImpl;
import com.dnd.moyeolak.global.client.kakao.KakaoLocalClient;
import com.dnd.moyeolak.global.client.kakao.dto.CategorySearchResponse;
import com.dnd.moyeolak.global.client.kakao.dto.KakaoKeywordSearchRequest;
import com.dnd.moyeolak.global.entity.BaseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NearbyPlaceSearchServiceImplTest {

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @Mock
    private NearbyPlaceRepository nearbyPlaceRepository;

    @Mock
    private BusinessHoursCalculator businessHoursCalculator;

    @InjectMocks
    private NearbyPlaceSearchServiceImpl nearbyPlaceSearchService;

    private static final String BASE_LAT = "37.5000000";
    private static final String BASE_LNG = "127.0000000";
    private static final BigDecimal BASE_LAT_BD = new BigDecimal(BASE_LAT);
    private static final BigDecimal BASE_LNG_BD = new BigDecimal(BASE_LNG);

    private void setUpdatedAt(NearbyPlace place, LocalDateTime time) throws Exception {
        Field field = BaseEntity.class.getDeclaredField("updatedAt");
        field.setAccessible(true);
        field.set(place, time);
    }

    private NearbyPlace createCachedPlace(PlaceCategory category, String providerPlaceId, String name) {
        return NearbyPlace.of(
                BASE_LAT_BD,
                BASE_LNG_BD,
                category,
                providerPlaceId,
                name,
                "서울 강남구 테헤란로 1",
                new BigDecimal("37.5001"),
                new BigDecimal("127.0001"),
                "https://place.map.kakao.com/12345",
                500
        );
    }

    private CategorySearchResponse kakaoResponse(CategorySearchResponse.Place... places) {
        return new CategorySearchResponse(
                new CategorySearchResponse.Meta(places.length, places.length, true, null),
                List.of(places)
        );
    }

    private CategorySearchResponse.Place kakaoPlace(
            String id,
            String name,
            String roadAddress,
            String address,
            String x,
            String y,
            String distance
    ) {
        return new CategorySearchResponse.Place(
                id,
                name,
                "음식점 > 카페",
                "CE7",
                "카페",
                "02-1234-5678",
                address,
                roadAddress,
                x,
                y,
                "https://place.map.kakao.com/" + id,
                distance
        );
    }

    @Nested
    @DisplayName("캐시")
    class CacheTest {

        @Test
        @DisplayName("신선한 캐시 데이터가 있으면 Kakao API 호출 없이 응답을 반환한다")
        void freshCache_returnsWithoutKakaoCall() throws Exception {
            NearbyPlace cached = createCachedPlace(PlaceCategory.CAFE, "kakao-1", "테스트카페");
            setUpdatedAt(cached, LocalDateTime.now().minusDays(10));

            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of(cached));
            when(businessHoursCalculator.calculateBusinessStatus(any()))
                    .thenReturn(new BusinessStatus(null, null));

            NearbyPlaceSearchResponse response = nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            assertThat(response.categories()).hasSize(1);
            assertThat(response.categories().getFirst().category()).isEqualTo("카페");
            assertThat(response.categories().getFirst().places()).hasSize(1);
            assertThat(response.categories().getFirst().places().getFirst().name()).isEqualTo("테스트카페");

            verify(kakaoLocalClient, never()).searchByKeyword(any());
            verify(nearbyPlaceRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("만료된 캐시 데이터가 있으면 삭제 후 Kakao API를 호출한다")
        void expiredCache_deletesAndCallsKakao() throws Exception {
            NearbyPlace cached = createCachedPlace(PlaceCategory.CAFE, "kakao-1", "테스트카페");
            setUpdatedAt(cached, LocalDateTime.now().minusDays(31));

            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of(cached));
            when(kakaoLocalClient.searchByKeyword(any())).thenReturn(kakaoResponse());

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            verify(nearbyPlaceRepository).deleteByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD);
            verify(kakaoLocalClient, atLeastOnce()).searchByKeyword(any());
            verify(nearbyPlaceRepository).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Kakao 검색")
    class KakaoSearchTest {

        @Test
        @DisplayName("캐시가 비어있으면 Kakao 결과를 저장하고 응답한다")
        void emptyCache_savesKakaoPlaces() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "kakao-1",
                            "테스트카페",
                            "서울 강남구 테헤란로 1",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            "250"
                    )));
            when(businessHoursCalculator.calculateBusinessStatus(any()))
                    .thenReturn(new BusinessStatus(null, null));

            NearbyPlaceSearchResponse response = nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            verify(kakaoLocalClient, atLeastOnce()).searchByKeyword(any());
            verify(nearbyPlaceRepository).saveAll(any());
            assertThat(response.categories()).isNotEmpty();
            assertThat(response.categories().getFirst().places().getFirst().name()).isEqualTo("테스트카페");
            assertThat(response.categories().getFirst().places().getFirst().isOpen()).isNull();
            assertThat(response.categories().getFirst().places().getFirst().businessStatusMessage()).isNull();
        }

        @Test
        @DisplayName("Kakao 호출은 기준 좌표, 반경 1000m, 거리순, size 15로 요청한다")
        void kakaoRequest_usesNearbySearchParameters() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any())).thenReturn(kakaoResponse());

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            ArgumentCaptor<KakaoKeywordSearchRequest> captor =
                    ArgumentCaptor.forClass(KakaoKeywordSearchRequest.class);
            verify(kakaoLocalClient, atLeastOnce()).searchByKeyword(captor.capture());

            KakaoKeywordSearchRequest request = captor.getValue();
            assertThat(request.x()).isEqualTo(BASE_LNG);
            assertThat(request.y()).isEqualTo(BASE_LAT);
            assertThat(request.radius()).isEqualTo(1000);
            assertThat(request.sort()).isEqualTo("distance");
            assertThat(request.size()).isEqualTo(15);
        }

        @Test
        @DisplayName("Kakao documents가 비어있으면 빈 응답을 반환한다")
        void kakaoReturnsEmptyDocuments_returnsEmptyResponse() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any())).thenReturn(kakaoResponse());

            NearbyPlaceSearchResponse response = nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            assertThat(response.categories()).isEmpty();
        }

        @Test
        @DisplayName("다른 카테고리에서 같은 providerPlaceId가 등장하면 두 번째는 스킵한다")
        void duplicateProviderPlaceId_skipsSecond() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "same-kakao-id",
                            "중복카페",
                            "서울 강남구 테헤란로 1",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            "100"
                    )));
            when(businessHoursCalculator.calculateBusinessStatus(any()))
                    .thenReturn(new BusinessStatus(null, null));

            NearbyPlaceSearchResponse response = nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            long totalPlaces = response.categories().stream()
                    .mapToLong(c -> c.places().size())
                    .sum();
            assertThat(totalPlaces).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Kakao 응답 매핑")
    class KakaoMappingTest {

        @Test
        @DisplayName("도로명 주소가 있으면 formattedAddress에 도로명 주소를 저장한다")
        void roadAddressExists_usesRoadAddress() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "kakao-1",
                            "도로명카페",
                            "서울 강남구 테헤란로 1",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            "100"
                    )));

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            List<NearbyPlace> saved = captureSavedPlaces();
            assertThat(saved.getFirst().getFormattedAddress()).isEqualTo("서울 강남구 테헤란로 1");
        }

        @Test
        @DisplayName("도로명 주소가 비어있으면 formattedAddress에 지번 주소를 저장한다")
        void roadAddressBlank_usesAddressName() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "kakao-1",
                            "지번카페",
                            "",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            "100"
                    )));

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            List<NearbyPlace> saved = captureSavedPlaces();
            assertThat(saved.getFirst().getFormattedAddress()).isEqualTo("서울 강남구 역삼동 1");
        }

        @Test
        @DisplayName("Kakao distance가 숫자면 distanceFromBase에 그대로 저장한다")
        void distanceIsNumeric_usesKakaoDistance() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "kakao-1",
                            "거리카페",
                            "서울 강남구 테헤란로 1",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            "321"
                    )));

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            List<NearbyPlace> saved = captureSavedPlaces();
            assertThat(saved.getFirst().getDistanceFromBase()).isEqualTo(321);
        }

        @Test
        @DisplayName("Kakao distance가 비어있으면 Haversine 거리로 대체한다")
        void distanceBlank_usesHaversineFallback() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "kakao-1",
                            "거리Fallback카페",
                            "서울 강남구 테헤란로 1",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            ""
                    )));

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            List<NearbyPlace> saved = captureSavedPlaces();
            assertThat(saved.getFirst().getDistanceFromBase()).isPositive();
        }

        @Test
        @DisplayName("신규 Kakao 결과는 영업시간 엔티티를 생성하지 않는다")
        void kakaoPlaces_doNotCreateBusinessHours() {
            when(nearbyPlaceRepository.findByBaseLatitudeAndBaseLongitude(BASE_LAT_BD, BASE_LNG_BD))
                    .thenReturn(List.of());
            when(kakaoLocalClient.searchByKeyword(any()))
                    .thenReturn(kakaoResponse(kakaoPlace(
                            "kakao-1",
                            "영업시간없는카페",
                            "서울 강남구 테헤란로 1",
                            "서울 강남구 역삼동 1",
                            "127.0001",
                            "37.5001",
                            "100"
                    )));

            nearbyPlaceSearchService.nearbyPlaceSearch(BASE_LAT, BASE_LNG);

            List<NearbyPlace> saved = captureSavedPlaces();
            assertThat(saved.getFirst().getNearbyPlaceHours()).isEmpty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<NearbyPlace> captureSavedPlaces() {
        ArgumentCaptor<List<NearbyPlace>> captor =
                ArgumentCaptor.forClass((Class<List<NearbyPlace>>) (Class<?>) List.class);
        verify(nearbyPlaceRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
