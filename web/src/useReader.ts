import { useCallback, useEffect, useRef, useState } from "react";
import {
  initialReader,
  moveTo,
  openDocument,
  type DisplayStyle,
  type DisplayTarget,
  type ReaderDocument,
  type ReaderState,
} from "./domain/reader";
import { persistReader, restoreReader } from "./services/storage";
import type { TranslationProfile } from "./domain/translationProfile";

export function useReader() {
  const [state, setState] = useState(initialReader);
  const [ready, setReady] = useState(false);
  const [restoreError, setRestoreError] = useState(false);
  const [saving, setSaving] = useState<"saved" | "saving" | "error">("saved");
  const [retry, setRetry] = useState(0);
  const revision = useRef(0);
  useEffect(() => {
    let cancelled = false;
    restoreReader()
      .then((value) => {
        if (!cancelled) {
          setState(value);
          setReady(true);
        }
      })
      .catch(() => {
        if (!cancelled) setRestoreError(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  useEffect(() => {
    if (!ready) return;
    const id = ++revision.current;
    setSaving("saving");
    persistReader(state)
      .then(() => {
        if (revision.current === id) setSaving("saved");
      })
      .catch(() => {
        if (revision.current === id) setSaving("error");
      });
  }, [state, ready, retry]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (saving !== "saved") {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [saving]);
  const changeDocument = useCallback(
    (change: (document: ReaderDocument) => ReaderDocument) =>
      setState((current) =>
        current.document
          ? { ...current, document: change(current.document) }
          : current,
      ),
    [],
  );
  const navigate = useCallback(
    (index: number) => changeDocument((doc) => moveTo(doc, index)),
    [changeDocument],
  );
  return {
    state,
    ready,
    restoreError,
    saving,
    retrySave: () => setRetry((value) => value + 1),
    open: (title: string, content: string) => {
      const next: ReaderState = openDocument(state, title, content);
      setState(next);
    },
    navigate,
    saveTranslation: (text: string) =>
      changeDocument((doc) => {
        const translations = { ...doc.translations };
        if (text.trim()) translations[doc.index] = text;
        else delete translations[doc.index];
        return { ...doc, translations };
      }),
    toggleBookmark: () =>
      changeDocument((doc) => ({
        ...doc,
        bookmarks: doc.bookmarks.includes(doc.index)
          ? doc.bookmarks.filter((index) => index !== doc.index)
          : [...doc.bookmarks, doc.index].sort((a, b) => a - b),
      })),
    removeBookmark: (index: number) =>
      changeDocument((doc) => ({
        ...doc,
        bookmarks: doc.bookmarks.filter((value) => value !== index),
      })),
    updateStyle: (target: DisplayTarget, style: DisplayStyle) =>
      setState((current) => ({
        ...current,
        settings: { ...current.settings, [target]: style },
      })),
    updateTranslationProfile: (profile: TranslationProfile) =>
      setState((current) => {
        const documentId = current.document?.id;
        if (!documentId) return current;
        return {
          ...current,
          translationProfilesByDocument: {
            ...current.translationProfilesByDocument,
            [documentId]: profile,
          },
        };
      }),
  };
}
