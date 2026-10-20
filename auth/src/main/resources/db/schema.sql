-- 인증 서버 테이블. 시작할 때 실행된다 (IF NOT EXISTS라 이미 있으면 건너뛴다). 구조를 바꾸려면 마이그레이션 도구(Flyway 등)로 옮긴다.
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt 해시 (60자). 비밀번호 원문은 어디에도 저장하지 않는다
    name          VARCHAR(30)  NOT NULL,
    role          VARCHAR(10)  NOT NULL,   -- USER, ADMIN
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)      -- 이메일 중복 가입을 DB가 마지막으로 막는다 (동시 가입 경쟁)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
