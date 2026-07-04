-- 원장 테이블: 당첨 기록
CREATE TABLE IF NOT EXISTS winner (
    id         BIGSERIAL    PRIMARY KEY,
    event_id   VARCHAR(64)  NOT NULL,
    user_id    VARCHAR(64)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_winner_event_user UNIQUE (event_id, user_id)
);

-- 접수 기록 테이블: 게이트 카운터 복구용
CREATE TABLE IF NOT EXISTS apply_log (
    id         BIGSERIAL    PRIMARY KEY,
    event_id   VARCHAR(64)  NOT NULL,
    user_id    VARCHAR(64)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
