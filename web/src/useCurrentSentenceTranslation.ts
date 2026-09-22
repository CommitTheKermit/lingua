import { useCallback, useEffect, useRef, useState } from "react";
import {
  MachineTranslationStore,
  type MachineTranslationState,
  type SentenceTranslationJob,
  type SentenceTranslationKey,
} from "./services/machineTranslation";
import {
  translateSentence,
  type TranslationResult,
} from "./services/translation";

export function useCurrentSentenceTranslation(
  key: SentenceTranslationKey | null,
  shouldTranslateCurrentSentence: boolean,
  onUsage: (result: TranslationResult) => void,
) {
  const storeRef = useRef<MachineTranslationStore | null>(null);
  if (storeRef.current === null) storeRef.current = new MachineTranslationStore();
  const store = storeRef.current;
  const [state, setState] = useState<MachineTranslationState>(() => store.state);

  const finishTranslation = useCallback(async (job: SentenceTranslationJob) => {
    try {
      const result = await translateSentence(job.key.sentence);
      onUsage(result);
      store.finish(job, result.translated);
    } catch (reason) {
      const message =
        reason instanceof Error
          ? reason.message
          : "문장 번역에 실패했습니다. 다시 시도해 주세요.";
      store.fail(job, message);
    }
    setState({ ...store.state });
  }, [onUsage, store]);

  useEffect(() => {
    if (!key || !shouldTranslateCurrentSentence) {
      store.deactivate();
      setState({ ...store.state });
      return;
    }
    store.select(key);
    const job = store.begin();
    setState({ ...store.state });
    if (job) void finishTranslation(job);
    return () => store.deactivate();
  }, [
    key?.documentId,
    key?.sentenceIndex,
    key?.sentence,
    shouldTranslateCurrentSentence,
    finishTranslation,
    store,
  ]);

  function retryCurrentSentence() {
    const job = store.retry();
    setState({ ...store.state });
    if (job) void finishTranslation(job);
  }

  return { state, retryCurrentSentence };
}
