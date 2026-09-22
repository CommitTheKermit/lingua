import { useEffect, useRef, type RefObject } from "react";
import type { DisplayStyle, ReaderDocument } from "../domain/reader";
import { textStyle } from "./Settings";

export function Viewer({
  document,
  style,
  onNavigate,
  scrollRequest,
}: {
  document: ReaderDocument;
  style: DisplayStyle;
  onNavigate: (index: number) => void;
  scrollRequest: RefObject<number | null>;
}) {
  const container = useRef<HTMLDivElement>(null);
  const first = useRef(true);
  const suppressScroll = useRef(false);
  const indexRef = useRef(document.index);
  indexRef.current = document.index;
  useEffect(() => {
    if (first.current || scrollRequest.current !== null) {
      first.current = false;
      const index = scrollRequest.current ?? document.index;
      scrollRequest.current = null;
      suppressScroll.current = true;
      container.current
        ?.querySelector(`[data-sentence="${index}"]`)
        ?.scrollIntoView({ block: "start" });
      setTimeout(() => {
        suppressScroll.current = false;
      }, 150);
    }
  });
  useEffect(() => {
    const root = container.current;
    if (!root) return;
    let frame = 0;
    const onScroll = () => {
      if (suppressScroll.current) return;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const rootTop = root.getBoundingClientRect().top + 40;
        const nodes = root.querySelectorAll<HTMLElement>("[data-sentence]");
        for (const node of nodes) {
          if (node.getBoundingClientRect().bottom > rootTop) {
            const index = Number(node.dataset.sentence);
            if (index !== indexRef.current) onNavigate(index);
            break;
          }
        }
      });
    };
    root.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      root.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(frame);
    };
  }, [onNavigate]);
  return (
    <div
      ref={container}
      className="viewer"
      style={textStyle(style)}
      aria-label="전체 읽기 화면"
    >
      <article>
        {document.sentences.map((sentence, index) => (
          <button
            className={`viewer-sentence ${document.index === index ? "current" : ""}`}
            data-sentence={index}
            aria-current={document.index === index ? "location" : undefined}
            key={index}
            onClick={() => onNavigate(index)}
          >
            <span className="sentence-number">{index + 1}</span>
            <span>{sentence}</span>
            {document.bookmarks.includes(index) && (
              <span className="bookmark-mark" aria-label="책갈피">
                ▮
              </span>
            )}
          </button>
        ))}
      </article>
    </div>
  );
}
