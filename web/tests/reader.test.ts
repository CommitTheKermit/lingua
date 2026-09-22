import { describe, expect, it } from "vitest";
import {
  contentId,
  initialReader,
  moveTo,
  openDocument,
  sentenceMatches,
  splitSentences,
  wordTokens,
} from "../src/domain/reader";
import { exportCsv } from "../src/domain/export";
describe("앱과 동일한 읽기 규칙", () => {
  it("약어, 소수와 이니셜 내부를 분리하지 않는다", () => {
    expect(
      splitSentences(
        "Dr. Smith paid 3.14 dollars. Mr. J. Kim agreed! A final line",
      ),
    ).toEqual([
      "Dr. Smith paid 3.14 dollars.",
      "Mr. J. Kim agreed!",
      "A final line",
    ]);
  });
  it("연속 종결 부호와 닫는 따옴표를 같은 문장에 포함한다", () => {
    expect(splitSentences("“Really?!” she asked. Yes.")).toEqual([
      "“Really?!”",
      "she asked.",
      "Yes.",
    ]);
  });
  it("문단 경계, 줄바꿈과 영문 아포스트로피를 정규화한다", () => {
    expect(
      splitSentences("It’s a line\r\nwrapped here\r\n\r\nLast paragraph"),
    ).toEqual(["It's a line\nwrapped here", "Last paragraph"]);
  });
  it("빈 문서는 거절하고 현재 상태를 유지한다", () => {
    const state = openDocument(initialReader(), "book.txt", "Keep this.");
    expect(() => openDocument(state, "empty.txt", "  \n ")).toThrow(
      "읽을 내용",
    );
    expect(state.document?.sentences).toEqual(["Keep this."]);
  });
  it("같은 내용의 파일을 다시 열면 위치, 입력과 책갈피를 유지한다", () => {
    const state = openDocument(initialReader(), "book.txt", "Same. Same.");
    const doc = {
      ...state.document!,
      index: 1,
      translations: { 1: "두 번째" },
      bookmarks: [1],
    };
    expect(
      openDocument({ ...state, document: doc }, "renamed.txt", doc.content)
        .document,
    ).toEqual({ ...doc, title: "renamed.txt" });
  });
  it("새 문서를 열면 이전 문서의 입력과 책갈피를 섞지 않고 표시 설정을 유지한다", () => {
    const state = openDocument(initialReader(), "first.txt", "One.");
    state.settings.original.size = 24;
    state.document!.translations[0] = "하나";
    state.document!.bookmarks = [0];
    const next = openDocument(state, "second.txt", "Two.");
    expect(next.document?.translations).toEqual({});
    expect(next.document?.bookmarks).toEqual([]);
    expect(next.settings.original.size).toBe(24);
  });
  it("문장 이동은 경계를 지키고 검색 결과는 동일한 인덱스를 반환한다", () => {
    const doc = openDocument(
      initialReader(),
      "book.txt",
      "Hello. A dog ran. Hello again.",
    ).document!;
    expect(moveTo(doc, -1)).toBe(doc);
    expect(moveTo(doc, 3)).toBe(doc);
    expect(moveTo(doc, NaN)).toBe(doc);
    expect(sentenceMatches(doc.sentences, " HELLO ")).toEqual([0, 2]);
    expect(sentenceMatches(doc.sentences, " ")).toEqual([]);
    expect(moveTo(doc, 2).index).toBe(2);
  });
  it("9번째 이후 단어도 조회할 수 있다", () => {
    expect(
      wordTokens("one two three four five six seven eight nine ten eleven"),
    ).toHaveLength(11);
  });
  it("콘텐츠 식별자는 UTF-8 길이와 FNV-1a를 사용한다", () => {
    expect(contentId("")).toBe("0-cbf29ce484222325");
    expect(contentId("hello")).toBe("5-a430d84680aabd0b");
    expect(contentId("한")).toMatch(/^3-/);
  });
  it("CSV는 입력한 문장만 원래 인덱스 순으로 내보내고 쉼표, 따옴표, 줄바꿈을 보존한다", () => {
    const doc = openDocument(
      initialReader(),
      "book.txt",
      "One, two. Same. Same.",
    ).document!;
    doc.translations = { 2: "둘\n셋", 0: '"하나", 둘', 1: "  " };
    expect(exportCsv(doc)).toBe(
      'sentence_index,source,user_translation\r\n0,"One, two.","""하나"", 둘"\r\n2,"Same.","둘\n셋"\r\n',
    );
  });
});
