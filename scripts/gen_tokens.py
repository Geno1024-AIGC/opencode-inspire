#!/usr/bin/env python3
"""Refresh tokens.txt from the OpenCode SQLite DB, scoped to this project.

Format consumed by app/build.gradle.kts and AboutScreen:
  line 0-6: totals input/output/reasoning/cacheRead/cacheWrite/msgs
  line 7+:  [*]model:input:output:reasoning:cacheRead:cacheWrite:msgs:cost  (desc by total)
"""
import sqlite3
import json
import sys

DB = "/home/geno1024/.local/share/opencode/opencode.db"
PID = "d8b3f3c991927ab17d475b9ccd2fa060fbe74456"
OUT = "/home/geno1024/remotes/Geno1024-AIGenerated/opencode-inspire/tokens.txt"

# Per-model USD-per-million rates; free models have 0.
RATES = {
    "big-pickle": (0.0, 0.0, 0.0, 0.0, 0.0),
    "mimo-v2.5-free": (0.0, 0.0, 0.0, 0.0, 0.0),
}

def model_id(m):
    try:
        return json.loads(m).get("id") if m else None
    except Exception:
        return None

con = sqlite3.connect(DB)
cur = con.cursor()
rows = cur.execute(
    "SELECT id, model, "
    "COALESCE(tokens_input,0), COALESCE(tokens_output,0), COALESCE(tokens_reasoning,0), "
    "COALESCE(tokens_cache_read,0), COALESCE(tokens_cache_write,0), COALESCE(cost,0) "
    "FROM session WHERE project_id=?", (PID,)
).fetchall()

agg = {}
tot = [0, 0, 0, 0, 0, 0]
for sid, m, ti, to, tr, tcr, tcw, cost in rows:
    mid = model_id(m)
    key = mid if mid else "__none__"
    a = agg.setdefault(key, [0, 0, 0, 0, 0, 0, 0])
    a[0] += ti; a[1] += to; a[2] += tr; a[3] += tcr; a[4] += tcw; a[6] += cost
    for i, v in enumerate([ti, to, tr, tcr, tcw]):
        tot[i] += v

# count user+assistant messages per session, attribute by session model id
msgcnt = {}
for sid, m, *_ in rows:
    mid = model_id(m)
    key = mid if mid else "__none__"
    n = cur.execute(
        "SELECT COUNT(*) FROM message WHERE session_id=? AND "
        "json_extract(data,'$.role') IN ('user','assistant')", (sid,)
    ).fetchone()[0]
    msgcnt[key] = msgcnt.get(key, 0) + n
tot[5] = sum(msgcnt.values())

lines = []
lines.append(str(tot[0]))
lines.append(str(tot[1]))
lines.append(str(tot[2]))
lines.append(str(tot[3]))
lines.append(str(tot[4]))
lines.append(str(tot[5]))

def total(a):
    return a[0] + a[1] + a[2] + a[3] + a[4]

order = sorted(((k, a) for k, a in agg.items() if k != "__none__"), key=lambda kv: -total(kv[1]))
star_max = order[0][1] if order else [0, 0, 0, 0, 0, 0, 0]
for k, a in order:
    rate = RATES.get(k, (0.0, 0.0, 0.0, 0.0, 0.0))
    cost = (a[0] * rate[0] + a[1] * rate[1] + a[2] * rate[2] + a[3] * rate[3] + a[4] * rate[4]) / 1e6
    star = "*" if total(a) >= total(star_max) * 0.5 else ""
    lines.append(f"{star}{k}:{a[0]}:{a[1]}:{a[2]}:{a[3]}:{a[4]}:{msgcnt.get(k,0)}:{cost:.4f}")

with open(OUT, "w") as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines))