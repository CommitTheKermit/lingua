# Lingua Web

최신 Compose 앱의 읽기 흐름과 Figma 자산을 옮긴 React/TypeScript 웹 앱이다. 데스크톱은 왼쪽 메뉴와 원문/입력의 두 열, 모바일은 기존 앱처럼 위아래 영역으로 구성한다.

## 기준

- 기능 기준: 앱 정상화 커밋 `425485b`, `4112ef3`, `fa407a5`, 문장 번역 교정 커밋 `e9d3d28`과 `../docs/cmp-kmp-dictionary-plan.md`
- 디자인: [Figma 메인](https://www.figma.com/design/eAlz9gxh72enryXYIaDZYH?node-id=203-4851), [사전](https://www.figma.com/design/eAlz9gxh72enryXYIaDZYH?node-id=203-6940), [읽기](https://www.figma.com/design/eAlz9gxh72enryXYIaDZYH?node-id=203-5569), [옵션](https://www.figma.com/design/eAlz9gxh72enryXYIaDZYH?node-id=203-5509)
- 앱의 아이콘, Noto Sans KR, SQLite 사전을 빌드 시 `shared/src/commonMain/composeResources`에서 복사한다. 생성된 사본은 Git에 넣지 않는다.
- 사전 데이터는 한국어 위키낱말사전 기반 CC BY-SA 4.0이며 배포물에 원본 `NOTICE.txt`를 포함한다.

## 실행

Node 24 LTS 권장. 지원 범위는 `package.json`의 `engines`에 명시한다.

```sh
cd web
npm ci
npm run dev
```

개발 서버는 `http://127.0.0.1:4174`다. 프로덕션 캐시와 개발 코드가 섞이지 않도록 미리보기와 포트를 나눈다.

```sh
npm test
npm run build
npm run preview
```

프로덕션 미리보기는 `http://127.0.0.1:4173`이다. `dist/` 전체를 HTTPS 서버의 루트에서 제공한다. 서비스 워커는 프로덕션에만 등록된다. 처음 접속해 `오프라인 읽기 준비됨`이 표시된 뒤 문서 읽기와 사전 검색을 오프라인으로 사용할 수 있다. 새 버전은 기존 탭을 닫고 다시 열 때 적용된다.

## 사용 흐름

1. TXT 파일을 선택하거나 화면에 끌어 놓는다. UTF-8, BOM이 있는 UTF-16과 10MB 이하 파일을 지원한다.
2. 한 문장씩 이동하며 직접 번역을 입력한다. 현재 문서, 읽기 위치, 문장별 번역, 책갈피, 표시 옵션은 IndexedDB에 자동 저장한다.
3. 문장 번역 영역이 보일 때 현재 문장을 DeepL로 자동 번역한다. 결과는 브라우저 세션 중 문장별로 재사용하고, 오류가 나면 같은 문장을 다시 시도할 수 있다.
4. 원문이나 단어 버튼을 누르면 오프라인 SQLite 사전이 열린다. 사전에서 온라인 번역을 요청하지 않는다.
5. 전체 읽기, 문서 검색, 줄 이동, 책갈피 목록은 같은 문장 인덱스를 공유한다. 읽기 모드에서 옵션을 열었다 닫아도 해당 모드를 유지한다.
6. 타이머로 읽는 시간을 기록하고 입력한 번역문을 CSV로 내려받는다.

현재 앱과 동일하게 활성 문서 한 개를 저장한다. 다른 내용의 파일을 열면 이전 문서의 번역과 책갈피가 교체되므로 필요한 번역은 먼저 내보낸다. 같은 내용을 다시 열면 현재 기록을 유지한다. 브라우저 데이터 삭제 시 기록이 사라지며 앱 또는 다른 기기와 동기화하지 않는다. 타이머와 화면 표시 토글은 세션 상태다.

## 온라인 번역 설정

기존 Firebase 프로젝트의 `asia-northeast3/translateProxy`를 사용한다. 익명 인증과 App Check를 통과한 요청만 서버로 전송한다. DeepL 비밀키는 서버에만 존재한다. 번역 영역이 켜져 있으면 현재 영어 문장 전체가 자동 전송된다. 문서 원문 전체와 사용자가 직접 쓴 번역은 전송하지 않는다.

Git에서 제외한 `.env.local`에 다음 환경변수를 설정한다. 실제 값은 커밋하거나 문서에 붙이지 않는다.

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_APP_ID`
- `VITE_FIREBASE_PROJECT_ID`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_RECAPTCHA_SITE_KEY`

운영 환경에는 Firebase 웹 앱과 reCAPTCHA Enterprise App Check 설정이 필요하다. 배포 도메인을 reCAPTCHA 허용 도메인에 등록한다. Firebase의 웹 클라이언트 설정과 공개 site key는 빌드 결과에 포함되지만, 서버 DeepL 키는 포함되지 않는다.

로컬 개발용 App Check debug token은 Git에서 제외한 `.env.development.local`의 `VITE_APPCHECK_DEBUG_TOKEN`으로만 제공한다. 운영 빌드에는 이 개발 환경 파일을 읽지 않으며 코드도 `import.meta.env.DEV`일 때만 사용한다. 로컬 프로덕션 미리보기에서 온라인 번역은 승인된 운영 도메인의 reCAPTCHA 검증을 통과하지 못할 수 있다. 로컬 온라인 번역 검증은 개발 서버에서 수행한다.

연결 정보가 없거나 서버 오류가 발생해도 오프라인 읽기와 사전 기능을 유지하고 재시도 안내를 표시한다. 온라인 사용량은 서버가 실제 반환한 수치만 표시한다.

## 구조

- `src/domain`: 앱과 동일한 문장 분리, 콘텐츠 식별, 문장 이동 및 CSV 규칙
- `src/services`: 브라우저 저장소, SQLite 사전, Firebase 문장 번역 연결
- `src/useCurrentSentenceTranslation.ts`: 현재 문장 자동 번역 요청의 수명, 결과, 재시도 연결
- `src/useReader.ts`: 복원, 저장 순서 보장, 실패 재시도
- `src/components`: 오프라인 사전, 옵션, 탐색 대화상자, 전체 읽기
- `src/App.tsx`: 화면 구성과 기능 연결

기존 앱의 `ReaderStore`가 파일, 저장, 사전, 번역을 함께 책임지는 단일 책임 원칙 위반을 그대로 복제하지 않고 각 서비스로 분리했다. 다만 `App.tsx`에는 화면 연결과 파일 선택/타이머 상태가 모여 있어 후속 기능 증가 시 별도 훅으로 나눌 여지가 있다.

## 검증

- Vitest 25개: 문장 분리 예외, 빈 문서, 동일/다른 문서 열기, 인덱스 경계, 9번째 이후 단어, CSV 이스케이프, 저장 순서와 복원, 실제 SQLite 데이터/활용형/라이선스, 문장 번역의 늦은 응답 무시·재사용·재시도·숨김 처리
- `npm run build`: TypeScript 검사, Vite 번들 생성, 정적 자원 사전 캐시 생성
- 브라우저: 파일 열기, 문장 이동, 번역 자동 저장 및 새로고침 복원, 책갈피, 사전 결과, 검색 이동, 읽기 모드/옵션 왕복, 모바일 입력 영역 복구
- 실제 Firebase 문장 번역 연결: 이전 사전 기반 동작에서는 `serendipitously`의 `우연히` 결과와 사용량 `1/200`을 확인했다. 현재 문장 자동 번역 전환 후 종단 성공 검증은 아직 필요하다.
- 반응형 화면: 1280×720, 390×844, 360×800 확인, 모바일 메뉴와 옵션 대화상자 및 가로 넘침 검사
- CSV: 브라우저에서 내려받은 실제 파일의 한국어 번역 내용 확인
- Node 24에서 테스트 25개 통과, npm 의존성 검사 취약점 0건
- 서버 중지 후 새로고침: 문서 및 번역 입력 복원, 캐시된 SQLite에서 `ran` 첫 조회 성공
- 정적 자원에만 `ignoreVary`를 적용해 미리보기 서버의 `Vary: Origin`으로 인한 오프라인 모듈 캐시 누락을 방지

최신 앱의 문장 자동 번역 흐름에 맞춘 웹 변경은 단위 테스트와 빌드로 확인한다. 실제 Firebase 종단 응답은 배포 환경의 App Check 조건에서 별도로 검증해야 한다.

인터넷 공개 배포와 실제 운영 도메인의 reCAPTCHA 검증은 별도 단계다. 기존 개인정보처리방침 Hosting 설정은 수정하지 않는다.
