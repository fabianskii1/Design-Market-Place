-- ============================================================================
-- 디자인 마켓플레이스 — 라이선스 / 가격 / 구매 / 구독 / 위시리스트
--
--   요구사항 1) 구매와 구독을 각각 별도 테이블로 관리한다.
--   요구사항 2) 가격은 라이선스 등급별로 전부 등록한다.
--
-- 이 스크립트는 신규 볼륨(01_init.sql 직후)과 기존 볼륨 양쪽에서 동작한다.
-- ============================================================================

-- 이전 버전에서 만든 동명의 뷰가 있다면 제거 (테이블로 대체)
DROP VIEW IF EXISTS purchases;
DROP VIEW IF EXISTS designs;


-- ─────────────────────────────────────────────────────────────
-- 1. 라이선스 등급 (마스터)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS license_tiers (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    code                  VARCHAR(30)  NOT NULL COMMENT 'PERSONAL | COMMERCIAL | EXTENDED',
    name                  VARCHAR(50)  NOT NULL,
    description           VARCHAR(255),
    allows_commercial     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '상업적 이용 가능 여부',
    allows_redistribution BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '재배포·2차 판매 가능 여부',
    max_end_products      INT                   COMMENT '제작 가능한 최종 결과물 수 (NULL = 무제한)',
    sort_order            INT          NOT NULL DEFAULT 0,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_license_tier_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO license_tiers
    (code, name, description, allows_commercial, allows_redistribution, max_end_products, sort_order, created_at, updated_at)
SELECT * FROM (
    SELECT 'PERSONAL'   AS code, '개인용'   AS name,
           '개인 프로젝트·포트폴리오 등 비영리 목적에 한해 사용할 수 있습니다.' AS description,
           FALSE AS ac, FALSE AS ar, 1    AS mep, 1 AS so, NOW(6) AS c, NOW(6) AS u
    UNION ALL SELECT 'COMMERCIAL', '상업용', '광고·제품·서비스 등 영리 목적으로 사용할 수 있습니다.',
           TRUE,  FALSE, 10,   2, NOW(6), NOW(6)
    UNION ALL SELECT 'EXTENDED',   '확장',   '재판매 가능한 템플릿·굿즈 등에 포함해 배포할 수 있습니다.',
           TRUE,  TRUE,  NULL, 3, NOW(6), NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM license_tiers);


-- ─────────────────────────────────────────────────────────────
-- 2. 가격 — 디자인 × 라이선스 등급
--
--    한 디자인이 등급마다 다른 가격을 가지므로 1:N 이다.
--    courses.price 단일 컬럼으로는 표현할 수 없어 별도 테이블로 분리한다.
--    디자인 등록 시 활성 등급 전부에 대해 가격을 입력해야 하며,
--    등급 수는 운영 중 늘어날 수 있으므로 서비스 계층에서 검증한다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS design_prices (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    design_id       BIGINT        NOT NULL COMMENT 'courses.id',
    license_tier_id BIGINT        NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'KRW',
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_design_price (design_id, license_tier_id),
    KEY idx_design_price_design (design_id),
    CONSTRAINT fk_design_price_design       FOREIGN KEY (design_id)       REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_design_price_license_tier FOREIGN KEY (license_tier_id) REFERENCES license_tiers(id),
    CONSTRAINT ck_design_price_non_negative CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- courses.price 는 삭제하지 않는다. 목록 정렬·필터용 '대표가(최저 라이선스 가격)' 캐시로 남긴다.
-- 결제 금액의 기준은 언제나 design_prices 이며, courses.price 를 결제에 사용하지 않는다.
ALTER TABLE courses
    MODIFY COLUMN price DECIMAL(10,2) NOT NULL
    COMMENT '대표가(최저 라이선스 가격) 캐시. 결제 기준 아님 — design_prices 참조';

-- 기존 데이터가 있으면 개인용 가격으로 이관
INSERT IGNORE INTO design_prices (design_id, license_tier_id, price, created_at, updated_at)
SELECT c.id, lt.id, c.price, NOW(6), NOW(6)
FROM courses c
CROSS JOIN license_tiers lt
WHERE lt.code = 'PERSONAL';


-- ─────────────────────────────────────────────────────────────
-- 3. 구매 (1회성) — 독립 테이블
--
--    기존 enrollments 를 purchases 로 승격시킨다.
--    컬럼명(user_id, course_id)은 그대로 두어 enrollment-service 코드 변경을
--    @Table(name = "purchases") 한 줄로 끝낼 수 있게 한다.
-- ─────────────────────────────────────────────────────────────

-- 3-1. enrollments 가 있고 purchases 가 없으면 이름을 바꾼다 (데이터 보존)
SET @has_enrollments := (SELECT COUNT(*) FROM information_schema.TABLES
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments'
                           AND TABLE_TYPE = 'BASE TABLE');
SET @has_purchases   := (SELECT COUNT(*) FROM information_schema.TABLES
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchases'
                           AND TABLE_TYPE = 'BASE TABLE');
SET @stmt := IF(@has_enrollments = 1 AND @has_purchases = 0,
                'RENAME TABLE enrollments TO purchases',
                'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 3-2. 둘 다 없는 경우(완전 신규)를 위한 생성
CREATE TABLE IF NOT EXISTS purchases (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT '구매자',
    course_id  BIGINT      NOT NULL COMMENT '디자인 (courses.id)',
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | ACTIVE | CANCELLED',
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_course (user_id, course_id),
    CONSTRAINT fk_purchase_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_purchase_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3-3. 라이선스 정보 추가
--      구매 시점의 라이선스와 지불 금액을 함께 못 박아 둔다.
--      정책이나 가격이 나중에 바뀌어도 과거 구매자의 권리는 유지되어야 한다.
ALTER TABLE purchases
    ADD COLUMN IF NOT EXISTS license_tier_id BIGINT NULL        COMMENT '구매한 라이선스 등급'   AFTER course_id,
    ADD COLUMN IF NOT EXISTS paid_amount     DECIMAL(10,2) NULL COMMENT '구매 시점의 지불 금액' AFTER license_tier_id,
    ADD COLUMN IF NOT EXISTS source          VARCHAR(20) NOT NULL DEFAULT 'PURCHASE'
        COMMENT 'PURCHASE(개별 구매) | SUBSCRIPTION(구독 다운로드)'                              AFTER paid_amount;

ALTER TABLE purchases
    ADD CONSTRAINT IF NOT EXISTS fk_purchase_license_tier
    FOREIGN KEY (license_tier_id) REFERENCES license_tiers(id);

-- 3-4. 전환 브리지
--      enrollment-service 의 @Table(name) 을 아직 바꾸지 않았어도 동작하도록
--      동명의 갱신 가능 뷰를 남긴다. 단일 테이블 기반이라 INSERT/UPDATE 가 그대로 전달된다.
--      코드 수정이 끝나면 이 뷰는 삭제할 것.
CREATE OR REPLACE VIEW enrollments AS
SELECT id, user_id, course_id, license_tier_id, paid_amount, source, status, created_at, updated_at
FROM purchases;


-- ─────────────────────────────────────────────────────────────
-- 4. 구독 (기간제) — 독립 테이블
--
--    구매와 분리한 이유:
--    구매는 한 번 성립하면 영구하지만, 구독은 기간·한도·갱신·해지라는
--    상태 전이를 갖는다. 한 테이블에 담으면 절반이 NULL 컬럼이 된다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscriptions (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    plan_code              VARCHAR(30)   NOT NULL COMMENT 'BASIC | PRO',
    license_tier_id        BIGINT        NOT NULL COMMENT '구독 다운로드에 적용될 라이선스',
    status                 VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                                         COMMENT 'ACTIVE | CANCELLED | EXPIRED | PAST_DUE',
    monthly_price          DECIMAL(10,2) NOT NULL,
    monthly_download_limit INT                    COMMENT '월 다운로드 한도 (NULL = 무제한)',
    downloads_used         INT           NOT NULL DEFAULT 0 COMMENT '현재 주기 사용량',
    started_at             DATETIME(6)   NOT NULL,
    current_period_start   DATETIME(6)   NOT NULL,
    current_period_end     DATETIME(6)   NOT NULL,
    auto_renew             BOOLEAN       NOT NULL DEFAULT TRUE,
    cancelled_at           DATETIME(6),
    created_at             DATETIME(6),
    updated_at             DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_subscription_user_status (user_id, status),
    KEY idx_subscription_period_end (current_period_end),
    CONSTRAINT fk_subscription_user         FOREIGN KEY (user_id)         REFERENCES users(id),
    CONSTRAINT fk_subscription_license_tier FOREIGN KEY (license_tier_id) REFERENCES license_tiers(id),
    CONSTRAINT ck_subscription_period       CHECK (current_period_end > current_period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 주의: '사용자당 ACTIVE 구독 1건'은 MySQL/MariaDB에 부분 유니크 인덱스가 없어
--      DB 제약으로 표현할 수 없다. 서비스 계층에서 검증한다.

-- 구독으로 내려받은 이력 (한도 차감 및 중복 차감 방지)
CREATE TABLE IF NOT EXISTS subscription_downloads (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    course_id       BIGINT      NOT NULL COMMENT '디자인 (courses.id)',
    period_start    DATETIME(6) NOT NULL COMMENT '차감된 정산 주기',
    created_at      DATETIME(6),
    PRIMARY KEY (id),
    -- 같은 주기 안에서 같은 디자인을 다시 받아도 한도를 두 번 차감하지 않는다
    UNIQUE KEY uq_subscription_download (subscription_id, course_id, period_start),
    KEY idx_subscription_download_user (user_id),
    CONSTRAINT fk_sub_download_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_download_course       FOREIGN KEY (course_id)       REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ─────────────────────────────────────────────────────────────
-- 5. 위시리스트
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wishlist (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    course_id  BIGINT NOT NULL COMMENT '디자인 (courses.id)',
    created_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_wishlist_user_course (user_id, course_id),
    KEY idx_wishlist_user (user_id),
    CONSTRAINT fk_wishlist_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ─────────────────────────────────────────────────────────────
-- 6. 조회 편의 뷰 — 디자인 + 최저가
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW design_catalog AS
SELECT c.id                AS design_id,
       c.title,
       c.description,
       c.category,
       c.instructor_id     AS designer_id,
       c.enrollment_count  AS sales_count,
       c.status,
       MIN(dp.price)       AS min_price,
       MAX(dp.price)       AS max_price,
       COUNT(dp.id)        AS price_tier_count,
       c.created_at
FROM courses c
LEFT JOIN design_prices dp ON dp.design_id = c.id AND dp.active = TRUE
GROUP BY c.id;
