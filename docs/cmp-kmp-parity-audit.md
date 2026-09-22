# Flutter - KMP/CMP 구현 패리티 감사

> 이 문서는 통합 커밋 `b8f6dfb`의 역사적 감사 기록이다. 현재 구현 상태와 정상화 검증은 `docs/cmp-kmp-feature-matrix.md`와 `docs/normalization-verification.md`를 따른다.

## 감사 기준

- Flutter 기준은 현 워크트리 커밋 `9fcace6`의 `lib/` 전체다.
- KMP/CMP와 Firebase Functions 기준은 통합 커밋 `b8f6dfb`의 `lingua_kmp/`와 `functions/` 전체다. 현재 워크트리의 KMP 파일은 기준으로 쓰지 않고 모든 KMP 인용은 `git show b8f6dfb:<경로>`로 확인했다.
- `b8f6dfb`는 `8ed96ce`, `bf00dd9`, `3aae6be`, `3059c23`을 충돌 없이 병합한 합집합이다. 문장 분리, iOS CSV 저장, 익명 UID 검증, 기능표가 이전 기준 `1cbd42b`보다 추가됐다.
- 같은 입력에 같은 결과면 `동등`, 양쪽 구현 차이가 계획된 학습용 재설계면 `재설계-의도됨`, 문서 근거가 없으면 `차이-예기치못함`이다. 한쪽 구현이 없으면 `미이식`, 명시적 제외면 `의도적 제외`, 코드나 실행으로 확정하지 못하면 `미확인`이다.
- 인터뷰 결정 A2의 핵심 수직 슬라이스 우선과 A3의 학습용 재설계는 통합 Seed의 공통 문장 인덱스 계약과 Flutter 구조 미이식 지시로 확인한다 (`.ouroboros/seeds/cmp-kmp-migration-v2.yaml:9-14, 26-27, 33-45`).

## 요약

동등 1건 / 재설계-의도됨 15건 / 차이-예기치못함 3건 / 미이식 3건 / 의도적 제외 4건 / 미확인 0건이다. 표는 Flutter `lib/` 전체를 사용자 기능 단위로 묶는다.

기존 `불일치` 16건은 재설계-의도됨 13건과 차이-예기치못함 3건으로 나눴다. 이전 `미이식`이던 원문·번역·입력 영역과 표시 토글은 새 기능표가 구현 완료로 바꾼 근거에 따라 재설계-의도됨으로 재판정했다. 기존 요약은 표 행 수와 맞지 않았고, 이 리포트는 26개 행을 다시 합산했다.

Flutter `ReadScreen`은 UI, 파일 저장, 번역, quota와 상태를 한 클래스에 결합해 단일 책임 원칙을 위반한다 (`lib/screens_mobile/main_screens/read_screen.dart:90-158, 178-258`). 이 구조를 복제하지 않는다는 계획도 확인된다 (`docs/cmp-kmp-dictionary-plan.md:146-165`).

## 전체 기능 대조표

KMP 줄번호는 모두 `b8f6dfb` 기준이다. 재설계 행의 비고에는 개별 근거 문서와 줄을 적었다.

| 기능 | Flutter 근거 | KMP/Functions 근거 | 판정 | 비고 |
| --- | --- | --- | --- | --- |
| TXT 선택과 UTF-8 내용 읽기 | `lib/util/file_process/file_process.dart:24-36, 39-61` | `lingua_kmp/shared/src/androidMain/kotlin/com/ao/lingua/reader/TextFilePicker.android.kt:20-37`, `lingua_kmp/shared/src/iosMain/kotlin/com/ao/lingua/reader/TextFilePicker.ios.kt:36-75` | 동등 | 유효한 UTF-8 TXT 전체를 리더 입력으로 전달한다. |
| 문서 제목 산출 | `lib/util/file_process/file_process.dart:30-32` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/App.kt:34-36`, `lingua_kmp/shared/src/androidMain/kotlin/com/ao/lingua/reader/TextFilePicker.android.kt:31-37` | 차이-예기치못함 | Flutter는 첫 `.` 앞을 제목으로 저장하고 KMP는 파일명을 유지한다. 이 제목 규칙 차이를 정당화한 계획 줄은 없다. |
| 문장 정규화와 문장 경계 분리 | `lib/util/file_process/file_process.dart:56-59, 67-112` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:10-67` | 재설계-의도됨 | Flutter 쪽 결함을 KMP가 교정한다. 마지막 문장 보존과 안정적 분리는 `docs/cmp-kmp-feature-matrix.md:15`, 공통 인덱스는 `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:9-11`이 근거다. |
| 이전과 다음, 줄 바로 이동의 경계 | `lib/screens_mobile/main_screens/read_screen.dart:117-131, 419-440`, `lib/widgets/read_widgets/dialog/dialog_line_search.dart:124-177` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:111-115, 127-135` | 재설계-의도됨 | KMP는 범위 밖 이동을 막는다. 줄 바로 이동 공통 `moveTo` 계약은 `docs/cmp-kmp-feature-matrix.md:20`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:11`이 근거다. |
| 읽기 위치 저장과 복원 키 | `lib/util/shared_preferences/save_index.dart:4-14`, `lib/screens_mobile/main_screens/read_screen.dart:111-123` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:119-125, 192-198`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderDatabase.kt:73-150` | 재설계-의도됨 | 제목 키 대신 콘텐츠 ID와 `reader.db`를 쓴다. `docs/cmp-kmp-feature-matrix.md:17`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:10-12`가 근거다. |
| 사용자 입력 저장 단위와 복원 | `lib/screens_mobile/main_screens/read_screen.dart:125-130, 447-456`, `lib/util/file_process/translate_input_process.dart:6-24` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:150-159`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderDatabase.kt:166-188` | 재설계-의도됨 | Flutter의 문장 문자열 JSON 키는 같은 문장의 다른 위치를 구분하지 못하는 결함이다. KMP는 문서 ID와 문장 인덱스로 즉시 저장한다. `docs/cmp-kmp-feature-matrix.md:18`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:11-12`가 근거다. |
| 문장별 기계 번역, 캐시, 원문·번역·입력 영역 | `lib/screens_mobile/main_screens/read_screen.dart:132-157, 297-343` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:78-106`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:254-304`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/translation/RemoteTranslation.kt:10-50` | 재설계-의도됨 | KMP는 사전 누락에서만 선택적으로 원격 번역을 연결하고 사용자 입력을 즉시 저장한다. 영역 표시 완료는 `docs/cmp-kmp-feature-matrix.md:21`, 선택형 번역은 `docs/cmp-kmp-dictionary-plan.md:104-113, 138-144`가 근거다. |
| 번역과 입력 영역의 표시 토글 | `lib/widgets/read_widgets/animation_widgets/translate_allow_button.dart:58-76`, `lib/widgets/read_widgets/animation_widgets/input_allow_button.dart:56-75` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:78-95, 162-165`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:355-394` | 재설계-의도됨 | Flutter의 두 토글 대신 KMP는 영역별 표시 스타일을 저장한다. `docs/cmp-kmp-feature-matrix.md:21`이 근거다. |
| 단어 토큰화와 불용어 | `lib/util/string_process/sentence_process.dart:3-17`, `lib/util/etc/stopword.dart:1-4`, `lib/widgets/read_widgets/words_widget.dart:24-30` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:275-286` | 차이-예기치못함 | Flutter는 정규화, 중복·불용어 제거를 하고 KMP는 공백 분리만 한다. 이 차이를 정당화한 계획 줄은 없다. |
| 문서 내 문자열 검색 | `lib/widgets/read_widgets/dialog/search_list_dialog.dart:85-100, 122-130`, `lib/screens_mobile/main_screens/read_mode_screen.dart:261-269` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:70-76, 137-139`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:305-315` | 재설계-의도됨 | KMP는 문장 인덱스 목록으로 이동한다. 검색 구현 완료와 인덱스 반환은 `docs/cmp-kmp-feature-matrix.md:19`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:11`이 근거다. |
| 페이지와 줄 분할 뷰어 | `lib/util/string_process/pager.dart:45-100`, `lib/screens_mobile/main_screens/read_mode_screen.dart:43-60, 113-166` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:167-169`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:172-178, 320-352` | 재설계-의도됨 | 화면 메트릭 물리 페이지 대신 문장 인덱스 전체 보기다. `docs/cmp-kmp-feature-matrix.md:24`가 근거다. |
| 뷰어 북마크와 저장 범위 | `lib/util/bookmark_process/bookmark_util.dart:6-32`, `lib/screens_mobile/main_screens/read_mode_screen.dart:220-244`, `lib/screens_mobile/bookmark_list_dialog.dart:95-108` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:141-148`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderDatabase.kt:153-164, 235-242` | 재설계-의도됨 | 전 문서 공용 페이지 배열 대신 문서 ID와 문장 인덱스 복합키다. `docs/cmp-kmp-feature-matrix.md:25`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:11-12`가 근거다. |
| CSV 기록 추출 | `lib/util/file_process/file_process.dart:115-149` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:179-201`, `lingua_kmp/shared/src/androidMain/kotlin/com/ao/lingua/reader/ReaderPlatform.android.kt:20-36`, `lingua_kmp/shared/src/iosMain/kotlin/com/ao/lingua/reader/ReaderPlatform.ios.kt:38-60` | 재설계-의도됨 | 공통 CSV는 문장 인덱스와 사용자 번역을 RFC 방식으로 이스케이프한다. `docs/cmp-kmp-feature-matrix.md:22`가 근거다. 이전 iOS의 `document.content as NSString` 억제 캐스트는 동작하지 않았지만 통합 커밋은 실제 UTF-8 바이트를 `NSData.create(bytes = pinned.addressOf(0), ...)`로 쓴다 (`1cbd42b:lingua_kmp/shared/src/iosMain/kotlin/com/ao/lingua/reader/ReaderPlatform.ios.kt:40-47`, `b8f6dfb:lingua_kmp/shared/src/iosMain/kotlin/com/ao/lingua/reader/ReaderPlatform.ios.kt:43-47`). |
| 4개 표시 옵션과 지속성 | `lib/models/read_option.dart:9-58`, `lib/screens_mobile/etc_screens/read_option_screen.dart:180-203, 419-447` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:78-95, 162-165`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderDatabase.kt:190-230`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:355-394` | 재설계-의도됨 | KMP는 SQLite와 제한된 글꼴·색 전환을 쓴다. 표시 설정 구현 완료는 `docs/cmp-kmp-feature-matrix.md:21`이다. |
| 리더 드로어와 화면 진입 | `lib/widgets/read_widgets/read_drawer.dart:17-127`, `lib/screens_mobile/main_screens/read_screen.dart:476-621` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:77-143` | 차이-예기치못함 | 메뉴 구성과 파일·CSV 진입 위치가 다르다. 기능표는 드로어를 아직 UI 결정 필요로 둔다 (`docs/cmp-kmp-feature-matrix.md:23`). |
| 오프라인 사전 결과와 표시 | `lib/util/api/api_util.dart:15-62`, `lib/widgets/read_widgets/dialog/dialog_word_widget.dart:18-26`, `lib/widgets/read_widgets/dictionary_result_widget.dart:37-73` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/dictionary/Dictionary.kt:25-69, 88-124`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/dictionary/DictionarySheet.kt:51-78` | 재설계-의도됨 | Django HTTP 렌더링 대신 번들 SQLite의 품사·sense 순서를 표시한다. `docs/cmp-kmp-dictionary-plan.md:11-16, 104-113`이 근거다. |
| 사전 조회 키 정규화와 활용형 | `lib/util/api/api_util.dart:15-32` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/dictionary/Dictionary.kt:25-27, 36-60`, `lingua_kmp/shared/src/commonTest/kotlin/com/ao/lingua/dictionary/DictionaryTest.kt:19-27` | 재설계-의도됨 | KMP는 trim·소문자·양끝 구두점 정규화와 사전 생성 활용형 키를 쓴다. `docs/cmp-kmp-dictionary-plan.md:34-41, 104-109`, `docs/cmp-kmp-feature-matrix.md:27`이 근거다. |
| 단어장 기록 | `lib/util/api/api_util.dart:31, 64-85` | 없음 | 미이식 | KMP 사전 조회에 기록 호출이나 저장소가 없다. |
| 원격 번역 요청, 인증, 응답, 오류 | `lib/util/api/api_util.dart:113-152` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/translation/RemoteTranslation.kt:10-50`, `functions/src/index.js:12-38, 45-61` | 재설계-의도됨 | Flutter 직접 DeepL 호출 대신 익명 인증과 callable `translateProxy`를 쓴다. `docs/cmp-kmp-dictionary-plan.md:138-144`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:14-16`이 근거다. Functions 소스와 Jest 테스트가 통합됐다. `requireAnonymous`는 이전 `!request.auth` 확인 대신 빈 문자열을 포함한 비문자열 UID를 거절한다 (`1cbd42b:functions/src/index.js:31-37`, `b8f6dfb:functions/src/index.js:31-38`). |
| quota 계산과 표시 | `lib/util/api/api_user.dart:213-280`, `lib/screens_mobile/main_screens/read_screen.dart:197-246`, `lib/widgets/read_widgets/call_limit_widget.dart:42-57, 123-133` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/translation/RemoteTranslation.kt:18-44`, `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:223-242`, `functions/src/translation.js:3-39` | 재설계-의도됨 | 이메일 Django refresh 대신 익명 UID callable quota 상태를 표시한다. `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:15-18, 44-45`가 근거다. |
| 클라이언트 DeepL 키 취득 | `lib/util/api/api_util.dart:154-173`, `lib/screens_mobile/main_screens/read_screen.dart:95-101` | 없음 | 미이식 | KMP는 직접 키 취득 경로가 없다. `docs/cmp-kmp-dictionary-plan.md:138-143`에 부합하지만 Flutter의 해당 기능은 이식되지 않았다. |
| 종료 확인, toast, 공통 Flutter 전환과 레거시 보조 모델 | `lib/util/etc/exit_confirm.dart:7-54`, `lib/util/etc/error_toast.dart:4-14`, `lib/util/etc/change_screen.dart:3-45`, `lib/models/text_info_model.dart:3-15`, `lib/screens_mobile/interactable_page_widget.dart:3-31` | 없음 | 미이식 | 동일 종료 확인, Fluttertoast, PageRouteBuilder, `TextInfo`, RichText 보조 위젯은 없다. `InteractableTextsWidget` 호출은 주석 처리돼 런타임 기능이 아니다. |
| 로그인과 로그아웃 | `lib/screens_mobile/user_screens/login_screen.dart:50-73, 219-257`, `lib/widgets/read_widgets/read_drawer.dart:84-104`, `lib/util/api/api_user.dart:96-120` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/App.kt:29-36` | 의도적 제외 | `docs/cmp-kmp-feature-matrix.md:29`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:14`가 로그인 없는 앱을 명시한다. 익명 인증은 로그인 기능이 아니다. |
| 회원가입과 이메일 인증 | `lib/screens_mobile/user_screens/signup_screens/signup_screen_first.dart:67-134`, `lib/util/api/api_user.dart:14-94`, `lib/util/etc/validators.dart:1-27` | 없음 | 의도적 제외 | `docs/cmp-kmp-feature-matrix.md:29`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:14`의 명시적 제외 범위다. |
| ID 찾기, 비밀번호 찾기와 변경 | `lib/screens_mobile/user_screens/Id_Pw_screens/id_find_screen.dart:106-131`, `lib/screens_mobile/user_screens/Id_Pw_screens/pw_find_screen.dart:133-177`, `lib/screens_mobile/user_screens/Id_Pw_screens/pw_change_screen.dart:104-210` | 없음 | 의도적 제외 | `docs/cmp-kmp-feature-matrix.md:29`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:14`의 계정 복구 제외 범위다. |
| 로그인 이메일 기억과 사용자 전역 상태 | `lib/screens_mobile/user_screens/login_screen.dart:50-73, 231-238`, `lib/models/user_model.dart:1-6`, `lib/util/shared_preferences/preference_manager.dart:3-26` | `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/translation/RemoteTranslation.kt:33-39` | 의도적 제외 | 로그인 흐름의 부속 상태다. `docs/cmp-kmp-feature-matrix.md:29`, `.ouroboros/seeds/cmp-kmp-migration-v2.yaml:14`가 근거다. |

## 동작 차이 상세

### 문장 정규화와 문장 경계

여섯 반례는 Flutter `lib/util/file_process/file_process.dart:67-112`와 KMP `lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:10-67`의 제어 흐름으로 확인했다. KMP 결과는 Flutter 결함을 교정하는 재설계다.

| 입력 | Flutter 결과 | KMP 결과 |
| --- | --- | --- |
| `Mr. Kim ran.` | `['Mr.', 'Kim ran.']` | `['Mr. Kim ran.']`, 약어 점 제외 |
| `Last line` | `[]` | `['Last line']`, 마지막 잔여 문장 보존 |
| `He left (finally.) Then slept.` | `['He left (finally.', ') Then slept.']` | `['He left (finally.)', 'Then slept.']`, 닫는 괄호 소비 |
| `3.14 is pi. Next!` | `['3.', '14 is pi.', 'Next!']` | `['3.14 is pi.', 'Next!']`, 소수점 제외 |
| `Hi... Next!` | `['Hi.', '.', '.', 'Next!']` | `['Hi...', 'Next!']`, 연속 종결부호 소비 |
| `Alpha\\n\\nBeta` | `[]` | `['Alpha', 'Beta']`, 빈 줄 문단 경계 |

닫는 작은따옴표만은 Flutter도 `startsWith` 재결합 분기에서 보정해 결과가 같다 (`lib/util/file_process/file_process.dart:79-85`). 통합 커밋은 도메인 내부 점도 문장 경계에서 제외하고 회귀 테스트가 `example.com`을 확인한다 (`lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:55-66`, `lingua_kmp/shared/src/commonTest/kotlin/com/ao/lingua/reader/ReaderTest.kt:20-23`).

### 나머지 반례

- 페이지: `A. B.`가 한 화면에 들어가면 Flutter는 `['A. B.']` 하나를 만들지만 (`lib/util/string_process/pager.dart:53-95`), KMP 전체 보기는 `['A.', 'B.']`다 (`lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:172-178, 320-352`).
- 검색: `pages = ['Hit.']`, 질의 `hit`에서 Flutter는 `move :Hit.`를 만들고 호출부가 `double.parse('Hit.')`를 해 `FormatException`이 난다 (`lib/widgets/read_widgets/dialog/search_list_dialog.dart:85-100, 122-130`, `lib/screens_mobile/main_screens/read_mode_screen.dart:261-269`). KMP는 `[0]`을 반환하고 `moveTo(0)`을 호출한다 (`lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/Reader.kt:70-76, 131-139`).
- 사전: `  RUN! `은 Flutter가 그대로 HTTP body로 보내지만 (`lib/util/api/api_util.dart:15-25`), KMP 조회 키는 `run`이다 (`lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/dictionary/Dictionary.kt:25-27, 36-60`). fixture의 `ran -> run`은 테스트로 확인한다 (`lingua_kmp/shared/src/commonTest/kotlin/com/ao/lingua/dictionary/DictionaryTest.kt:19-27`). Django 서버가 없으므로 Flutter `ran`의 실제 반환 표제어는 미확인이다.
- 토큰: `The cat's 3.14`는 Flutter에서 `['cats']`가 되고 (`lib/util/string_process/sentence_process.dart:3-17`, `lib/util/etc/stopword.dart:1-4`), KMP에서는 `['The', "cat's", '3.14']`가 된다 (`lingua_kmp/shared/src/commonMain/kotlin/com/ao/lingua/reader/ReaderScreen.kt:275-286`). 이는 계획 근거 없는 차이-예기치못함이다.

## 테스트 실행 증거

아래는 제공된 이 기계의 실제 실행 결과다. 이 감사에서는 Gradle을 재실행하지 않았다.

### Firebase Functions

`functions/`에서 `npm test`를 실행했다.

```text
PASS test/translation.test.js
PASS test/index.test.js
Test Suites: 2 passed, 2 total
Tests:       11 passed, 11 total
```

### KMP

`lingua_kmp/`에서 `./gradlew test`는 종료 코드 0과 `BUILD SUCCESSFUL`을 냈다. 실제 실행된 테스트 태스크는 `:shared:iosSimulatorArm64Test` 하나다.

```text
shared/build/test-results/iosSimulatorArm64Test/TEST-...ReaderTest.xml
  tests="3" skipped="0" failures="0" errors="0"
shared/build/test-results/iosSimulatorArm64Test/TEST-...DictionaryTest.xml
  tests="1" skipped="0" failures="0" errors="0"
```

테스트 이름은 다음 네 개다.

```text
splitsAndNormalizesDocumentTextWithStableIndexes
readerFeaturesShareAndResumeTheSentenceIndexContract
navigationAndViewerIndexesStopAtDocumentBounds
exactLookupNormalizesInputAndPreservesSenseOrder
```

소스의 네 테스트 선언도 확인했다 (`lingua_kmp/shared/src/commonTest/kotlin/com/ao/lingua/reader/ReaderTest.kt:9-100`, `lingua_kmp/shared/src/commonTest/kotlin/com/ao/lingua/dictionary/DictionaryTest.kt:11-30`).

## Ouroboros `FABRICATION_SUSPECTED` 근본 원인

- 측정상 `./gradlew test`는 종료 코드 0이어도 stdout에 테스트 건수나 통과·실패 줄을 하나도 내지 않았다. UP-TO-DATE 상태와 `cleanAllTests` 후 `:shared:iosSimulatorArm64Test`가 실제 실행된 상태 모두 테스트 증거 줄 수는 0이었다. 증거는 `shared/build/test-results/*/TEST-*.xml`뿐이다. stdout만 훑는 검증기는 테스트 증거를 찾지 못해 날조로 판정한다.
- `commonTest`는 `iosSimulatorArm64` 타깃에서만 실행된다. `shared/build.gradle.kts:34-45`는 `androidLibrary { }`를 쓰지만 `withHostTest { }`를 선언하지 않아 Android/JVM 호스트 단위 테스트 컴파일이 만들어지지 않는다. `:androidApp:test`는 UP-TO-DATE이고 `androidApp/src`에는 `main` 소스셋만 있다.
- 이는 인터뷰 결정 A1b인 "CMP/KMP 구조는 유지하되 개발/빌드/검증은 Android만"과 어긋난다. 현재 공통 로직 검증은 iOS 시뮬레이터에서만 이뤄진다.

## 저장소 레이아웃 사실

`chore/flatten-flutter-root`의 `e90b14e`는 Flutter를 저장소 루트로 옮겼고 `lingua_kmp/`와 `functions/`를 포함하지 않는다. KMP 라인은 `lingua_flutter/ + lingua_kmp/ + functions/` 레이아웃을 유지한다. 두 갈래의 공통 조상은 `7307e98`이며, `git merge-base --is-ancestor` 양방향 검사는 모두 거짓이다. 따라서 현재 저장소에는 호환되지 않는 두 레이아웃이 병존한다.

## 미확인 항목

- Django 서버 소스가 없으므로 Django 사전 응답, 단어장과 quota 서버 동작은 Flutter 호출 코드만으로 확정할 수 없다.
- 번들 사전 DB의 전수 데이터 품질, 출처 메타데이터와 라이선스 고지는 DB 내용을 전수 검증하지 않았다.
- `paginateText`의 실제 기기별 화면 측정은 하지 않았다.
