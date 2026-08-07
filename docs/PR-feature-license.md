# [프론트] 라이선스 구매 · 구독 · 판매 대시보드 연동

`feature/license_front` → `dev`

---

## 요약

라이선스 등급별 구매 플로우, 디자이너 구독(월 정액 할인), 판매 대시보드, 구매자별 워터마크 추적을 프론트-백엔드 전 구간에 걸쳐 연결하고, 다른 페어 브랜치(`feature/subscription-check`, `fix/detailpage`)를 합치는 과정에서 생긴 충돌·누락·중복을 정리했습니다.

---

## 주요 변경

### 1. 라이선스 등급 구매 (course-service / enrollment-service / vue-frontend)

- 디자인 상세 화면에 라이선스 등급(개인용 / 상업용 소규모 / 상업용 대규모) 선택 UI 복원. 프론트 병합 과정에서 템플릿 블록 자체가 통째로 빠져 있던 것을 발견해 되살렸습니다.
- 구매 요청이 등급 문자열(`PERSONAL` 등)이 아니라 실제 `license_tiers.id`(숫자)를 보내도록 수정 — `EnrollRequest.licenseTier`는 이름과 달리 등급 레코드의 PK를 기대하는데, 병합 이후 문자열 코드를 그대로 보내고 있었습니다.
- `course-service`에 `GET/POST /courses/{id}/license-tiers` 라우팅 복원 (서비스 로직은 있었으나 컨트롤러 매핑이 병합 중 유실됨).

### 2. 디자이너 구독 (payment-service / enrollment-service / vue-frontend)

- 구독 = 월 9,900원 고정 / 전체 30% 할인 정책으로 통일 (`SubscriptionController`, `SubscriptionService`).
- 게이트웨이가 `/api/{users,courses,enrollments,payments,recommend}/**`만 라우팅하도록 고정돼 있어 `/api/subscriptions/**`가 전부 404였던 문제 발견 → `SubscriptionController` 매핑을 `/api/payments/subscriptions`로 이동. 이에 맞춰 `enrollment-service`(할인 확인)와 `course-service`(구독자 수 조회) 내부 호출, 프론트 `subscription.js` 경로 동기화.
- 취소/만료 후 재구독 시 `(user_id, designer_id)` 유니크 제약 위반으로 500이 나던 버그 수정 — 기존 row가 있으면 새로 insert하지 않고 재활성화(`resubscribe()`)하도록 변경.
- `Payment` 엔티티에 `@DynamicInsert` 적용 — 구독 결제는 `courseId`/`licenseTierId`가 없는데, 값이 없는 컬럼도 INSERT 문에 그대로 바인딩되면서 스키마 오류가 반복돼 우회.

### 3. 구매자별 워터마크 추적 (watermark-service)

- 구매 확정(`purchase.completed`) 이벤트를 받아 구매자 전용 워터마크 사본을 생성·저장하고, 다운로드 시 원본 대신 그 사본을 내려주는 파이프라인 확인 (신규 구현 아님 — `fix/detailpage` 병합분, 정상 동작 재검증만 수행).
- 유출본 역추적(`/api/watermark/internal/trace`) 엔드포인트 포함.

### 4. 판매 대시보드 / 마이페이지 (course-service / vue-frontend)

- `CourseService`/`CourseDto`에 병합 충돌로 남아있던 중복 클래스·메서드 정리(`SalesDashboardResponse`, `LicenseTierResponse` 등 중복 선언 제거) — 컴파일 자체가 안 되던 상태였습니다.
- 마이페이지 구매자 추천 문구를 카테고리 조합형에서 고정 문구로 단순화.
- (설계 변경 이력) 초기에 "디자이너별 구독가 설정" UI를 마이페이지에 추가했다가, 실제 백엔드가 고정 정책이라는 걸 뒤늦게 확인하고 전면 제거했습니다.

### 5. 디버깅 편의성

- `payment-service`의 공용 예외 핸들러가 모든 예외를 "서버 오류가 발생했습니다"로 뭉개고 로그를 남기지 않아 원인 파악이 어려웠던 부분에 `log.error(e)` 추가.

---

## 리뷰 시 봐주셨으면 하는 부분

### DB — 반드시 확인 필요

**`subscriptions` 테이블에 지금 엔티티에 없는 레거시 컬럼이 남아있습니다.**

`license_tier_id`, `plan_code`(둘 다 NOT NULL)가 예전 설계(디자이너별 구독 플랜) 흔적으로 남아있어, 구독 시도 시 500이 발생합니다. `ddl-auto: update`가 컬럼을 자동으로 정리해주지 않기 때문에, 이 브랜치를 pull 받는 모든 팀원이 로컬 DB에 아래를 직접 실행해야 합니다.

```bash
docker exec -it lecturedb mariadb -u manager -pSqlDba-1 lecture_db -e "
ALTER TABLE subscriptions MODIFY COLUMN license_tier_id BIGINT NULL;
ALTER TABLE subscriptions MODIFY COLUMN plan_code ENUM('BASIC','PRO') NULL;
ALTER TABLE payments MODIFY COLUMN course_id BIGINT NULL;
ALTER TABLE payments MODIFY COLUMN license_tier_id BIGINT NULL;
"
```

> **팀 논의 필요** — 임시로 로컬에서 ALTER로 우회했는데, 정식 마이그레이션 스크립트(`init-db` 또는 별도 migration 파일)로 남길지, 아니면 컬럼 자체를 DROP할지 정해야 합니다. 지금 상태로는 신규 환경(CI, 팀원 로컬)마다 같은 500을 반복해서 만납니다.

### 알려진 이슈

- **찜하기(Wishlist) API 미구현** — 엔티티(`Wishlist.java`)만 있고 컨트롤러/서비스/레포지토리가 없습니다.
- **라이선스 문서 확인 기능 없음** — 상세 화면의 고정 안내 문구 3줄만 있고, 실제 증빙 문서·다운로드 기능은 없습니다.
- **`GlobalExceptionHandler`의 catch-all 패턴이 course-service에도 동일하게 있습니다.** payment-service만 로깅을 추가했고, course-service는 아직 그대로라 원인 불명 500이 나면 똑같이 로그가 안 남습니다.
- **게이트웨이가 사전 빌드 이미지라 라우팅 테이블을 직접 못 고칩니다.** 이번엔 컨트롤러 경로를 게이트웨이 라우팅에 맞춰 옮기는 쪽으로 우회했는데, 앞으로 새 prefix가 필요한 기능이 추가되면 같은 문제가 반복됩니다.

---

## 테스트

- [x] 라이선스 등급 선택 → 가격 반영 → 구매 요청 (실제 `license_tiers.id` 전송 확인)
- [x] 구독 후 새로고침 시 할인가 반영
- [x] 구독 재시도(취소 후 재구독) 500 재현 및 수정 확인
- [x] course-service 컴파일 오류(중복 클래스) 정리 후 관련 엔드포인트 라우팅 확인
- [ ] `./gradlew compileJava` 로컬 재검증 필요 (샌드박스에 컴파일러가 없어 직접 빌드 검증은 못 했습니다)
- [ ] 구매자별 워터마크 다운로드 실경로 재확인
- [ ] 위 DB 컬럼 정리를 마이그레이션 파일로 전환

## 배포 시 주의

```bash
docker compose up -d --build payment-service enrollment-service course-service
```

**위 "DB — 반드시 확인 필요" 섹션의 ALTER 문을 먼저 실행하지 않으면 구독 기능이 500으로 실패합니다.**
