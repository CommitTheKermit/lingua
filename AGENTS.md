# 프로젝트 메모리

## CMP/KMP 영한 사전 결정

- 목표 구조와 구현 계획의 기준 문서는 `docs/cmp-kmp-dictionary-plan.md`다.
- 포팅 완료 대상은 Android와 iOS이며 UI와 화면 상태는 Compose Multiplatform로 공유한다.
- 기본 사전은 한국어 위키낱말사전의 영어 직접 풀이를 가공한 오프라인 SQLite다.
- 사전 조회는 오프라인으로만 처리한다. 읽기 화면의 현재 문장 기계번역에 `translateProxy`를 사용한다.
- 앱은 DeepL 키를 보유하거나 전달받지 않는다.
- Glosbe, WiktApi 등 외부 사전 API에 런타임 의존하지 않는다.
- 사전 데이터와 파생 DB의 CC BY-SA 4.0 출처 및 라이선스를 앱과 배포물에 고지한다.
- 현재 Flutter의 다목적 `ApiUtil`과 UI 렌더링 중 데이터 변경 구조를 포팅하지 않는다.
- 사전 구현을 시작하기 전에 기준 문서의 완료 조건과 검증 계획을 확인한다.
