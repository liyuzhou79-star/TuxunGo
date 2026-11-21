#!/usr/bin/env python3
"""死活题数据自检：解析 App 内置的 8 道题（TsumegoScreen.kt 里的
TSUMEGO_PROBLEMS），与逐手用规则求解器验证过的参考数据逐项核对
（棋盘大小、黑白子列表、先行方、脚本步的落子与应招）。

改题目数据后先跑一遍这个脚本：全部一致才说明 App 里装的题和
验证过的题是同一套。用法：python3 tools/check_tsumego_data.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(
    ROOT, "app/src/main/java/com/tuxungo/android/ui/TsumegoScreen.kt")

src = open(KT, encoding="utf-8").read()
start = src.index("val TSUMEGO_PROBLEMS")
chunk = src[start:]
blocks = chunk.split("TsumegoProblem(")[1:]
problems = []
for b in blocks:
    w = int(re.search(r"width = (\d+)", b).group(1))
    h = int(re.search(r"height = (\d+)", b).group(1))

    def stones(field):
        m = re.search(field + r" = listOf\((.*?)\)", b, re.S)
        return sorted(
            (int(x), int(y))
            for x, y in re.findall(r"(\d+) to (\d+)", m.group(1))
        )

    steps_raw = re.search(r"steps = listOf\((.*?)\)\s*,\s*\n\s*hint", b, re.S)
    steps = []
    if steps_raw:
        for sm in re.finditer(
                r"TsumegoStep\(setOf\((.*?)\), (null|\d+ to \d+)\)",
                steps_raw.group(1)):
            accept = sorted(
                (int(x), int(y))
                for x, y in re.findall(r"(\d+) to (\d+)", sm.group(1))
            )
            reply = None
            if sm.group(2) != "null":
                rx, ry = sm.group(2).split(" to ")
                reply = (int(rx), int(ry))
            steps.append((accept, reply))
    problems.append({
        "w": w, "h": h,
        "black": stones("black"), "white": stones("white"),
        "userWhite": "userWhite = true" in b,
        "steps": steps,
    })

print(f"parsed {len(problems)} problems from Kotlin")

wall6 = sorted([(0, 3), (1, 3), (2, 3), (3, 3), (4, 3)])
ring6 = sorted([(0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (0, 1), (4, 1),
                (0, 2), (1, 2), (2, 2), (3, 2), (4, 2)])
EXPECTED = [
    # (w, h, black, white, userWhite, script [(move, reply), ...])
    (5, 5, [(0, 2), (2, 0)], [(0, 0), (0, 1), (1, 0)], False,
     [((1, 1), None)]),
    (5, 5, [(0, 1), (0, 2), (1, 0), (1, 3), (2, 3), (3, 2)],
     [(1, 1), (2, 2)], False, [((2, 1), None)]),
    (6, 4, [(0, 0), (0, 1), (0, 2), (1, 0), (1, 2), (3, 0), (3, 1),
            (3, 2), (3, 3)],
     [(1, 1), (2, 0), (2, 1), (2, 2), (2, 3)], False, [((1, 3), None)]),
    (5, 5, [(0, 1), (0, 2), (1, 3), (2, 3), (3, 0), (3, 1), (3, 2)],
     [(0, 0), (1, 0), (1, 2), (2, 0), (2, 1), (2, 2)], False,
     [((1, 1), None)]),
    (7, 7, [(1, 2), (2, 1), (3, 3)], [(2, 2)], False,
     [((3, 2), (2, 3)), ((2, 4), (1, 3)), ((1, 4), (0, 3)),
      ((0, 4), (0, 2)), ((0, 1), None)]),
    (5, 5, wall6, ring6, True, [((2, 1), None)]),
    (5, 5, wall6, sorted(ring6 + [(1, 1)]), False,
     [((2, 1), (3, 1)), ((2, 1), None)]),
    (5, 5, wall6, ring6, False,
     [((2, 1), (1, 1)), ((3, 1), None)]),
]

fails = 0
if len(problems) != len(EXPECTED):
    print(f"FAIL count: kotlin={len(problems)} expected={len(EXPECTED)}")
    sys.exit(1)
for i, (p, e) in enumerate(zip(problems, EXPECTED), 1):
    ew, eh, eb, ewht, euw, escript = e
    ok = True
    if (p["w"], p["h"]) != (ew, eh):
        ok = False
        print(f"P{i} FAIL size kotlin={(p['w'], p['h'])} expected={(ew, eh)}")
    if p["black"] != sorted(eb):
        ok = False
        print(f"P{i} FAIL black kotlin={p['black']} expected={sorted(eb)}")
    if p["white"] != sorted(ewht):
        ok = False
        print(f"P{i} FAIL white kotlin={p['white']} expected={sorted(ewht)}")
    if p["userWhite"] != euw:
        ok = False
        print(f"P{i} FAIL userWhite kotlin={p['userWhite']} expected={euw}")
    if len(p["steps"]) < len(escript):
        ok = False
        print(f"P{i} FAIL steps count kotlin={len(p['steps'])} "
              f"expected>={len(escript)}")
    else:
        for j, (emove, ereply) in enumerate(escript):
            accept, reply = p["steps"][j]
            if emove not in accept:
                ok = False
                print(f"P{i} FAIL step{j} move {emove} not in accept {accept}")
            if reply != ereply:
                ok = False
                print(f"P{i} FAIL step{j} reply kotlin={reply} "
                      f"expected={ereply}")
    print(("PASS " if ok else "FAIL ") + f"P{i}")
    if not ok:
        fails += 1

if fails:
    print(f"{fails} problem(s) drifted")
    sys.exit(1)
print("ALL 8 KOTLIN PROBLEMS MATCH THE VERIFIED COPIES")
