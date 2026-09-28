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


describe("여러 책의 기록 보호", () => {
  it("책 전환과 재실행 후 위치, 번역, 책갈피와 프로필을 복원한다", async () => {
    let state = openDocument(initialReader(), "A.txt", "First. Second.");
    const id = state.document!.id;
    state.document!.index = 1;
    state.document!.translations[1] = "둘째 문장";
    state.document!.bookmarks = [1];
    state.translationProfilesByDocument[id] = { presetId: "custom", context: "fiction", instructions: "소설체" };
    state = openDocument(state, "B.txt", "Another book.");
    state.document!.translations[0] = "다른 책";
    await persistReader(state);
    state = openDocument(await restoreReader(), "A renamed.txt", "First. Second.");
    expect(state.document).toMatchObject({ title: "A renamed.txt", index: 1, translations: {1: "둘째 문장"}, bookmarks: [1] });
    expect(state.translationProfilesByDocument[id].instructions).toBe("소설체");
    expect(state.archivedDocuments[id]).toBeUndefined();
    state = openDocument(state, "B.txt", "Another book.");
    expect(state.document!.translations[0]).toBe("다른 책");
  });
  it("서재가 없던 기존 저장 데이터의 현재 책도 보호한다", async () => {
    const state = openDocument(initialReader(), "old.txt", "Keep me.");
    state.document!.translations[0] = "기존 기록";
    const { archivedDocuments, ...legacy } = state;
    const db = await openDB("lingua-reader", 1);
    await db.put("reader", { version: SPLITTER_VERSION, state: legacy }, "current");
    db.close();
    const restored = await restoreReader();
    expect(restored.archivedDocuments).toEqual({});
    const switched = openDocument(restored, "new.txt", "New book.");
    expect(switched.archivedDocuments[state.document!.id].translations[0]).toBe("기존 기록");
  });
});
