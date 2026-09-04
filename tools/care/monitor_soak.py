"""Read-only ADB sampler for the debug CareScenePreviewActivity soak.

Start the lab separately. This script never installs, clears data, or starts a
service. It records process continuity, heap/PSS samples and the lab's completion
counts. A completed lab soak is not equivalent to NE2213 visual approval.
"""
import argparse
import json
import re
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=1920)
    args = parser.parse_args()
    command = ["adb", "-s", args.serial]
    def adb(*values):
        return subprocess.check_output(command + list(values), text=True, timeout=15)
    initial_pid = adb("shell", "pidof", "com.pixelpals.app.debug").strip()
    report = {"startedAt": datetime.now(timezone.utc).isoformat(), "serial": args.serial,
              "pid": initial_pid, "completed": False, "samples": []}
    started = time.monotonic()
    while time.monotonic() - started < args.timeout:
        pid = adb("shell", "pidof", "com.pixelpals.app.debug").strip()
        if pid != initial_pid:
            report["failure"] = "Process changed during soak"
            break
        memory = adb("shell", "dumpsys", "meminfo", "com.pixelpals.app.debug")
        logs = adb("logcat", "-d", "--pid=" + initial_pid, "-v", "brief", "-s", "CARE_SCENE_LAB:I", "AndroidRuntime:E")
        rounds = re.findall(r"round=(\d+) commits=(\d+) elapsed=(\d+) heap=(\d+) bitmap=(\d+)", logs)
        pss = re.search(r"TOTAL\s+(\d+)\s+", memory)
        sample = {"seconds": round(time.monotonic() - started), "pssKb": int(pss.group(1)) if pss else None}
        if rounds:
            sample.update(dict(zip(("round", "commits", "elapsedMs", "javaHeapBytes", "activeBitmapBytes"), map(int, rounds[-1]))))
        report["samples"].append(sample)
        done = re.search(r"SOAK_COMPLETE rounds=(\d+) commits=(\d+) elapsed=(\d+)", logs)
        if done:
            report["rounds"], report["commits"], report["elapsedMs"] = map(int, done.groups())
            report["completed"] = report["rounds"] == report["commits"] and report["elapsedMs"] >= 1_800_000
        report["maxPssKb"] = max((item["pssKb"] or 0) for item in report["samples"])
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(sample), flush=True)
        if done or "FATAL EXCEPTION" in logs:
            break
        time.sleep(40)
    if not report["completed"]:
        report.setdefault("failure", "No successful uninterrupted 30 minute completion observed")
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "samples"}), flush=True)
    raise SystemExit(0 if report["completed"] else 1)


if __name__ == "__main__":
    main()
