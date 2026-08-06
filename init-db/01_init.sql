-- 온라인 강의 플랫폼 초기 DDL
-- Spring JPA ddl-auto: update 로도 생성되지만
-- 명시적 DDL로 테이블 선후 관계를 문서화

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL COMMENT 'STUDENT | INSTRUCTOR',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 강사가 강의 개설 (instructor_id → users.id)
CREATE TABLE IF NOT EXISTS courses (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(255)    NOT NULL,
    description      TEXT,
    category         VARCHAR(50)     NOT NULL COMMENT 'BACKEND|FRONTEND|DEVOPS|DATA_SCIENCE|MOBILE|SECURITY|DATABASE|OTHER',
    price            DECIMAL(10,2)   NOT NULL,
    instructor_id    BIGINT          NOT NULL,
    enrollment_count INT             NOT NULL DEFAULT 0,
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (instructor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수강생이 수강 신청 (user_id → users.id, course_id → courses.id)
CREATE TABLE IF NOT EXISTS enrollments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    course_id   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | ACTIVE | CANCELLED',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_course (user_id, course_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수강 확정 후 결제 (user_id → users.id, course_id → courses.id)
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    course_id       BIGINT          NOT NULL,
    amount          DECIMAL(10,2)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | COMPLETED | FAILED | CANCELLED',
    transaction_id  VARCHAR(255)    UNIQUE,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 디자인 자산 (원본 이미지 바이너리 + 워터마크본)
-- course_id → courses.id (1:1). FK 없이 ID만 보관 — 서비스별 DB 분리를 고려
CREATE TABLE IF NOT EXISTS design_assets (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    course_id         BIGINT        NOT NULL,
    owner_id          BIGINT        NOT NULL,
    asset_token       VARCHAR(36)   NOT NULL COMMENT '워터마크에 삽입되는 추적용 UUID',
    original_filename VARCHAR(255)  NOT NULL,
    content_type      VARCHAR(100)  NOT NULL,
    width             INT           NOT NULL,
    height            INT           NOT NULL,
    file_size         BIGINT        NOT NULL,
    checksum          VARCHAR(64)   NOT NULL COMMENT '워터마크본의 SHA-256',
    watermark_payload VARCHAR(255)  NOT NULL,
    watermarked_data  LONGBLOB      NOT NULL COMMENT 'LSB 워터마크 삽입된 판매본 (PNG)',
    preview_data      LONGBLOB      NOT NULL COMMENT '가시적 워터마크 미리보기 (PNG)',
    download_count    BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_design_asset_course (course_id),
    KEY idx_design_asset_token (asset_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
