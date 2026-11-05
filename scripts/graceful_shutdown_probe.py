"""优雅停机实验：一个"正在上传请求体"的下单请求在途时给应用发 SIGTERM，观察它能否正常完成。

前提：应用已经在 8080 端口启动（比如 java -jar monolith-app/target/monolith-app.jar --spring.profiles.active=demo）。
用法：python3 scripts/graceful_shutdown_probe.py
注意：脚本会给 8080 端口上的进程发 SIGTERM，跑完应用就停了。
密码从测试代码的常量（DemoAccounts.PASSWORD）里读，不打印。
"""
import json
import os
import re
import signal
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

DEMO_ACCOUNTS = (
    Path(__file__).resolve().parent.parent
    / "monolith-app/src/test/java/com/example/monolith/support/DemoAccounts.java"
)
BASE = "http://localhost:8080"
T0 = time.time()


def ts():
    return f"+{time.time() - T0:5.2f}s"


def login(username, password):
    req = urllib.request.Request(
        BASE + "/api/auth/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.load(resp)["data"]["accessToken"]


def dechunk(data):
    """解码 HTTP/1.1 分块传输（Transfer-Encoding: chunked）的响应体。"""
    out = b""
    while data:
        size_line, _, rest = data.partition(b"\r\n")
        size = int(size_line.split(b";")[0], 16)
        if size == 0:
            break
        out += rest[:size]
        data = rest[size + 2 :]
    return out


def slow_order(token, result):
    """手写 HTTP 请求：请求头一次发完，请求体每秒发 4 个字节，约 10 秒发完。"""
    body = b'{"items":[{"skuId":4,"quantity":1}]}'
    head = (
        "POST /api/orders HTTP/1.1\r\n"
        "Host: localhost:8080\r\n"
        f"Authorization: Bearer {token}\r\n"
        f"Idempotency-Key: graceful-{int(time.time())}\r\n"
        "Content-Type: application/json\r\n"
        f"Content-Length: {len(body)}\r\n"
        "Connection: close\r\n\r\n"
    ).encode()
    s = socket.create_connection(("localhost", 8080), timeout=60)
    s.sendall(head)
    result["sent_headers"] = ts()
    for i in range(0, len(body), 4):
        s.sendall(body[i : i + 4])
        time.sleep(1)
    result["sent_body"] = ts()
    chunks = []
    while True:
        data = s.recv(65536)
        if not data:
            break
        chunks.append(data)
    s.close()
    raw = b"".join(chunks)
    result["got_response"] = ts()
    head_bytes, _, payload_bytes = raw.partition(b"\r\n\r\n")
    head_text = head_bytes.decode("utf-8", "replace")
    result["status_line"] = head_text.split("\r\n", 1)[0]
    if "transfer-encoding: chunked" in head_text.lower():
        payload_bytes = dechunk(payload_bytes)
    payload = payload_bytes.decode("utf-8", "replace")
    try:
        data = json.loads(payload)["data"] or {}
        result["order"] = {k: data.get(k) for k in ("orderNo", "status")}
    except Exception:  # noqa: BLE001  响应不是 JSON 时原样记录前 200 个字符
        result["order"] = payload[:200]


def main():
    source = DEMO_ACCOUNTS.read_text(encoding="utf-8")
    password = re.search(r'String PASSWORD = "([^"]*)"', source).group(1)
    token = login("editor", password)

    pid = int(subprocess.check_output(["lsof", "-t", "-iTCP:8080", "-sTCP:LISTEN"]).split()[0])
    print(f"{ts()} 应用进程 pid={pid}")

    result = {}
    worker = threading.Thread(target=slow_order, args=(token, result))
    worker.start()
    time.sleep(2)
    print(f"{ts()} 慢请求已发出请求头，请求体还在发送中 → 发送 SIGTERM")
    os.kill(pid, signal.SIGTERM)

    time.sleep(1)
    try:
        with urllib.request.urlopen(BASE + "/actuator/health", timeout=3) as resp:
            print(f"{ts()} 停机过程中的新请求：HTTP {resp.status}")
    except urllib.error.URLError as ex:
        print(f"{ts()} 停机过程中的新请求：失败（{ex.reason}）")
    except Exception as ex:  # noqa: BLE001
        print(f"{ts()} 停机过程中的新请求：失败（{type(ex).__name__}: {ex}）")

    worker.join()
    print(f"{ts()} 慢请求：请求头发送于 {result.get('sent_headers')}，请求体发完于 {result.get('sent_body')}，"
          f"收到响应于 {result.get('got_response')}")
    print(f"{ts()} 慢请求的响应：{result.get('status_line')} {result.get('order')}")

    for _ in range(60):
        if subprocess.run(["kill", "-0", str(pid)], capture_output=True).returncode != 0:
            print(f"{ts()} 进程已退出")
            break
        time.sleep(0.5)
    else:
        print(f"{ts()} 30 秒后进程仍在运行")


if __name__ == "__main__":
    main()
