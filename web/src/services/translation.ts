import { initializeApp } from "firebase/app";
import {
  initializeAppCheck,
  ReCaptchaEnterpriseProvider,
} from "firebase/app-check";
import { getAuth, signInAnonymously } from "firebase/auth";
import { getFunctions, httpsCallable } from "firebase/functions";
import type { TranslationProfile } from "../domain/translationProfile";
export type TranslationResult = {
  translated: string;
  quotaRemaining: number;
  quotaMax: number;
  nextRefillAtMs?: number | null;
  cached: boolean;
};
let callTranslation:
  | ((text: string, profile: TranslationProfile) => Promise<TranslationResult>)
  | undefined;
function configure() {
  const env = import.meta.env;
  if (
    !env.VITE_FIREBASE_API_KEY ||
    !env.VITE_FIREBASE_APP_ID ||
    !env.VITE_FIREBASE_PROJECT_ID ||
    !env.VITE_RECAPTCHA_SITE_KEY
  ) {
    throw new Error(
      "온라인 문장 번역 연결을 준비 중입니다. 오프라인 사전은 계속 사용할 수 있습니다.",
    );
  }
  if (env.DEV && env.VITE_APPCHECK_DEBUG_TOKEN) {
    (
      globalThis as typeof globalThis & {
        FIREBASE_APPCHECK_DEBUG_TOKEN?: string;
      }
    ).FIREBASE_APPCHECK_DEBUG_TOKEN = env.VITE_APPCHECK_DEBUG_TOKEN;
  }
  const app = initializeApp({
    apiKey: env.VITE_FIREBASE_API_KEY,
    appId: env.VITE_FIREBASE_APP_ID,
    projectId: env.VITE_FIREBASE_PROJECT_ID,
    authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
  });
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(env.VITE_RECAPTCHA_SITE_KEY),
    isTokenAutoRefreshEnabled: true,
  });
  const auth = getAuth(app);
  const translate = httpsCallable<
    {
      text: string;
      sourceLang: string;
      targetLang: string;
      context: string;
      instructions: string;
    },
    TranslationResult
  >(getFunctions(app, "asia-northeast3"), "translateProxy", { timeout: 30000 });
  return async (text: string, profile: TranslationProfile) => {
    await auth.authStateReady();
    if (!auth.currentUser) await signInAnonymously(auth);
    return (
      await translate({
        text: text.trim(),
        sourceLang: "EN",
        targetLang: "KO",
        context: profile.context,
        instructions: profile.instructions,
      })
    ).data;
  };
}
export async function translateSentence(
  text: string,
  profile: TranslationProfile,
): Promise<TranslationResult> {
  if (!navigator.onLine)
    throw new Error(
      "오프라인 상태입니다. 인터넷에 연결한 뒤 문장 번역을 다시 시도해 주세요.",
    );
  callTranslation ??= configure();
  try {
    return await callTranslation(text, profile);
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (code === "functions/resource-exhausted")
      throw new Error("번역 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
    throw new Error(
      "문장 번역에 실패했습니다. 네트워크 연결을 확인한 뒤 다시 시도해 주세요.",
    );
  }
}
