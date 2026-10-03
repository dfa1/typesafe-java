"""Markdown for the GitHub job summary: agreement with Jev (JevComparison output) and throughput (JMH JSON).
Usage: python3 .github/scripts/local_summary.py jev.txt jmh.json >> $GITHUB_STEP_SUMMARY"""
import json
import os
import platform
import re
import sys

jev_file, jmh_file = sys.argv[1], sys.argv[2]
print(f"## Local engines on `{os.environ.get('RUNNER_OS', platform.system())}` "
      f"({platform.machine()}, {os.cpu_count()} CPUs)\n")

print("### Agreement with Jev (104 cached requests)\n")
print("| engine | yes/no agree | choice agree | score error (0–1) |")
print("|---|---|---|---|")
engine = None
for line in open(jev_file) if os.path.exists(jev_file) else []:
    m = re.match(r"(\S+) vs jev", line)
    if m:
        engine = m.group(1)
    m = re.search(r"ALL\s+noul agree\s+(\S+)\s+\(\s*(\d+)%\).*choice agree\s+(\S+)\s+\(\s*(\d+)%\).*score MAE ([\d.]+)", line)
    if m and engine:
        print(f"| {engine} | {m.group(2)}% ({m.group(1)}) | {m.group(4)}% ({m.group(3)}) | {m.group(5)} |")

print("\n### Throughput (JMH, sequential requests)\n")
print("| engine | questions/request | requests/s | ms/request | questions/s |")
print("|---|---|---|---|---|")
rows = json.load(open(jmh_file)) if os.path.exists(jmh_file) else []
for r in sorted(rows, key=lambda r: (int(r["params"]["questions"]), -float(r["primaryMetric"]["score"]))):
    m, q = r["primaryMetric"], int(r["params"]["questions"])
    score, error = float(m["score"]), float(m["scoreError"])  # JMH writes "NaN" as a string with < 3 iterations
    spread = "" if error != error else f" ± {error:.2f}"
    print(f"| {r['params']['engine']} | {q} | {score:.2f}{spread} | {1000 / score:.0f} | {score * q:.1f} |")
if not rows:
    print("| (no results) | | | | |")
