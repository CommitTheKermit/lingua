import initSqlJs, { type Database } from "sql.js";
import wasmUrl from "sql.js/dist/sql-wasm.wasm?url";
export type DictionaryEntry = {
  headword: string;
  partOfSpeech: string;
  senses: string[];
};
export function normalizeQuery(raw: string) {
  return raw
    .trim()
    .toLowerCase()
    .replace(/^[^\p{L}\p{N}'-]+|[^\p{L}\p{N}'-]+$/gu, "");
}
export function queryDictionary(db: Database, raw: string): DictionaryEntry[] {
  const query = normalizeQuery(raw);
  if (!query) return [];
  const statement =
    db.prepare(`SELECT e.id, e.headword, e.part_of_speech, s.korean_definition
    FROM dictionary_lookup_key k JOIN dictionary_entry e ON e.id = k.entry_id
    JOIN dictionary_sense s ON s.entry_id = e.id WHERE k.lookup_key = ?
    ORDER BY k.priority, e.headword, e.part_of_speech, s.sequence`);
  const entries = new Map<number, DictionaryEntry>();
  try {
    statement.bind([query]);
    while (statement.step()) {
      const [id, headword, partOfSpeech, sense] = statement.get();
      const entry = entries.get(Number(id)) ?? {
        headword: String(headword),
        partOfSpeech: String(partOfSpeech),
        senses: [],
      };
      entry.senses.push(String(sense));
      entries.set(Number(id), entry);
    }
  } finally {
    statement.free();
  }
  return [...entries.values()];
}
let dictionary: Promise<Database> | undefined;
async function getDatabase() {
  if (!dictionary)
    dictionary = Promise.all([
      initSqlJs({ locateFile: () => wasmUrl }),
      fetch("/dict/wiktionary_en_ko.db").then((response) => {
        if (!response.ok)
          throw new Error(
            "사전을 불러오지 못했습니다. 연결 후 다시 시도해 주세요.",
          );
        return response.arrayBuffer();
      }),
    ])
      .then(([SQL, bytes]) => new SQL.Database(new Uint8Array(bytes)))
      .catch((error) => {
        dictionary = undefined;
        throw error;
      });
  return dictionary;
}
export async function searchDictionary(query: string) {
  return queryDictionary(await getDatabase(), query);
}
