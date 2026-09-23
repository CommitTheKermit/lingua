export type TranslationProfileId =
  | "default"
  | "warhammer-40k"
  | "age-of-sigmar"
  | "custom";

export type TranslationProfile = {
  presetId: TranslationProfileId;
  context: string;
  instructions: string;
};

export type TranslationProfilePreset = TranslationProfile & {
  name: string;
  summary: string;
};

export const translationProfilePresets: TranslationProfilePreset[] = [
  {
    presetId: "default",
    name: "기본 번역",
    summary: "별도 세계관 지침 없이 번역합니다",
    context: "",
    instructions: "",
  },
  {
    presetId: "warhammer-40k",
    name: "Warhammer 40,000",
    summary: "암울한 미래 전쟁과 제국의 이야기",
    context:
      "Warhammer 40,000 universe, a grimdark science fantasy setting. The Imperium of Man, the Emperor, Space Marines, Chaos, xenos, Astra Militarum, ranks, factions, weapons, and in-world terminology are fictional proper nouns. Preserve their meanings and use consistent Korean renderings across sentences.",
    instructions:
      "출판된 장편 SF 소설처럼 자연스러운 한국어로 번역하세요. 전쟁과 종교적 열광의 암울하고 엄숙한 분위기, 인물별 말투, 군사 계급과 전술 용어를 유지하세요. 고유명과 세계관 용어의 표기를 일관되게 하고 현대적 유행어나 가벼운 말투로 바꾸지 마세요.",
  },
  {
    presetId: "age-of-sigmar",
    name: "Age of Sigmar",
    summary: "필멸의 렐름을 무대로 한 신화적 서사",
    context:
      "Warhammer Age of Sigmar universe, an epic fantasy setting across the Mortal Realms. Stormcast Eternals, the Mortal Realms, gods, factions, ranks, places, magical forces, and in-world terminology are fictional proper nouns. Preserve their meanings and use consistent Korean renderings across sentences.",
    instructions:
      "서사적인 하이 판타지 소설처럼 자연스러운 한국어로 번역하세요. 신화적이고 장엄한 분위기, 인물별 말투, 군대와 마법의 용어를 유지하세요. 고유명과 세계관 용어의 표기를 일관되게 하고 현대적 구어체로 바꾸지 마세요.",
  },
  {
    presetId: "custom",
    name: "직접 설정",
    summary: "세계관과 번역 어투를 직접 입력합니다",
    context: "",
    instructions: "",
  },
];

export function defaultTranslationProfile(): TranslationProfile {
  return { ...translationProfilePresets[0] };
}
