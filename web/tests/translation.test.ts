import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { defaultTranslationProfile } from "../src/domain/translationProfile";
const ok = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
let saved: Map<string,string>;
beforeEach(() => {
  vi.resetModules();
  saved = new Map();
  vi.stubEnv("VITE_TRANSLATION_API_URL", "https://api.example");
  vi.stubGlobal("navigator", {onLine: true});
  vi.stubGlobal("localStorage", {getItem:(key:string)=>saved.get(key) ?? null, setItem:(key:string,value:string)=>saved.set(key,value), removeItem:(key:string)=>saved.delete(key)});
});
afterEach(() => {vi.unstubAllGlobals(); vi.unstubAllEnvs();});
describe("홈서버 번역 연결", () => {
  it("익명 세션을 만들고 문맥과 어투를 함께 전송한다", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(ok({token:"test-session"})).mockResolvedValueOnce(ok({translated:"결과", quotaRemaining:199, quotaMax:200, cached:false}));
    vi.stubGlobal("fetch", fetcher);
    const {translateSentence} = await import("../src/services/translation");
    await expect(translateSentence(" Hello. ", {...defaultTranslationProfile(),context:"fiction",instructions:"존댓말"})).resolves.toMatchObject({translated:"결과"});
    expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual({text:"Hello.",context:"fiction",instructions:"존댓말"});
    expect(fetcher.mock.calls[1][1].headers.Authorization).toBe("Bearer test-session");
  });
  it("만료된 세션만 갱신하고 번역을 재시도한다", async () => {
    saved.set("lingua-translation-session", "expired");
    const fetcher = vi.fn().mockResolvedValueOnce(ok({error:"session_expired"},401)).mockResolvedValueOnce(ok({token:"renewed"})).mockResolvedValueOnce(ok({translated:"새 결과",quotaRemaining:199}));
    vi.stubGlobal("fetch",fetcher);
    const {translateSentence} = await import("../src/services/translation");
    await expect(translateSentence("Hi.", defaultTranslationProfile())).resolves.toMatchObject({translated:"새 결과"});
    expect(saved.get("lingua-translation-session")).toBe("renewed");
  });
  it("지침 거절을 일반 번역 성공으로 숨기지 않는다", async () => {
    saved.set("lingua-translation-session", "test-session");
    vi.stubGlobal("fetch",vi.fn().mockResolvedValue(ok({error:"provider_rejected_profile"},502)));
    const {translateSentence} = await import("../src/services/translation");
    await expect(translateSentence("Hi.",defaultTranslationProfile())).rejects.toThrow("어투 지침");
  });
});
