# [페어 1] 디자인 자산 파이프라인 — 업로드 · 워터마크 · 탐색

`feature/watermark` → `dev`

---

## 요약

디자인 이미지를 업로드하면 워터마크가 삽입된 두 벌(판매용 원본 / 공개 미리보기)로 저장되고,
목록·상세 화면에서 미리보기가 표시되도록 자산 파이프라인 전체를 연결했습니다.
함께 목록 검색·정렬 UI를 추가했습니다.

---

## 주요 변경

### 1. 워터마크 (course-service)

`WatermarkService` 신규. 업로드 1장을 받아 두 벌로 가공합니다.

| 산출물 | 워터마크 | 노출 대상 |
|---|---|---|
| `original_url` | 비가시적만 | 구매자 · 소유자 |
| `watermark_url` | 가시적 + 비가시적 | 전체 공개 |

- **가시적** — 반투명 텍스트를 30도 기울여 반복 배치 + 하단 저작권 라벨
- **비가시적** — 픽셀 파랑 채널 최하위 비트에 `DMP1|{courseId}|{ownerId}|{epoch}` 삽입 (LSB)

구현상 지켜야 했던 제약 두 가지:

- **결과물은 항상 PNG.** JPEG로 재인코딩하면 최하위 비트가 파괴되어 비가시적 워터마크를 추출할 수 없습니다.
- **가시적 → 비가시적 순서.** 가시적 처리가 픽셀을 바꾸므로 LSB는 반드시 그 뒤에 심어야 합니다.

원본 체크섬(SHA-256)을 `courses.asset_checksum`에 함께 저장해, 유출 사본이 재가공됐는지 판별할 수 있게 했습니다.

### 2. 자산 API

| 메서드 | 경로 | 권한 |
|---|---|---|
| POST | `/api/courses/{id}/asset` | 소유자 |
| GET | `/api/courses/{id}/asset/preview` | 공개 |
| GET | `/api/courses/{id}/asset/download` | 구매자 · 소유자 |
| POST | `/api/courses/assets/verify` | 공개 |

- `EnrollmentServiceClient`로 구매 여부를 확인해 다운로드 권한을 판정합니다.
- `AssetNotFoundException`(404) / `AssetAccessDeniedException`(403)을 분리해, 클라이언트 입력 문제가 500으로 나가지 않도록 정리했습니다.

### 3. 프론트엔드

- **업로드 UI** (`CourseCreateView`) — 드래그앤드롭 · 로컬 미리보기 · 진행률 · 형식/용량/해상도 사전 검증
- **미리보기 표시** (`CourseCard`, `CourseDetailView`) — `useAssetImage` 컴포저블로 blob 로딩 후 objectURL 표시. 자산이 없으면 카테고리 기본 이미지로 폴백
- **상세 화면** — 소유자용 파일 교체, 구매자용 원본 다운로드
- **목록 검색·정렬** (`CourseListView`) — 키워드 검색 + 정렬 5종(최신 · 인기 · 가격↑↓ · 이름) + 결과 개수

### 4. 문서

- `docs/api-contract.md` — 4개 핵심 엔드포인트 요청/응답 스펙, 미결 사항 7건
- `docs/watermark-api-spec.md` — 워터마크 관련 계약 및 구현 제약

---

## 리뷰 시 봐주셨으면 하는 부분

### 인프라 — 반드시 확인 필요

**1. `course-service` 볼륨 마운트가 없습니다.**

`/app/uploads`가 컨테이너 내부라, `docker compose down`이나 재빌드 시 업로드 파일이 전부 사라집니다.
DB에는 URL이 남는데 실제 파일이 없어 깨진 이미지가 됩니다.

```yaml
  course-service:
    volumes:
      - course_uploads:/app/uploads
volumes:
  course_uploads:
```

**2. Alpine JRE 폰트 (`Dockerfile`에 반영 완료)**

Alpine JRE에는 폰트가 없어 `Graphics2D.drawString()`이 실패합니다.
`apk add fontconfig ttf-dejavu` + `-Djava.awt.headless=true`가 없으면 가시적 워터마크를 그릴 수 없습니다.

**3. Maven Central 429 우회 (`course-service`에만 반영)**

빌드 중 `repo.maven.apache.org`가 429를 반환해 진행이 막혔습니다.
Google Maven Central 미러를 우선 저장소로 추가하고, Gradle 홈을 BuildKit 캐시로 마운트했습니다.

- `settings.gradle`의 `pluginManagement`까지 함께 고쳐야 합니다. 플러그인 클래스패스는 여기서 해석되므로 `build.gradle`만 바꾸면 `spring-boot-gradle-plugin` 해석에서 그대로 실패합니다.
- 캐시 마운트가 없으면 빌드 실패 시 받아둔 jar가 통째로 버려져, 재시도해도 매번 처음부터 받게 됩니다.

**나머지 4개 서비스(`user`, `enrollment`, `payment`, `eureka`)에는 아직 미적용**입니다. 재빌드하는 팀원은 같은 문제를 겪습니다.

**4. MariaDB 호스트 포트 (3379 / 3380) — `docker-compose.yml`에 경고 주석 추가**

이전 스프링부트 수업의 MariaDB 컨테이너가 3379를 점유하고 있으면 `port is already allocated`로 기동에 실패합니다. 빌드 전에 확인하도록 파일 상단에 안내를 넣었습니다.

```bash
lsof -i :3379 ; lsof -i :3380
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

포트를 바꿔도 서비스 간 통신은 내부 네트워크의 `mariadb:3306`을 쓰므로 애플리케이션 설정은 손댈 필요가 없습니다. Workbench 접속 포트만 달라집니다.

> **팀 논의 필요** — 현재 커밋된 값이 `3380`입니다. pull 받는 팀원은 3380으로 뜨게 되므로, 기본값을 3379로 되돌릴지 3380으로 통일할지 정해야 합니다. 각자 환경이 다르면 로컬에서만 바꾸고 커밋하지 않기로 합의하는 편이 낫습니다.

### 알려진 이슈

- **`SalesTab.vue`가 `item.id`를 읽는데 백엔드는 `courseId`로 내려줍니다.** 링크나 `:key`에 쓰고 있다면 동작하지 않습니다.
- **`CourseResponse`에 디자이너 이름이 없습니다.** 상세 화면에 "디자이너 정보 없음"으로 표시됩니다. user-service 연동 또는 필드 추가가 필요합니다.
- **미리보기 인증 정책이 정리되지 않았습니다.** 컨트롤러 주석은 "인증 없이 접근 가능"인데 게이트웨이가 막고 있어, 프론트는 blob으로 우회하고 있습니다. `<img src>`로 직접 쓰려면 게이트웨이 설정이 필요합니다.
- **검색·정렬은 현재 프론트에서 처리**합니다. 데이터가 늘면 `GET /api/designs?keyword=&sort=`로 서버 이관이 필요합니다.
- **`assets/verify` 엔드포인트는 프론트에 아직 연결되지 않았습니다.**

---

## 테스트

- [x] 디자인 등록 → 파일 업로드 → 목록·상세에 미리보기 표시
- [x] 자산 미등록 시 카테고리 기본 이미지로 폴백
- [x] 형식·용량·해상도 사전 검증 동작
- [x] 소유자 파일 교체
- [ ] 구매자 원본 다운로드 (구매 플로우 연동 후)
- [ ] 워터마크 추출·검증
- [ ] 서비스 재시작 후 파일 유지 (볼륨 마운트 적용 후)

## 배포 시 주의

```bash
docker compose build course-service
docker compose up -d course-service
```

`build`만 하면 기존 컨테이너가 옛 이미지로 계속 돕니다.
**이 PR 이전에 업로드된 파일에는 워터마크가 없으므로 재업로드가 필요합니다.**
