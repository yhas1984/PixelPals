import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from monitor_soak import advance_monitor, initial_monitor_state, parse_soak_log


class MonitorSoakTest(unittest.TestCase):
    def test_cumulative_repeated_and_rotated_logs_do_not_reset(self):
        def rounds(*numbers):
            return parse_soak_log("\n".join(
                f"round={n} commits={n} elapsed={n * 4000} heap=1 bitmap=2" for n in numbers))
        state = initial_monitor_state(rounds(1, 2))
        for observation in (rounds(1, 2, 3), rounds(1, 2, 3), rounds(), rounds(2, 3, 4)):
            state = advance_monitor(state, observation)
            self.assertIsNone(state.failure)
            self.assertFalse(state.completed)
        self.assertEqual(4, state.last_round)

    def test_old_close_followed_by_active_run_in_baseline_is_ignored(self):
        old = "SOAK_COMPLETE rounds=386 commits=386 elapsed=1801691\n"
        first = "round=4 commits=4 elapsed=16000 heap=1 bitmap=2\n"
        state = initial_monitor_state(parse_soak_log(old + first))
        result = advance_monitor(state, parse_soak_log(old + first +
            "round=5 commits=5 elapsed=20000 heap=1 bitmap=2"))
        self.assertFalse(result.completed)
        self.assertIsNone(result.failure)

    def test_new_round_after_completion_is_not_success(self):
        state = initial_monitor_state(parse_soak_log(""))
        result = advance_monitor(state, parse_soak_log(
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1800000\n"
            "round=1 commits=1 elapsed=4000 heap=1 bitmap=2"))
        self.assertFalse(result.completed)
        self.assertIsNotNone(result.failure)

    def test_fatal_revokes_previously_accepted_success(self):
        state = advance_monitor(initial_monitor_state(parse_soak_log("")), parse_soak_log(
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1800000"))
        self.assertTrue(state.completed)
        result = advance_monitor(state, parse_soak_log("FATAL EXCEPTION"))
        self.assertFalse(result.completed)
        self.assertIsNotNone(result.failure)

    def test_historical_completion_followed_by_new_lower_round_fails(self):
        baseline = parse_soak_log(
            "round=386 commits=386 elapsed=1801691 heap=1 bitmap=2\n"
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1801691"
        )
        state = initial_monitor_state(baseline)
        current = parse_soak_log(
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1801691\n"
            "round=4 commits=4 elapsed=16000 heap=1 bitmap=2"
        )
        result = advance_monitor(state, current)
        self.assertFalse(result.completed)
        self.assertIn("reset", result.failure)

    def test_new_valid_completion_is_accepted(self):
        state = initial_monitor_state(parse_soak_log("round=1 commits=1 elapsed=4000 heap=1 bitmap=2"))
        current = parse_soak_log(
            "round=2 commits=2 elapsed=8000 heap=1 bitmap=2\n"
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1800000"
        )
        result = advance_monitor(state, current)
        self.assertTrue(result.completed)
        self.assertIsNone(result.failure)

    def test_fatal_closure_cannot_complete(self):
        state = initial_monitor_state(parse_soak_log("round=10 commits=10 elapsed=40000 heap=1 bitmap=2"))
        current = parse_soak_log(
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1800000\n"
            "AndroidRuntime: FATAL EXCEPTION: main"
        )
        result = advance_monitor(state, current)
        self.assertFalse(result.completed)
        self.assertIn("Fatal", result.failure)

    def test_round_reset_same_pid_fails(self):
        state = initial_monitor_state(parse_soak_log("round=120 commits=120 elapsed=500000 heap=1 bitmap=2"))
        result = advance_monitor(state, parse_soak_log("round=3 commits=3 elapsed=12000 heap=1 bitmap=2"))
        self.assertFalse(result.completed)
        self.assertIn("regressed", result.failure)

    def test_parser_preserves_completion_and_new_round_order(self):
        observation = parse_soak_log(
            "SOAK_COMPLETE rounds=386 commits=386 elapsed=1801691\n"
            "round=2 commits=2 elapsed=8000 heap=1 bitmap=2"
        )
        self.assertEqual(("complete", "round"), tuple(kind for kind, _ in observation.events))


if __name__ == "__main__":
    unittest.main()
