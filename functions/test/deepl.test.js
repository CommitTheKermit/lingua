const {translate} = require("../src/deepl");

test("sends context and custom instructions to DeepL without exposing its key", async () => {
  let request;
  const translated = await translate({
    text: "The Emperor protects.",
    sourceLang: "EN",
    targetLang: "KO",
    context: "Warhammer 40,000 universe",
    instructions: "엄숙한 소설체로 번역하세요.",
    apiKey: "server-only-test-key",
    fetchImpl: async (url, options) => {
      request = {url, options};
      return {ok: true, json: async () => ({translations: [{text: "황제께서 지켜보신다."}]})};
    },
  });

  expect(translated).toBe("황제께서 지켜보신다.");
  expect(request.options.headers.Authorization).toBe("DeepL-Auth-Key server-only-test-key");
  expect(JSON.parse(request.options.body)).toEqual({
    text: ["The Emperor protects."],
    source_lang: "EN",
    target_lang: "KO",
    preserve_formatting: true,
    context: "Warhammer 40,000 universe",
    custom_instructions: ["엄숙한 소설체로 번역하세요."],
  });
});

test("omits empty optional translation guidance", async () => {
  let body;
  await translate({
    text: "Hello.",
    sourceLang: "EN",
    targetLang: "KO",
    apiKey: "server-only-test-key",
    fetchImpl: async (_url, options) => {
      body = JSON.parse(options.body);
      return {ok: true, json: async () => ({translations: [{text: "안녕하세요."}]})};
    },
  });
  expect(body).toEqual({
    text: ["Hello."],
    source_lang: "EN",
    target_lang: "KO",
    preserve_formatting: true,
  });
});
