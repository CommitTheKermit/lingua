import type { ReaderDocument } from "../domain/reader";
import { Modal } from "./Modal";

export function LibraryDialog({ documents, currentId, onSelect, onSample, onClose }: {
  documents: ReaderDocument[];
  currentId?: string;
  onSelect: (document: ReaderDocument) => void;
  onSample: () => void;
  onClose: () => void;
}) {
  return (
    <Modal title="내 서재" description="책별 읽던 위치, 나의 번역과 책갈피를 이 브라우저에 보관합니다." onClose={onClose}>
      <div className="result-list">
        {documents.map((document) => (
          <button className="library-book" key={document.id} onClick={() => onSelect(document)}>
            <strong>{document.title}</strong>
            <span>{document.id === currentId ? "읽는 중 · " : ""}{document.index + 1}/{document.sentences.length} 문장 · 번역 {Object.keys(document.translations).length} · 책갈피 {document.bookmarks.length}</span>
          </button>
        ))}
        {!documents.length && <p>파일을 열면 서재에 보관됩니다.</p>}
      </div>
      <button className="secondary-button" onClick={onSample}>샘플 글로 체험하기</button>
      <p className="result-count">브라우저 데이터를 지우면 기록도 삭제됩니다. 다른 기기와 동기화되지 않습니다.</p>
    </Modal>
  );
}
