const {createHash, randomUUID} = require("node:crypto");

const MAX_QUOTA = 200;
const REFILL_AMOUNT = 3;
const REFILL_INTERVAL_MS = 2 * 60 * 1000;
const MONTHLY_CAP = 100000;

function monthKey(nowMs) {
  return new Date(nowMs).toISOString().slice(0, 7);
}

function cacheKey({text, sourceLang, targetLang}) {
  return createHash("sha256")
    .update(JSON.stringify([text, sourceLang, targetLang]))
    .digest("hex");
}

function quotaAt(data, nowMs) {
  const lastMs = data.quotaLastTs?.toMillis?.() ?? data.quotaLastTs ?? nowMs;
  const intervals = Math.floor(Math.max(0, nowMs - lastMs) / REFILL_INTERVAL_MS);
  return {
    remaining: Math.min(MAX_QUOTA, (data.quotaRemaining ?? MAX_QUOTA) + intervals * REFILL_AMOUNT),
    lastMs: lastMs + intervals * REFILL_INTERVAL_MS,
  };
}

async function reserve(db, request, uid, reservationId, nowMs) {
  const key = cacheKey(request);
  const cacheRef = db.collection("translationCache").doc(key);
  const userRef = db.collection("users").doc(uid);
  const budgetRef = db.collection("meta").doc("translateBudget");
  return db.runTransaction(async (transaction) => {
    const cacheSnapshot = await transaction.get(cacheRef);
    const cache = cacheSnapshot.exists ? cacheSnapshot.data() : undefined;
    if (cache?.status === "completed") return {kind: "cached", translated: cache.translated};
    if (cache?.status === "pending") return {kind: "pending", cacheRef};

    const userSnapshot = await transaction.get(userRef);
    const budgetSnapshot = await transaction.get(budgetRef);
    const quota = quotaAt(userSnapshot.exists ? userSnapshot.data() : {}, nowMs);
    const month = monthKey(nowMs);
    const budgetData = budgetSnapshot.exists ? budgetSnapshot.data() : {};
    const count = budgetData.month === month ? budgetData.count ?? 0 : 0;
    if (quota.remaining < 1 || count >= MONTHLY_CAP) return {kind: "exhausted"};

    transaction.set(userRef, {
      quotaRemaining: quota.remaining - 1,
      quotaLastTs: new Date(quota.lastMs),
    }, {merge: true});
    transaction.set(budgetRef, {month, count: count + 1}, {merge: true});
    transaction.set(cacheRef, {status: "pending", reservationId, uid, month, createdAt: new Date(nowMs)});
    return {kind: "reserved", cacheRef, remaining: quota.remaining - 1, month};
  });
}

async function commit(db, cacheRef, reservationId, translated) {
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(cacheRef);
    const data = snapshot.exists ? snapshot.data() : {};
    if (data.status !== "pending" || data.reservationId !== reservationId) return false;
    transaction.set(cacheRef, {status: "completed", translated}, {merge: true});
    return true;
  });
}

async function rollback(db, cacheRef, reservationId, nowMs) {
  return db.runTransaction(async (transaction) => {
    const cacheSnapshot = await transaction.get(cacheRef);
    const reservation = cacheSnapshot.exists ? cacheSnapshot.data() : {};
    if (reservation.status !== "pending" || reservation.reservationId !== reservationId) return false;
    const userRef = db.collection("users").doc(reservation.uid);
    const budgetRef = db.collection("meta").doc("translateBudget");
    const userSnapshot = await transaction.get(userRef);
    const budgetSnapshot = await transaction.get(budgetRef);
    const quota = quotaAt(userSnapshot.exists ? userSnapshot.data() : {}, nowMs);
    const budget = budgetSnapshot.exists ? budgetSnapshot.data() : {};
    transaction.set(userRef, {
      quotaRemaining: Math.min(MAX_QUOTA, quota.remaining + 1),
      quotaLastTs: new Date(quota.lastMs),
    }, {merge: true});
    if (budget.month === reservation.month) {
      transaction.set(budgetRef, {count: Math.max(0, (budget.count ?? 0) - 1)}, {merge: true});
    }
    transaction.delete(cacheRef);
    return true;
  });
}

async function waitForCache(cacheRef, wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    await wait(50);
    const snapshot = await cacheRef.get();
    if (!snapshot.exists) return undefined;
    const data = snapshot.data();
    if (data.status === "completed") return data.translated;
  }
  throw new Error("Timed out waiting for duplicate translation");
}

async function executeTranslation({db, request, uid, translate, now = Date.now, wait}) {
  for (;;) {
    const reservationId = randomUUID();
    const result = await reserve(db, request, uid, reservationId, now());
    if (result.kind === "cached") return {translated: result.translated, cached: true};
    if (result.kind === "exhausted") return {exhausted: true};
    if (result.kind === "pending") {
      const translated = await waitForCache(result.cacheRef, wait);
      if (translated !== undefined) return {translated, cached: true};
      continue;
    }
    try {
      const translated = await translate(request);
      if (!await commit(db, result.cacheRef, reservationId, translated)) {
        throw new Error("Translation reservation was lost");
      }
      return {translated, quotaRemaining: result.remaining, cached: false};
    } catch (error) {
      await rollback(db, result.cacheRef, reservationId, now());
      throw error;
    }
  }
}

module.exports = {
  MAX_QUOTA,
  MONTHLY_CAP,
  cacheKey,
  executeTranslation,
  monthKey,
  quotaAt,
  reserve,
  rollback,
};
