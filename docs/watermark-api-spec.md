# 워터마크 API 명세 (프론트 → 백엔드 요청)

> 작성: 페어 1 프론트 / 대상: course-service 담당
> 목적: 프론트가 이미 호출 중인 경로와, 워터마크 기능에 필요한 응답 스펙 정리

---

## 1. 현재 상태

프론트는 아래 3개를 이미 호출하고 있고 **경로는 바꾸지 않아도 됩니다.**

| 동작 | 메서드 · 경로 | 상태 |
|---|---|---|
| 업로드 | `POST /api/courses/{id}/asset` | ✅ 구현됨 |
| 미리보기 | `GET /api/courses/{id}/asset/preview` | ✅ 구현됨 |
| 원본 다운로드 | `GET /api/courses/{id}/asset/download` | ✅ 구현됨 |

다만 **워터마크 삽입 로직이 아직 없습니다.**

```java
// CourseService.java:67
// 워터마크 처리는 아직 없으므로 원본을 썸네일로도 사용한다.
course.updateAssets(storedName, storedName);
```

`Course` 엔티티에 `watermark_url` 컬럼과 `updateWatermarkUrl()`은 있으나 호출부가 없습니다.

---

## 2. 요청 사항

### 2-1. 업로드 시 두 벌을 만들어 주세요

```
POST /api/courses/{id}/asset
Content-Type: multipart/form-data
Header: X-User-Id  (Gateway 주입)
Part:   file  (image/png | image/jpeg | image/webp, 10MB 이하)
```

서버에서 원본 1장을 받아 **2개 파일**로 저장:

| 파일 | 용도 | 워터마크 | 노출 대상 |
|---|---|---|---|
| `original_url` | 판매용 원본 | 비가시적만 | 구매자·소유자 |
| `watermark_url` | 미리보기 | 가시적 + 비가시적 | 전체 공개 |

지금은 `original_url`과 `thumbnail_url`에 같은 파일명이 들어가 있어서
**구매자도 워터마크본을 받게 됩니다.** 이 분리가 핵심입니다.

### 2-2. 응답 필드

프론트는 아래 두 필드로 이미지 표시 여부를 판단합니다. **둘 다 필수입니다.**

```json
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 7,
    "title": "미니멀 로고 세트",
    "category": "FRONTEND",
    "price": 40000,
    "instructorId": 5,
    "enrollmentCount": 0,
    "status": "ACTIVE",
    "createdAt": "2026-08-07T09:12:11",

    "hasAsset": true,
    "thumbnailUrl": "/api/courses/7/asset/preview",
    "downloadCount": 0
  }
}
```

- `hasAsset` — `false`면 프론트가 미리보기 요청을 아예 보내지 않고 카테고리 기본 이미지로 갑니다. 불필요한 404를 막는 용도입니다.
- `thumbnailUrl` — 워터마크본 경로. 저장 위치가 S3로 바뀌어도 프론트는 이 값만 쓰므로 코드 변경이 없습니다.

목록(`GET /api/courses`)과 상세(`GET /api/courses/{id}`) **응답에도 동일하게 포함**되어야 합니다. 목록에서 카드마다 썸네일을 그리기 때문입니다.

### 2-3. 미리보기 응답

```
GET /api/courses/{id}/asset/preview
→ 200, Content-Type: image/png (또는 image/jpeg)
   바디는 이미지 바이트 (JSON 래퍼 없이)
```

자산이 없으면 **404**를 주세요. 현재는 500이 나옵니다.

### 2-4. 원본 다운로드 권한

```
GET /api/courses/{id}/asset/download
Header: X-User-Id
```

- 소유자(`instructorId == X-User-Id`) 또는 구매자(enrollment ACTIVE) → 200
- 그 외 → **403**

프론트는 403을 "구매한 사용자만 내려받을 수 있습니다"로 안내합니다.

---

## 3. 워터마크 구현 시 참고

### 가시적 (미리보기용)

- 디자이너 이름 또는 `DesignMarket #{id}`를 30도 기울여 반복 배치
- 불투명도 0.3 안팎 — 구도·색감은 확인 가능해야 함
- 하단에 `© 이름` 라벨 한 줄
- Java2D `Graphics2D.drawString()` 으로 충분

> ⚠️ **Alpine JRE에는 폰트가 없습니다.** `Dockerfile` 런타임 스테이지에 아래를 넣지 않으면 `drawString`이 실패합니다.
> ```dockerfile
> RUN apk add --no-cache fontconfig ttf-dejavu
> ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
> ```

### 비가시적 (추적용)

- 픽셀 파랑 채널 최하위 1비트에 식별자를 삽입 (LSB 스테가노그래피)
- 페이로드 예: `DMP1|{assetToken}|{ownerId}|{epochSeconds}`
- 채널값이 최대 1만큼 변하므로 육안 식별 불가

**제약 2가지**

1. **무손실 포맷 필수.** JPEG로 저장하면 최하위 비트가 파괴되어 추출 불가. 결과물은 PNG로 저장해야 합니다.
2. **최소 픽셀 수 필요.** 페이로드 길이 × 8 + 64 비트 이상의 픽셀이 있어야 합니다. 짧은 변 400px 이상이면 충분하며, 프론트에서 이미 이 조건을 검증해 걸러내고 있습니다.

---

## 4. 검증 엔드포인트 (선택)

유출 사본의 출처를 확인하는 용도입니다. 우선순위는 낮습니다.

```
POST /api/courses/assets/verify
Content-Type: multipart/form-data
Part: file
```

```json
{
  "success": true,
  "data": {
    "watermarkFound": true,
    "registered": true,
    "designId": 7,
    "ownerId": 5,
    "issuedAt": "2026-08-07T09:12:11",
    "checksumMatched": false,
    "message": "워터마크는 일치하나 파일이 재가공되었습니다."
  }
}
```

---

## 5. 인프라 — 지금 고쳐야 할 것

`docker-compose.yml`의 `course-service`에 **볼륨 마운트가 없습니다.**
`/app/uploads`가 컨테이너 내부라, `docker compose down`이나 재빌드 시 **업로드한 파일이 전부 사라집니다.**
DB의 URL만 남고 실제 파일이 없어 깨진 이미지가 됩니다.

```yaml
  course-service:
    ...
    volumes:
      - course_uploads:/app/uploads

volumes:
  mariadb_data:
  kafka_data:
  course_uploads:
```

---

## 6. 프론트 처리 규약

| 상태 코드 | 프론트 동작 |
|---|---|
| 200 / 201 | 정상 처리 |
| 400 | 서버 `message`를 그대로 사용자에게 표시 |
| 403 | "구매한 사용자만 내려받을 수 있습니다" |
| 404 / 405 | "API가 아직 준비되지 않았습니다" (개발 중 구분용) |
| 413 | "파일 크기가 허용 범위를 초과했습니다" |
| 500 | "서버 오류가 발생했습니다" |

**클라이언트 입력 오류는 400으로 주세요.** 500으로 오면 사용자가 무엇을 고쳐야 할지 알 수 없고, 프론트도 안내 문구를 구분할 수 없습니다.

---

## 7. 프론트 사전 검증 (참고)

서버 부하를 줄이려고 프론트에서 미리 거르는 조건입니다. 서버에서도 동일하게 검증해 주세요.

- 형식: PNG / JPEG / WEBP
- 용량: 10MB 이하
- 해상도: 짧은 변 400px 이상
