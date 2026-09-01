const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const {translate} = require("./deepl");
const {executeTranslation} = require("./translation");

initializeApp();
const DEEPL_API_KEY = defineSecret("DEEPL_API_KEY");

async function handleTranslate(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Authentication is required");
  if (request.auth.token?.firebase?.sign_in_provider !== "anonymous") {
    throw new HttpsError("permission-denied", "Anonymous authentication is required");
  }
  const {text, sourceLang = "EN", targetLang = "KO"} = request.data ?? {};
  if (typeof text !== "string" || text.trim() === "") {
    throw new HttpsError("invalid-argument", "text must be a non-empty string");
  }
  const result = await executeTranslation({
    db: getFirestore(),
    request: {text, sourceLang, targetLang},
    uid: request.auth.uid,
    translate: (input) => translate({...input, apiKey: DEEPL_API_KEY.value()}),
  }).catch((error) => {
    console.error("DeepL translation failed", {message: error.message});
    throw new HttpsError("internal", "Translation failed");
  });
  if (result.exhausted) throw new HttpsError("resource-exhausted", "Translation quota exceeded");
  return result;
}

exports.translateProxy = onCall({
  region: "asia-northeast3",
  minInstances: 0,
  enforceAppCheck: true,
  secrets: [DEEPL_API_KEY],
}, handleTranslate);
exports.handleTranslate = handleTranslate;
