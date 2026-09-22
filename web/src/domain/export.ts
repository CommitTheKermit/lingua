import type { ReaderDocument } from "./reader";
const cell = (text: string) => `"${text.replace(/"/g, '""')}"`;
export function exportCsv(document: ReaderDocument): string {
  const rows = Object.entries(document.translations)
    .filter(([, text]) => text.trim())
    .sort(([a], [b]) => Number(a) - Number(b));
  return (
    "sentence_index,source,user_translation\r\n" +
    rows
      .map(
        ([index, translation]) =>
          `${index},${cell(document.sentences[Number(index)])},${cell(translation)}\r\n`,
      )
      .join("")
  );
}
export function downloadTranslations(document: ReaderDocument) {
  const url = URL.createObjectURL(
    new Blob(["\ufeff", exportCsv(document)], {
      type: "text/csv;charset=utf-8",
    }),
  );
  const link = globalThis.document.createElement("a");
  link.href = url;
  link.download = `${document.title.replace(/\.txt$/i, "")}-번역.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
