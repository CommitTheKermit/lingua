import type { TranslationProfile } from "../domain/translationProfile";
export type TranslationResult = {
  translated: string;
  quotaRemaining: number;
  quotaMax: number;
  nextRefillAtMs?: number | null;
  cached: boolean;
};
const sessionKey = "lingua-translation-session";
let session: Promise<string> | undefined;

function apiBase() {
  const value = import.meta.env.VITE_TRANSLATION_API_URL?.replace(/\/$/, "");
  if (!value) throw new Error("온라인 번역 연결을 준비 중입니다. 오프라인 사전은 계속 사용할 수 있습니다.");
  return value;
}
async function request(path: string, body?: unknown, token?: string) {
  return fetch(`${apiBase()}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body ?? {}),
    signal: AbortSignal.timeout(30000),
    credentials: "omit",
  });
}
async function getSession() {
  session ??= (async () => {
    const saved = localStorage.getItem(sessionKey);
    if (saved) return saved;
    const response = await request("/session");
    if (!response.ok) throw new Error("번역 연결이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
    const result = await response.json();
    if (typeof result.token !== "string") throw new Error("번역 세션을 만들지 못했습니다.");
    localStorage.setItem(sessionKey, result.token);
    return result.token as string;
  })().catch((error) => { session = undefined; throw error; });
  return session;
}
export async function translateSentence(text: string, profile: TranslationProfile): Promise<TranslationResult> {
  if (!navigator.onLine) throw new Error("오프라인 상태입니다. 인터넷에 연결한 뒤 문장 번역을 다시 시도해 주세요.");
  const payload = { text: text.trim(), context: profile.context, instructions: profile.instructions };
  let response: Response;
  try {
    response = await request("/translate", payload, await getSession());
    if (response.status === 401) {
      // 만료된 익명 세션만 갱신한다. 책과 입력 기록에는 영향을 주지 않는다.
      localStorage.removeItem(sessionKey);
      session = undefined;
      response = await request("/translate", payload, await getSession());
    }
  } catch (error) {
    if (error instanceof Error && error.name === "TimeoutError") throw new Error("번역 응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.");
    throw error;
  }
  const result = await response.json();
  if (!response.ok) {
    if (result.error === "budget_exhausted") throw new Error("이번 달 베타 번역 제공량을 모두 사용했습니다. 오프라인 읽기와 사전은 계속 사용할 수 있습니다.");
    if (response.status === 429) throw new Error("번역 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
    if (result.error === "provider_rejected_profile") throw new Error("번역 서비스가 이 어투 지침을 처리하지 못했습니다. 프로필을 확인해 주세요.");
    if (response.status === 503) throw new Error("번역 요청이 몰리고 있습니다. 잠시 후 다시 시도해 주세요.");
    throw new Error("문장 번역에 실패했습니다. 연결을 확인한 뒤 다시 시도해 주세요.");
  }
  if (typeof result.translated !== "string" || !Number.isFinite(result.quotaRemaining)) throw new Error("번역 응답을 확인하지 못했습니다.");
  return result;
}
