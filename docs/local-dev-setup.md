# 로컬 개발 환경 세팅 가이드

## 사전 요구사항

- Docker & Docker Compose
- Java 25
- Gradle
- 외부 API 키가 담긴 `.env`

---

## 1. PostgreSQL+PostGIS 컨테이너 실행

local 프로필로 애플리케이션을 실행하면 `spring-boot-docker-compose`가 `docker-compose.yml`을 감지해 `postgres`와 `redis`를 자동으로 기동합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

컨테이너를 직접 관리하고 싶으면 아래 명령을 사용할 수 있습니다.

```bash
docker-compose up -d postgres redis
docker ps
```

### DB 접속 정보

| 항목 | 값 |
|------|-----|
| Host | localhost |
| Port | 5432 |
| Database | moyeolak |
| Username | moyeolak |
| Password | moyeolak |

### 컨테이너 관리 명령어

```bash
# 컨테이너 중지
docker-compose down

# 컨테이너 중지 + 데이터 삭제
docker-compose down -v

# 로그 확인
docker-compose logs -f postgres
```

---

## 2. Spring Boot 실행

`local` 프로파일로 애플리케이션을 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

첫 기동 시 Flyway가 아래 마이그레이션을 적용합니다.

| 파일 | 설명 |
|------|------|
| `V1__init_schema.sql` | 도메인/공간 스키마, PostGIS extension, GiST 인덱스 |
| `V2__seed_stations.sql` | 수도권 지하철역 시드 |

Flyway/PostGIS 마이그레이션은 Testcontainers 기반 테스트로도 검증합니다.

```bash
./gradlew test --tests com.dnd.moyeolak.global.config.PostgisFlywayMigrationTest
```

Docker가 없는 CI/로컬 환경에서는 Testcontainers JUnit 설정에 의해 해당 테스트가 비활성화됩니다.

### IDE에서 실행 (IntelliJ)

1. Run/Debug Configurations 열기
2. Active profiles에 `local` 입력
3. `.env`의 `KAKAO_API_KEY`, `GOOGLE_API_KEY`, `ODSAY_API_KEY`가 로드되는지 확인
4. 실행

### 모니터링 스택 실행

Prometheus, Loki, Grafana는 기본 자동 기동 대상에서 제외되어 있습니다. 필요할 때만 Docker Compose profile을 켭니다.

```bash
docker-compose --profile monitoring up -d
```

---

## 3. 초기 데이터

서버 시작 시 Flyway가 스키마와 역 데이터를 만들고, local 프로필에서 `data.sql`이 데모 모임 데이터를 생성합니다.

`data.sql`은 재기동 멱등성을 위해 먼저 `meeting`을 `TRUNCATE ... CASCADE` 한 뒤 다시 삽입합니다. `stations`는 Flyway가 관리하므로 `data.sql`에서 초기화하지 않습니다.

### 생성되는 데이터

| 테이블 | 개수 | 설명 |
|--------|------|------|
| stations | 수백 개 | Flyway V2 지하철역 시드 |
| meeting | 10개 | test-meeting-001 ~ 010 |
| schedule_poll | 10개 | 각 모임당 1개 |
| location_poll | 10개 | 각 모임당 1개 |
| participant | 다수 | 각 모임 방장 및 일부 참여자 |
| location_vote | 다수 | 중간지점 추천 실험용 출발지 |

### 테스트 모임 ID 목록

```text
test-meeting-001
test-meeting-002
test-meeting-003
test-meeting-004
test-meeting-005
test-meeting-006
test-meeting-007
test-meeting-008
test-meeting-009
test-meeting-010
```

---

## 4. 설정 파일 구조

```text
src/main/resources/
├── application.yml
├── application-local.yml
├── application-prod.yml
├── data.sql
└── db/migration/
    ├── V1__init_schema.sql
    └── V2__seed_stations.sql
```

### application-local.yml 주요 설정

| 설정 | 값 | 설명 |
|------|-----|------|
| datasource | `jdbc:postgresql://localhost:5432/moyeolak` | 단일 PostgreSQL+PostGIS DB |
| ddl-auto | `validate` | Flyway 스키마와 엔티티 일치 검증 |
| sql.init.mode | `always` | local demo `data.sql` 실행 |

---

## 5. 데이터 유지하고 싶을 때

local 프로필은 데모 데이터 재삽입을 위해 `data.sql`이 매번 실행됩니다. 직접 만든 도메인 데이터를 유지하려면 임시로 아래 설정을 사용합니다.

```yaml
spring:
  sql:
    init:
      mode: never
```

스키마 변경은 `ddl-auto`로 처리하지 않고 새 Flyway 마이그레이션을 추가합니다.

---

## 6. 트러블슈팅

### 포트 충돌 (5432 사용 중)

```bash
lsof -i :5432
```

필요하면 `docker-compose.yml`의 호스트 포트를 변경하고 `application-local.yml`의 JDBC URL도 같은 포트로 맞춥니다.

```yaml
ports:
  - "5433:5432"
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/moyeolak
```

### 컨테이너 접속

```bash
docker exec -it moyeolak-postgres psql -U moyeolak -d moyeolak
docker exec -it moyeolak-postgres bash
```

### 데이터 초기화

```bash
docker-compose down -v
docker-compose up -d postgres redis
./gradlew bootRun --args='--spring.profiles.active=local'
```

### `database "moyeolak" does not exist`

이전 로컬 볼륨이 `moyeolak_spatial` DB명으로 초기화된 상태에서 새 설정을 실행하면 발생할 수 있습니다. 현재 `docker-compose.yml`에는 `postgres-init` 서비스가 있어 `moyeolak` DB가 없으면 자동 생성합니다.

이미 앱 기동이 실패한 상태라면 아래 중 하나로 복구합니다.

```bash
# 기존 데이터를 버려도 되는 경우
docker-compose down -v
docker-compose up -d postgres redis
```

```bash
# 기존 볼륨을 유지하고 DB만 추가하는 경우
docker exec moyeolak-postgres psql -U moyeolak -d postgres -c "CREATE DATABASE moyeolak;"
```
