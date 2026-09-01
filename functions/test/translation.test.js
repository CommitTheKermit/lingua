const {
  MAX_QUOTA,
  MONTHLY_CAP,
  PENDING_TTL_MS,
  cacheKey,
  executeTranslation,
  getQuotaStatus,
  monthKey,
  quotaAt,
} = require("../src/translation");

class Snapshot {
  constructor(data) {
    this.value = data;
    this.exists = data !== undefined;
  }
  data() { return this.value; }
}

class Ref {
  constructor(db, path) {
    this.db = db;
    this.path = path;
  }
  async get() { return new Snapshot(this.db.data.get(this.path)); }
}

class FakeFirestore {
  constructor(entries = {}) {
    this.data = new Map(Object.entries(entries));
    this.queue = Promise.resolve();
  }
  collection(name) {
    return {doc: (id) => new Ref(this, `${name}/${id}`)};
  }
  runTransaction(callback) {
    const run = this.queue.then(async () => {
      const writes = [];
      const transaction = {
        get: async (ref) => new Snapshot(this.data.get(ref.path)),
        set: (ref, value, options) => writes.push(() => {
          const previous = this.data.get(ref.path) ?? {};
          this.data.set(ref.path, options?.merge ? {...previous, ...value} : value);
        }),
        delete: (ref) => writes.push(() => this.data.delete(ref.path)),
      };
      const result = await callback(transaction);
      writes.forEach((write) => write());
      return result;
    });
    this.queue = run.catch(() => {});
    return run;
  }
}

const request = {text: "Hello", sourceLang: "EN", targetLang: "KO"};
const nowMs = Date.parse("2026-01-01T00:00:00Z");
const pathFor = (value) => `translationCache/${cacheKey(value)}`;

test("quota refills by three every two minutes and never exceeds 200", () => {
  expect(quotaAt({}, nowMs)).toEqual({remaining: MAX_QUOTA, lastMs: nowMs});
  expect(quotaAt({quotaRemaining: 1, quotaLastTs: nowMs}, nowMs + 4 * 60 * 1000))
    .toEqual({remaining: 7, lastMs: nowMs + 4 * 60 * 1000});
  expect(quotaAt({quotaRemaining: 199, quotaLastTs: nowMs}, nowMs + 2 * 60 * 1000).remaining)
    .toBe(MAX_QUOTA);
  expect(monthKey(Date.parse("2026-01-31T23:59:59Z"))).toBe("2026-01");
});

test("quota status reports the next refill without mutating stored quota", async () => {
  const db = new FakeFirestore({
    "users/anon": {quotaRemaining: 7, quotaLastTs: new Date(nowMs)},
  });
  await expect(getQuotaStatus(db, "anon", nowMs)).resolves.toEqual({
    quotaRemaining: 7,
    quotaMax: 200,
    nextRefillAtMs: nowMs + 2 * 60 * 1000,
  });
  expect(db.data.get("users/anon").quotaRemaining).toBe(7);
});

test("a cache hit is returned without translation or quota use", async () => {
  const db = new FakeFirestore({
    [pathFor(request)]: {status: "completed", translated: "안녕하세요"},
  });
  const translate = jest.fn();

  await expect(executeTranslation({db, request, uid: "anon", translate, now: () => nowMs}))
    .resolves.toEqual({translated: "안녕하세요", cached: true});
  expect(translate).not.toHaveBeenCalled();
  expect(db.data.has("users/anon")).toBe(false);
  expect(db.data.has("meta/translateBudget")).toBe(false);
});

test("a successful translation consumes one personal and monthly reservation", async () => {
  const db = new FakeFirestore();

  await expect(executeTranslation({
    db, request, uid: "anon", now: () => nowMs, translate: async () => "안녕하세요",
  })).resolves.toEqual({translated: "안녕하세요", quotaRemaining: 199, cached: false});
  expect(db.data.get("users/anon").quotaRemaining).toBe(199);
  expect(db.data.get("meta/translateBudget")).toMatchObject({month: "2026-01", count: 1});
  expect(db.data.get(pathFor(request))).toMatchObject({status: "completed", translated: "안녕하세요"});
});

test("a failed translation restores both reservations and removes pending cache", async () => {
  const db = new FakeFirestore({
    "users/anon": {quotaRemaining: 7, quotaLastTs: new Date(nowMs)},
    "meta/translateBudget": {month: "2026-01", count: 9},
  });

  await expect(executeTranslation({
    db, request, uid: "anon", now: () => nowMs,
    translate: async () => { throw new Error("upstream failed"); },
  })).rejects.toThrow("upstream failed");
  expect(db.data.get("users/anon").quotaRemaining).toBe(7);
  expect(db.data.get("meta/translateBudget").count).toBe(9);
  expect(db.data.has(pathFor(request))).toBe(false);
});

test("concurrent duplicate requests call DeepL and consume quota only once", async () => {
  const db = new FakeFirestore();
  let finish;
  const translate = jest.fn(() => new Promise((resolve) => { finish = resolve; }));
  const first = executeTranslation({db, request, uid: "anon", translate, now: () => nowMs});
  while (!finish) await new Promise(setImmediate);
  const second = executeTranslation({
    db, request, uid: "anon", translate, now: () => nowMs,
    wait: () => new Promise(setImmediate),
  });
  finish("안녕하세요");

  const results = await Promise.all([first, second]);
  expect(results).toContainEqual({translated: "안녕하세요", quotaRemaining: 199, cached: false});
  expect(results).toContainEqual({translated: "안녕하세요", cached: true});
  expect(translate).toHaveBeenCalledTimes(1);
  expect(db.data.get("users/anon").quotaRemaining).toBe(199);
  expect(db.data.get("meta/translateBudget").count).toBe(1);
});

test("a stale pending request is refunded before a replacement reserves quota", async () => {
  const staleRequest = {
    status: "pending",
    reservationId: "stale",
    uid: "anon",
    month: "2026-01",
    createdAt: new Date(nowMs - PENDING_TTL_MS),
  };
  const db = new FakeFirestore({
    [pathFor(request)]: staleRequest,
    "users/anon": {quotaRemaining: 199, quotaLastTs: new Date(nowMs)},
    "meta/translateBudget": {month: "2026-01", count: 1},
  });

  await expect(executeTranslation({
    db, request, uid: "anon", now: () => nowMs, translate: async () => "복구됨",
  })).resolves.toEqual({translated: "복구됨", quotaRemaining: 199, cached: false});
  expect(db.data.get("users/anon").quotaRemaining).toBe(199);
  expect(db.data.get("meta/translateBudget").count).toBe(1);
  expect(db.data.get(pathFor(request))).toMatchObject({status: "completed", translated: "복구됨"});
});

test("transactions prevent concurrent requests from exceeding either limit", async () => {
  const oneToken = new FakeFirestore({
    "users/anon": {quotaRemaining: 1, quotaLastTs: new Date(nowMs)},
  });
  const other = {...request, text: "World"};
  const translate = jest.fn(async ({text}) => text);
  const personal = await Promise.all([
    executeTranslation({db: oneToken, request, uid: "anon", translate, now: () => nowMs}),
    executeTranslation({db: oneToken, request: other, uid: "anon", translate, now: () => nowMs}),
  ]);
  expect(personal.filter((result) => result.exhausted)).toHaveLength(1);
  expect(oneToken.data.get("users/anon").quotaRemaining).toBe(0);

  const oneGlobal = new FakeFirestore({
    "meta/translateBudget": {month: "2026-01", count: MONTHLY_CAP - 1},
  });
  const global = await Promise.all([
    executeTranslation({db: oneGlobal, request, uid: "a", translate, now: () => nowMs}),
    executeTranslation({db: oneGlobal, request: other, uid: "b", translate, now: () => nowMs}),
  ]);
  expect(global.filter((result) => result.exhausted)).toHaveLength(1);
  expect(oneGlobal.data.get("meta/translateBudget").count).toBe(MONTHLY_CAP);
});
