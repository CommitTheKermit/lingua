import "fake-indexeddb/auto";
import { describe, expect, it } from "vitest";
import { openDB } from "idb";
import {
  initialReader,
  openDocument,
  SPLITTER_VERSION,
} from "../src/domain/reader";
import { persistReader, restoreReader } from "../src/services/storage";
describe("브라우저 저장과 재실행 복원", () => {
  it("문서 위치, 같은 문장별 입력, 책갈피와 설정을 복원한다", async () => {
    const state = openDocument(initialReader(), "reading.txt", "Same. Same.");
    state.document!.index = 1;
    state.document!.translations = { 0: "첫 번째", 1: "두 번째" };
    state.document!.bookmarks = [1];
    state.settings.viewer.size = 24;
    state.translationProfilesByDocument[state.document!.id] = {
      presetId: "warhammer-40k",
      context: "Warhammer 40,000",
      instructions: "엄숙한 소설체",
    };
    await persistReader(state);
    expect(await restoreReader()).toEqual(state);
  });
  it("빠른 연속 저장은 마지막 입력으로 끝난다", async () => {
    const state = openDocument(initialReader(), "typing.txt", "Hello.");
    await Promise.all(
      ["하", "하나", "하나 둘"].map((text) =>
        persistReader({
          ...state,
          document: { ...state.document!, translations: { 0: text } },
        }),
      ),
    );
    expect((await restoreReader()).document?.translations[0]).toBe("하나 둘");
  });
  it("손상되거나 지원하지 않는 저장값을 빈 문서로 덮어쓰지 않는다", async () => {
    const db = await openDB("lingua-reader", 1);
    await db.put("reader", { version: -1, state: "broken" }, "current");
    await expect(restoreReader()).rejects.toThrow();
    expect(await db.get("reader", "current")).toEqual({
      version: -1,
      state: "broken",
    });
    db.close();
  });
  it("기존 사전 안내 표시 설정을 문장 번역 설정으로 옮긴다", async () => {
    const db = await openDB("lingua-reader", 1);
    const state = initialReader();
    const settings: Record<string, unknown> = {
      ...state.settings,
      guide: { ...state.settings.machineTranslation, size: 23 },
    };
    delete settings.machineTranslation;
    await db.put(
      "reader",
      { version: SPLITTER_VERSION, state: { ...state, settings } },
      "current",
    );
    expect((await restoreReader()).settings.machineTranslation.size).toBe(23);
    db.close();
  });
});
