# watermark-service 분리 명세

> 작성: 페어 1 / 대상: 백엔드 전원
> 기준 브랜치: `feature/watermark`
>
> 워터마크 처리를 별도 서비스로 분리하고, 구매 확정 이벤트로 구매자별 사본을 만드는 구조에 대한 명세입니다.

---

## 1. 분리 개요

### 왜 분리했는가

| 이유 | 설명 |
|---|---|
| 자원 격리 | 워터마크는 이미지 전체 픽셀을 순회하는 CPU 집약 작업이다. 조회 트래픽을 받는 course-service와 같은 인스턴스에서 돌면 응답 지연이 그대로 전파된다. |
| 책임 분리 | 구매 확정에 반응해 사본을 만드는 일은 디자인 CRUD를 담당하는 course-service의 책임이 아니다. |
| 독립 확장 | 판매가 몰리는 시점에 워터마크 처리만 인스턴스를 늘릴 수 있다. |

### 서비스 경계

| 서비스 | 책임 | 소유 데이터 |
|---|---|---|
| **course-service** | 디자인 CRUD, 업로드본 저장, 권한 판정 | `courses`, `course_uploads` 볼륨 |
| **watermark-service** | 워터마크 생성·추출, 구매자별 사본 | `buyer_watermarks`, `watermark_files` 볼륨 |
| **enrollment-service** | 구매 상태 관리, 구매 확정 이벤트 발행 | `enrollments` |

**저장소를 공유하지 않습니다.** 각 서비스가 자기 볼륨을 갖고, 필요한 데이터는 HTTP로 요청합니다.

---

## 2. 이벤트 흐름

```
결제 완료
  │
  ├─ payment.completed        payment-service ──▶ enrollment-service
  │                           (기존, 변경 없음)
  ▼
구매 ACTIVE 확정
  │
  ├─ enrollment.completed     enrollment-service ──▶ recommend-service
  │                           (기존, 변경 없음)
  │
  └─ purchase.completed       enrollment-service ──▶ watermark-service   ★ 신규
        │
        ▼
     원본 조회 (HTTP) ──▶ course-service
        │
        ▼
     구매자 ID 삽입 → watermark-service 볼륨에 저장
```

두 이벤트를 같은 지점(`EnrollmentService.activateEnrollment`)에서 발행합니다.
소비자가 서로 다르고 목적도 달라 하나로 합치지 않았습니다. 나중에 한쪽 스키마가 바뀌어도 다른 쪽에 영향이 없습니다.

### `purchase.completed` 스키마

```json
{
  "purchaseId": 12,
  "buyerId": 7,
  "courseId": 10,
  "occurredAt": 1786100000
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `purchaseId` | Long | `enrollments.id` |
| `buyerId` | Long | 구매자. `userId`가 아니라 `buyerId`로 이름 붙였다 — 수신 측에서 의미가 분명해야 하기 때문 |
| `courseId` | Long | 디자인 ID |
| `occurredAt` | Long | epoch seconds |

- **토픽**: `purchase.completed`
- **파티션 키**: `courseId`
  같은 디자인에 대한 이벤트가 한 파티션에 모여 순서가 보장된다. 동시 구매 시 같은 원본을 중복 처리하는 상황을 줄인다.
- **컨슈머 그룹**: `watermark-service`

---

## 3. 워터마크 페이로드

이미지 픽셀 파랑 채널 최하위 비트에 심는 문자열입니다.

```
업로드본   DMP1|{courseId}|{ownerId}|{epoch}
구매자본   DMP1|{courseId}|{ownerId}|{buyerId}|{epoch}
```

`extract()`는 토큰 수로 두 형식을 구분합니다. 기존 파일도 그대로 읽힙니다.

**구매자본에만 `buyerId`가 있습니다.** 유출본에서 `buyerId`를 못 읽으면 업로드 시점 파일이라는 뜻이고, 유출 경로를 특정할 수 없습니다.

### 지켜야 하는 제약

| 제약 | 이유 |
|---|---|
| 결과물은 항상 PNG | JPEG로 재인코딩하면 최하위 비트가 파괴되어 추출 불가 |
| 가시적 → 비가시적 순서 | 가시적 처리가 픽셀을 바꾸므로 LSB는 반드시 그 뒤에 심어야 한다 |
| 짧은 변 400px 이상 | 페이로드를 담을 픽셀 수가 필요하다 |
| Alpine에 폰트 설치 | Alpine JRE에는 폰트가 없어 `Graphics2D.drawString()`이 실패한다 |

---

## 4. API 명세

모든 경로가 `/api/watermark/internal/**` 입니다.
**게이트웨이 라우팅에 포함되지 않아 외부에서 호출할 수 없습니다.** 서비스 간 호출과 Kafka로만 동작합니다.

> 게이트웨이가 사전 빌드된 이미지(`msa-lecture/api-gateway:1.0`)라 새 프리픽스를 추가할 수 없습니다.
> 이 서비스는 외부 노출이 필요 없으므로 문제되지 않습니다.

### 4-1. `POST /api/watermark/internal/preview`

미리보기본 생성 (가시적 + 비가시적). course-service가 업로드 시점에 호출합니다.

```
Content-Type: multipart/form-data
Part:   file        이미지 바이트
Query:  courseId    Long
        ownerId     Long
        ownerLabel  String (선택) — 가시적 워터마크에 찍을 이름
```

**응답** `200 image/png` — 이미지 바이트

### 4-2. `POST /api/watermark/internal/original`

판매용 원본 생성 (비가시적만).

```
Content-Type: multipart/form-data
Part:   file
Query:  courseId, ownerId
```

**응답** `200 image/png`

| 헤더 | 내용 |
|---|---|
| `X-Watermark-Payload` | 삽입된 페이로드 |
| `X-Watermark-Checksum` | 결과물 SHA-256 |

### 4-3. `GET /api/watermark/internal/buyer-copy/{courseId}/{buyerId}`

구매자 전용 사본 조회.

**응답**
- `200 image/png` — 사본 바이트
- `404` — 아직 생성 전. **호출 측은 원본으로 폴백해야 합니다.**

### 4-4. `POST /api/watermark/internal/trace`

유출본 추적.

```
Content-Type: multipart/form-data
Part: file
```

```json
{
  "watermarkFound": true,
  "payload": "DMP1|10|5|7|1786100000",
  "courseId": 10,
  "ownerId": 5,
  "buyerId": 7,
  "issuedAt": "2026-08-07T14:20:00",
  "traced": true,
  "checksumMatched": false,
  "message": "구매자 7 에게 배포된 사본입니다."
}
```

| 필드 | 의미 |
|---|---|
| `watermarkFound` | 워터마크 추출 성공 여부 |
| `buyerId` | `null`이면 업로드 시점 파일 — 유출 경로 특정 불가 |
| `traced` | 구매 이력(`buyer_watermarks`)에서 일치 건을 찾았는지 |
| `checksumMatched` | 배포한 사본과 바이트 단위로 동일한지. `false`면 재가공된 것 |

### 4-5. course-service가 제공하는 통로

watermark-service가 원본을 가져가는 경로입니다.

```
GET /api/courses/internal/{id}/asset/original   →  200 image/png
GET /api/courses/internal/{id}                  →  CourseResponse (instructorId 참조용)
```

---

## 5. 데이터 모델

### `buyer_watermarks` (watermark-service 소유)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | |
| `course_id` | BIGINT | |
| `buyer_id` | BIGINT | |
| `owner_id` | BIGINT | 판매자 |
| `stored_name` | VARCHAR(255) | `buyer-{courseId}-{buyerId}.png` |
| `payload` | VARCHAR(255) | 삽입된 페이로드 원문 |
| `checksum` | VARCHAR(64) | 사본 SHA-256 |
| `file_size` | BIGINT | |
| `created_at` | DATETIME(6) | |

`UNIQUE (course_id, buyer_id)` — 같은 사람이 같은 디자인을 다시 사도 사본은 하나만 유지하고 갱신합니다.

`ddl-auto: update`로 자동 생성됩니다. 별도 DDL 스크립트는 없습니다.

> **논의 필요** — 지금은 `lecture_db`를 공유합니다. Database per Service를 지키려면 `watermark_db`로 분리해야 하지만, 실습 범위에서는 단일 DB를 유지했습니다.

---

## 6. 장애 처리 방침

### 컨슈머는 예외를 던지지 않습니다

```java
catch (Exception e) {
    log.error("구매자 사본 생성 실패 - event: {}", event, e);
    // 오프셋은 넘긴다
}
```

예외를 전파하면 같은 메시지를 무한 재시도하며 컨슈머가 멈춥니다.
실패한 건은 로그로 남기고 넘어갑니다.

### 다운로드는 원본으로 폴백합니다

```
구매자 다운로드 요청
  → watermark-service에 구매자 사본 요청
  → 404 또는 통신 실패
  → 원본(판매자 워터마크만)으로 폴백  + WARN 로그
```

**이벤트 처리가 늦어져도 구매자가 파일을 못 받는 상황은 만들지 않습니다.**
다만 이 경우 유출 추적이 불가능하므로, 폴백이 잦다면 로그를 확인해야 합니다.

### 판매자 정보 조회 실패

`ownerId`를 못 가져와도 `0`으로 채우고 사본 생성을 진행합니다.
추적에 반드시 필요한 것은 `buyerId`이기 때문입니다.

---

## 7. 배포

### docker-compose

```yaml
  watermark-service:
    build: { context: ./watermark-service }
    container_name: lecture-watermark
    ports: ["8086:8086"]
    volumes:
      - watermark_files:/app/watermarks
    depends_on:
      mariadb:       { condition: service_healthy }
      eureka-server: { condition: service_healthy }
      kafka:         { condition: service_healthy }
      course-service: { condition: service_started }

volumes:
  course_uploads:      # course-service 업로드본
  watermark_files:     # watermark-service 구매자 사본
```

| 항목 | 값 |
|---|---|
| 포트 | 8086 |
| Eureka 등록명 | `WATERMARK-SERVICE` |
| 저장 경로 | `/app/watermarks` |

### 기동 확인

```bash
docker compose build
docker compose up -d
docker compose logs --tail=30 watermark-service
```

정상 로그:

```
[Storage] 워터마크 저장 경로 준비 완료: /app/watermarks
Registering application WATERMARK-SERVICE with eureka with status UP
```

### 동작 확인

구매를 한 건 진행한 뒤:

```bash
docker compose logs -f watermark-service
```

```
[Kafka Consumer] purchase.completed 수신: {purchaseId=12, buyerId=7, courseId=10, ...}
[Watermark] 구매자 사본 생성 courseId=10 buyerId=7 payload=DMP1|10|5|7|... size=...B
[BuyerWatermark] 사본 준비 완료 courseId=10 buyerId=7 file=buyer-10-7.png
```

---

## 8. 남은 과제

| 항목 | 내용 |
|---|---|
| 프론트 검증 화면 | `trace` API가 열려 있으나 UI가 없다. 파일을 올려 출처를 확인하는 화면이 필요하다 |
| 멱등성 | 같은 이벤트를 두 번 받으면 사본을 두 번 만든다. `UNIQUE(course_id, buyer_id)`로 결과는 같지만 처리 비용은 중복된다 |
| DB 분리 | `lecture_db` 공유 중. Database per Service 미적용 |
| 동기 의존 | 업로드 시 course-service가 watermark-service를 동기 호출한다. watermark-service가 죽으면 업로드가 실패한다 |
| 저장 공간 | 구매자마다 사본이 쌓인다. 보관 기간 정책이 필요하다 |
