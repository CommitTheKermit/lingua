import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import initSqlJs, { type Database } from "sql.js";
import { beforeAll, afterAll, describe, expect, it } from "vitest";
import { normalizeQuery, queryDictionary } from "../src/services/dictionary";
const require = createRequire(import.meta.url);
let db: Database;
beforeAll(async () => {
  const SQL = await initSqlJs({
    locateFile: (file) => require.resolve(`sql.js/dist/${file}`),
  });
  db = new SQL.Database(
    await readFile(
      new URL(
        "../../shared/src/commonMain/composeResources/files/dict/wiktionary_en_ko.db",
        import.meta.url,
      ),
    ),
  );
});
afterAll(() => db.close());
describe("앱 번들 사전으로 실제 조회", () => {
  it.each(["dog", "run", "bank", "ran"])(
    "%s의 한국어 풀이를 반환한다",
    (word) => {
      const entries = queryDictionary(db, word);
      expect(entries.length).toBeGreaterThan(0);
      expect(
        entries.some((entry) =>
          entry.senses.some((sense) => /[가-힣]/.test(sense)),
        ),
      ).toBe(true);
    },
  );
  it("과거형 ran은 원형 run의 동사 뜻으로 연결한다", () => {
    expect(queryDictionary(db, "ran")).toEqual(
      queryDictionary(db, "run").filter(
        (entry) => entry.partOfSpeech === "동사",
      ),
    );
  });
  it("대소문자와 구두점을 정규화하고 모르는 단어는 빈 결과를 반환한다", () => {
    expect(normalizeQuery("  (DOG!)  ")).toBe("dog");
    expect(queryDictionary(db, "(DOG!)")).toEqual(queryDictionary(db, "dog"));
    expect(queryDictionary(db, "zzzzlinguatestword")).toEqual([]);
    expect(queryDictionary(db, "  ")).toEqual([]);
  });
  it("SQLite가 정상이고 라이선스 메타데이터가 있다", () => {
    expect(db.exec("PRAGMA integrity_check")[0].values[0][0]).toBe("ok");
    expect(
      db.exec("SELECT license FROM dictionary_metadata")[0].values[0][0],
    ).toBe("https://creativecommons.org/licenses/by-sa/4.0/");
  });
});
