import { useEffect, useRef, useState } from "react";
import {
  ArrowDownToLine,
  Bookmark,
  BookOpen,
  Check,
  ChevronRight,
  FileText,
  LoaderCircle,
  PanelLeftClose,
  Search,
  SlidersHorizontal,
  ShieldCheck,
  WifiOff,
  X,
} from "lucide-react";
import { LibraryDialog } from "./components/LibraryDialog";
import { sampleDocument } from "./domain/sample";
import { useReader } from "./useReader";
import { wordTokens } from "./domain/reader";
import { downloadTranslations } from "./domain/export";
import { Icon } from "./components/Icon";
import { Modal } from "./components/Modal";
import { Dictionary } from "./components/Dictionary";
import { Settings, textStyle } from "./components/Settings";
import { TranslationProfileDialog } from "./components/TranslationProfileDialog";
import {
  BookmarkDialog,
  JumpDialog,
  SearchDialog,
} from "./components/ReaderDialogs";
import { Viewer } from "./components/Viewer";
import type { SentenceTranslationKey } from "./services/machineTranslation";
import { useCurrentSentenceTranslation } from "./useCurrentSentenceTranslation";
import type { TranslationResult } from "./services/translation";
import {
  defaultTranslationProfile,
  translationProfilePresets,
} from "./domain/translationProfile";

type DialogName =
  | "library"
  | "search"
  | "bookmarks"
  | "settings"
  | "translation-profile"
  | "jump"
  | "menu"
  | null;
export default function App() {
  const reader = useReader();
  const doc = reader.state.document;
  const [dialog, setDialog] = useState<DialogName>(null);
  const [dictionary, setDictionary] = useState<string | null>(null);
  const [viewer, setViewer] = useState(false);
  const [translationVisible, setTranslationVisible] = useState(true);
  const [showInput, setShowInput] = useState(true);
  const [fileError, setFileError] = useState("");
  const [opening, setOpening] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [online, setOnline] = useState(navigator.onLine);
  const [offlineReady, setOfflineReady] = useState(false);
  const [usage, setUsage] = useState<TranslationResult | null>(null);
  const [timerStarted, setTimerStarted] = useState<number | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [tick, setTick] = useState(Date.now());
  const fileInput = useRef<HTMLInputElement>(null);
  const translationInput = useRef<HTMLTextAreaElement>(null);
  const scrollRequest = useRef<number | null>(null);
  const position = doc ? `${doc.index + 1}/${doc.sentences.length}` : "0/0";
  const seconds = Math.floor(
    (elapsed + (timerStarted ? tick - timerStarted : 0)) / 1000,
  );
  const timeLabel = `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  function toggleTimer() {
    if (timerStarted) {
      setElapsed((value) => value + Date.now() - timerStarted);
      setTimerStarted(null);
    } else {
      setTick(Date.now());
      setTimerStarted(Date.now());
    }
  }
  useEffect(() => {
    const timer = setInterval(() => setTick(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    if ("serviceWorker" in navigator && import.meta.env.PROD)
      navigator.serviceWorker
        .register("/sw.js", { updateViaCache: "none" })
        .then(() => navigator.serviceWorker.ready)
        .then(() => setOfflineReady(true))
        .catch(() => {});
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  function navigate(index: number) {
    scrollRequest.current = index;
    reader.navigate(index);
  }
  const translationProfile = doc
    ? reader.state.translationProfilesByDocument[doc.id] ??
      defaultTranslationProfile()
    : defaultTranslationProfile();
  const translationProfileName =
    translationProfilePresets.find(
      (preset) => preset.presetId === translationProfile.presetId,
    )?.name ?? "직접 설정";
  const sentenceTranslationKey: SentenceTranslationKey | null = doc
    ? {
        documentId: doc.id,
        sentenceIndex: doc.index,
        sentence: doc.sentences[doc.index],
        context: translationProfile.context,
        instructions: translationProfile.instructions,
      }
    : null;
  const currentSentenceTranslation = useCurrentSentenceTranslation(
    sentenceTranslationKey,
    !!sentenceTranslationKey &&
      !viewer &&
      dialog !== "settings" &&
      dialog !== "translation-profile" &&
      translationVisible,
    setUsage,
  );
  const machineTranslation = currentSentenceTranslation.state;
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if (
        !doc ||
        dialog ||
        dictionary !== null ||
        (event.target instanceof HTMLElement &&
          event.target.closest(
            "input,textarea,select,button,[contenteditable]",
          ))
      )
        return;
      if (event.key === "ArrowLeft") {
        event.preventDefault();
        navigate(doc.index - 1);
      }
      if (event.key === "ArrowRight") {
        event.preventDefault();
        navigate(doc.index + 1);
      }
    };
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  });
  async function openFile(file: File) {
    setFileError("");
    setDragging(false);
    setOpening(true);
    try {
      if (!/\.txt$/i.test(file.name))
        throw new Error("TXT 파일을 선택해 주세요.");
      if (file.size > 10 * 1024 * 1024)
        throw new Error("10MB 이하의 TXT 파일을 선택해 주세요.");
      const bytes = new Uint8Array(await file.arrayBuffer());
      const encoding =
        bytes[0] === 0xff && bytes[1] === 0xfe
          ? "utf-16le"
          : bytes[0] === 0xfe && bytes[1] === 0xff
            ? "utf-16be"
            : "utf-8";
      let content: string;
      try {
        content = new TextDecoder(encoding, { fatal: true }).decode(bytes);
      } catch {
        throw new Error("UTF-8 또는 UTF-16로 저장된 TXT 파일을 선택해 주세요.");
      }
      reader.open(file.name, content);
      setViewer(false);
      setDialog(null);
    } catch (reason) {
      setFileError((reason as Error).message);
    } finally {
      setOpening(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  }
  function openSample() {
    reader.open(sampleDocument.title, sampleDocument.content);
    setViewer(false);
    setDialog(null);
    setFileError("");
  }
  function focusInput() {
    setShowInput(true);
    setViewer(false);
    requestAnimationFrame(() => translationInput.current?.focus());
  }
  const onSelect = (index: number) => {
    navigate(index);
    setDialog(null);
  };
  const navigation = (
    <>
      <button
        className="file-open"
        onClick={() => {
          setDialog(null);
          fileInput.current?.click();
        }}
        disabled={opening}
      >
        <Icon name="menu_file" />
        <span>파일 열기</span>
        <span className="file-extension">TXT</span>
      </button>
      <button className="nav-item" onClick={() => setDialog("library")}>
        <BookOpen size={20} /><span>내 서재</span>
      </button>
      <div className="nav-group">
        <button
          className={!viewer ? "nav-item active" : "nav-item"}
          disabled={!doc}
          onClick={() => {
            setViewer(false);
            setDialog(null);
          }}
        >
          <Icon name="edit" />
          <span>문장 번역</span>
        </button>
        <button
          className={viewer ? "nav-item active" : "nav-item"}
          disabled={!doc}
          onClick={() => {
            setViewer(true);
            setDialog(null);
          }}
        >
          <Icon name="menu_read" />
          <span>읽기 모드</span>
        </button>
      </div>
      <div className="nav-group">
        <button
          className="nav-item"
          onClick={() => {
            setDictionary("");
            setDialog(null);
          }}
        >
          <Icon name="menu_book" />
          <span>사전 검색</span>
        </button>
        <button
          className="nav-item"
          disabled={!doc}
          onClick={() => setDialog("bookmarks")}
        >
          <Bookmark size={22} />
          <span>책갈피</span>
          {!!doc?.bookmarks.length && (
            <span className="nav-count">{doc.bookmarks.length}</span>
          )}
        </button>
        <button
          className="nav-item"
          disabled={!doc}
          onClick={() => setDialog("jump")}
        >
          <Icon name="menu_move" />
          <span>줄 이동</span>
        </button>
        <button className="nav-item" onClick={() => setDialog("settings")}>
          <Icon name="menu_settings" />
          <span>읽기 옵션</span>
        </button>
      </div>
      <div className="nav-group">
        <button
          className="nav-item"
          disabled={!doc || !Object.keys(doc.translations).length}
          onClick={() => {
            if (doc) downloadTranslations(doc);
            setDialog(null);
          }}
        >
          <ArrowDownToLine size={22} />
          <span>번역문 내보내기</span>
        </button>
      </div>
    </>
  );
  if (reader.restoreError)
    return (
      <div className="startup">
        <h1>읽기 기록을 불러오지 못했습니다</h1>
        <p>브라우저의 저장 공간 접근을 허용한 뒤 다시 시도해 주세요.</p>
        <button className="primary-button" onClick={() => location.reload()}>
          다시 불러오기
        </button>
      </div>
    );
  if (!reader.ready)
    return (
      <div className="startup">
        <LoaderCircle className="spin" />
        <p>읽던 문서를 불러오고 있습니다</p>
      </div>
    );
  return (
    <div
      className="app-shell"
      onDragOver={(event) => {
        event.preventDefault();
        if (event.dataTransfer.types.includes("Files")) setDragging(true);
      }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node))
          setDragging(false);
      }}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        if (!opening && event.dataTransfer.files[0])
          void openFile(event.dataTransfer.files[0]);
      }}
    >
      <a href="#reading-content" className="skip-link">
        본문으로 이동
      </a>
      <input
        ref={fileInput}
        type="file"
        accept=".txt,text/plain"
        className="sr-only"
        tabIndex={-1}
        aria-label="TXT 파일 선택"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) void openFile(file);
        }}
      />
      <aside className="sidebar">
        <div className="brand">
          <img
            src="/assets/reader_menu_logo.png"
            alt=""
            width={39}
            height={26}
          />
          <span>Lingua</span>
          <small>읽고, 이해하고, 기록하다</small>
        </div>
        <nav aria-label="주 메뉴">{navigation}</nav>
        <div className="sidebar-footer">
          <p>
            <ShieldCheck size={16} />
            문서는 이 브라우저에 저장됩니다
          </p>
          <a
            href="https://lingua-af9c2.web.app/privacy/"
            target="_blank"
            rel="noreferrer"
          >
            개인정보처리방침
          </a>
          <a href="/dict/NOTICE.txt" target="_blank" rel="noreferrer">
            사전 출처 및 라이선스
          </a>
        </div>
      </aside>
      <main className="workspace">
        <header className="topbar">
          <button
            className="icon-button mobile-menu"
            aria-label="메뉴 열기"
            onClick={() => setDialog("menu")}
          >
            <Icon name="menu" />
          </button>
          <div className="document-heading">
            <span className="document-eyebrow">
              {viewer ? "읽기 모드" : "문장 번역"}
            </span>
            <h1 title={doc?.title}>{doc?.title ?? "파일을 선택해 주세요"}</h1>
          </div>
          <div className="topbar-actions">
            {!viewer && (
              <>
                <button
                  className="icon-button"
                  aria-label="번역 입력 영역 표시"
                  aria-pressed={showInput}
                  onClick={() => setShowInput(!showInput)}
                >
                  <Icon name="edit" />
                </button>
                <button
                  className="icon-button"
                  aria-label="문장 번역 영역 표시"
                  aria-pressed={translationVisible}
                  onClick={() => setTranslationVisible(!translationVisible)}
                >
                  <Icon name="translate" />
                </button>
                <button
                  className="icon-button"
                  aria-label="번역 프로필"
                  disabled={!doc}
                  onClick={() => setDialog("translation-profile")}
                >
                  <SlidersHorizontal size={20} />
                </button>
              </>
            )}
            {viewer && (
              <>
                <button
                  className="icon-button"
                  aria-label="문장 번역으로 돌아가기"
                  onClick={() => setViewer(false)}
                >
                  <PanelLeftClose size={22} />
                </button>
                <button
                  className="icon-button"
                  aria-label="읽기 옵션"
                  onClick={() => setDialog("settings")}
                >
                  <Icon name="menu_settings" />
                </button>
              </>
            )}
            <button
              className="icon-button"
              aria-label="문서 검색"
              disabled={!doc}
              onClick={() => setDialog("search")}
            >
              <Icon name="search" />
            </button>
          </div>
        </header>
        <div className="workspace-status">
          <span className="save-status" role="status">
            {reader.saving === "error" ? (
              <>
                <span className="error-message">
                  저장 실패 · 현재 입력은 유지됩니다
                </span>
                <button className="text-button" onClick={reader.retrySave}>
                  다시 저장
                </button>
              </>
            ) : reader.saving === "saving" ? (
              <>
                <LoaderCircle size={14} className="spin" />
                저장 중
              </>
            ) : doc ? (
              <>
                <Check size={14} />이 브라우저에 저장됨
              </>
            ) : (
              "로그인 없이 바로 읽기"
            )}
          </span>
          <span>
            {!online ? (
              <>
                <WifiOff size={14} />
                오프라인
              </>
            ) : offlineReady ? (
              "오프라인 읽기 준비됨"
            ) : (
              "Lingua Web"
            )}
          </span>
        </div>
        {fileError && (
          <div role="alert" className="file-error">
            {fileError}
            <button
              className="icon-button"
              aria-label="오류 닫기"
              onClick={() => setFileError("")}
            >
              <X size={18} />
            </button>
          </div>
        )}
        {!doc ? (
          <div className="empty-workspace" id="reading-content">
            <div className="empty-document">
              <div className="empty-file-icon">
                <FileText size={40} strokeWidth={1.25} />
              </div>
              <h2>오늘 읽을 문서를 열어보세요</h2>
              <p>
                한 문장씩 읽고, 직접 번역하며
                <br />
                영어 문장을 나의 언어로 만들어 보세요.
              </p>
              <button
                className="primary-button"
                disabled={opening}
                onClick={() => fileInput.current?.click()}
              >
                {opening ? (
                  <LoaderCircle className="spin" size={18} />
                ) : (
                  <Icon name="menu_file" size={20} />
                )}
                텍스트 파일 열기
              </button>
              <button className="secondary-button" onClick={openSample}>샘플 글로 체험하기</button>
              <small>TXT 파일을 여기에 끌어다 놓아도 됩니다</small>
            </div>
            <div className="empty-bottom">
              <BookOpen size={19} />
              <span>모르는 단어는 오프라인 영한 사전으로 찾아보세요.</span>
              <button className="text-button" onClick={() => setDictionary("")}>
                사전 열기
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="reading-toolbar">
              <div className="reading-mode-label">
                <span>{viewer ? "전체 읽기" : "한 문장씩 읽기"}</span>
                <span className="position-label" data-testid="position">
                  {position}
                </span>
              </div>
              <div className="reading-tools">
                <button
                  className={`timer-button ${timerStarted ? "running" : ""}`}
                  onClick={toggleTimer}
                  aria-label={timerStarted ? "타이머 일시정지" : "타이머 시작"}
                  aria-pressed={!!timerStarted}
                >
                  <Icon
                    name={timerStarted ? "timer_on" : "timer_off"}
                    size={20}
                  />
                  <time>{timeLabel}</time>
                </button>
                <button
                  className="icon-button"
                  aria-label={
                    doc.bookmarks.includes(doc.index)
                      ? "현재 문장 책갈피 해제"
                      : "현재 문장 책갈피 추가"
                  }
                  aria-pressed={doc.bookmarks.includes(doc.index)}
                  onClick={reader.toggleBookmark}
                >
                  <Bookmark
                    size={21}
                    fill={
                      doc.bookmarks.includes(doc.index)
                        ? "currentColor"
                        : "none"
                    }
                  />
                </button>
              </div>
            </div>
            <div
              id="reading-content"
              className={`reading-content ${viewer ? "viewer-content" : ""}`}
            >
              {viewer ? (
                <Viewer
                  document={doc}
                  style={reader.state.settings.viewer}
                  onNavigate={reader.navigate}
                  scrollRequest={scrollRequest}
                />
              ) : (
                <div
                  className={`sentence-layout ${!showInput ? "input-hidden" : ""} ${!translationVisible ? "translation-hidden" : ""}`}
                >
                  <section className="original-panel reading-panel">
                    <div className="panel-heading">
                      <h2>원문</h2>
                      <span>English</span>
                    </div>
                    <div
                      className="original-text"
                      style={textStyle(reader.state.settings.original)}
                    >
                      {doc.sentences[doc.index]
                        .split(/([A-Za-z]+(?:['’-][A-Za-z]+)*)/g)
                        .map((part, index) =>
                          /^[A-Za-z]/.test(part) ? (
                            <button
                              key={index}
                              className="inline-word"
                              onClick={() => setDictionary(part)}
                            >
                              {part}
                            </button>
                          ) : (
                            <span key={index}>{part}</span>
                          ),
                        )}
                    </div>
                    <div className="original-caption">
                      <span>단어를 누르면 뜻을 확인할 수 있습니다</span>
                      <span>{doc.sentences[doc.index].length}자</span>
                    </div>
                  </section>
                  {translationVisible && (
                    <section className="machine-translation-panel reading-panel">
                      <div className="panel-heading">
                        <h2>문장 번역</h2>
                        <span>
                          DeepL · {translationProfile.presetId === "default"
                            ? "자동"
                            : translationProfileName}
                        </span>
                      </div>
                      <div
                        className="machine-translation-copy"
                        style={textStyle(reader.state.settings.machineTranslation)}
                        aria-live="polite"
                        aria-busy={machineTranslation.loading}
                      >
                        {machineTranslation.error ? (
                          <div className="translation-error" role="alert">
                            <p>{machineTranslation.error}</p>
                            <button
                              className="text-button"
                              onClick={currentSentenceTranslation.retryCurrentSentence}
                            >
                              다시 시도
                            </button>
                          </div>
                        ) : machineTranslation.loading ? (
                          <p className="translation-loading">
                            <LoaderCircle className="spin" size={18} />
                            문장을 번역하고 있습니다
                          </p>
                        ) : machineTranslation.translated ? (
                          <p>{machineTranslation.translated}</p>
                        ) : (
                          <p>문장 번역을 준비하고 있습니다</p>
                        )}
                      </div>
                    </section>
                  )}
                  {showInput && (
                    <section className="translation-panel reading-panel">
                      <div className="panel-heading">
                        <h2>
                          <label htmlFor="user-translation">나의 번역</label>
                        </h2>
                        <span>한국어</span>
                      </div>
                      <textarea
                        id="user-translation"
                        ref={translationInput}
                        style={textStyle(reader.state.settings.translation)}
                        placeholder="이 문장을 나의 언어로 옮겨 보세요."
                        value={doc.translations[doc.index] ?? ""}
                        onChange={(event) =>
                          reader.saveTranslation(event.target.value)
                        }
                      />
                      <div className="translation-caption">
                        <span>
                          {(doc.translations[doc.index] ?? "").length}자
                        </span>
                      </div>
                    </section>
                  )}
                </div>
              )}
            </div>
            {!viewer && (
              <section className="word-section" aria-label="현재 문장의 단어">
                <span className="word-label">단어 찾기</span>
                <div className="word-strip">
                  {wordTokens(doc.sentences[doc.index]).map((word) => (
                    <button key={word} onClick={() => setDictionary(word)}>
                      {word}
                    </button>
                  ))}
                </div>
              </section>
            )}
            <footer className="reader-bottom">
              <div className="progress-row">
                <input
                  type="range"
                  aria-label="읽기 위치"
                  min={1}
                  max={doc.sentences.length}
                  value={doc.index + 1}
                  onChange={(event) => navigate(Number(event.target.value) - 1)}
                />
                <button
                  className="text-button progress-label"
                  onClick={() => setDialog("jump")}
                >
                  {Math.round(((doc.index + 1) / doc.sentences.length) * 100)}%
                </button>
                <button
                  className="text-button bookmarks-shortcut"
                  onClick={() => setDialog("bookmarks")}
                >
                  <Bookmark size={15} />
                  책갈피 {doc.bookmarks.length}
                </button>
              </div>
              <div className="bottom-row">
                <div className="quota-label">
                  <span>온라인 번역 사용량</span>
                  <strong>
                    {usage
                      ? `${usage.quotaMax - usage.quotaRemaining}/${usage.quotaMax}`
                      : "확인 전"}
                  </strong>
                </div>
                <div className="sentence-navigation">
                  <button
                    className="secondary-button"
                    disabled={doc.index === 0}
                    onClick={() => navigate(doc.index - 1)}
                  >
                    <Icon name="arrow_left" size={14} />
                    이전 줄
                  </button>
                  <button
                    className="primary-button"
                    disabled={doc.index === doc.sentences.length - 1}
                    onClick={() => navigate(doc.index + 1)}
                  >
                    다음 줄<Icon name="arrow_right" size={14} />
                  </button>
                  <button
                    className="secondary-button input-button"
                    onClick={focusInput}
                  >
                    입력
                  </button>
                </div>
              </div>
            </footer>
          </>
        )}
      </main>
      {dragging && (
        <div className="drop-overlay">
          <FileText size={42} />
          <strong>TXT 파일을 놓아주세요</strong>
        </div>
      )}
      {dialog === "menu" && (
        <Modal
          title="Lingua"
          onClose={() => setDialog(null)}
          className="menu-modal"
        >
          <nav aria-label="모바일 메뉴">{navigation}</nav>
          <a
            className="privacy-link"
            href="https://lingua-af9c2.web.app/privacy/"
            target="_blank"
            rel="noreferrer"
          >
            개인정보처리방침
          </a>
        </Modal>
      )}
      {dialog === "library" && (
        <LibraryDialog
          documents={[...(doc ? [doc] : []), ...Object.values(reader.state.archivedDocuments)]}
          currentId={doc?.id}
          onSelect={(book) => {
            reader.open(book.title, book.content);
            setViewer(false);
            setDialog(null);
          }}
          onSample={openSample}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "settings" && (
        <Settings
          settings={reader.state.settings}
          viewer={viewer}
          onChange={reader.updateStyle}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "translation-profile" && doc && (
        <TranslationProfileDialog
          profile={translationProfile}
          onApply={(profile) => {
            reader.updateTranslationProfile(profile);
            setDialog(null);
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "search" && doc && (
        <SearchDialog
          document={doc}
          onSelect={onSelect}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "bookmarks" && doc && (
        <BookmarkDialog
          document={doc}
          onSelect={onSelect}
          onRemove={reader.removeBookmark}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === "jump" && doc && (
        <JumpDialog
          document={doc}
          onSelect={onSelect}
          onClose={() => setDialog(null)}
        />
      )}
      {dictionary !== null && (
        <Dictionary
          initialQuery={dictionary}
          onClose={() => setDictionary(null)}
        />
      )}
    </div>
  );
}
