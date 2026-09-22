import { useState } from "react";
import { Bookmark, Search, Trash2 } from "lucide-react";
import { sentenceMatches, type ReaderDocument } from "../domain/reader";
import { Modal } from "./Modal";

export function SearchDialog({
  document,
  onSelect,
  onClose,
}: {
  document: ReaderDocument;
  onSelect: (index: number) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const matches = sentenceMatches(document.sentences, query);
  const [limit, setLimit] = useState(50);
  return (
    <Modal title="문서 검색" onClose={onClose}>
      <div className="dictionary-search">
        <Search size={20} />
        <input
          autoFocus
          aria-label="문서 검색어"
          placeholder="문서에서 찾을 내용을 입력하세요"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setLimit(50);
          }}
        />
      </div>
      <p className="result-count" role="status">
        {query.trim()
          ? `${matches.length}개 문장`
          : "문장이나 단어를 검색해 바로 이동하세요."}
      </p>
      <div className="result-list">
        {matches.slice(0, limit).map((index) => (
          <button
            className="sentence-result"
            key={index}
            onClick={() => onSelect(index)}
          >
            <span>{index + 1}</span>
            <p>{document.sentences[index]}</p>
          </button>
        ))}
        {matches.length > limit && (
          <button className="text-button" onClick={() => setLimit(limit + 50)}>
            더 보기
          </button>
        )}
        {query.trim() && !matches.length && (
          <div className="lookup-message">일치하는 문장이 없습니다.</div>
        )}
      </div>
    </Modal>
  );
}
export function BookmarkDialog({
  document,
  onSelect,
  onRemove,
  onClose,
}: {
  document: ReaderDocument;
  onSelect: (index: number) => void;
  onRemove: (index: number) => void;
  onClose: () => void;
}) {
  return (
    <Modal title={`책갈피 ${document.bookmarks.length}`} onClose={onClose}>
      <div className="result-list">
        {document.bookmarks.length ? (
          document.bookmarks.map((index) => (
            <div className="bookmark-result" key={index}>
              <button
                className="sentence-result"
                onClick={() => onSelect(index)}
              >
                <span>{index + 1}</span>
                <p>{document.sentences[index]}</p>
              </button>
              <button
                className="icon-button"
                aria-label={`${index + 1}번째 문장 책갈피 삭제`}
                onClick={() => onRemove(index)}
              >
                <Trash2 size={18} />
              </button>
            </div>
          ))
        ) : (
          <div className="lookup-message">
            <Bookmark size={32} />
            <p>
              다시 읽고 싶은 문장에
              <br />
              책갈피를 남겨보세요.
            </p>
          </div>
        )}
      </div>
    </Modal>
  );
}
export function JumpDialog({
  document,
  onSelect,
  onClose,
}: {
  document: ReaderDocument;
  onSelect: (index: number) => void;
  onClose: () => void;
}) {
  const [line, setLine] = useState(String(document.index + 1));
  const valid =
    /^\d+$/.test(line) &&
    Number(line) >= 1 &&
    Number(line) <= document.sentences.length;
  return (
    <Modal
      title="줄 이동"
      description={`1부터 ${document.sentences.length}까지 이동할 문장 번호를 입력하세요.`}
      onClose={onClose}
      className="small-modal"
    >
      <form
        className="jump-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (valid) onSelect(Number(line) - 1);
        }}
      >
        <label>
          문장 번호
          <input
            autoFocus
            type="number"
            min={1}
            max={document.sentences.length}
            value={line}
            onChange={(event) => setLine(event.target.value)}
          />
        </label>
        <button className="primary-button" disabled={!valid}>
          이동
        </button>
      </form>
    </Modal>
  );
}
