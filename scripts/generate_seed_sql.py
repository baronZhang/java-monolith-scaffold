#!/usr/bin/env python3
"""
生成 Flyway 种子数据 V2__seed_data.sql。

用户数据和 Vue 项目 mock/data.ts 的 createUsers 完全一致：同样的线性同余伪随机数（种子 7）、
同样的调用顺序，所以 60 个用户的昵称、角色、状态、创建时间都和 Mock 一样，前端联调时看到的数据不变。

三个演示账号的密码哈希不在这里生成（脚本里不出现明文密码）：
用 Spring Security 的 DelegatingPasswordEncoder 算好后，以"账号<TAB>哈希"的格式放进一个文件，作为参数传入。

用法：python3 scripts/generate_seed_sql.py <哈希文件> > monolith-app/src/main/resources/db/migration/common/V2__seed_data.sql
"""
import math
import sys
from datetime import datetime, timedelta, timezone


def create_random(seed):
    """和 mock/data.ts 的 createRandom 一样的线性同余法"""
    state = seed

    def random():
        nonlocal state
        state = (state * 9301 + 49297) % 233280
        return state / 233280

    return random


def sql_str(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    hashes = {}
    with open(sys.argv[1], encoding="utf-8") as f:
        for line in f:
            account, password_hash = line.rstrip("\n").split("\t")
            hashes[account] = password_hash

    random = create_random(7)

    def pick(items):
        return items[math.floor(random() * len(items))]

    surnames = list("张王李赵刘陈杨黄周吴徐孙")
    given_names = list("伟芳娜敏静磊洋勇艳杰涛明超婷")
    roles = ["editor", "guest", "guest", "editor", "guest"]
    # 2026-01-01T09:00:00+08:00 = 2026-01-01T01:00:00Z
    base_time = datetime(2026, 1, 1, 1, 0, 0, tzinfo=timezone.utc)

    accounts = [
        (1, "admin", "管理员", "admin"),
        (2, "editor", "运营小王", "editor"),
        (3, "guest", "访客", "guest"),
    ]
    users = []
    for user_id, username, nickname, role in accounts:
        users.append((user_id, username, nickname, f"{username}@example.com", role, "active", hashes[username], base_time))
    for user_id in range(len(accounts) + 1, 61):
        # 求值顺序和 JS 对象字面量一致：nickname（姓、名、是否双字名、第二个名）→ role → status
        nickname = pick(surnames) + pick(given_names)
        if random() > 0.5:
            nickname += pick(given_names)
        role = pick(roles)
        status = "active" if random() > 0.2 else "disabled"
        created_at = base_time + timedelta(hours=36 * user_id)
        users.append((user_id, f"user{user_id:03d}", nickname, f"user{user_id}@example.com", role, status, None, created_at))

    out = []
    out.append("-- =====================================================================")
    out.append("-- V2：种子数据。由 scripts/generate_seed_sql.py 生成，不要手改。")
    out.append("-- 用户数据与 Vue 项目 mock/data.ts 一致（同一个伪随机算法和种子），前端切到真实后端后看到的数据不变。")
    out.append("-- 只有三个演示账号有密码（与 Mock 的 DEMO_PASSWORD 相同），这里存的是 BCrypt 哈希，不是明文。")
    out.append("-- 时间都是 UTC。")
    out.append("-- =====================================================================")
    out.append("")
    out.append("INSERT INTO sys_role (code, name) VALUES ('admin', '管理员'), ('editor', '运营'), ('guest', '访客');")
    out.append("")
    out.append("-- admin 的 '*' 表示全部权限；editor 能看用户列表、能启用/禁用；guest 没有任何权限")
    out.append("INSERT INTO sys_role_permission (role, permission) VALUES")
    out.append("    ('admin', '*'),")
    out.append("    ('editor', 'user:list'),")
    out.append("    ('editor', 'user:edit');")
    out.append("")
    out.append("INSERT INTO sys_user (id, username, nickname, email, role, status, password_hash, deleted, created_at, updated_at) VALUES")
    rows = []
    for user_id, username, nickname, email, role, status, password_hash, created_at in users:
        time = created_at.strftime("%Y-%m-%d %H:%M:%S")
        hash_sql = sql_str(password_hash) if password_hash else "NULL"
        rows.append(
            f"    ({user_id}, {sql_str(username)}, {sql_str(nickname)}, {sql_str(email)}, {sql_str(role)}, "
            f"{sql_str(status)}, {hash_sql}, 0, '{time}', '{time}')"
        )
    out.append(",\n".join(rows) + ";")
    out.append("")
    out.append("-- 商品和库存：和微服务版约定的同一组数据，6 号商品库存少，用来做并发防超卖演示")
    products = [
        (1, "机械键盘 K8", "399.00", 100),
        (2, "无线鼠标 M3", "129.00", 200),
        (3, "27 英寸显示器", "1499.00", 30),
        (4, "USB-C 扩展坞", "259.00", 80),
        (5, "降噪耳机 H5", "899.00", 60),
        (6, "限量帆布包（秒杀）", "9.90", 50),
    ]
    time = base_time.strftime("%Y-%m-%d %H:%M:%S")
    out.append("INSERT INTO product (id, name, price, status, created_at, updated_at) VALUES")
    out.append(",\n".join(
        f"    ({pid}, {sql_str(name)}, {price}, 'ON_SALE', '{time}', '{time}')" for pid, name, price, _ in products
    ) + ";")
    out.append("")
    out.append("INSERT INTO inventory (sku_id, available, reserved, sold, version, updated_at) VALUES")
    out.append(",\n".join(
        f"    ({pid}, {stock}, 0, 0, 0, '{time}')" for pid, _, _, stock in products
    ) + ";")
    print("\n".join(out))


if __name__ == "__main__":
    main()
