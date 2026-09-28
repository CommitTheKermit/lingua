import { cp, mkdir, rm } from "node:fs/promises";
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
// 이전 빌드의 대용량 TTF가 배포물에 남지 않게 제거한다.
await rm(new URL("assets/noto_sans_kr.ttf", target), { force: true });
await mkdir(new URL("licenses/", target), { recursive: true });
await cp(new URL("../node_modules/@fontsource-variable/noto-sans-kr/LICENSE", import.meta.url), new URL("licenses/noto-sans-kr.txt", target));
await cp(
  fileURLToPath(new URL("files/dict/", source)),
  fileURLToPath(new URL("dict/", target)),
  { recursive: true },
);
console.log("앱의 피그마 자산, 글꼴, 사전 DB와 출처 고지를 복사했습니다.");
