# API 계약 (초안)

> Day 1 항목 2. **전원 합의 필요** — 확정 전까지는 초안입니다.
> 작성: 페어 1 (프론트) / 검토 필요: 페어 2·3, 백엔드 담당

---

## 0. 먼저 합의해야 할 것

### 0-1. 경로 프리픽스 — `/api/designs`는 현재 동작하지 않습니다

`api-gateway`는 사전 빌드된 이미지(`msa-lecture/api-gateway:1.0`)이고 저장소에 소스가 없습니다.
라우팅 규칙이 jar 안에 고정되어 있어 아래 5개만 통과합니다.

```
/api/users/**       → user-service
/api/courses/**     → course-service
/api/enrollments/** → enrollment-service
/api/payments/**    → payment-service
/api/recommend/**   → recommend-service
```

`/api/designs/**` 라우트가 없으므로 그대로 호출하면 **404**입니다.

| 선택지 | 작업량 | 비고 |
|---|---|---|
| **A. 백엔드는 `/api/courses`, 프론트만 `/api/designs` 표기** | 5분 | Vite 프록시에서 rewrite. **권장** |
| B. 컨트롤러에 `@RequestMapping({"/api/courses","/api/designs"})` 이중 매핑 | 10분 | 직접 호출(8082)도 designs로 가능 |
| C. docker-compose 환경변수로 Gateway 라우트 재정의 | 1시간+ | 리스트는 소스 간 병합이 안 돼 **기존 5개를 전부 재선언**해야 함 |

> 본 문서는 A/B 를 전제로, 논리 경로를 `/api/designs`로 표기합니다.
> **실제 호출 경로는 `/api/courses`** 입니다.

### 0-2. 인증

- 클라이언트는 `Authorization: Bearer {accessToken}` 을 보냅니다.
- Gateway가 JWT를 검증한 뒤 다운스트림에 `X-User-Id`, `X-User-Role` 헤더를 주입합니다.
- **각 서비스는 JWT를 다시 검증하지 않고 이 헤더를 신뢰합니다.**
  따라서 서비스 포트(8081~8085)를 외부에 노출하면 인증 우회가 가능합니다.

### 0-3. 공통 응답 래퍼

모든 JSON 응답은 아래 형태를 따릅니다. (`CourseDto.ApiResponse` 기준)

```json
{ "success": true, "message": "성공", "data": { } }
```

실패 시:

```json
{ "success": false, "message": "가격은 0 이상이어야 합니다", "data": null }
```

### 0-4. 상태 코드 규약

| 코드 | 의미 |
|---|---|
| 200 / 201 | 성공 / 생성됨 |
| 400 | 클라이언트 입력 오류 (검증 실패, 정의되지 않은 Enum 값 등) |
| 401 | 인증 정보 없음 |
| 403 | 권한 없음 (타인의 디자인 수정, 미구매자 다운로드) |
| 404 | 대상 없음 |
| 413 | 업로드 용량 초과 |
| 500 | 서버 오류 — **클라이언트 입력 문제를 500으로 반환하지 않습니다** |

---

## 1. `GET /api/designs` — 목록 · 검색

### 요청

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `keyword` | string | N | 제목·설명 부분 일치 |
| `category` | string | N | Enum 코드. 미지정 시 전체 |
| `sort` | string | N | `latest`(기본) / `popular` / `priceAsc` / `priceDesc` |
| `page` | int | N | 0부터. 기본 0 |
| `size` | int | N | 기본 20 |

```
GET /api/designs?keyword=로고&category=BACKEND&sort=popular&page=0&size=20
```

### 응답 200

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "content": [
      {
        "id": 7,
        "title": "미니멀 로고 세트",
        "description": "브랜드 아이덴티티용 로고 12종",
        "category": "BACKEND",
        "designerId": 5,
        "designerName": "김디자이너",
        "salesCount": 34,
        "status": "ACTIVE",
        "minPrice": 30000,
        "maxPrice": 250000,
        "previewUrl": "/api/designs/7/asset/preview",
        "hasAsset": true,
        "createdAt": "2026-08-06T14:32:11"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7
  }
}
```

**논의 필요**

- `category` Enum 코드가 화면 라벨과 어긋나 있습니다. 현재 매핑:
  `BACKEND`=로고/브랜딩, `FRONTEND`=UX/UI 키트, `DEVOPS`=일러스트, `DATA_SCIENCE`=템플릿.
  마켓플레이스 의미에 맞는 Enum으로 바꿀지 결정이 필요합니다.
  (프론트·백엔드에 Enum을 각각 정의하면 반드시 어긋납니다 — 실제로 어제 500 오류의 원인이었습니다.)
- `minPrice`/`maxPrice`는 라이선스별 가격(`design_prices`)에서 파생됩니다.
  목록 정렬 성능을 위해 `courses.price`를 대표가(최저가) 캐시로 유지하기로 했습니다.

---

## 2. `POST /api/designs` — 등록 (multipart)

### 요청

`Content-Type: multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `data` | application/json | Y | 아래 JSON |
| `file` | image/png·jpeg·webp | Y | 최대 10MB, 짧은 변 400px 이상 |

```json
{
  "title": "미니멀 로고 세트",
  "description": "브랜드 아이덴티티용 로고 12종",
  "category": "BACKEND",
  "prices": [
    { "licenseTier": "PERSONAL",   "price": 30000 },
    { "licenseTier": "COMMERCIAL", "price": 90000 },
    { "licenseTier": "EXTENDED",   "price": 250000 }
  ]
}
```

**규칙**

- `prices`는 **활성 라이선스 등급 전부**를 포함해야 합니다. 하나라도 빠지면 400.
- 파일은 짧은 변이 400px 이상이어야 합니다. LSB 워터마크 삽입에 최소 픽셀 수가 필요합니다.
- 권한: `X-User-Role: INSTRUCTOR`(디자이너)만 가능. 아니면 403.

### 응답 201

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 7,
    "title": "미니멀 로고 세트",
    "category": "BACKEND",
    "designerId": 5,
    "prices": [
      { "licenseTier": "PERSONAL", "price": 30000 }
    ],
    "asset": {
      "assetToken": "3f2a9c1e-77b4-4d0a-9a11-8c6de0c5b2f1",
      "width": 2400, "height": 1600,
      "fileSize": 1843200,
      "checksum": "9f2b...",
      "previewUrl":  "/api/designs/7/asset/preview",
      "downloadUrl": "/api/designs/7/asset/download"
    },
    "createdAt": "2026-08-06T14:32:11"
  }
}
```

**논의 필요 — 등록과 업로드를 한 번에 할지, 두 단계로 나눌지**

현재 프론트는 **2단계**로 구현되어 있습니다.

```
POST /api/designs        (JSON)      → id 발급
POST /api/designs/{id}/asset (multipart) → 파일 업로드
```

2단계의 장점은 업로드가 실패해도 등록은 남아 상세 화면에서 재시도할 수 있다는 점입니다.
1단계(multipart 통합)로 갈 경우 프론트 수정이 필요하니 **오늘 결정해 주세요.**

---

## 3. `GET /api/designs/{id}` — 상세

### 응답 200

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 7,
    "title": "미니멀 로고 세트",
    "description": "브랜드 아이덴티티용 로고 12종",
    "category": "BACKEND",
    "designerId": 5,
    "designerName": "김디자이너",
    "salesCount": 34,
    "status": "ACTIVE",
    "prices": [
      { "licenseTier": "PERSONAL",   "name": "개인용", "price": 30000,
        "allowsCommercial": false, "allowsRedistribution": false },
      { "licenseTier": "COMMERCIAL", "name": "상업용", "price": 90000,
        "allowsCommercial": true,  "allowsRedistribution": false },
      { "licenseTier": "EXTENDED",   "name": "확장",   "price": 250000,
        "allowsCommercial": true,  "allowsRedistribution": true }
    ],
    "asset": {
      "width": 2400, "height": 1600,
      "previewUrl": "/api/designs/7/asset/preview",
      "hasAsset": true
    },
    "purchased": false,
    "purchasedLicenseTier": null,
    "wishlisted": false,
    "createdAt": "2026-08-06T14:32:11"
  }
}
```

`purchased` / `wishlisted`는 `X-User-Id` 기준으로 계산합니다. 비로그인 시 `false`.

---

## 4. `GET /api/designs/sales/me` — 내 판매 현황

`X-User-Id`(디자이너) 기준.

### 응답 200

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "summary": {
      "totalDesigns": 12,
      "totalSales": 148,
      "totalRevenue": 8940000,
      "thisMonthSales": 23,
      "thisMonthRevenue": 1380000
    },
    "designs": [
      {
        "designId": 7,
        "title": "미니멀 로고 세트",
        "previewUrl": "/api/designs/7/asset/preview",
        "salesCount": 34,
        "revenue": 2010000,
        "salesByLicense": [
          { "licenseTier": "PERSONAL",   "count": 20, "revenue": 600000 },
          { "licenseTier": "COMMERCIAL", "count": 12, "revenue": 1080000 },
          { "licenseTier": "EXTENDED",   "count": 2,  "revenue": 500000 }
        ],
        "status": "ACTIVE"
      }
    ]
  }
}
```

---

## 5. 자산 부속 엔드포인트 (페어 1)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/designs/{id}/asset` | 소유자 | 업로드·교체 (multipart, 필드명 `file`) |
| GET | `/api/designs/{id}/asset/preview` | 불필요 | 가시적 워터마크 미리보기 (image/png) |
| GET | `/api/designs/{id}/asset/download` | 구매자·소유자 | 워터마크본 원본 (image/png) |
| DELETE | `/api/designs/{id}/asset` | 소유자 | 삭제 |
| POST | `/api/designs/assets/verify` | 불필요 | 워터마크 추출·출처 검증 (multipart) |

### 5-1. 업로드 응답 201

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "assetId": 3, "designId": 7, "assetToken": "3f2a9c1e-...",
    "width": 2400, "height": 1600, "fileSize": 1843200,
    "checksum": "9f2b...",
    "previewUrl": "/api/designs/7/asset/preview",
    "downloadUrl": "/api/designs/7/asset/download"
  }
}
```

### 5-2. 미리보기 / 다운로드

- 응답은 **JSON이 아니라 이미지 바이트**입니다. `Content-Type: image/png`
- 미리보기는 `<img src>`에 URL을 그대로 사용합니다. 인증이 필요 없어야 합니다.
- 다운로드는 인증이 필요하므로 프론트에서 `responseType: 'blob'`으로 받습니다.
  파일명은 `Content-Disposition: attachment; filename*=UTF-8''...` 에서 읽습니다.
- 다운로드 응답에 `X-Content-Checksum` 헤더로 SHA-256을 함께 내려주면
  유출 사본 대조에 사용할 수 있습니다.

### 5-3. 워터마크 검증 응답

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "watermarkFound": true,
    "registered": true,
    "assetToken": "3f2a9c1e-...",
    "designId": 7,
    "designTitle": "미니멀 로고 세트",
    "ownerId": 5,
    "issuedAt": "2026-08-06T14:32:11",
    "checksumMatched": false,
    "message": "워터마크는 일치하나 파일이 재가공되었습니다(리사이즈·재저장 등)."
  }
}
```

---

## 6. 미결 사항

| # | 항목 | 결정자 | 기한 |
|---|---|---|---|
| 1 | `/api/designs` 프리픽스 처리 방식 (A/B/C) | 전원 | Day 1 |
| 2 | 카테고리 Enum 코드 재정의 여부 | 전원 | Day 1 |
| 3 | 등록 = 1단계(multipart 통합) vs 2단계 | 전원 | Day 1 |
| 4 | 이미지 저장 위치 — DB LONGBLOB / 파일시스템 / MinIO | 백엔드 | Day 1 |
| 5 | `courses.image_url`·`watermarked_url` 컬럼 필요 여부 (4번에 종속) | 백엔드 | Day 1 |
| 6 | 목록 응답 페이지네이션 적용 여부 | 전원 | Day 1 |
| 7 | 구독으로 받은 다운로드의 라이선스 등급 | 페어 2 | Day 2 |
