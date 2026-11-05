-- =====================================================================
-- V1：初始表结构。同一份脚本在 H2（MySQL 兼容模式）和 MySQL 8 上都能执行。
--
-- 约定：
-- 1. 每个模块只读写自己的表（表名前缀区分模块），模块之间不建外键、不跨模块 JOIN：
--    将来按模块拆成独立服务、独立数据库时，表可以原样搬走。
-- 2. 时间列统一存 UTC（应用层用 Instant，仓储里显式按 UTC 转换），SQL 里不用 NOW()。
-- 3. 为了两种数据库都兼容，没写 ENGINE / CHARSET / COMMENT 子句（MySQL 8 默认就是 InnoDB + utf8mb4），
--    字段说明写在 SQL 注释里。
-- =====================================================================

-- ---------- 用户模块 ----------
-- 角色
CREATE TABLE sys_role (
    code VARCHAR(32) NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL
);

-- 角色拥有的权限码；'*' 表示全部权限
CREATE TABLE sys_role_permission (
    role       VARCHAR(32) NOT NULL,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (role, permission)
);

-- 用户。deleted：0 = 未删除；逻辑删除时写成自己的 id，这样 (username, deleted) 唯一索引不会挡住同名新用户
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    email         VARCHAR(128) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    password_hash VARCHAR(100),
    deleted       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    CONSTRAINT uk_sys_user_username UNIQUE (username, deleted)
);

-- ---------- 认证模块 ----------
-- 登录会话：一次登录一个会话，刷新换出来的 refresh token 都挂在它下面
CREATE TABLE auth_session (
    id            CHAR(36)    NOT NULL PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    status        VARCHAR(16) NOT NULL,
    revoke_reason VARCHAR(32),
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL
);
CREATE INDEX idx_auth_session_user ON auth_session (user_id, status);

-- refresh token：只存 SHA-256 哈希，不存原文
CREATE TABLE auth_refresh_token (
    token_hash CHAR(64)    NOT NULL PRIMARY KEY,
    session_id CHAR(36)    NOT NULL,
    status     VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    used_at    DATETIME(3)
);
CREATE INDEX idx_refresh_token_session ON auth_refresh_token (session_id, status);

-- ---------- 商品模块 ----------
CREATE TABLE product (
    id         BIGINT        NOT NULL PRIMARY KEY,
    name       VARCHAR(128)  NOT NULL,
    price      DECIMAL(12, 2) NOT NULL,
    status     VARCHAR(16)   NOT NULL,
    created_at DATETIME(3)   NOT NULL,
    updated_at DATETIME(3)   NOT NULL
);

-- ---------- 库存模块 ----------
-- 库存三列：可售 + 预占 + 已售 = 总库存。CHECK 约束是数据库层的最后一道防线：任何 bug 都不能把可售库存扣成负数
-- （但它防不住"丢失更新"那种超卖，见 docs 里的超卖实验）
CREATE TABLE inventory (
    sku_id     BIGINT      NOT NULL PRIMARY KEY,
    available  INT         NOT NULL,
    reserved   INT         NOT NULL DEFAULT 0,
    sold       INT         NOT NULL DEFAULT 0,
    version    INT         NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT ck_inventory_available CHECK (available >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0)
);

-- 预占记录：哪张订单占了哪个商品多少件；确认、释放都按订单号找它
CREATE TABLE inventory_reservation (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no   BIGINT      NOT NULL,
    sku_id     BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_reservation_order_sku UNIQUE (order_no, sku_id)
);

-- ---------- 订单模块 ----------
-- 订单号是雪花 ID，由应用生成。(user_id, idempotency_key) 唯一：同一个下单请求最多生成一张订单
CREATE TABLE orders (
    order_no        BIGINT         NOT NULL PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    total_amount    DECIMAL(12, 2) NOT NULL,
    idempotency_key VARCHAR(64)    NOT NULL,
    request_hash    CHAR(64)       NOT NULL,
    cancel_reason   VARCHAR(16),
    created_at      DATETIME(3)    NOT NULL,
    paid_at         DATETIME(3),
    cancelled_at    DATETIME(3),
    updated_at      DATETIME(3)    NOT NULL,
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key)
);
-- 超时扫描：WHERE status = ? AND created_at < ? ORDER BY created_at
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
-- 我的订单：WHERE user_id = ? ORDER BY created_at DESC
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);

CREATE TABLE order_line (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no     BIGINT         NOT NULL,
    sku_id       BIGINT         NOT NULL,
    product_name VARCHAR(128)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL
);
CREATE INDEX idx_order_line_order ON order_line (order_no);
