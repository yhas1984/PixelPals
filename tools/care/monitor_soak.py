"""Read-only ADB sampler for the debug CareScenePreviewActivity soak.

The monitor never installs, clears data, or starts a service. Historical
logcat output is treated as baseline evidence and cannot complete a new soak.
The lab currently exposes no run id, so a same-PID round reset is terminal.
"""
import argparse
import fcntl
import json
import re
import subprocess
import time
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path

EVENT_RE = re.compile(
    r"(?P<round>round=(\d+) commits=(\d+) elapsed=(\d+) heap=(\d+) bitmap=(\d+))|"
    r"(?P<complete>SOAK_COMPLETE rounds=(\d+) commits=(\d+) elapsed=(\d+))"
)


@dataclass(frozen=True)
class SoakObservation:
    events: tuple[tuple[str, tuple[int, ...]], ...] = ()
    rounds: tuple[tuple[int, int, int, int, int], ...] = ()
    completions: tuple[tuple[int, int, int], ...] = ()
    fatal: bool = False


@dataclass(frozen=True)
class MonitorState:
    baseline_completions: frozenset[tuple[int, int, int]] = frozenset()
    seen_events: tuple[tuple[str, tuple[int, ...]], ...] = ()
    last_round: int | None = None
    last_elapsed_ms: int | None = None
    completed: bool = False
    failure: str | None = None
    completion: tuple[int, int, int] | None = None


def parse_soak_log(logs: str) -> SoakObservation:
    """Parse cumulative logcat output without accepting any completion."""
    events = []
    for match in EVENT_RE.finditer(logs):
        if match.group("round"):
            events.append(("round", tuple(map(int, match.groups()[1:6]))))
        else:
            events.append(("complete", tuple(map(int, match.groups()[7:10]))))
    return SoakObservation(
        events=tuple(events),
        rounds=tuple(values for kind, values in events if kind == "round"),
        completions=tuple(values for kind, values in events if kind == "complete"),
        fatal="FATAL EXCEPTION" in logs or "AndroidRuntime: FATAL" in logs,
    )


def initial_monitor_state(observation: SoakObservation) -> MonitorState:
    """Capture historical closures and the latest historical progress."""
    latest = observation.rounds[-1] if observation.rounds else None
    return MonitorState(
        baseline_completions=frozenset(observation.completions),
        seen_events=observation.events,
        last_round=latest[0] if latest else None,
        last_elapsed_ms=latest[2] if latest else None,
    )


def advance_monitor(state: MonitorState, observation: SoakObservation) -> MonitorState:
    """Apply one cumulative logcat observation; fatal/reset always wins."""
    if observation.fatal:
        return replace(state, completed=False, failure="Fatal exception observed during soak")
    if state.failure or state.completed:
        return state
    if not observation.events:
        return state
    previous = state.seen_events
    overlap = max((size for size in range(1, min(len(previous), len(observation.events)) + 1)
                   if previous[-size:] == observation.events[:size]), default=0)
    events = observation.events[overlap:]
    next_state = replace(state, seen_events=observation.events)
    accepted_completion = False
    accepted_values = None
    for kind, values in events:
        if kind == "round":
            round_number, _, elapsed_ms, _, _ = values
            if next_state.last_round is not None and (
                round_number < next_state.last_round or elapsed_ms < (next_state.last_elapsed_ms or 0)
            ):
                return replace(next_state, failure="Soak progress reset (round or elapsed regressed)")
            next_state = replace(next_state, last_round=round_number, last_elapsed_ms=elapsed_ms)
        elif values not in next_state.baseline_completions:
            rounds, commits, elapsed_ms = values
            if rounds < (next_state.last_round or 0) or elapsed_ms < (next_state.last_elapsed_ms or 0):
                return replace(next_state, failure="Completion regressed behind observed progress")
            if rounds == commits and elapsed_ms >= 1_800_000:
                accepted_completion = True
                accepted_values = values
                next_state = replace(next_state, last_round=rounds, last_elapsed_ms=elapsed_ms)
    if accepted_completion:
        return replace(next_state, completed=True, completion=accepted_values)
    return next_state


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--adb-lock", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=1920)
    args = parser.parse_args()
    command = [args.adb, "-s", args.serial]

    def adb(*values):
        if args.adb_lock:
            with args.adb_lock.open("a") as lock:
                fcntl.flock(lock, fcntl.LOCK_EX)
                return subprocess.check_output(command + list(values), text=True, timeout=15)
        return subprocess.check_output(command + list(values), text=True, timeout=15)

    initial_pid = adb("shell", "pidof", "com.pixelpals.app.debug").strip()
    initial_logs = adb("logcat", "-d", "--pid=" + initial_pid, "-v", "brief", "-s", "CARE_SCENE_LAB:I", "AndroidRuntime:E")
    state = initial_monitor_state(parse_soak_log(initial_logs))
    report = {
        "startedAt": datetime.now(timezone.utc).isoformat(), "serial": args.serial,
        "pid": initial_pid, "runId": None, "runIdAvailable": False,
        "limitations": ["No run id is exposed; historical closures are excluded and same-PID progress resets fail."],
        "completed": False, "samples": [],
    }
    started = time.monotonic()
    while time.monotonic() - started < args.timeout:
        pid = adb("shell", "pidof", "com.pixelpals.app.debug").strip()
        if pid != initial_pid:
            state = replace(state, failure="Process changed during soak")
            break
        memory = adb("shell", "dumpsys", "meminfo", "com.pixelpals.app.debug")
        logs = adb("logcat", "-d", "--pid=" + initial_pid, "-v", "brief", "-s", "CARE_SCENE_LAB:I", "AndroidRuntime:E")
        observation = parse_soak_log(logs)
        state = advance_monitor(state, observation)
        pss = re.search(r"TOTAL PSS:\s*(\d+)|TOTAL\s+(\d+)\s+", memory)
        sample = {"seconds": round(time.monotonic() - started), "pssKb": int(pss.group(1) or pss.group(2)) if pss else None}
        if observation.rounds:
            sample.update(dict(zip(("round", "commits", "elapsedMs", "javaHeapBytes", "activeBitmapBytes"), observation.rounds[-1])))
        report["samples"].append(sample)
        report["completed"] = state.completed and state.failure is None
        if state.completion:
            report["rounds"], report["commits"], report["elapsedMs"] = state.completion
        report["maxPssKb"] = max((item["pssKb"] or 0) for item in report["samples"])
        if state.failure:
            report["failure"] = state.failure
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(sample), flush=True)
        if state.completed or state.failure:
            break
        time.sleep(40)
    if state.failure:
        report.update(completed=False, failure=state.failure)
    if not report["completed"]:
        report.setdefault("failure", "No successful uninterrupted 30 minute completion observed")
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "samples"}), flush=True)
    raise SystemExit(0 if report["completed"] else 1)


if __name__ == "__main__":
    main()
