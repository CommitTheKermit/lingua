const mockOnCall = jest.fn((options, handler) => ({options, handler}));
jest.mock("firebase-admin/app", () => ({initializeApp: jest.fn()}));
jest.mock("firebase-admin/firestore", () => ({getFirestore: jest.fn()}));
jest.mock("firebase-functions/v2/https", () => {
  class HttpsError extends Error {
    constructor(code, message) { super(message); this.code = code; }
  }
  return {onCall: mockOnCall, HttpsError};
});
jest.mock("firebase-functions/params", () => ({
  defineSecret: () => ({value: () => "test-secret"}),
}));

const {handleTranslate, translateProxy} = require("../src/index");

test("callable enforces App Check in asia-northeast3", () => {
  expect(translateProxy.options).toMatchObject({
    enforceAppCheck: true,
    region: "asia-northeast3",
  });
});

test("handler accepts only an anonymous Firebase UID", async () => {
  await expect(handleTranslate({data: {text: "Hello"}})).rejects.toMatchObject({code: "unauthenticated"});
  await expect(handleTranslate({
    auth: {token: {firebase: {sign_in_provider: "anonymous"}}},
    data: {text: "Hello"},
  })).rejects.toMatchObject({code: "unauthenticated"});
  await expect(handleTranslate({
    auth: {uid: "user", token: {firebase: {sign_in_provider: "password"}}},
    data: {text: "Hello"},
  })).rejects.toMatchObject({code: "permission-denied"});
});
