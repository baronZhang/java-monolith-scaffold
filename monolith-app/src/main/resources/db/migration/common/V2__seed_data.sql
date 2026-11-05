-- =====================================================================
-- V2：种子数据。由 scripts/generate_seed_sql.py 生成，不要手改。
-- 用户数据与 Vue 项目 mock/data.ts 一致（同一个伪随机算法和种子），前端切到真实后端后看到的数据不变。
-- 只有三个演示账号有密码（与 Mock 的 DEMO_PASSWORD 相同），这里存的是 BCrypt 哈希，不是明文。
-- 时间都是 UTC。
-- =====================================================================

INSERT INTO sys_role (code, name) VALUES ('admin', '管理员'), ('editor', '运营'), ('guest', '访客');

-- admin 的 '*' 表示全部权限；editor 能看用户列表、能启用/禁用；guest 没有任何权限
INSERT INTO sys_role_permission (role, permission) VALUES
    ('admin', '*'),
    ('editor', 'user:list'),
    ('editor', 'user:edit');

INSERT INTO sys_user (id, username, nickname, email, role, status, password_hash, deleted, created_at, updated_at) VALUES
    (1, 'admin', '管理员', 'admin@example.com', 'admin', 'active', '{bcrypt}$2a$10$C/ZQ.rbplvhdJ4s6DDoEFeuV6tt0brZezyArxuTshOxcNcBZ94yje', 0, '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (2, 'editor', '运营小王', 'editor@example.com', 'editor', 'active', '{bcrypt}$2a$10$6FKZ.8iPIjjg.lMx64dAF.Fmv.muMkOR8n03W3whSf44XnA7L7n92', 0, '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (3, 'guest', '访客', 'guest@example.com', 'guest', 'active', '{bcrypt}$2a$10$yTsJ5TAD4kME2Xur77yHUuVPrbEsHsgF4uYXGxlQJnF2klqGlg0K2', 0, '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (4, 'user004', '陈勇', 'user4@example.com', 'guest', 'active', NULL, 0, '2026-01-07 01:00:00', '2026-01-07 01:00:00'),
    (5, 'user005', '杨芳艳', 'user5@example.com', 'guest', 'active', NULL, 0, '2026-01-08 13:00:00', '2026-01-08 13:00:00'),
    (6, 'user006', '吴杰', 'user6@example.com', 'guest', 'active', NULL, 0, '2026-01-10 01:00:00', '2026-01-10 01:00:00'),
    (7, 'user007', '周娜洋', 'user7@example.com', 'editor', 'disabled', NULL, 0, '2026-01-11 13:00:00', '2026-01-11 13:00:00'),
    (8, 'user008', '孙婷', 'user8@example.com', 'guest', 'active', NULL, 0, '2026-01-13 01:00:00', '2026-01-13 01:00:00'),
    (9, 'user009', '李磊杰', 'user9@example.com', 'guest', 'disabled', NULL, 0, '2026-01-14 13:00:00', '2026-01-14 13:00:00'),
    (10, 'user010', '孙洋', 'user10@example.com', 'guest', 'active', NULL, 0, '2026-01-16 01:00:00', '2026-01-16 01:00:00'),
    (11, 'user011', '王涛', 'user11@example.com', 'guest', 'disabled', NULL, 0, '2026-01-17 13:00:00', '2026-01-17 13:00:00'),
    (12, 'user012', '孙敏洋', 'user12@example.com', 'guest', 'active', NULL, 0, '2026-01-19 01:00:00', '2026-01-19 01:00:00'),
    (13, 'user013', '刘涛芳', 'user13@example.com', 'guest', 'active', NULL, 0, '2026-01-20 13:00:00', '2026-01-20 13:00:00'),
    (14, 'user014', '王伟', 'user14@example.com', 'editor', 'active', NULL, 0, '2026-01-22 01:00:00', '2026-01-22 01:00:00'),
    (15, 'user015', '孙静', 'user15@example.com', 'guest', 'active', NULL, 0, '2026-01-23 13:00:00', '2026-01-23 13:00:00'),
    (16, 'user016', '徐洋', 'user16@example.com', 'guest', 'active', NULL, 0, '2026-01-25 01:00:00', '2026-01-25 01:00:00'),
    (17, 'user017', '周芳', 'user17@example.com', 'editor', 'active', NULL, 0, '2026-01-26 13:00:00', '2026-01-26 13:00:00'),
    (18, 'user018', '孙超涛', 'user18@example.com', 'editor', 'active', NULL, 0, '2026-01-28 01:00:00', '2026-01-28 01:00:00'),
    (19, 'user019', '张静', 'user19@example.com', 'guest', 'active', NULL, 0, '2026-01-29 13:00:00', '2026-01-29 13:00:00'),
    (20, 'user020', '周涛', 'user20@example.com', 'guest', 'disabled', NULL, 0, '2026-01-31 01:00:00', '2026-01-31 01:00:00'),
    (21, 'user021', '刘艳', 'user21@example.com', 'editor', 'active', NULL, 0, '2026-02-01 13:00:00', '2026-02-01 13:00:00'),
    (22, 'user022', '陈超洋', 'user22@example.com', 'editor', 'active', NULL, 0, '2026-02-03 01:00:00', '2026-02-03 01:00:00'),
    (23, 'user023', '徐敏', 'user23@example.com', 'editor', 'active', NULL, 0, '2026-02-04 13:00:00', '2026-02-04 13:00:00'),
    (24, 'user024', '周杰敏', 'user24@example.com', 'guest', 'disabled', NULL, 0, '2026-02-06 01:00:00', '2026-02-06 01:00:00'),
    (25, 'user025', '张芳', 'user25@example.com', 'guest', 'active', NULL, 0, '2026-02-07 13:00:00', '2026-02-07 13:00:00'),
    (26, 'user026', '徐娜', 'user26@example.com', 'editor', 'active', NULL, 0, '2026-02-09 01:00:00', '2026-02-09 01:00:00'),
    (27, 'user027', '陈伟', 'user27@example.com', 'guest', 'active', NULL, 0, '2026-02-10 13:00:00', '2026-02-10 13:00:00'),
    (28, 'user028', '吴芳', 'user28@example.com', 'editor', 'active', NULL, 0, '2026-02-12 01:00:00', '2026-02-12 01:00:00'),
    (29, 'user029', '王磊', 'user29@example.com', 'guest', 'active', NULL, 0, '2026-02-13 13:00:00', '2026-02-13 13:00:00'),
    (30, 'user030', '张涛', 'user30@example.com', 'editor', 'active', NULL, 0, '2026-02-15 01:00:00', '2026-02-15 01:00:00'),
    (31, 'user031', '孙涛敏', 'user31@example.com', 'guest', 'disabled', NULL, 0, '2026-02-16 13:00:00', '2026-02-16 13:00:00'),
    (32, 'user032', '徐芳', 'user32@example.com', 'editor', 'active', NULL, 0, '2026-02-18 01:00:00', '2026-02-18 01:00:00'),
    (33, 'user033', '张艳', 'user33@example.com', 'guest', 'active', NULL, 0, '2026-02-19 13:00:00', '2026-02-19 13:00:00'),
    (34, 'user034', '徐勇', 'user34@example.com', 'editor', 'active', NULL, 0, '2026-02-21 01:00:00', '2026-02-21 01:00:00'),
    (35, 'user035', '黄洋杰', 'user35@example.com', 'guest', 'active', NULL, 0, '2026-02-22 13:00:00', '2026-02-22 13:00:00'),
    (36, 'user036', '徐磊', 'user36@example.com', 'editor', 'active', NULL, 0, '2026-02-24 01:00:00', '2026-02-24 01:00:00'),
    (37, 'user037', '徐勇艳', 'user37@example.com', 'guest', 'active', NULL, 0, '2026-02-25 13:00:00', '2026-02-25 13:00:00'),
    (38, 'user038', '张婷杰', 'user38@example.com', 'guest', 'active', NULL, 0, '2026-02-27 01:00:00', '2026-02-27 01:00:00'),
    (39, 'user039', '吴勇', 'user39@example.com', 'guest', 'active', NULL, 0, '2026-02-28 13:00:00', '2026-02-28 13:00:00'),
    (40, 'user040', '张磊', 'user40@example.com', 'guest', 'active', NULL, 0, '2026-03-02 01:00:00', '2026-03-02 01:00:00'),
    (41, 'user041', '王杰', 'user41@example.com', 'guest', 'active', NULL, 0, '2026-03-03 13:00:00', '2026-03-03 13:00:00'),
    (42, 'user042', '赵伟勇', 'user42@example.com', 'editor', 'active', NULL, 0, '2026-03-05 01:00:00', '2026-03-05 01:00:00'),
    (43, 'user043', '李杰', 'user43@example.com', 'editor', 'active', NULL, 0, '2026-03-06 13:00:00', '2026-03-06 13:00:00'),
    (44, 'user044', '徐艳', 'user44@example.com', 'guest', 'disabled', NULL, 0, '2026-03-08 01:00:00', '2026-03-08 01:00:00'),
    (45, 'user045', '刘磊勇', 'user45@example.com', 'editor', 'disabled', NULL, 0, '2026-03-09 13:00:00', '2026-03-09 13:00:00'),
    (46, 'user046', '陈敏', 'user46@example.com', 'guest', 'active', NULL, 0, '2026-03-11 01:00:00', '2026-03-11 01:00:00'),
    (47, 'user047', '张伟洋', 'user47@example.com', 'guest', 'active', NULL, 0, '2026-03-12 13:00:00', '2026-03-12 13:00:00'),
    (48, 'user048', '王磊', 'user48@example.com', 'guest', 'disabled', NULL, 0, '2026-03-14 01:00:00', '2026-03-14 01:00:00'),
    (49, 'user049', '王涛伟', 'user49@example.com', 'guest', 'active', NULL, 0, '2026-03-15 13:00:00', '2026-03-15 13:00:00'),
    (50, 'user050', '刘敏洋', 'user50@example.com', 'guest', 'active', NULL, 0, '2026-03-17 01:00:00', '2026-03-17 01:00:00'),
    (51, 'user051', '徐婷', 'user51@example.com', 'guest', 'active', NULL, 0, '2026-03-18 13:00:00', '2026-03-18 13:00:00'),
    (52, 'user052', '刘芳', 'user52@example.com', 'editor', 'active', NULL, 0, '2026-03-20 01:00:00', '2026-03-20 01:00:00'),
    (53, 'user053', '黄婷', 'user53@example.com', 'guest', 'active', NULL, 0, '2026-03-21 13:00:00', '2026-03-21 13:00:00'),
    (54, 'user054', '王超明', 'user54@example.com', 'editor', 'active', NULL, 0, '2026-03-23 01:00:00', '2026-03-23 01:00:00'),
    (55, 'user055', '黄芳婷', 'user55@example.com', 'editor', 'active', NULL, 0, '2026-03-24 13:00:00', '2026-03-24 13:00:00'),
    (56, 'user056', '刘勇芳', 'user56@example.com', 'guest', 'active', NULL, 0, '2026-03-26 01:00:00', '2026-03-26 01:00:00'),
    (57, 'user057', '周勇艳', 'user57@example.com', 'guest', 'disabled', NULL, 0, '2026-03-27 13:00:00', '2026-03-27 13:00:00'),
    (58, 'user058', '王磊涛', 'user58@example.com', 'guest', 'active', NULL, 0, '2026-03-29 01:00:00', '2026-03-29 01:00:00'),
    (59, 'user059', '徐洋', 'user59@example.com', 'guest', 'active', NULL, 0, '2026-03-30 13:00:00', '2026-03-30 13:00:00'),
    (60, 'user060', '李敏', 'user60@example.com', 'guest', 'active', NULL, 0, '2026-04-01 01:00:00', '2026-04-01 01:00:00');

-- 商品和库存：和微服务版约定的同一组数据，6 号商品库存少，用来做并发防超卖演示
INSERT INTO product (id, name, price, status, created_at, updated_at) VALUES
    (1, '机械键盘 K8', 399.00, 'ON_SALE', '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (2, '无线鼠标 M3', 129.00, 'ON_SALE', '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (3, '27 英寸显示器', 1499.00, 'ON_SALE', '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (4, 'USB-C 扩展坞', 259.00, 'ON_SALE', '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (5, '降噪耳机 H5', 899.00, 'ON_SALE', '2026-01-01 01:00:00', '2026-01-01 01:00:00'),
    (6, '限量帆布包（秒杀）', 9.90, 'ON_SALE', '2026-01-01 01:00:00', '2026-01-01 01:00:00');

INSERT INTO inventory (sku_id, available, reserved, sold, version, updated_at) VALUES
    (1, 100, 0, 0, 0, '2026-01-01 01:00:00'),
    (2, 200, 0, 0, 0, '2026-01-01 01:00:00'),
    (3, 30, 0, 0, 0, '2026-01-01 01:00:00'),
    (4, 80, 0, 0, 0, '2026-01-01 01:00:00'),
    (5, 60, 0, 0, 0, '2026-01-01 01:00:00'),
    (6, 50, 0, 0, 0, '2026-01-01 01:00:00');
