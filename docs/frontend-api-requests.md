# 프론트엔드 → 백엔드 API 요청 명세

> 작성: 페어 1 프론트 / 대상: course-service · user-service 담당
> 기준: `feature/watermark` 브랜치 (2026-08-07)
>
> 프론트 구현을 진행하면서 **백엔드가 있어야 완성되는 것들**만 모았습니다.
> 우선순위 순으로 정렬했고, 각 항목에 프론트가 이미 어떻게 대비해 뒀는지 적었습니다.

---

## 우선순위 요약

| # | 항목 | 영향 | 난이도 |
|---|---|---|---|
| 1 | `CourseResponse`에 디자이너 이름·이메일 추가 | **목록에서 N+1 호출 발생 중** | 낮음 |
| 2 | 위시리스트 API | 기능 자체가 동작 불가 | 중간 |
| 3 | 라이선스 등급 등록·수정 API | 등록 화면에서 가격 입력 불가 | 중간 |
| 4 | 목록 검색·정렬 파라미터 | 데이터 늘면 성능 문제 | 중간 |
| 5 | 미리보기 인증 정책 정리 | 이미지 로딩이 비효율적 | 낮음 |
| 6 | 구매자별 워터마크 | 유출 추적 불가 | 높음 |

---

## 1. `CourseResponse`에 디자이너 정보 추가 ⭐ 가장 시급

### 문제

`CourseResponse`에 `instructorId`(숫자)만 있고 이름·이메일이 없습니다.
프론트는 디자이너 연락처를 보여주려고 `GET /api/users/{id}`를 **별도로 호출**하고 있습니다.

상세 화면은 1회라 괜찮지만, **목록 화면은 카드 수만큼 호출이 발생합니다.**
디자인 20개면 목록 1회 + 사용자 20회 = 21회입니다. 전형적인 N+1입니다.

### 요청

`CourseResponse`에 두 필드를 추가해 주세요.

```json
{
  "id": 10,
  "title": "미니멀 로고 세트",
  "instructorId": 5,
  "designerName": "김디자이너",
  "designerEmail": "designer@example.com",
  ...
}
```

`GET /api/courses`(목록), `GET /api/courses/{id}`(상세) **양쪽 모두** 필요합니다.

### 프론트 대비 상태

이미 아래 순서로 읽고 있어서, **백엔드가 채워주면 추가 호출을 자동으로 건너뜁니다.** 프론트 수정 불필요합니다.

```js
course.designerEmail ?? course.instructorEmail ?? (별도 조회 결과)
```

### 구현 참고

course-service에서 user-service를 매번 호출하면 같은 N+1이 서버로 옮겨갈 뿐입니다.
`instructor_id` 목록을 모아 한 번에 조회하는 벌크 엔드포인트(`POST /api/users/internal/bulk`)를 두거나,
`courses` 테이블에 디자이너명을 비정규화해 두는 방식이 일반적입니다.

---

## 2. 위시리스트 API

### 현재

`wishlist` 테이블과 `Wishlist` 엔티티는 있는데 **컨트롤러·서비스가 없습니다.**

### 요청

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/courses/wishlist/me` | 내 찜 목록 |
| POST | `/api/courses/{id}/wishlist` | 찜 추가 |
| DELETE | `/api/courses/{id}/wishlist` | 찜 해제 |

`X-User-Id` 기준으로 판정합니다. 중복 추가는 409 또는 멱등 처리(200) 중 편한 쪽으로 정해 주세요.

**추가로** `CourseResponse`에 `wishlisted`(boolean)가 있으면 목록에서 하트 상태를 바로 칠할 수 있습니다.
비로그인 시 `false`로 내려주세요.

---

## 3. 라이선스 등급 등록·수정 API

### 현재

`GET /api/courses/{id}/license-tiers` **조회만** 있습니다. 등록 API가 없어 항상 빈 배열입니다.

### 요청

디자인 등록 시 라이선스별 가격을 함께 받아야 합니다.

```json
POST /api/courses
{
  "title": "미니멀 로고 세트",
  "description": "...",
  "category": "FRONTEND",
  "prices": [
    { "tier": "PERSONAL",   "price": 30000 },
    { "tier": "COMMERCIAL", "price": 90000 },
    { "tier": "EXTENDED",   "price": 250000 }
  ]
}
```

또는 등록 후 별도 호출도 괜찮습니다.

```
POST /api/courses/{id}/license-tiers
```

**정해야 할 것** — 활성 등급을 전부 입력해야 하는지, 일부만 허용할지.
전부 필수라면 누락 시 400과 함께 어느 등급이 빠졌는지 메시지에 담아 주세요.

`courses.price`(대표가)는 최저 등급 가격으로 서버에서 계산해 채워 주시면,
목록 정렬·필터가 지금 코드 그대로 동작합니다.

---

## 4. 목록 검색·정렬 파라미터

### 현재

`GET /api/courses`가 전체를 반환하고, **프론트에서 필터링·정렬**하고 있습니다.
데이터가 적을 때는 문제없지만 늘어나면 전량 전송이 부담됩니다.

### 요청

```
GET /api/courses?keyword=로고&category=FRONTEND&sort=popular&page=0&size=20
```

| 파라미터 | 값 |
|---|---|
| `keyword` | 제목·설명 부분 일치 |
| `category` | Enum 코드 |
| `sort` | `latest`(기본) / `popular` / `priceAsc` / `priceDesc` |
| `page`, `size` | 페이지네이션 |

### 프론트 대비 상태

`CourseListView`의 필터 로직에 주석으로 표시해 뒀습니다.
서버 파라미터가 열리면 클라이언트 필터링을 걷어내고 응답을 그대로 쓰면 됩니다.

**페이지네이션을 넣을지 먼저 정해 주세요.** 응답이 배열에서 `{content, totalPages, ...}` 객체로 바뀌면 프론트 파싱을 함께 고쳐야 합니다.

---

## 5. 미리보기 인증 정책 정리

### 현재 상태가 어긋나 있습니다

`CourseController`의 주석은 이렇게 되어 있습니다.

```java
// img 태그의 src 로 직접 쓰이므로 인증 헤더 없이 접근 가능해야 한다.
@GetMapping("/{id}/asset/preview")
```

그런데 **실제로는 게이트웨이가 인증을 요구**합니다.
`<img src="/api/courses/10/asset/preview">`로 쓰면 브라우저가 `Authorization` 헤더를 붙이지 않아 401이 납니다.

그래서 프론트는 axios로 blob을 받아 `objectURL`로 변환해 쓰고 있습니다(`useAssetImage`).
동작은 하지만 이미지마다 JS를 거치고 브라우저 캐시를 못 쓰는 구조입니다.

### 요청

둘 중 하나로 정해 주세요.

- **A.** 게이트웨이에서 `/api/courses/*/asset/preview`를 인증 예외로 열기 → 프론트는 `<img src>`로 단순화, 브라우저 캐시 활용
- **B.** 현재 방식 유지 → 프론트 변경 없음. 다만 위 주석은 실제와 다르므로 수정 필요

미리보기에는 워터마크가 찍혀 있으니 A로 열어도 원본 유출 위험은 없다고 봅니다.

---

## 6. 구매자별 워터마크 (구매확정 이벤트)

### 계획과 현재 구현의 차이

백로그에는 **"워터마크 삽입 로직 (구매자ID + 판매자ID 인코딩) — 구매확정 이벤트 수신"** 으로 되어 있습니다.

현재 구현은 **업로드 시점에 판매자ID만** 심습니다.

```
DMP1|{courseId}|{ownerId}|{epoch}     ← 구매자 정보 없음
```

이러면 유출본을 찾아도 **"누가 유출했는지"를 특정할 수 없습니다.** 워터마크 추적의 핵심 목적이 빠진 상태입니다.

### 요청

`payment.completed` 이벤트를 받아 **구매자별 사본**을 생성해야 합니다.

```
DMP1|{courseId}|{ownerId}|{buyerId}|{epoch}
```

다운로드 시 구매자에게는 자기 ID가 박힌 사본을 내려줍니다.

**설계 판단이 필요한 지점** — 구매 시점에 미리 만들어 둘지, 다운로드 시점에 생성할지.
전자는 저장 공간이 구매 수만큼 늘고, 후자는 다운로드마다 이미지 처리 비용이 듭니다.
구매 수가 적은 지금은 다운로드 시점 생성이 단순해 보입니다.

---

## 7. 소소한 것들

### 7-1. `SalesItemResponse`의 식별자 이름

백엔드는 `courseId`로 내려주는데 `SalesTab.vue`는 `item.id`를 읽습니다.
**프론트에서 고칠 수 있는 부분**이라 백엔드 변경은 필요 없지만, 페어 2에 공유가 필요합니다.

### 7-2. `docker-compose.yml` 볼륨 마운트

`course-service`에 볼륨이 없어 `/app/uploads`가 컨테이너와 함께 사라집니다.
재빌드하면 업로드한 이미지가 전부 날아가고 DB에는 URL만 남아 깨진 이미지가 됩니다.

```yaml
  course-service:
    volumes:
      - course_uploads:/app/uploads
volumes:
  course_uploads:
```

### 7-3. Maven Central 미러 (다른 서비스 4개)

`course-service`에만 적용돼 있습니다. `user`, `enrollment`, `payment`, `eureka`를 재빌드하는 팀원은 429로 막힙니다.
`settings.gradle`의 `pluginManagement`까지 함께 고쳐야 합니다.

---

## 프론트에서 이미 처리한 것 (백엔드 작업 불필요)

참고용입니다. 아래는 요청 사항이 아닙니다.

- 업로드 전 형식·용량·해상도 검증 (PNG/JPG/WEBP, 10MB, 짧은 변 400px)
- 자산 미등록 시 카테고리 기본 이미지로 폴백 (`hasAsset` 활용)
- 미리보기 blob 캐싱 (목록에서 같은 이미지 중복 요청 방지)
- 다운로드 시 `Content-Disposition` 파일명 파싱
- 403 / 404 / 413 / 500 상태 코드별 안내 문구 분기

---

## 상태 코드 규약 (재확인)

| 코드 | 프론트 동작 |
|---|---|
| 400 | 서버 `message`를 그대로 사용자에게 표시 |
| 401 | 로그인 안내 |
| 403 | "구매한 사용자만 이용할 수 있습니다" |
| 404 | "등록된 이미지가 없습니다" |
| 413 | "파일 크기가 허용 범위를 초과했습니다" |
| 500 | "서버 오류가 발생했습니다" (원인 표시 불가) |

**클라이언트 입력 오류는 400으로 주세요.** 500으로 오면 사용자가 무엇을 고쳐야 할지 알 수 없습니다.
