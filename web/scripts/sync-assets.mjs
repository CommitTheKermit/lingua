import { cp, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const source = new URL(
  "../../shared/src/commonMain/composeResources/",
  import.meta.url,
);
const target = new URL("../public/", import.meta.url);
await mkdir(new URL("assets/", target), { recursive: true });
await cp(
  fileURLToPath(new URL("drawable/", source)),
  fileURLToPath(new URL("assets/", target)),
  { recursive: true },
);
await cp(
  fileURLToPath(new URL("font/noto_sans_kr.ttf", source)),
  fileURLToPath(new URL("assets/noto_sans_kr.ttf", target)),
);
await cp(
  fileURLToPath(new URL("files/dict/", source)),
  fileURLToPath(new URL("dict/", target)),
  { recursive: true },
);
console.log("앱의 피그마 자산, 글꼴, 사전 DB와 출처 고지를 복사했습니다.");
