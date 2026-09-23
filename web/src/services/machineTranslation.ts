export type SentenceTranslationKey = {
  documentId: string;
  sentenceIndex: number;
  sentence: string;
  context: string;
  instructions: string;
};

export type MachineTranslationState = {
  key: SentenceTranslationKey | null;
  translated: string | null;
  loading: boolean;
  error: string | null;
};

export type SentenceTranslationJob = {
  id: number;
  key: SentenceTranslationKey;
};

function sameSentence(
  left: SentenceTranslationKey | null,
  right: SentenceTranslationKey,
): boolean {
  return (
    left?.documentId === right.documentId &&
    left.sentenceIndex === right.sentenceIndex &&
    left.sentence === right.sentence &&
    left.context === right.context &&
    left.instructions === right.instructions
  );
}

export class MachineTranslationStore {
  private readonly completedBySentence = new Map<string, string>();
  private activeJob: SentenceTranslationJob | null = null;
  private nextJobId = 0;
  private currentState: MachineTranslationState = {
    key: null,
    translated: null,
    loading: false,
    error: null,
  };

  get state(): MachineTranslationState {
    return this.currentState;
  }

  select(key: SentenceTranslationKey): void {
    if (sameSentence(this.currentState.key, key)) return;
    this.activeJob = null;
    this.currentState = {
      key,
      translated: this.completedBySentence.get(this.cacheKey(key)) ?? null,
      loading: false,
      error: null,
    };
  }

  deactivate(): void {
    this.activeJob = null;
    this.currentState = { ...this.currentState, loading: false };
  }

  begin(): SentenceTranslationJob | null {
    const key = this.currentState.key;
    if (
      !key ||
      !key.sentence.trim() ||
      this.currentState.translated !== null ||
      this.currentState.loading ||
      this.currentState.error !== null
    )
      return null;

    const job = { id: ++this.nextJobId, key };
    this.activeJob = job;
    this.currentState = { ...this.currentState, loading: true };
    return job;
  }

  retry(): SentenceTranslationJob | null {
    this.currentState = { ...this.currentState, error: null };
    return this.begin();
  }

  finish(job: SentenceTranslationJob, translated: string): void {
    if (this.activeJob !== job) return;
    this.activeJob = null;
    this.completedBySentence.set(this.cacheKey(job.key), translated);
    this.currentState = {
      ...this.currentState,
      translated,
      loading: false,
      error: null,
    };
  }

  fail(job: SentenceTranslationJob, error: string): void {
    if (this.activeJob !== job) return;
    this.activeJob = null;
    this.currentState = { ...this.currentState, loading: false, error };
  }

  private cacheKey(key: SentenceTranslationKey): string {
    return JSON.stringify([
      key.documentId,
      key.sentenceIndex,
      key.sentence,
      key.context,
      key.instructions,
    ]);
  }
}
