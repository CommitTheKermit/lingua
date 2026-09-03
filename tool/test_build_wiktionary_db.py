import hashlib
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from tool.build_wiktionary_db import (
    BuildMetadata,
    build_database,
    normalize_lookup,
    write_notice,
)


SOURCE_URL = "https://example.test/ko-extract.jsonl.gz"
SOURCE_SHA256 = "1" * 64
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
METADATA = BuildMetadata(
    data_version="fixture-2026-08-31",
    source_url=SOURCE_URL,
    source_sha256=SOURCE_SHA256,
    generated_at="2026-08-31T00:00:00Z",
)
ROOT = Path(__file__).resolve().parents[1]
BUNDLED_DATABASE = (
    ROOT
    / "shared/src/commonMain/composeResources/files/dict/wiktionary_en_ko.db"
)
BUNDLED_NOTICE = BUNDLED_DATABASE.with_name("NOTICE.txt")


def fixture_lines() -> list[str]:
    entries = [
        {
            "word": "dog",
            "lang_code": "en",
            "pos": "noun",
            "senses": [{"glosses": ["개.", "", "개.", "사냥개."]}],
        },
        {
            "word": "dog",
            "lang_code": "en",
            "pos": "verb",
            "senses": [{"glosses": ["미행하다."]}],
        },
        {
            "word": "run",
            "lang_code": "en",
            "pos": "verb",
            "senses": [{"glosses": ["달리다.", "작동하다."]}],
        },
        {
            "word": "run",
            "lang_code": "en",
            "pos": "noun",
            "senses": [{"glosses": ["달리기, 구보."]}],
        },
        {
            "word": "bank",
            "lang_code": "en",
            "pos": "noun",
            "senses": [{"glosses": ["은행.", "둑, 제방."]}],
        },
        {
            "word": "bank",
            "lang_code": "en",
            "pos": "verb",
            "senses": [{"glosses": ["은행과 거래하다."]}],
        },
        {
            "word": "달리다",
            "lang_code": "ko",
            "pos": "verb",
            "senses": [{"glosses": ["run"]}],
        },
    ]
    return [json.dumps(entry, ensure_ascii=False) for entry in entries]


class BuildWiktionaryDatabaseTest(unittest.TestCase):
    def test_builds_ordered_dictionary_with_lookup_keys_and_metadata(self):
        expected = {
            "dog": [
                ("명사", 0, "개."),
                ("명사", 1, "사냥개."),
                ("동사", 0, "미행하다."),
            ],
            "run": [
                ("동사", 0, "달리다."),
                ("동사", 1, "작동하다."),
                ("명사", 0, "달리기, 구보."),
            ],
            "bank": [
                ("명사", 0, "은행."),
                ("명사", 1, "둑, 제방."),
                ("동사", 0, "은행과 거래하다."),
            ],
        }

        with tempfile.TemporaryDirectory() as temporary:
            database = Path(temporary) / "dictionary.db"
            self.assertEqual(6, build_database(fixture_lines(), database, METADATA))

            connection = sqlite3.connect(database)
            for headword, rows in expected.items():
                actual = connection.execute(
                    """
                    SELECT e.part_of_speech, s.sequence, s.korean_definition
                    FROM dictionary_entry AS e
                    JOIN dictionary_sense AS s ON s.entry_id = e.id
                    WHERE e.headword = ?
                    ORDER BY e.id, s.sequence
                    """,
                    (headword,),
                ).fetchall()
                self.assertEqual(rows, actual)

            lookups = connection.execute(
                """
                SELECT k.lookup_key, e.headword, e.part_of_speech, k.priority
                FROM dictionary_lookup_key AS k
                JOIN dictionary_entry AS e ON e.id = k.entry_id
                WHERE k.lookup_key IN ('run', 'ran', 'running')
                ORDER BY k.lookup_key, e.id
                """
            ).fetchall()
            self.assertEqual(
                [
                    ("ran", "run", "동사", 1),
                    ("run", "run", "동사", 0),
                    ("run", "run", "명사", 0),
                    ("running", "run", "동사", 1),
                ],
                lookups,
            )
            self.assertEqual(
                (6, 0),
                connection.execute(
                    """
                    SELECT
                        SUM(k.priority = 0),
                        SUM(k.priority != 0)
                    FROM dictionary_lookup_key AS k
                    JOIN dictionary_entry AS e ON e.id = k.entry_id
                    WHERE k.lookup_key = e.headword
                    """
                ).fetchone(),
            )
            self.assertEqual(
                0,
                connection.execute(
                    "SELECT COUNT(*) FROM dictionary_entry WHERE headword = '달리다'"
                ).fetchone()[0],
            )
            self.assertEqual(
                [],
                connection.execute(
                    "SELECT entry_id FROM dictionary_lookup_key WHERE lookup_key = ?",
                    ("zzzzlinguafixturemissingwordzzzz",),
                ).fetchall(),
            )

            expected_columns = {
                "dictionary_entry": ["id", "headword", "part_of_speech"],
                "dictionary_sense": ["entry_id", "sequence", "korean_definition"],
                "dictionary_lookup_key": ["lookup_key", "entry_id", "priority"],
                "dictionary_metadata": [
                    "data_version",
                    "source_url",
                    "source_sha256",
                    "license",
                    "generated_at",
                ],
            }
            for table, columns in expected_columns.items():
                actual = [
                    row[1] for row in connection.execute(f"PRAGMA table_info({table})")
                ]
                self.assertEqual(columns, actual)
            metadata = connection.execute(
                """
                SELECT data_version, source_url, source_sha256, license, generated_at
                FROM dictionary_metadata
                """
            ).fetchone()
            connection.close()

        self.assertEqual(
            (
                METADATA.data_version,
                SOURCE_URL,
                SOURCE_SHA256,
                LICENSE_URL,
                METADATA.generated_at,
            ),
            metadata,
        )

    def test_normalizes_case_whitespace_and_boundary_punctuation(self):
        self.assertEqual("run", normalize_lookup("\t“RUN,”\n"))

    def test_writes_traceable_notice_and_reproducible_database(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            first = directory / "first.db"
            second = directory / "second.db"
            notice = directory / "NOTICE.txt"
            build_database(fixture_lines(), first, METADATA)
            build_database(fixture_lines(), second, METADATA)
            write_notice(notice, METADATA, first)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            notice_text = notice.read_text(encoding="utf-8")
            database_sha256 = hashlib.sha256(first.read_bytes()).hexdigest()
            for expected in (
                SOURCE_URL,
                "CC BY-SA 4.0",
                METADATA.data_version,
                SOURCE_SHA256,
                database_sha256,
            ):
                self.assertIn(expected, notice_text)

    def test_bundled_asset_passes_representative_quality_gate(self):
        connection = sqlite3.connect(BUNDLED_DATABASE)
        self.assertEqual("ok", connection.execute("PRAGMA integrity_check").fetchone()[0])

        rows = connection.execute(
            """
            SELECT k.lookup_key, e.headword, e.part_of_speech, k.priority,
                   s.korean_definition
            FROM dictionary_lookup_key AS k
            JOIN dictionary_entry AS e ON e.id = k.entry_id
            JOIN dictionary_sense AS s ON s.entry_id = e.id
            WHERE k.lookup_key IN ('dog', 'run', 'bank', 'ran', 'running')
            ORDER BY k.lookup_key, k.priority, e.id, s.sequence
            """
        ).fetchall()
        by_lookup = {
            lookup: [row for row in rows if row[0] == lookup]
            for lookup in ("dog", "run", "bank", "ran", "running")
        }
        self.assertTrue(any(row[2:] == ("명사", 0, "개.") for row in by_lookup["dog"]))
        self.assertTrue(any(row[2] == "동사" and "미행하다" in row[4] for row in by_lookup["dog"]))
        self.assertTrue(any("달리다" in row[4] for row in by_lookup["run"]))
        self.assertTrue(any("작동하다" in row[4] for row in by_lookup["run"]))
        self.assertTrue(any("은행" in row[4] for row in by_lookup["bank"]))
        self.assertTrue(any("제방" in row[4] for row in by_lookup["bank"]))
        for lookup in ("ran", "running"):
            self.assertTrue(by_lookup[lookup])
            self.assertTrue(all(row[1:4] == ("run", "동사", 1) for row in by_lookup[lookup]))

        self.assertEqual(
            0,
            connection.execute(
                "SELECT COUNT(*) FROM dictionary_lookup_key WHERE lookup_key = ?",
                ("zzzzlinguafixturemissingwordzzzz",),
            ).fetchone()[0],
        )
        metadata = connection.execute(
            "SELECT data_version, source_sha256, license FROM dictionary_metadata"
        ).fetchone()
        connection.close()

        self.assertEqual(
            (
                "kaikki-ko-2026-08-28-3bf5784e7600",
                "3bf5784e7600cfc557d70ff504bc3a4fc3fd9c42bd54e74946a8f0d6861d58bd",
                LICENSE_URL,
            ),
            metadata,
        )
        database_sha256 = hashlib.sha256(BUNDLED_DATABASE.read_bytes()).hexdigest()
        self.assertIn(database_sha256, BUNDLED_NOTICE.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
