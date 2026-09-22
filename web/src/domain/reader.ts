export const SPLITTER_VERSION = 2;
const abbreviations = new Set([
  "dr",
  "e.g",
  "etc",
  "i.e",
  "jr",
  "mr",
  "mrs",
  "ms",
  "prof",
  "sr",
  "st",
  "u.k",
  "u.s",
  "vs",
]);
export function splitSentences(raw: string): string[] {
  const text = raw
    .replace(/([A-Za-z])’([A-Za-z])/g, "$1'$2")
    .replace(/\r\n?/g, "\n");
  const sentences: string[] = [];
  let start = 0,
    index = 0;
  const add = (end: number) => {
    const sentence = text.slice(start, end).trim();
    if (sentence) sentences.push(sentence);
    start = end;
  };
  while (index < text.length) {
    const char = text[index];
    if (".!?".includes(char)) {
      if (char === ".") {
        const token = (text.slice(0, index).match(/[\p{L}.]+$/u)?.[0] ?? "")
          .toLowerCase()
          .replace(/^\.+|\.+$/g, "");
        const insideWord =
          /[\p{L}\p{N}]/u.test(text[index - 1] ?? "") &&
          /[\p{L}\p{N}]/u.test(text[index + 1] ?? "");
        if (
          insideWord ||
          abbreviations.has(token) ||
          (token.length === 1 && token !== "i" && /\p{L}/u.test(token))
        ) {
          index++;
          continue;
        }
      }
      do {
        index++;
      } while (index < text.length && ".!?".includes(text[index]));
      while (index < text.length && "'’\"”)]}".includes(text[index])) index++;
      add(index);
    } else if (char === "\n") {
      const paragraphEnd = index;
      while (text[index] === "\n") index++;
      if (index - paragraphEnd > 1) add(paragraphEnd);
    } else index++;
  }
  if (start < text.length) add(text.length);
  return sentences;
}

export function contentId(content: string): string {
  const bytes = new TextEncoder().encode(content);
  let hash = 14695981039346656037n;
  for (const byte of bytes)
    hash = BigInt.asUintN(64, (hash ^ BigInt(byte)) * 1099511628211n);
  return `${bytes.length}-${hash.toString(16)}`;
}
export type DisplayTarget =
  | "original"
  | "machineTranslation"
  | "translation"
  | "viewer";
export type DisplayStyle = {
  font: "sans" | "serif" | "mono";
  size: number;
  lineHeight: number;
  color: string;
  background: string;
};
export type DisplaySettings = Record<DisplayTarget, DisplayStyle>;
export type ReaderDocument = {
  id: string;
  title: string;
  content: string;
  sentences: string[];
  index: number;
  translations: Record<number, string>;
  bookmarks: number[];
};
export type ReaderState = {
  document: ReaderDocument | null;
  settings: DisplaySettings;
};
const style: DisplayStyle = {
  font: "sans",
  size: 18,
  lineHeight: 1.4,
  color: "#181b1e",
  background: "#f8f9fa",
};
export function initialReader(): ReaderState {
  return {
    document: null,
    settings: {
      original: { ...style },
      machineTranslation: { ...style },
      translation: { ...style },
      viewer: { ...style, size: 17 },
    },
  };
}
export function openDocument(
  state: ReaderState,
  title: string,
  content: string,
): ReaderState {
  const sentences = splitSentences(content);
  if (!sentences.length)
    throw new Error(
      "파일에 읽을 내용이 없습니다. 다른 TXT 파일을 선택해 주세요.",
    );
  const id = contentId(content);
  if (state.document?.id === id)
    return { ...state, document: { ...state.document, title } };
  return {
    ...state,
    document: {
      id,
      title,
      content,
      sentences,
      index: 0,
      translations: {},
      bookmarks: [],
    },
  };
}
export function moveTo(
  document: ReaderDocument,
  index: number,
): ReaderDocument {
  return Number.isInteger(index) &&
    index >= 0 &&
    index < document.sentences.length
    ? { ...document, index }
    : document;
}
export function sentenceMatches(sentences: string[], query: string): number[] {
  const term = query.trim().toLowerCase();
  return term
    ? sentences.flatMap((sentence, index) =>
        sentence.toLowerCase().includes(term) ? [index] : [],
      )
    : [];
}
export function wordTokens(sentence: string): string[] {
  return [...new Set(sentence.match(/[A-Za-z]+(?:['’-][A-Za-z]+)*/g) ?? [])];
}
