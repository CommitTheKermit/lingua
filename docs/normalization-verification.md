# KMP 앱 정상화 검증

검증일: 2026-09-22. 작업 브랜치: `feat/figma-reader-ui`.

## 적용한 동작

- KMP 사전 중심 앱을 유지하고, 리더의 `입력` 버튼을 실제 기능인 `CSV 저장`으로 바로잡았다.
- 저장형 단어장으로 오해할 수 있던 메뉴를 `사전 검색`으로 바꾸고, 진입 시 빈 검색창을 연다.
- 본문 단어 목록의 8개 제한을 제거하고 가로 스크롤로 모든 토큰에 접근한다.
- 실제 번역이 없는 리더 영역은 `사전 이용 안내`로 표시한다. 원문과 안내는 스크롤할 수 있다.
- 번역 요청마다 검색어와 식별자를 고정한다. 검색 변경, 닫기, 재진입 뒤 늦게 도착한 응답과 오류는 무시한다.
- 로컬 사전 결과, 빈 검색, 중복 요청에서는 원격 번역을 시작하지 않는다.
- 온라인 결과에는 `DeepL 번역 결과`를 표시한다.
- Firebase 설정이 없는 빌드에서도 오프라인 리더와 사전은 실행하고, 번역 요청에만 설정 오류를 표시한다.

## iOS 빌드 복구

기존 Xcode 프로젝트는 CocoaPods 의존성을 선언하면서 `embedAndSignAppleFrameworkForXcode`를 호출해 빌드가 실패했다.
CocoaPods가 `Shared` 프레임워크와 Firebase 의존성을 연결하도록 통일하고, 앱 초기화에서 Firebase와 debug App Check를 준비한다.
release는 Firebase App Check SDK 기본 DeviceCheck 공급자를 사용한다. 서버의 공급자 등록과 release 실기기 검증은 별도다.

처음 빌드할 때 저장소 루트에서 다음을 실행한다.

```sh
./gradlew :shared:generateDummyFramework :shared:podspec
```

`iosApp` 디렉터리에서 `pod install`을 실행한 뒤 `iosApp/iosApp.xcworkspace`를 연다.
`Podfile.lock`을 버전 관리하므로 기존 해석된 의존성을 재사용한다.

온라인 번역 설정 파일은 `iosApp/iosApp/GoogleService-Info.plist`에 둔다.
사용자가 제공한 로컬 파일의 bundle ID 일치를 확인했으며, 파일은 Git 제외 상태다.
Xcode 빌드 단계는 파일이 있을 때만 앱 번들에 복사하고 설정 없는 빌드에는 남기지 않는다.
비밀값과 App Check debug token은 이 문서나 커밋에 포함하지 않는다.

## 통과한 검증

| 검증 | 결과 |
| --- | --- |
| Android debug APK 빌드 | `:androidApp:assembleDebug` 성공 |
| KMP 공통 테스트 | iOS Simulator ARM64에서 8개 통과. Android 테스트는 `NO-SOURCE`이며 실행됐다고 간주하지 않음 |
| 번역 경합 회귀 테스트 | 검색 변경 후 이전 성공 무시, 닫고 같은 단어 재진입 후 이전 오류 무시, 허용된 상태에서만 단일 요청 시작 |
| 리더 공통 테스트 | 문장 분리, 검색·북마크·위치·입력 복원, CSV 이스케이프, 이동 경계 통과 |
| Firebase Functions | Jest 11개 통과, 배포 서버 검증과 구분 |
| 사전 생성기 | Python unittest 4개 통과 |
| 번들 사전 | SQLite `integrity_check` 정상. `dog`, `run`, `bank`, `ran` 조회 결과 확인 |
| iOS 앱 빌드 | Xcode workspace, Debug, iPhone 17 Pro / iOS 26.2 simulator 성공 |
| iOS 앱 실행 | `com.ao.lingua` 실행, 시작 화면과 사전 검색 진입, 로컬 사전 결과 표시 확인 |
| iOS 번역 실패 흐름 | 번역 중 표시 후 오류 대화상자 표시 확인. 성공 응답은 확인되지 않음 |
| 비밀 파일 | iOS 설정 파일 Git 제외 확인 |

## 아직 완료로 볼 수 없는 항목

- Android에서 TXT 열기, 입력 후 재실행, 검색·북마크 왕복, 실제 CSV 저장, 9번째 이후 단어 선택의 전체 수동 검증.
  APK 설치와 시작 화면은 확인했으나 재설치 중 파일 선택 흐름이 끊겼고 전체 왕복을 검증하지 않았다.
- iOS에서 위와 동일한 파일 입출력·복원 흐름. 자동 키 입력이 의도한 검색어를 안정적으로 전달하지 않아 입력 검증도 보류했다.
- 실제 `translateProxy` 성공, App Check 등록, 익명 인증 설정, 서버 quota 오류의 종단 검증.
  설정 파일 연결만으로 서버와의 계약이 정상이라고 판단하지 않는다.
- Android와 iOS의 비행기 모드 사전 조회, DB 최초 설치·업데이트 교체, release 빌드와 실기기 검증.
- Android Gradle Plugin 9.1.0과 compile SDK 37의 지원 범위 경고는 남아 있다. 경고를 숨기거나 임의로 버전을 바꾸지 않았다.

## 사용자 우선순위 변경

2026-09-22 사용자 지시에 따라 iOS 추가 작업과 기능 정상화 확대를 중단한다.
이 문서의 수정·검증은 중간 작업 기록이며 전체 정상화 완료가 아니다.
다음 작업은 Figma 원본을 기준으로 Android UI를 먼저 일치시키는 것이며 Android에서만 검증한다.

## 보류된 후속 작업 순서

1. 동일한 고정 TXT로 Android와 iOS의 파일 열기부터 CSV 저장까지 왕복 조작을 완료한다.
2. 번역 오류를 익명 인증, App Check, callable 배포 상태로 나눠 원인을 확정하고 실제 번역 성공을 확인한다.
3. 비행기 모드, DB 교체, release 빌드와 실기기 조건을 검증한 뒤 완료 판정한다.

객체지향 관점에서 `ReaderStore`가 화면 상태 조정과 CSV 형식 생성을 함께 담당하는 단일 책임 원칙 위반은 남아 있다.
이번 변경은 사용자 동작과 빌드 차단 복구에 한정했다. CSV 책임 분리는 동작 검증 후 별도 변경으로 다룬다.
