# Firebase 백엔드 대체 명세

## 1. 문서 목적과 상태

이 문서는 기존 Django HTTP 백엔드를 Firebase 서버리스 구성으로 대체했던
지점을 삭제 전 로컬 대화 세션에서 복원해 명세한다.

현재 `main`에는 이 Firebase 구현이 반영되어 있지 않다. 현재 Flutter 코드는
`ServerInfo.baseUrl` 기반 Django API와 클라이언트의 DeepL 호출을 사용한다.
따라서 이 문서는 현행 동작 설명이 아니라 재구현 시 따라야 할 목표 계약이다.

확인 수준은 다음과 같이 구분한다.

- 확인: 세션에 소스, 설정 또는 실행 결과가 남은 항목
- 계약 확인: 소스 전문은 없지만 삭제 전 README와 파일 목록에 명시된 항목
- 미확인: 파일 존재만 확인됐거나 클라이언트 호출 구현을 확인하지 못한 항목

## 2. 범위

Firebase로 대체된 범위는 다음과 같다.

| 기능 | 기존 구현 | Firebase 대체 구현 | 확인 수준 |
|---|---|---|---|
| 회원가입과 로그인 | Django `/users/*` HTTP API | Firebase Auth 이메일과 비밀번호 인증 | 확인 |
| 이메일 확인 | Django 인증 코드 발송과 검증 | Firebase Auth 확인 메일과 `emailVerified` | 확인 |
| 비밀번호 재설정 | 전화번호와 이메일 기반 Django API | Firebase Auth 비밀번호 재설정 메일 | 확인 |
| 사용자 정보 | Django 사용자 저장소 | Firestore `users/{uid}` | 확인 |
| 번역 | 클라이언트가 DeepL을 직접 호출 | callable Function `translateProxy` | 확인 |
| DeepL 키 | 클라이언트 또는 Django API에서 전달 | Functions secret `DEEPL_API_KEY` | 확인 |
| 단어장 | Django `/dictionary/wordbook` | Firestore `users/{uid}/wordbook` | 확인 |
| 번역문 저장 | Django HTTP API | Firestore `users/{uid}/translations` | 확인 |
| 번역 쿼터 | Django `/users/refreshclient` | Firestore 사용자 문서와 서버 트랜잭션 | 계약 확인 |
| 전체 비용 제한 | 없음 | Firestore `meta/translateBudget` 월별 cap | 계약 확인 |
| 요청 출처 검증 | 없음 | Firebase App Check | 확인 |
| epub 저장 | 자체 서버 저장소 | Storage `users/{uid}/epubs` | 계약 확인 |

다음 항목은 Firebase 대체 범위가 아니다.

- 사전 검색은 Django `/dictionary/word`에서 오프라인 SQLite 번들로 옮긴 별도 변경이다.
- 로컬 문장 입력과 번역 캐시는 기기 파일 또는 로컬 저장소에 남는다.
- 삭제된 Firebase 프로젝트 식별자, 앱 식별자, API 키는 복구 대상이 아니다.

## 3. Flutter 초기화와 플랫폼 연결

이 절에서 인용하는 `lingua_flutter/...` 경로는 Flutter 원본 기준이다. Flutter 코드는
`legacy/flutter` 브랜치에 보존되어 있고 그 브랜치에서는 루트 레이아웃이므로,
`lingua_flutter/lib/...` 경로는 `lib/...`로 읽는다.

앱 시작 순서는 다음 계약을 따른다.

1. `WidgetsFlutterBinding.ensureInitialized()`를 호출한다.
2. 생성된 `DefaultFirebaseOptions.currentPlatform`으로 Firebase를 초기화한다.
3. App Check를 활성화한다.
4. 나머지 앱 초기화 후 `runApp`을 호출한다.

App Check 공급자는 빌드 모드에 따라 구분한다.

| 플랫폼 | 디버그 | 릴리스 |
|---|---|---|
| Android | `AndroidProvider.debug` | `AndroidProvider.playIntegrity` |
| iOS | `AppleProvider.debug` | `AppleProvider.appAttest` |

삭제 전 Flutter 의존성은 다음과 같았다.

```yaml
firebase_core: ^3.6.0
firebase_auth: ^5.3.1
firebase_app_check: ^0.3.1+4
cloud_firestore: ^5.4.4
cloud_functions: ^5.1.3
```

플랫폼 설정은 새 Firebase 프로젝트에서 `flutterfire configure`로 다시
생성해야 한다. 과거의 다음 파일 값은 재사용하지 않는다.

- `.firebaserc`
- `lingua_flutter/lib/firebase_options.dart`
- `lingua_flutter/android/app/google-services.json`
- `lingua_flutter/ios/Runner/GoogleService-Info.plist`

## 4. 인증 대체 계약

### 4.1 회원가입

`ApiUser.signUp`은 다음 순서로 동작한다.

1. `createUserWithEmailAndPassword`로 계정을 만든다.
2. Firestore `users/{uid}`에 사용자 문서를 merge 방식으로 기록한다.
3. Firebase Auth 확인 메일을 보낸다.

사용자 문서의 초기 필드는 다음과 같다.

| 필드 | 값 |
|---|---|
| `email` | 가입 이메일 |
| `phone_no` | 기존 UI에서 받은 전화번호 |
| `created_at` | 서버 타임스탬프 |

### 4.2 로그인과 로그아웃

- 로그인은 `signInWithEmailAndPassword`를 사용한다.
- 로그아웃은 `FirebaseAuth.signOut`을 사용한다.
- 현재 사용자는 `FirebaseAuth.currentUser`를 기준으로 판단한다.

### 4.3 이메일 확인과 비밀번호

- 확인 메일 재전송은 현재 사용자의 `sendEmailVerification`을 호출한다.
- 확인 여부는 사용자 reload 후 `emailVerified`를 읽는다.
- 비밀번호 찾기는 `sendPasswordResetEmail`로 대체한다.
- 로그인 사용자의 비밀번호 변경은 `updatePassword`를 사용한다.

기존 UI 호환 메서드는 다음 의미로 축소됐다.

| 기존 메서드 | Firebase 전환 후 의미 |
|---|---|
| `emailSend` | 확인 메일 흐름과 호환되는 성공 응답 유지 |
| `emailVerify` | Firebase의 확인 상태 조회로 대체 |
| `idFind` | 지원하지 않음 |
| `pwFind` | 전화번호를 사용하지 않고 재설정 메일 발송 |
| `pwChange` | 로그인 사용자의 비밀번호 변경 |
| `periodicRefresh` | Firestore 쿼터 조회 |
| `setQuota` | 제거, 클라이언트 쿼터 쓰기 금지 |

## 5. 번역 Function 계약

### 5.1 배포 속성

| 항목 | 값 |
|---|---|
| 함수명 | `translateProxy` |
| 트리거 | Cloud Functions v2 callable |
| 리전 | `asia-northeast3` |
| 런타임 | Node 20 |
| 최소 인스턴스 | 0 |
| App Check | 강제 |
| 비밀값 | `DEEPL_API_KEY` Functions secret |
| DeepL 기본 엔드포인트 | Free API |

### 5.2 요청과 응답

Flutter는 서울 리전의 callable Function에 다음 데이터를 보낸다.

```json
{
  "text": "번역할 영어 문장",
  "sourceLang": "EN",
  "targetLang": "KO"
}
```

성공 응답의 필수 계약은 다음과 같다.

```json
{
  "translated": "번역 결과"
}
```

`text`는 비어 있지 않은 문자열이어야 한다. 인증되지 않은 요청은
`unauthenticated`, 잘못된 입력은 `invalid-argument`, 예산이나 쿼터를 넘은
요청은 `resource-exhausted`로 거절한다. DeepL 내부 오류는 키나 응답 본문을
노출하지 않는 일반 오류로 변환한다.

### 5.3 서버 처리 순서

서버는 다음 순서를 지킨다.

1. App Check 검증
2. Firebase Auth 사용자 확인
3. 전역 월 예산 트랜잭션
4. 사용자별 쿼터 트랜잭션
5. DeepL 요청
6. 번역 문자열 반환

DeepL 키는 Flutter 코드, 설정 파일, 로그, Firestore에 저장하지 않는다.

## 6. 비용과 쿼터 계약

### 6.1 사용자 쿼터

사용자 쿼터는 `users/{uid}` 문서에 저장하고 Admin SDK 트랜잭션으로만
변경한다.

| 항목 | 값 |
|---|---|
| 잔량 필드 | `quotaRemaining` |
| 마지막 충전 기준 필드 | `quotaLastTs` |
| 최대치 | 200 |
| 충전량 | 2분마다 3 |
| 번역 비용 | 요청당 1 |

Flutter는 `quotaRemaining`을 읽어 기존 `AppLingua.requestQuota` 표시값을
갱신할 수 있지만 두 쿼터 필드를 쓰면 안 된다.

### 6.2 전역 월 예산

- 문서 경로는 `meta/translateBudget`이다.
- 기본 월 cap은 번역 요청 100000회다.
- 월이 바뀌면 카운트를 새 월 기준으로 초기화한다.
- cap에 도달하면 번역을 호출하기 전에 `resource-exhausted`로 차단한다.
- 클라이언트는 예산 문서를 읽거나 쓸 수 없다.

## 7. Firestore 데이터 계약

### 7.1 단어장

경로는 `users/{uid}/wordbook/{documentId}`다.

| 필드 | 값 |
|---|---|
| `word` | 소문자로 정규화한 단어 |
| `timestamp` | 서버 타임스탬프 |

### 7.2 번역 저장

경로는 `users/{uid}/translations/{documentId}`다.

| 필드 | 값 |
|---|---|
| `sentence_index` | 문장 위치 |
| `original` | 원문 |
| `machine_translated` | 기계 번역문 |
| `translated` | 사용자 입력 번역문 |
| `updated_at` | 서버 타임스탬프 |

### 7.3 보안 규칙

- 인증 사용자는 자신의 `users/{uid}` 문서만 읽고 쓸 수 있다.
- `wordbook`과 `translations`도 경로의 uid 소유자만 접근할 수 있다.
- 클라이언트는 `quotaRemaining`, `quotaLastTs`를 생성하거나 변경할 수 없다.
- `meta/**`는 클라이언트 읽기와 쓰기를 모두 거부한다.
- 명시되지 않은 경로는 기본 거부한다.

## 8. Storage 계약

epub 파일의 허용 경로는 `users/{uid}/epubs/**`다. 인증 사용자는 경로의
uid가 자신의 uid와 같을 때만 읽고 쓸 수 있다. 삭제 전 자료에서는 Storage
규칙과 아키텍처는 확인됐지만 Flutter 업로드 호출의 소스 전문은 확인하지
못했다.

## 9. 기존 HTTP 제거 기준

Firebase 전환이 완료되면 다음 의존이 남아 있으면 안 된다.

- `ApiUser`의 `$baseUrl/users/*` 호출
- `ApiUtil.wordRecord`의 `$baseUrl/dictionary/wordbook` 호출
- `ApiUtil.sendTranslatedText`의 Django 호출
- `ApiUtil.getApiKey`와 `$baseUrl/util/getapikey` 호출
- Flutter의 `api-free.deepl.com` 직접 호출
- 번역 요청 헤더의 DeepL 인증 키
- 클라이언트의 쿼터 변경 API

`ServerInfo.baseUrl`은 다른 HTTP 기능이 모두 제거됐을 때 삭제한다. 사전
검색은 Firebase가 아니라 오프라인 SQLite 계약으로 별도 이관한다.

## 10. 새 프로젝트 연결과 검증 기준

새 프로젝트를 연결할 때 다음 순서를 따른다.

1. Firebase 프로젝트와 Android, iOS 앱을 새로 등록한다.
2. `flutterfire configure`로 플랫폼 설정을 생성한다.
3. 이메일과 비밀번호 Auth 공급자를 활성화한다.
4. Android Play Integrity와 iOS App Attest를 등록한다.
5. 디버그 App Check 토큰을 개발 환경에만 등록한다.
6. 새 DeepL 키를 `DEEPL_API_KEY` Functions secret으로 저장한다.
7. Functions, Firestore 규칙, Storage 규칙을 배포한다.

구현 완료의 최소 검증 기준은 다음과 같다.

- Functions의 DeepL 요청, 쿼터, 월 예산 단위 테스트 통과
- Firestore 에뮬레이터에서 소유자 격리와 쿼터 쓰기 금지 테스트 통과
- Flutter 테스트 통과
- Android debug 빌드 통과
- 저장소에서 API 키와 삭제된 Firebase 프로젝트 설정이 검출되지 않음

삭제 전 세션에는 Functions Jest 21개와 Flutter 테스트 6개가 통과한 기록이
있다. 이 수치는 과거 실행 증거이며 현재 `main`의 검증 결과는 아니다.

## 11. 미복구 항목과 보안 주의

- 원본 Functions JavaScript 전문은 세션에 남지 않아 byte 단위 복구가 불가능하다.
- 삭제된 Firebase 프로젝트 설정은 무효이므로 새로 생성해야 한다.
- 과거 세션에 평문으로 남은 DeepL 키는 노출된 것으로 간주하고 폐기해야 한다.
- 새 키는 채팅, 소스, 커밋, 일반 환경 파일에 기록하지 않는다.

## 12. 객체지향 설계상 확인된 부채

- `ApiUtil`은 사전 검색, 번역, 단어장, 번역 저장을 함께 맡아 단일 책임 원칙을 위반한다.
- `ApiUser`와 `ApiUtil`의 전역 static 접근은 구현 교체와 테스트를 어렵게 해 의존 역전 원칙에 어긋난다.
- `AppLingua`의 전역 mutable 상태는 인증과 쿼터 상태의 생명주기를 UI 루트에 결합한다.

Firebase 동작 복원이 우선일 때는 기존 호출 표면을 유지할 수 있다. 이후
인증, 번역, 저장소를 각각의 repository로 분리하고 생성자 주입으로 전환한다.

## 13. 근거

현재 코드:

- `lingua_flutter/lib/util/api/api_user.dart`
- `lingua_flutter/lib/util/api/api_util.dart`
- `lingua_flutter/lib/models/server_info.dart`
- `lingua_flutter/lib/main.dart`

삭제 전 세션 기록:

- `~/.codex/sessions/2026/07/18/rollout-2026-07-18T16-12-04-019f7411-655a-7981-9921-407b647a0a27.jsonl`
- `~/.codex/sessions/2026/08/16/rollout-2026-08-16T14-46-27-01a0091b-6ed8-7af0-99dd-5fe5dbb3f8a9.jsonl`
- `~/.codex/sessions/2026/08/16/rollout-2026-08-16T15-54-00-01a00959-45e6-7130-843e-1c674d8d2db6.jsonl`
- `~/.claude/projects/-Users-ujeonghyeon-Desktop-dev-myDev-Lingua-flutter/2f147db9-9d6d-41e5-838a-dc9ce83f266e.jsonl`

Git 객체, reflog, stash와 원격 브랜치에는 삭제된 구현 커밋이 남아 있지
않았다. 이 문서의 서버 내부 동작은 세션에 남은 README, 파일 manifest,
Flutter 호출 코드와 테스트 기록을 합쳐 복원한 계약이다.
