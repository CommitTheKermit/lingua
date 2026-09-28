# Lingua 웹 번역 서버

Python 3.10 이상, Waitress와 SQLite로 실행한다. HTTP 경계는 `Application`, 번역/중복 요청 조정은 `Translator`, 영속화/원자적 한도 예약은 `Store`, DeepL 연결은 `deepl_provider`가 맡는다. `Store`에 세션/한도/캐시 저장 책임이 모여 있으므로 정책이 커지면 인터페이스 분리가 필요하다.

## 실행

가상 환경에 `pip install -r requirements.txt`로 설치하고 `python -m unittest discover -s tests -q`, `python app.py`로 검사/실행한다. Git 밖의 환경 파일을 `LINGUA_ENV_FILE`로 지정한다. 기본은 실행 디렉터리의 `.env`다. 비밀값을 명령 인자나 로그에 넣지 않는다.

| 변수 | 용도 |
| --- | --- |
| `DEEPL_API_KEY` | 서버 전용 DeepL 키 |
| `SESSION_SECRET` | 최소 32자의 무작위 해시 비밀값 |
| `ALLOWED_ORIGINS` | 허용 웹 origin 쉼표 목록, 와일드카드 없음 |
| `DATABASE_PATH` | 영속 SQLite 위치, 기본 `lingua.sqlite` |
| `MONTHLY_CHAR_LIMIT` | 월별 원문 문자 예약 예산, 기본 450000 |
| `PORT` | 기본 8787, 항상 loopback 바인딩 |
| `TRUST_TUNNEL` | 전용 Cloudflare 터널 연결만 사용할 때 `1` |

공개 연결은 전용 터널 -> loopback으로 제한한다. `TRUST_TUNNEL=1`은 터널의 IP 헤더를 신뢰하므로 외부 인터페이스로 바인딩하면 안 된다. CORS는 브라우저 origin 제한이며 독립 클라이언트를 인증하는 수단이 아니다.

## API와 한도

- `GET /health`: 준비 상태만 반환한다.
- `POST /session`: 임의 익명 토큰 발급. 브라우저 localStorage, 서버 SHA-256 해시로 저장, 30일 만료.
- `POST /translate`: Bearer 세션 및 `{text, context, instructions}`. 영어 -> 한국어. 원문 10000자/문맥 5000자/어투 300자 제한.
- 네트워크당 세션 발급 분당 10회, 번역 요청 분당 60회. 원 IP 대신 HMAC 해시를 저장한다.
- 세션/네트워크 각각 200회, 120초마다 3회 회복. 같은 공유기/회사망 사용자는 네트워크 한도를 공유할 수 있다.
- 신규 외부 번역 최대 4개 동시 실행, 초과 503. 요청 한도/월 문자 예산 초과 429.
- 원문+문맥+어투 해시로 캐시를 구별하고 진행 중 동일 요청을 병합한다. 캐시 응답은 번역 횟수를 차감하지 않는다. 번역 결과는 30일 유효, 저장 시 만료 캐시 정리. 원문/문맥은 DB에 저장하지 않는다.
- 외부 호출 실패 시 요청 횟수만 환불한다. 외부에서 처리했을 수 있어 문자 예산은 보수적으로 유지한다.
- 월 예산은 이 서버만 계산한다. 같은 키를 쓰는 모바일 등 다른 서비스 사용량은 별도로 DeepL 계정에서 확인해야 한다.
- 동시 실행/중복 병합은 단일 프로세스 기준이다. 다중 프로세스 전에 공통 조정 저장소가 필요하다.

## 홈서버 운영

2026-09-28 별도 서비스 폴더에 Python 환경과 예약 작업 `Lingua Translation API`를 등록했다. 시작 트리거, 실행 시간 제한 없음, 중복 방지, 실패 시 1분 간격 3회 재시도 설정. 가상 환경의 Windows launcher 자식 프로세스가 예약 작업 종료 후 남는 문제를 피하려고 기본 Python 실행 파일에서 가상 환경 site-packages를 로드한다. 실제 수동 시작 후 `/health`와 Running 상태, 작업 중지 시 리스너 종료 및 재시작을 확인했다. 재부팅 후 자동 시작은 실제 재부팅으로 검증하지 않았다.

`Lingua Beta Tunnel`은 임시 Quick Tunnel용 별도 예약 작업이다. 재시작하면 주소가 바뀌므로 자동 재시작/부팅 트리거는 두지 않았다. 종료 시 작업 재시작 -> 새 API 주소 확인 -> 웹 환경 파일 수정/재배포 -> 실제 번역으로 복구한다.

정식 홍보 전 고정 도메인 named tunnel과 터널 자동 복구가 필요하다. [Cloudflare 공식 문서](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)도 Quick Tunnel을 시험/개발용으로 안내한다.

비밀 환경 파일은 저장소 밖에 두고 실행 사용자/SYSTEM만 ACL로 허용한다. SQLite는 재시작 후 유지한다. 백업은 SQLite 온라인 백업 또는 서비스 정지 후 복사를 사용해야 한다. 자동 백업은 미구성이다.

## 검증

로컬/홈서버에서 12개 테스트 통과: origin/인증, 입력 검증, 세션 만료, 새 세션의 네트워크 한도 유지, 동시 예산, 중복 병합, 실패 환불, SQLite 재시작, DeepL 지침 payload, health/preflight, 혼잡 처리.

실제 기본/존댓말 번역 결과가 달라졌고 40K/AoS도 성공했다. 전체 소설 품질 검수는 별도다. 신규 번역 약 1.2~1.5초, 캐시 20개 동시 요청은 20/20 성공, 중앙값 95ms/p95 99ms. 특정 시점/클라이언트 측정이며 대규모 부하 시험은 아니다.
