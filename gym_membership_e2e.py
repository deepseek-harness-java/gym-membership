#!/usr/bin/env python3
"""gym-membership E2E：通过业务应用 SSE 代理调用 DSH Agent，验证 6 个工具全链路。"""
import json, subprocess, sys

AGENT = "gym-copilot"
URL = "http://127.0.0.1:18107/api/assistant/stream"

CASES = [
    ("T1 卡种列表", "健身房有什么卡种？年卡多少钱？简洁回答", ["年卡", "3299"]),
    ("T2 团课列表", "这周健身房有哪些团课？流瑜伽还有几个位置？简洁回答", ["流瑜伽", "普拉提核心"]),
    ("T3 会员查询", "查一下健身房会员 M5001 的会籍信息，简洁回答", ["钱女士", "年卡"]),
    ("T4 预约团课", "我是健身房会员钱女士，帮我预约周四 19:30 的流瑜伽团课，告诉我预约结果", ["流瑜伽", "预约"]),
    ("T5 办卡入会", "我是新客户测试用户王芳，想在健身房办一张季卡，帮我办理入会，告诉我会员号和金额", ["季卡", "999"]),
    ("T6 运营统计", "健身房今天运营情况怎么样？有效会员多少？简洁回答", ["会员", "有效"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== gym-membership E2E: {passed}/{len(cases)} PASS =====")
    if failed:
        print("失败用例:", ", ".join(failed))
        sys.exit(1)

if __name__ == "__main__":
    main()
