import { describe, expect, it } from "vitest";
import { MachineTranslationStore } from "../src/services/machineTranslation";

const firstSentence = {
  documentId: "book-a",
  sentenceIndex: 0,
  sentence: "The first sentence.",
  context: "",
  instructions: "",
};
const secondSentence = {
  documentId: "book-a",
  sentenceIndex: 1,
  sentence: "The second sentence.",
  context: "",
  instructions: "",
};

describe("현재 문장의 기계번역 흐름", () => {
  it("문장 이동 뒤 늦게 도착한 이전 결과를 무시한다", () => {
    const translations = new MachineTranslationStore();
    translations.select(firstSentence);
    const firstRequest = translations.begin();
    translations.select(secondSentence);
    const secondRequest = translations.begin();

    expect(firstRequest).not.toBeNull();
    expect(secondRequest).not.toBeNull();
    translations.finish(firstRequest!, "이전 문장의 번역");
    expect(translations.state.translated).toBeNull();
    translations.finish(secondRequest!, "현재 문장의 번역");
    expect(translations.state.translated).toBe("현재 문장의 번역");
  });

  it("같은 문장으로 돌아오면 성공한 번역을 재사용한다", () => {
    const translations = new MachineTranslationStore();
    translations.select(firstSentence);
    const request = translations.begin();
    expect(request).not.toBeNull();
    translations.finish(request!, "첫 문장 번역");

    translations.select(secondSentence);
    translations.select(firstSentence);

    expect(translations.state.translated).toBe("첫 문장 번역");
    expect(translations.begin()).toBeNull();
  });

  it("번역 프로필이 바뀌면 같은 문장도 새로 번역한다", () => {
    const translations = new MachineTranslationStore();
    translations.select(firstSentence);
    const defaultRequest = translations.begin();
    translations.finish(defaultRequest!, "기본 번역");

    translations.select({ ...firstSentence, context: "Warhammer 40,000" });

    expect(translations.state.translated).toBeNull();
    expect(translations.begin()).not.toBeNull();
  });

  it("실패한 현재 문장은 재시도할 수 있고 오래된 응답은 반영하지 않는다", () => {
    const translations = new MachineTranslationStore();
    translations.select(firstSentence);
    const failedRequest = translations.begin();
    expect(failedRequest).not.toBeNull();
    translations.fail(failedRequest!, "연결 오류");
    expect(translations.state.error).toBe("연결 오류");

    const retryRequest = translations.retry();
    expect(retryRequest).not.toBeNull();
    translations.finish(failedRequest!, "오래된 번역");
    expect(translations.state.translated).toBeNull();
    translations.finish(retryRequest!, "재시도 번역");
    expect(translations.state.translated).toBe("재시도 번역");
  });

  it("번역 영역을 숨기면 요청을 무효화하고 다시 보일 때 다시 시작한다", () => {
    const translations = new MachineTranslationStore();
    translations.select(firstSentence);
    const hiddenRequest = translations.begin();
    expect(hiddenRequest).not.toBeNull();

    translations.deactivate();
    expect(translations.state.loading).toBe(false);
    expect(translations.begin()).not.toBeNull();
    translations.finish(hiddenRequest!, "숨긴 동안 도착한 번역");
    expect(translations.state.translated).toBeNull();
  });
});
