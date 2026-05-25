DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    email       VARCHAR(100) NOT NULL,
    age         INT          NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    role        VARCHAR(20)  NOT NULL DEFAULT 'viewer',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
