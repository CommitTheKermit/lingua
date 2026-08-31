#!/usr/bin/env python3
"""Kaikki 한국어 위키낱말사전 추출물로 오프라인 영한 사전을 만든다."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import tempfile
import unicodedata
from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from pathlib import Path


LICENSE_NAME = "CC BY-SA 4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
POS_NAMES = {
    "adj": "형용사",
    "adv": "부사",
    "noun": "명사",
    "verb": "동사",
}
APOSTROPHES = str.maketrans({"‘": "'", "’": "'"})
CURATED_FORMS = {
    # ponytail: 현재 품질 표본만 보완한다. 범위가 늘면 검증된 활용형 데이터로 교체한다.
    ("run", "verb"): ("ran", "running"),
}


@dataclass(frozen=True)
class BuildMetadata:
    data_version: str
    source_url: str
    source_sha256: str
    generated_at: str


def normalize_lookup(text: str) -> str:
    """조회 입력을 DB 조회 키와 같은 형식으로 정규화한다."""
    normalized = " ".join(
        unicodedata.normalize("NFKC", text).translate(APOSTROPHES).split()
    ).casefold()
    start = 0
    end = len(normalized)
    while start < end and unicodedata.category(normalized[start]).startswith("P"):
        start += 1
    while end > start and unicodedata.category(normalized[end - 1]).startswith("P"):
        end -= 1
    return normalized[start:end].strip()


def _display_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(unicodedata.normalize("NFKC", value).translate(APOSTROPHES).split())


def _entry_data(entry: object) -> tuple[str, str, list[str], list[str]] | None:
    if not isinstance(entry, dict):
        return None
    if entry.get("lang_code") != "en":
        return None

    headword = _display_text(entry.get("word"))
    if not headword:
        return None

    definitions: list[str] = []
    senses = entry.get("senses")
    if not isinstance(senses, list):
        senses = []
    for sense in senses:
        if not isinstance(sense, dict):
            continue
        glosses = sense.get("glosses")
        if not isinstance(glosses, list):
            continue
        for gloss in glosses:
            definition = _display_text(gloss)
            if definition and definition not in definitions:
                definitions.append(definition)
    if not definitions:
        return None

    pos = _display_text(entry.get("pos"))
    part_of_speech = _display_text(entry.get("pos_title"))
    if not part_of_speech or part_of_speech.casefold() == "unknown":
        part_of_speech = POS_NAMES.get(pos, "기타" if pos.casefold() == "unknown" else pos)

    forms: list[str] = []
    raw_forms = entry.get("forms")
    if not isinstance(raw_forms, list):
        raw_forms = []
    for form in raw_forms:
        if isinstance(form, dict):
            lookup = normalize_lookup(_display_text(form.get("form")))
            if lookup and lookup not in forms:
                forms.append(lookup)
    for lookup in CURATED_FORMS.get((normalize_lookup(headword), pos), ()):
        if lookup not in forms:
            forms.append(lookup)
    return headword, part_of_speech, definitions, forms


def _create_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        PRAGMA page_size = 4096;
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA foreign_keys = ON;
        CREATE TABLE dictionary_entry (
            id INTEGER PRIMARY KEY,
            headword TEXT NOT NULL,
            part_of_speech TEXT NOT NULL,
            UNIQUE (headword, part_of_speech)
        );
        CREATE TABLE dictionary_sense (
            entry_id INTEGER NOT NULL REFERENCES dictionary_entry (id),
            sequence INTEGER NOT NULL,
            korean_definition TEXT NOT NULL,
            PRIMARY KEY (entry_id, sequence),
            UNIQUE (entry_id, korean_definition)
        );
        CREATE TABLE dictionary_lookup_key (
            lookup_key TEXT NOT NULL,
            entry_id INTEGER NOT NULL REFERENCES dictionary_entry (id),
            priority INTEGER NOT NULL,
            PRIMARY KEY (lookup_key, entry_id)
        );
        CREATE TABLE dictionary_metadata (
            data_version TEXT NOT NULL,
            source_url TEXT NOT NULL,
            source_sha256 TEXT NOT NULL,
            license TEXT NOT NULL,
            generated_at TEXT NOT NULL
        );
        PRAGMA user_version = 1;
        """
    )


def build_database(
    lines: Iterable[str],
    database_path: Path,
    metadata: BuildMetadata,
) -> int:
    """JSONL 입력을 원자적으로 새 SQLite 데이터베이스로 교체한다."""
    database_path = Path(database_path)
    database_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = tempfile.NamedTemporaryFile(
        prefix=f".{database_path.name}.", dir=database_path.parent, delete=False
    )
    temporary_path = Path(temporary_file.name)
    temporary_file.close()
    connection: sqlite3.Connection | None = None
    try:
        connection = sqlite3.connect(temporary_path)
        _create_schema(connection)

        for line in lines:
            data = _entry_data(json.loads(line))
            if data is None:
                continue
            headword, part_of_speech, definitions, forms = data
            connection.execute(
                "INSERT OR IGNORE INTO dictionary_entry (headword, part_of_speech) VALUES (?, ?)",
                (headword, part_of_speech),
            )
            entry_id = connection.execute(
                "SELECT id FROM dictionary_entry WHERE headword = ? AND part_of_speech = ?",
                (headword, part_of_speech),
            ).fetchone()[0]

            existing_senses = connection.execute(
                "SELECT sequence, korean_definition FROM dictionary_sense "
                "WHERE entry_id = ? ORDER BY sequence",
                (entry_id,),
            ).fetchall()
            known_definitions = {row[1] for row in existing_senses}
            next_sequence = len(existing_senses)
            for definition in definitions:
                if definition in known_definitions:
                    continue
                connection.execute(
                    "INSERT INTO dictionary_sense "
                    "(entry_id, sequence, korean_definition) VALUES (?, ?, ?)",
                    (entry_id, next_sequence, definition),
                )
                known_definitions.add(definition)
                next_sequence += 1

            lookups = [(normalize_lookup(headword), 0), *((form, 1) for form in forms)]
            for lookup_key, priority in lookups:
                if not lookup_key or len(lookup_key) > 128:
                    continue
                connection.execute(
                    "INSERT INTO dictionary_lookup_key (lookup_key, entry_id, priority) "
                    "VALUES (?, ?, ?) ON CONFLICT (lookup_key, entry_id) DO UPDATE SET "
                    "priority = MIN(priority, excluded.priority)",
                    (lookup_key, entry_id, priority),
                )

        connection.execute(
            "INSERT INTO dictionary_metadata "
            "(data_version, source_url, source_sha256, license, generated_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (
                metadata.data_version,
                metadata.source_url,
                metadata.source_sha256,
                LICENSE_URL,
                metadata.generated_at,
            ),
        )
        entry_count = connection.execute("SELECT COUNT(*) FROM dictionary_entry").fetchone()[0]
        connection.commit()
        connection.execute("VACUUM")
        connection.close()
        connection = None
        os.replace(temporary_path, database_path)
        database_path.chmod(0o644)
        return entry_count
    finally:
        if connection is not None:
            connection.close()
        temporary_path.unlink(missing_ok=True)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_notice(notice_path: Path, metadata: BuildMetadata, database_path: Path) -> None:
    notice_path = Path(notice_path)
    notice_path.parent.mkdir(parents=True, exist_ok=True)
    notice_path.write_text(
        "\n".join(
            [
                "Lingua 오프라인 영한 사전",
                "",
                "한국어 위키낱말사전의 영어 표제어 데이터를 Kaikki.org의 Wiktextract 추출물로 가공했습니다.",
                f"데이터 버전: {metadata.data_version}",
                f"생성일: {metadata.generated_at}",
                f"입력 출처: {metadata.source_url}",
                f"입력 SHA-256: {metadata.source_sha256}",
                f"DB SHA-256: {_sha256(Path(database_path))}",
                f"라이선스: {LICENSE_NAME} ({LICENSE_URL})",
                "",
                "원본과 파생 데이터의 이용 시 저작자 표시와 동일조건변경허락 조건을 따라야 합니다.",
                "",
            ]
        ),
        encoding="utf-8",
        newline="\n",
    )


def _download(source: str, destination: Path) -> None:
    if source.startswith(("https://", "http://")):
        subprocess.run(
            [
                "curl",
                "--fail",
                "--location",
                "--silent",
                "--show-error",
                source,
                "--output",
                str(destination),
            ],
            check=True,
        )
    else:
        shutil.copyfile(source, destination)


def _text_lines(source_path: Path) -> Iterator[str]:
    with source_path.open("rb") as source:
        is_gzip = source.read(2) == b"\x1f\x8b"
    opener = gzip.open if is_gzip else open
    with opener(source_path, "rt", encoding="utf-8") as source:
        yield from source


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--notice", required=True, type=Path)
    parser.add_argument("--data-version", required=True)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--source-url", required=True)
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as temporary_directory:
        source_path = Path(temporary_directory) / "source"
        _download(args.source, source_path)
        metadata = BuildMetadata(
            data_version=args.data_version,
            source_url=args.source_url,
            source_sha256=_sha256(source_path),
            generated_at=args.generated_at,
        )
        entry_count = build_database(_text_lines(source_path), args.output, metadata)
    write_notice(args.notice, metadata, args.output)
    print(f"사전 표제어 {entry_count:,}개 생성: {args.output}")


if __name__ == "__main__":
    main()
