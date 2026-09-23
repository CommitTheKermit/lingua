const FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate";

async function translate({
  text,
  sourceLang,
  targetLang,
  context = "",
  instructions = "",
  apiKey,
  fetchImpl = fetch,
}) {
  const body = {
    text: [text],
    source_lang: sourceLang,
    target_lang: targetLang,
    preserve_formatting: true,
  };
  if (context.trim()) body.context = context.trim();
  if (instructions.trim()) body.custom_instructions = [instructions.trim()];
  const response = await fetchImpl(FREE_ENDPOINT, {
    method: "POST",
    headers: {
      Authorization: `DeepL-Auth-Key ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`DeepL request failed with status ${response.status}`);
  const translated = (await response.json()).translations?.[0]?.text;
  if (typeof translated !== "string") throw new Error("DeepL response did not contain a translation");
  return translated;
}

module.exports = {FREE_ENDPOINT, translate};
