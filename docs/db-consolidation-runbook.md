# 운영 DB 일원화 전환 절차

> 전제: 운영 데이터 보존 불필요 확정. MySQL(primary) + PostGIS(secondary)를 PostgreSQL+PostGIS 단일 DB로 재생성한다.

## 1. 배포 전 EC2 정리

```bash
ssh <ec2>
cd /app/moyeolak
docker compose -f docker-compose.prod.yml down backend mysql postgis
docker volume ls
docker volume rm <mysql-volume> <pgdata-volume>
rm -f postgis-init.sql
```

볼륨 이름은 환경마다 다를 수 있으므로 `docker volume ls`로 실제 이름을 확인한 뒤 삭제한다.

## 2. `.env` 갱신

`infra/deploy/.env.example` 기준으로 `/app/moyeolak/.env`를 정리한다.

유지/설정:

```env
DOMAIN=api.example.com
ECR_REGISTRY=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com

DB_NAME=moyeolak
DB_USERNAME=moyeolak
DB_PASSWORD=change-me

KAKAO_API_KEY=change-me
GOOGLE_API_KEY=change-me
ODSAY_API_KEY=change-me

GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me
```

삭제:

- `MYSQL_ROOT_PASSWORD`
- `POSTGIS_DB_NAME`
- `POSTGIS_USERNAME`
- `POSTGIS_PASSWORD`
- `JPA_DDL_AUTO`

## 3. 배포

`dev` 또는 `main` 배포 워크플로를 실행한다. 새 `docker-compose.prod.yml`은 `postgres` 서비스만 올리고, backend는 `DB_HOST=postgres`, `DB_PORT=5432`로 접속한다.

첫 backend 기동 시 Flyway가 다음 마이그레이션을 적용한다.

- `V1__init_schema.sql`: 도메인/공간 스키마 + PostGIS extension + GiST 인덱스
- `V2__seed_stations.sql`: 수도권 지하철역 시드

## 4. 검증

```bash
docker ps --format '{{.Names}}\t{{.Status}}'
docker exec moyeolak-postgres psql -U <user> -d moyeolak -c "SELECT count(*) FROM stations;"
curl -s https://<domain>/actuator/health
```

확인 기준:

- `moyeolak-postgres`, `moyeolak-backend`가 healthy/running
- `stations` count가 0보다 큼
- health 응답이 `UP`
- backend 로그에 Flyway V1/V2 적용과 Hibernate validate 성공이 보임

## 5. 롤백 메모

운영 데이터 보존 없는 재생성 전환이므로 DB 데이터 롤백은 지원하지 않는다. 배포 파일만 이전 버전으로 되돌리는 경우에도 기존 MySQL/PostGIS 볼륨을 이미 삭제했다면 데이터는 복구되지 않는다.
