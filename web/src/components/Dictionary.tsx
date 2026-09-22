import { useEffect, useRef, useState } from "react";
import { Search, LoaderCircle, BookOpen } from "lucide-react";
import { Modal } from "./Modal";
import {
  searchDictionary,
  normalizeQuery,
  type DictionaryEntry,
} from "../services/dictionary";
import {
  translateMissingWord,
  type TranslationResult,
} from "../services/translation";

export function Dictionary({
  initialQuery,
  onClose,
  onUsage,
}: {
  initialQuery: string;
  onClose: () => void;
  onUsage: (result: TranslationResult) => void;
}) {
  const [query, setQuery] = useState(initialQuery);
  const [entries, setEntries] = useState<DictionaryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [lookupError, setLookupError] = useState("");
  const [remote, setRemote] = useState("");
  const [error, setError] = useState("");
  const [translating, setTranslating] = useState(false);
  const [retry, setRetry] = useState(0);
  const generation = useRef(0);
  const activeRequest = useRef(false);
  useEffect(() => {
    const id = ++generation.current;
    activeRequest.current = false;
    setLoading(true);
    setLookupError("");
    setEntries([]);
    setRemote("");
    setError("");
    setTranslating(false);
    searchDictionary(query)
      .then((result) => {
        if (generation.current === id) {
          setEntries(result);
          setLoading(false);
        }
      })
      .catch(() => {
        if (generation.current === id) {
          setLookupError(
            "사전을 불러오지 못했습니다. 인터넷 연결 후 다시 시도해 주세요.",
          );
          setLoading(false);
        }
      });
    return () => {
      generation.current++;
    };
  }, [query, retry]);
  function changeQuery(value: string) {
    generation.current++;
    activeRequest.current = false;
    setQuery(value);
    setLoading(true);
    setEntries([]);
    setError("");
    setRemote("");
  }
  async function translate() {
    if (
      activeRequest.current ||
      loading ||
      lookupError ||
      entries.length ||
      !normalizeQuery(query)
    )
      return;
    activeRequest.current = true;
    const id = generation.current;
    setTranslating(true);
    setError("");
    try {
      const result = await translateMissingWord(normalizeQuery(query));
      onUsage(result);
      if (generation.current === id) setRemote(result.translated);
    } catch (reason) {
      if (generation.current === id) setError((reason as Error).message);
    } finally {
      if (generation.current === id) {
        activeRequest.current = false;
        setTranslating(false);
      }
    }
  }
  return (
    <Modal title="영한 사전" onClose={onClose} className="dictionary-modal">
      <div className="dictionary-search">
        <Search size={20} />
        <input
          autoFocus
          aria-label="사전 검색어"
          placeholder="영어 단어를 검색하세요"
          value={query}
          onChange={(event) => changeQuery(event.target.value)}
        />
        <kbd>EN</kbd>
      </div>
      <div
        className="dictionary-results"
        aria-live="polite"
        aria-busy={loading || translating}
      >
        {loading ? (
          <div className="lookup-message">
            <LoaderCircle className="spin" size={24} />
            사전을 찾고 있습니다
          </div>
        ) : lookupError ? (
          <div className="lookup-message" role="alert">
            <p>{lookupError}</p>
            <button
              className="primary-button"
              onClick={() => setRetry(retry + 1)}
            >
              사전 다시 불러오기
            </button>
          </div>
        ) : !normalizeQuery(query) ? (
          <div className="lookup-message">
            <BookOpen size={34} />
            <p>궁금한 영어 단어를 찾아보세요.</p>
            <small>인터넷 없이도 한국어 뜻을 확인할 수 있습니다.</small>
          </div>
        ) : entries.length ? (
          <>
            <h3 className="headword">{entries[0].headword}</h3>
            {entries.map((entry, index) => (
              <article className="dictionary-entry" key={index}>
                <div className="entry-heading">
                  <strong>{entry.headword}</strong>
                  <span className="part-of-speech">{entry.partOfSpeech}</span>
                </div>
                <ol>
                  {entry.senses.map((sense, i) => (
                    <li key={i}>{sense}</li>
                  ))}
                </ol>
              </article>
            ))}
          </>
        ) : (
          <div className="missing-word">
            <h3 className="headword">{normalizeQuery(query)}</h3>
            <p>사전에 없는 단어입니다.</p>
            <p className="muted">
              필요한 경우 DeepL로 뜻을 확인할 수 있습니다.
            </p>
            {remote ? (
              <article className="remote-result">
                <span className="part-of-speech">DeepL 번역 결과</span>
                <p>{remote}</p>
              </article>
            ) : (
              <button
                className="primary-button"
                onClick={translate}
                disabled={translating}
              >
                {translating ? (
                  <>
                    <LoaderCircle className="spin" size={18} />
                    번역 중
                  </>
                ) : error ? (
                  "다시 시도"
                ) : (
                  "DeepL로 번역"
                )}
              </button>
            )}
            {error && (
              <p className="error-message" role="alert">
                {error}
              </p>
            )}
          </div>
        )}
      </div>
      <footer className="dictionary-footer">
        <span>한국어 위키낱말사전 · CC BY-SA 4.0</span>
        <a href="/dict/NOTICE.txt" target="_blank" rel="noreferrer">
          출처 및 라이선스
        </a>
      </footer>
    </Modal>
  );
}
