import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[1] / "compare-shared-runtime-transport-evidence.py"
SPEC = importlib.util.spec_from_file_location("sr6_transport_compare", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class TransportEvidenceComparatorTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def write(self, name: str, content: str) -> Path:
        path = self.root / name
        path.write_text(content, encoding="utf-8")
        return path

    def binder_log(self, digest: str = "a" * 64) -> Path:
        return self.write(
            "binder.log",
            "\n".join(
                [
                    f"SR6_SHARED_RUNTIME identity modelDigestSha256={digest} negotiatedMinor=1 enabledFeatures=streaming",
                    "SR6_SHARED_RUNTIME generationProfile presetId=none presetVersion=-1 contextSize=2048 maxOutputTokens=64 thinkingMode=DISABLED temperature=0.0 topP=0.9 topK=40 minP=0.0 presencePenalty=0.0 repeatPenalty=1.0 repeatLastN=0",
                    "SR6_SHARED_RUNTIME generation ttftMs=120 coreTotalMs=1000 clientObservedTotalMs=1025 transportEnvelopeMs=25 inputTokens=20 outputTokens=30 decodeTokensPerSecond=34.5 stopReason=LENGTH",
                ]
            )
            + "\n",
        )

    def in_process_log(self, digest: str = "a" * 64, case_id: str = "sr6-transport-v1") -> Path:
        records = [
            {
                "tuningCaseId": case_id,
                "modelDigest": digest,
                "contextTokens": 2048,
                "thinkingMode": "DISABLED",
                "modelLoadKind": "COLD",
                "ttftMs": 150,
                "totalMs": 1100,
                "decodeTokensPerSecond": 30.0,
            },
            {
                "tuningCaseId": case_id,
                "modelDigest": digest,
                "contextTokens": 2048,
                "thinkingMode": "DISABLED",
                "modelLoadKind": "WARM",
                "ttftMs": 100,
                "totalMs": 980,
                "decodeTokensPerSecond": 35.0,
            },
            {
                "tuningCaseId": case_id,
                "modelDigest": digest,
                "contextTokens": 2048,
                "thinkingMode": "DISABLED",
                "modelLoadKind": "WARM",
                "ttftMs": 110,
                "totalMs": 1000,
                "decodeTokensPerSecond": 34.0,
            },
            {
                "tuningCaseId": case_id,
                "modelDigest": digest,
                "contextTokens": 2048,
                "thinkingMode": "DISABLED",
                "modelLoadKind": "WARM",
                "ttftMs": 105,
                "totalMs": 990,
                "decodeTokensPerSecond": 34.5,
            },
        ]
        content = "\n".join(
            f"LOCAL_LLM_TUNING_JSON {json.dumps(record)}" for record in records
        ) + "\n"
        return self.write("in-process.log", content)

    def test_builds_comparable_summary_from_matching_warm_samples(self):
        binder = MODULE.parse_binder_log(self.binder_log())
        records = MODULE.parse_in_process_log(self.in_process_log())
        warm = MODULE.matching_warm_records(records, binder, "sr6-transport-v1")
        summary = MODULE.build_summary(binder, warm, "sr6-transport-v1")

        self.assertEqual("COMPARABLE", summary["status"])
        self.assertEqual(3, summary["identity"]["warmSampleCount"])
        self.assertEqual(25.0, summary["binder"]["transportEnvelopeMs"])
        self.assertEqual(990.0, summary["inProcessWarmMedian"]["totalMs"])
        self.assertEqual(10.0, summary["comparison"]["binderCoreVsInProcessMedianDeltaMs"])
        self.assertTrue(summary["comparison"]["transportEnvelopeInternallyConsistent"])

    def test_rejects_model_identity_mismatch(self):
        binder = MODULE.parse_binder_log(self.binder_log("a" * 64))
        records = MODULE.parse_in_process_log(self.in_process_log("b" * 64))

        with self.assertRaises(MODULE.EvidenceError):
            MODULE.matching_warm_records(records, binder, "sr6-transport-v1")

    def test_rejects_wrong_tuning_case(self):
        binder = MODULE.parse_binder_log(self.binder_log())
        records = MODULE.parse_in_process_log(self.in_process_log(case_id="other-case"))

        with self.assertRaises(MODULE.EvidenceError):
            MODULE.matching_warm_records(records, binder, "sr6-transport-v1")

    def test_rejects_inconsistent_transport_envelope(self):
        binder_path = self.write(
            "bad-binder.log",
            "\n".join(
                [
                    f"SR6_SHARED_RUNTIME identity modelDigestSha256={'a' * 64} negotiatedMinor=1 enabledFeatures=streaming",
                    "SR6_SHARED_RUNTIME generationProfile contextSize=2048 maxOutputTokens=64 thinkingMode=DISABLED",
                    "SR6_SHARED_RUNTIME generation ttftMs=120 coreTotalMs=1000 clientObservedTotalMs=1025 transportEnvelopeMs=80 outputTokens=30 decodeTokensPerSecond=34.5",
                ]
            )
            + "\n",
        )
        binder = MODULE.parse_binder_log(binder_path)
        records = MODULE.parse_in_process_log(self.in_process_log())
        warm = MODULE.matching_warm_records(records, binder, "sr6-transport-v1")

        with self.assertRaises(MODULE.EvidenceError):
            MODULE.build_summary(binder, warm, "sr6-transport-v1")


if __name__ == "__main__":
    unittest.main()
