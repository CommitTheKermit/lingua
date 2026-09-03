# 에뮬레이터 1대1 대조 실행 체크리스트

## 목적과 범위

하나의 Android 에뮬레이터에 KMP 앱과 로그인 우회 패치를 적용한 Flutter 앱을 함께 설치하고, 동일한 TXT 입력을 열어 서버 없이 동작하는 리더 기능을 1대1로 눈으로 대조한다. 이 절차는 `docs/cmp-kmp-parity-audit.md`에서 `재설계-의도됨` 또는 `차이-예기치못함`으로 판정된 항목 중 에뮬레이터에서 확인 가능한 항목만 다룬다. 빌드나 설치 전에 이 문서를 수정하지 말고, Flutter 원본 브랜치 `legacy/flutter` 및 KMP 브랜치 `main`에는 패치를 커밋하거나 적용하지 않는다.

## 사전 준비

모든 KMP 명령은 저장소 루트에서 실행한다. 아래 `SDK_ROOT`, `ADB`, `DEVICE_ID` 변수는 현재 셸에서만 사용한다.

1. AVD를 부팅하고 연결을 확인한다.

   첫 터미널에서 실행한다.

   ```sh
   SDK_ROOT="$HOME/Library/Android/sdk"
   "$SDK_ROOT/emulator/emulator" -avd Pixel_6a_API_33_2
   ```

   부팅이 끝난 뒤 두 번째 터미널에서 실행한다.

   ```sh
   SDK_ROOT="$HOME/Library/Android/sdk"
   ADB="$SDK_ROOT/platform-tools/adb"
   "$ADB" wait-for-device
   "$ADB" devices
   ```

   기대 결과는 `Pixel_6a_API_33_2`의 단일 기기가 `device` 상태로 보이는 것이다. 이후 같은 셸에서 아래를 실행해 대상 직렬 번호를 고정한다.

   ```sh
   DEVICE_ID="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
   test -n "$DEVICE_ID" && printf '%s\n' "$DEVICE_ID"
   ```

   기대 결과는 비어 있지 않은 직렬 번호 한 개다.

2. KMP 앱의 Firebase 설정 파일을 배치하고 debug 앱을 설치한다.

   `androidApp/src/main/kotlin/com/friendshiphyun/lingua/MainActivity.kt:22`는 초기화된 FirebaseApp을 요구한다. 다음 명령은 확인된 원본 파일을 KMP 앱 모듈에 복사한다. 파일 내용과 키는 보거나 기록하지 않는다.

   ```sh
   cp "/Users/ujeonghyeon/.codex/worktrees/lingua-cmp-kmp-completion/lingua_kmp/androidApp/google-services.json" androidApp/google-services.json
   ./gradlew :androidApp:installDebug
   ```

   기대 결과는 Gradle의 `BUILD SUCCESSFUL` 및 applicationId `com.friendshipHyun`인 KMP debug 앱의 설치다. `androidApp/google-services.json`은 `.gitignore:16`의 `**/google-services.json` 규칙으로 추적되지 않는다.

   설치 후 앱을 띄우려면 아래를 실행한다.

   ```sh
   "$ADB" shell monkey -p com.friendshipHyun -c android.intent.category.LAUNCHER 1
   ```

3. Flutter 전용 작업 트리와 패치 브랜치를 만들고, 로그인 없이 ReadScreen으로 시작하는 별도 앱을 설치한다.

   저장소 루트에서 실행한다. 이 방식은 현재 `main` 작업 트리를 유지한 채 `legacy/flutter`에서만 파생 브랜치를 만든다.

   ```sh
   git worktree add ../lingua-flutter-legacy-parity -b chore/emulator-parity-legacy legacy/flutter
   cd ../lingua-flutter-legacy-parity
   ```

   Flutter 작업 셸에서 아래 세 변수를 다시 정의한다.

   ```sh
   SDK_ROOT="$HOME/Library/Android/sdk"
   ADB="$SDK_ROOT/platform-tools/adb"
   DEVICE_ID="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
   ```

   다음 두 편집을 편집기로 직접 수행한다. `lib/screens_mobile/main_screens/read_screen.dart:35`의 `const ReadScreen({super.key})`는 필수 인자가 없으므로 그대로 사용한다. debug applicationId suffix는 KMP 앱을 덮어쓰지 않게 한다.

   - 편집 A - `lib/main.dart`
     - 6행 `import 'package:lingua/screens_mobile/user_screens/login_screen.dart';` 바로 위에 `import 'package:lingua/screens_mobile/main_screens/read_screen.dart';`를 추가한다.
     - 88행 `      home: const LoginScreen(),`을 `      home: const ReadScreen(),`로 바꾼다. 들여쓰기 6칸을 유지한다.
   - 편집 B - `android/app/build.gradle`
     - 57행 `    buildTypes {` 바로 다음 줄에 아래 3줄을 추가한다. `debug {`는 8칸, `applicationIdSuffix`는 12칸, 닫는 `}`는 8칸 들여쓰기한다.

       ```gradle
           debug {
               applicationIdSuffix ".legacy"
           }
       ```

   편집 뒤 아래를 확인한다. 각각 한 줄씩 나와야 한다.

   ```sh
   grep -n "home: const ReadScreen" lib/main.dart
   grep -n "applicationIdSuffix" android/app/build.gradle
   ```

   ```sh
   /Users/ujeonghyeon/fvm/default/bin/flutter pub get
   /Users/ujeonghyeon/fvm/default/bin/flutter install -d "$DEVICE_ID"
   ```

   설치 후 앱을 띄우려면 아래를 실행한다.

   ```sh
   "$ADB" shell monkey -p com.friendshipHyun.legacy -c android.intent.category.LAUNCHER 1
   ```

   기대 결과는 패치 브랜치에서 Flutter 앱이 설치되고, 로그인 화면 대신 리더 시작 화면이 바로 표시되는 것이다. Flutter에는 Firebase 의존성과 초기화가 없으므로 `google-services.json`은 필요 없다. 근거는 `legacy/flutter:pubspec.yaml`의 Firebase 의존성 부재와 `legacy/flutter:lib/main.dart:9-15`의 초기화 코드 부재다.

4. 두 패키지가 같은 AVD에 함께 설치되었는지 확인한다.

   ```sh
   "$ADB" shell pm list packages | grep friendshipHyun
   ```

   기대 결과는 아래 두 줄이다.

   ```text
   package:com.friendshipHyun
   package:com.friendshipHyun.legacy
   ```

   둘 중 하나만 보이면 대조를 시작하지 않는다. Flutter 패치의 `applicationIdSuffix ".legacy"` 적용 여부를 먼저 확인한다.

## 대조용 고정 입력 파일

아래 파일 하나만 두 앱에서 각각 연다. 파일명과 내용은 결과 기록의 기준이므로 바꾸지 않는다. `Alpha`와 `Beta.` 사이에는 빈 줄 하나를 둔다. 마지막 줄에는 종결부호를 넣지 않는다.

```sh
PARITY_TMP="$(mktemp -d /private/tmp/lingua-emulator-parity.XXXXXX)"
INPUT_FILE="$PARITY_TMP/emulator-parity-input.txt"
cat > "$INPUT_FILE" <<'EOF'
Mr. Kim ran.
3.14 is pi. Next!
Hi... Next!
He left (finally.) Then slept.
The cat's 3.14.
Alpha

Beta.
Last line has no terminator
EOF
"$ADB" push "$INPUT_FILE" /sdcard/Download/emulator-parity-input.txt
```

기대 결과는 `1 file pushed`와 `/sdcard/Download/emulator-parity-input.txt`의 생성이다. 양쪽 앱에서 같은 파일 선택기를 통해 이 경로의 TXT 파일을 열고, 표시되는 문장 목록을 비교한다. 앱별 파일 선택과 최초 로딩 화면도 각각 스크린샷으로 남긴다.

| 입력 | Flutter 기대 | KMP 기대 | 겨냥하는 차이 |
| --- | --- | --- | --- |
| `Mr. Kim ran.` | 2문장 `Mr.` / `Kim ran.` | 1문장 | 약어 뒤 점 처리 |
| `3.14 is pi. Next!` | 3문장 `3.` / `14 is pi.` / `Next!` | 2문장 | 소수점 처리 |
| `The cat's 3.14.` | 2문장 `The cat's 3.` / `14.` | 1문장 `The cat's 3.14.` | 소수점 처리와 단어 토큰화 대상 확보 |
| `Hi... Next!` | 4문장 `Hi.` / `.` / `.` / `Next!` | 2문장 `Hi...` / `Next!` | 연속 종결부호 처리 |
| `He left (finally.) Then slept.` | 2문장, 닫는 괄호가 다음 문장 앞에 붙음 | 2문장, 닫는 괄호를 앞 문장이 흡수 | 닫는 괄호 처리 |
| `Alpha`와 빈 줄, `Beta.` | 문단 경계 무시 | 문단 경계로 분리 | 빈 줄 문단 경계 |
| 마지막 줄 `Last line has no terminator` | 버려짐 | 보존됨 | 종결부호 없는 마지막 줄 |

고정 입력 파일 전체를 한 번에 열었을 때 나오는 문장 총개수다. 두 알고리즘을 그대로 재현해 산출한 값이며, 항목별 대조에 들어가기 전에 이 숫자만으로 전체가 맞는지 먼저 확인할 수 있다.

| 앱 | 문장 총개수 |
| --- | --- |
| Flutter | 14 |
| KMP | 11 |

Flutter 14문장의 순서는 다음과 같다.

```text
Mr.
Kim ran.
3.
14 is pi.
Next!
Hi.
.
.
Next!
He left (finally.
) Then slept.
The cat's 3.
14.
Alpha\n\nBeta.
```

KMP 11문장의 순서는 다음과 같다.

```text
Mr. Kim ran.
3.14 is pi.
Next!
Hi...
Next!
He left (finally.)
Then slept.
The cat's 3.14.
Alpha
Beta.
Last line has no terminator
```

Flutter 목록의 마지막 14번째 항목은 `Alpha`와 `Beta.`가 한 문장으로 이어진 것이다. 둘 사이에는 빈 줄 하나가 있어 줄바꿈 문자가 두 개이며, Flutter는 줄바꿈을 문장 경계로 보지 않아 그대로 이어 붙는다. KMP는 연속된 줄바꿈을 문단 경계로 판단해 두 문장으로 나눈다. 위 코드 블록에서는 이 줄바꿈을 `\n`으로 표기했다.

Flutter 목록에는 `Last line has no terminator`가 없다. 종결부호가 없어 버려지기 때문이다. 이것이 두 앱의 가장 눈에 띄는 차이다.

문장 총개수가 각각 14와 11이 아니면 입력 파일이 잘못 만들어졌거나 앱이 예상과 다르게 동작하는 것이므로, 항목별 대조로 넘어가기 전에 원인을 먼저 확인한다.

위 일곱 반례의 검증 근거는 `docs/cmp-kmp-parity-audit.md`의 `동작 차이 상세 > 문장 정규화와 문장 경계`다.

## 대조 항목

아래 표는 감사 문서의 `재설계-의도됨` 및 `차이-예기치못함` 중 서버 연결 없이 화면에서 확인할 수 있는 행만 골랐다. 동일한 고정 입력 파일을 먼저 연 뒤, 각 행의 절차를 수행한다. 결과 기록란은 실행자가 채운다.

| 확인 항목 | Flutter 기대 동작 | KMP 기대 동작 | 감사 문서 근거 | 확인 절차 | 결과 기록란 |
| --- | --- | --- | --- | --- | --- |
| 문서 제목 산출 | 첫 `.` 앞 문자열을 제목으로 사용 | 선택한 파일명 `emulator-parity-input.txt`를 유지 | `전체 기능 대조표 > 문서 제목 산출`, 차이-예기치못함 | 파일을 연 직후 제목이 보이는 화면에서 두 앱의 제목을 기록한다. | |
| 문장 정규화와 문장 경계 분리 | 고정 입력 표의 Flutter 결과 | 고정 입력 표의 KMP 결과 | `동작 차이 상세 > 문장 정규화와 문장 경계`, 재설계-의도됨 | 문장 목록에서 일곱 입력 구간의 경계와 문장 수를 순서대로 대조한다. | |
| 이전과 다음, 줄 바로 이동의 경계 | 화면 기반 페이지와 줄 이동을 사용 | 문장 인덱스 범위 밖 이동을 막음 | `전체 기능 대조표 > 이전과 다음, 줄 바로 이동의 경계`, 재설계-의도됨 | 첫 문장에서 이전, 마지막 문장에서 다음을 각각 시도한다. 줄 바로 이동 UI가 있으면 첫 문장과 마지막 문장 번호로 이동한다. | |
| 읽기 위치 저장과 복원 키 | 제목을 저장 키로 사용 | 콘텐츠 ID와 `reader.db`를 저장 키로 사용 | `전체 기능 대조표 > 읽기 위치 저장과 복원 키`, 재설계-의도됨 | 각 앱에서 중간 문장으로 이동한 뒤 앱을 백그라운드로 보내고 다시 연다. 복원된 위치를 기록한다. | |
| 사용자 입력 저장 단위와 복원 | 문장 문자열 JSON 키로 입력을 저장 | 문서 ID와 문장 인덱스로 즉시 저장 | `전체 기능 대조표 > 사용자 입력 저장 단위와 복원`, 재설계-의도됨 | 같은 문장 위치에 식별 가능한 짧은 입력을 넣고 다른 문장으로 이동한 후 돌아온다. 앱 재진입 뒤에도 남는지 기록한다. | |
| 단어 토큰화와 불용어 | 정규화, 중복 및 불용어 제거 | 공백 기준 토큰화 | `동작 차이 상세 > 나머지 반례 > 토큰`, 차이-예기치못함 | 고정 파일의 `The cat's 3.14.` 구간에서 단어 영역을 열고 표시 토큰을 기록한다. 종결 점 때문에 감사 문서의 `The cat's 3.14` 반례와 표기는 다를 수 있으나, 정규화·불용어 제거 여부와 공백 분리 여부를 대조한다. | |
| 문서 내 문자열 검색 | 페이지 이동 값 처리에 실패할 수 있음 | 일치 문장 인덱스 목록으로 이동 | `동작 차이 상세 > 나머지 반례 > 검색`, 재설계-의도됨 | 고정 파일에서 `Next`를 검색해 결과 선택 뒤 이동한다. 가능하면 `Hit.` 하나만 든 별도 파일은 만들지 말고, 감사 문서의 `Hit.` 반례는 코드 근거로만 남긴다. | |
| 페이지와 줄 분할 뷰어 | 화면 메트릭 기반 물리 페이지 | 문장 인덱스 전체 보기 | `전체 기능 대조표 > 페이지와 줄 분할 뷰어`, 재설계-의도됨 | 리더 뷰 모드에서 문장 여러 개가 보이게 하고, 페이지 또는 줄 전환 단위와 표시 형식을 기록한다. | |
| 뷰어 북마크와 저장 범위 | 전 문서 공용 페이지 배열 | 문서 ID와 문장 인덱스 복합키 | `전체 기능 대조표 > 뷰어 북마크와 저장 범위`, 재설계-의도됨 | 중간 문장에 북마크를 만들고 목록에서 선택해 해당 위치로 돌아가는지 기록한다. | |
| CSV 기록 추출 | Flutter 형식으로 기록을 파일로 저장 | 문장 인덱스와 사용자 번역을 RFC 방식으로 이스케이프해 저장 | `전체 기능 대조표 > CSV 기록 추출`, 재설계-의도됨 | 입력을 하나 이상 저장한 뒤 각 앱의 CSV 저장 진입 UI를 열어 저장 대상과 성공 표시를 기록한다. 파일 내용 대조는 이 문서의 범위 밖이다. | |
| 4개 표시 옵션과 지속성 | Flutter의 4개 읽기 옵션 | SQLite에 저장하는 영역별 표시 스타일과 제한된 글꼴·색 전환 | `전체 기능 대조표 > 4개 표시 옵션과 지속성`, 재설계-의도됨 | 글꼴 크기, 줄 간격, 글꼴, 색상 옵션을 각각 한 번 바꾸고 리더로 돌아와 반영과 재진입 후 지속 여부를 기록한다. | |
| 리더 드로어와 화면 진입 | Flutter 리더 드로어의 메뉴 구성 | KMP 리더 드로어의 메뉴와 파일·CSV 진입 위치가 다름 | `전체 기능 대조표 > 리더 드로어와 화면 진입`, 차이-예기치못함 | 각 앱의 리더 드로어를 열어 메뉴 항목과 파일 선택 및 CSV 저장 진입 위치를 나란히 기록한다. | |

### 우선 확인 대상: 차이-예기치못함 3건

아래 세 항목은 감사 문서에 이를 정당화하는 계획 근거가 없다고 기록되어 있다. 다른 재설계 항목보다 먼저 스크린샷과 관찰 결과를 남긴다.

1. 문서 제목 산출 - Flutter는 첫 점 앞 문자열, KMP는 파일명이다.
2. 단어 토큰화와 불용어 - Flutter는 정규화·중복·불용어 제거, KMP는 공백 분리다.
3. 리더 드로어와 화면 진입 - 메뉴 구성과 파일·CSV 진입 위치가 다르다.

## 대조 불가 항목

Django 백엔드 `http://15.165.69.200`은 Flutter `lib/models/server_info.dart:2`에 설정되어 있다. 실측상 HTTP는 HTTPS로 301 전환되고 HTTPS는 503을 반환하므로 Flutter는 로그인과 서버 기능을 정상 경로로 통과할 수 없다. 로그인 우회 패치는 리더 화면을 열기 위한 조치일 뿐 서버 기능을 되살리지 않는다.

다음 항목은 Flutter와 KMP의 1대1 대조 대상에서 제외한다.

| 항목 | Flutter에서 대조 불가인 이유 | KMP 처리 |
| --- | --- | --- |
| 사전 조회 | Django `/dictionary/word` 호출에 의존 | Firebase 기반 KMP 사전의 단독 동작 확인으로만 남긴다. |
| 원격 번역과 quota 표시 | Flutter는 DeepL 직접 호출과 Django refresh에 의존 | Firebase 기반 KMP 번역과 quota의 단독 동작 확인으로만 남긴다. |
| 단어장 기록 | Django 서버 호출에 의존 | KMP 단독 동작 확인으로만 남긴다. |

이 절차에서 KMP의 Firebase 기능을 눌러볼 수는 있으나, Flutter와 같은 결과인지 판정하거나 대조 통과로 기록하지 않는다.

## 결과 기록 형식

스크린샷은 저장소 밖 임시 디렉터리에만 저장한다. 다음 명령으로 결과 디렉터리를 만들고, 모든 캡처에 같은 `PARITY_RESULT_DIR`를 사용한다. 저장소에 복사하거나 커밋하지 않는다.

```sh
PARITY_RESULT_DIR="$(mktemp -d /private/tmp/lingua-emulator-parity-result.XXXXXX)"
printf '%s\n' "$PARITY_RESULT_DIR"
```

파일명은 `<순번>-<항목>-<앱>.png` 형식으로 한다. `<앱>`은 `flutter` 또는 `kmp`만 사용하고 `<항목>`은 영문 소문자 hyphen 형식으로 쓴다. 예시는 아래와 같다.

```sh
"$ADB" exec-out screencap -p > "$PARITY_RESULT_DIR/01-sentence-boundaries-flutter.png"
"$ADB" exec-out screencap -p > "$PARITY_RESULT_DIR/01-sentence-boundaries-kmp.png"
```

각 표의 결과 기록란에는 다음 한 줄 형식으로 남긴다.

```text
판정: 일치 | 의도된 차이 확인 | 예기치 않은 차이 확인 | 미확인 / 관찰: <짧은 사실> / 스크린샷: <파일명>
```

## 정리

대조가 끝난 뒤 Flutter 전용 작업 트리에서 패치 브랜치를 제거한다. 제거 전에 필요한 스크린샷이 모두 저장소 밖 임시 디렉터리에 있는지 확인한다.

```sh
cd /Users/ujeonghyeon/Desktop/dev/myDev/lingua-flutter
git worktree remove ../lingua-flutter-legacy-parity
git branch -D chore/emulator-parity-legacy
```

`androidApp/google-services.json`은 gitignore 대상이므로 KMP 작업 트리에 남아도 무방하며 커밋하지 않는다. AVD에서 두 앱을 제거하려면 아래를 실행한다.

```sh
"$ADB" uninstall com.friendshipHyun
"$ADB" uninstall com.friendshipHyun.legacy
```

기대 결과는 각 명령의 `Success`다. 에뮬레이터 종료는 에뮬레이터 창에서 수행한다.
