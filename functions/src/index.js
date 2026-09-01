const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {defineSecret} = require("firebase-functions/params");
const {translate} = require("./deepl");
const {cleanupStaleReservations, executeTranslation, getQuotaStatus} = require("./translation");

initializeApp();
const DEEPL_API_KEY = defineSecret("DEEPL_API_KEY");

async function handleTranslate(request) {
  requireAnonymous(request);
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
  return {...result, ...await getQuotaStatus(getFirestore(), request.auth.uid)};
}

function requireAnonymous(request) {
  if (typeof request.auth?.uid !== "string" || request.auth.uid === "") {
    throw new HttpsError("unauthenticated", "Authentication is required");
  }
  if (request.auth.token?.firebase?.sign_in_provider !== "anonymous") {
    throw new HttpsError("permission-denied", "Anonymous authentication is required");
  }
}

async function handleQuotaStatus(request) {
  requireAnonymous(request);
  return getQuotaStatus(getFirestore(), request.auth.uid);
}

exports.translateProxy = onCall({
  region: "asia-northeast3",
  minInstances: 0,
  enforceAppCheck: true,
  secrets: [DEEPL_API_KEY],
}, handleTranslate);
exports.quotaStatus = onCall({
  region: "asia-northeast3",
  minInstances: 0,
  enforceAppCheck: true,
}, handleQuotaStatus);
exports.cleanupTranslationReservations = onSchedule({
  region: "asia-northeast3",
  schedule: "every 5 minutes",
}, () => cleanupStaleReservations(getFirestore()));
exports.handleTranslate = handleTranslate;
exports.handleQuotaStatus = handleQuotaStatus;
