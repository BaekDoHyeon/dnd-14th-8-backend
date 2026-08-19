# AWS 크레딧 잔액 Slack 알림 설정

매일 09:00(KST) AWS 크레딧 차감 현황을 Slack으로 보낸다.
구현: `global/client/aws/AwsCostExplorerClient`, `global/metrics/alert/AwsCreditNotifier`

## 왜 크레딧 총액을 직접 설정해야 하나

AWS는 **크레딧 잔액을 조회하는 공개 API를 제공하지 않는다.** 콘솔의
[Credits 페이지](https://console.aws.amazon.com/billing/home#/credits)에서만 볼 수 있다.

그래서 이 알림은 다음처럼 잔액을 낸다.

```
잔액 = AWS_CREDIT_TOTAL (수동 설정) - Cost Explorer가 보고한 누적 크레딧 차감액
```

`AWS_CREDIT_TOTAL`이 비어 있으면 잔액 줄 없이 누적 사용액·일평균만 보낸다.
크레딧을 추가로 부여받으면 이 값을 직접 올려야 한다.

## 준비 1: 읽기 전용 IAM 사용자

배포용 AWS 키와 섞지 않기 위해 별도 사용자를 만든다.

```bash
aws iam create-user --user-name moyeolak-cost-reader

cat > /tmp/cost-reader-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["ce:GetCostAndUsage"],
      "Resource": "*"
    }
  ]
}
JSON

aws iam put-user-policy \
  --user-name moyeolak-cost-reader \
  --policy-name CostExplorerReadOnly \
  --policy-document file:///tmp/cost-reader-policy.json

aws iam create-access-key --user-name moyeolak-cost-reader
```

마지막 명령이 출력하는 `AccessKeyId` / `SecretAccessKey`를 아래 `.env`에 넣는다.

> Cost Explorer가 콘솔에서 한 번도 활성화되지 않았다면 API가 빈 데이터를 돌려준다.
> Billing 콘솔 > Cost Explorer를 한 번 열어 활성화한다.

## 준비 2: EC2 `.env`

`/app/moyeolak/.env`에 추가한다. `docker-compose.prod.yml`이 이 값을 컨테이너로 넘긴다.

```bash
AWS_COST_ALERT_ENABLED=true
AWS_COST_ACCESS_KEY_ID=AKIA...
AWS_COST_SECRET_ACCESS_KEY=...
AWS_CREDIT_TOTAL=300           # 콘솔 Credits 페이지에서 확인한 부여 총액(USD)
AWS_CREDIT_START_DATE=2026-07-01
```

`AWS_COST_ALERT_ENABLED`가 `false`(기본값)면 관련 빈이 아예 생성되지 않는다.

## 설정 항목

| 프로퍼티 | 환경변수 | 기본값 | 설명 |
|---|---|---|---|
| `aws.cost.enabled` | `AWS_COST_ALERT_ENABLED` | `false` | 알림 on/off. 꺼지면 SDK 클라이언트도 안 만든다 |
| `aws.cost.access-key` | `AWS_COST_ACCESS_KEY_ID` | (빈값) | 비우면 SDK 기본 자격증명 체인 사용 |
| `aws.cost.secret-key` | `AWS_COST_SECRET_ACCESS_KEY` | (빈값) | |
| `aws.cost.credit-total` | `AWS_CREDIT_TOTAL` | (빈값) | 부여 크레딧 총액(USD). 비우면 잔액 계산 생략 |
| `aws.cost.credit-start-date` | `AWS_CREDIT_START_DATE` | `2026-07-01` | 누적 차감 집계 시작일 |
| `aws.cost.cron` | — | `0 0 9 * * *` | 발송 시각 |

## 메시지 예시

```
:moneybag: AWS 크레딧 현황 (2026-08-18 기준)

남은 크레딧: $290.77 / $300.00 (96.9%)
누적 사용: $9.23 (2026-07-01 이후)
이번 달 사용: $3.79
최근 7일 일평균: $0.21
소진 예상: 2030-06-09 (1391일 남음)
```

## 알아둘 것

- **Cost Explorer 데이터는 약 24시간 지연된다.** 메시지의 "기준" 날짜는 어제다.
  당일 데이터는 부분 집계라 일평균에서 제외한다.
- **Cost Explorer API는 요청당 $0.01.** 하루 2회 호출하므로 월 약 $0.6.
- 크레딧은 Cost Explorer에서 음수(`RECORD_TYPE=Credit`)로 내려온다. 코드가 절대값으로 뒤집어 "사용액"으로 다룬다.
- 조회에 실패하면 로그만 남기고 Slack 전송을 건너뛴다. 애플리케이션 동작에는 영향이 없다.

## 수동 확인

```bash
# 누적 크레딧 차감
aws ce get-cost-and-usage --region us-east-1 \
  --time-period Start=2026-07-01,End=$(date -u -v+1d +%Y-%m-%d) \
  --granularity MONTHLY --metrics UnblendedCost \
  --filter '{"Dimensions":{"Key":"RECORD_TYPE","Values":["Credit"]}}'
```
