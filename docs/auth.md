# 인증 서버 (auth)

회원가입·로그인으로 **액세스 토큰(JWT)**과 **리프레시 토큰**을 발급한다. 다른 서비스(쿠폰 서버 등)는 인증 서버에 묻지 않고 **공개키로 토큰을 직접 검증**한다.

## API (`/auth` 아래, 맥에서 http://192.168.252.2/auth/swagger-ui.html)

- `POST /auth/signup` 회원가입 → 201 (역할 USER). 400 `INVALID_REQUEST`(이메일 형식, 비밀번호 규칙), 409 `EMAIL_ALREADY_EXISTS`
- `POST /auth/login` 로그인 → 액세스 토큰(15분) + 리프레시 토큰(14일). 401 `INVALID_CREDENTIALS`, 429 `LOGIN_LOCKED`(+ `Retry-After`)
- `POST /auth/refresh` 리프레시 토큰 → 새 토큰 쌍. 쓴 리프레시 토큰은 무효. 401 `INVALID_REFRESH_TOKEN`
- `POST /auth/logout` (토큰 필요) 리프레시 토큰 삭제 → 204
- `GET /auth/me` (토큰 필요) 내 정보
- `GET /auth/.well-known/jwks.json` 토큰 검증용 공개키 목록(JWKS), 5분 캐시 가능

에러 응답은 모두 `{"code", "message", "errors": [{"field", "message"}]}` (쿠폰 서버와 같은 모양). 컨트롤러 앞에서 거절되는 401·403도 같은 모양이다.

## 액세스 토큰 (JWT)

```
헤더   {"alg": "RS256", "kid": "2026-10-20", "typ": "JWT"}
내용   {"iss": "platform-auth", "aud": ["platform"], "sub": "2", "iat": ..., "exp": ...(15분 뒤), "jti": "...",
        "email": "...", "roles": ["USER"]}
서명   RSA 개인키로 서명 (RS256)
```

- 받는 쪽은 서명(공개키), `iss`(발급자), `aud`(대상), `exp`(만료)를 확인하고 `sub`를 사용자 id로, `roles`를 권한(ROLE_USER, ROLE_ADMIN)으로 쓴다
- **RS256만 받는다**: 헤더의 `alg`를 `none`(서명 없음)이나 `HS256`으로 바꾼 위조 토큰을 거부한다 (실제로 시험함)
- 수명 15분: 토큰은 서명만 맞으면 통과하므로 빼앗기면 만료까지 쓸 수 있다. 짧게 두고 리프레시 토큰으로 갱신한다

## RS256(비대칭 키)을 고른 이유 (HS256과 비교)

- **HS256(대칭 키)**: 서명과 검증에 같은 비밀 키를 쓴다. 쿠폰 서버가 토큰을 검증하려면 그 비밀 키를 가져야 하고,
  그 키로 **토큰을 만들 수도 있다**. 서비스가 늘수록 키를 아는 곳이 늘고, 그중 하나만 뚫려도 아무 사용자(ADMIN 포함)의 토큰을 위조할 수 있다
- **RS256(비대칭 키)**: 개인키(서명)는 인증 서버에만, 공개키(검증)는 누구에게나. 쿠폰 서버는 공개키만 받으므로 **검증은 해도 위조는 못 한다**.
  공개키는 JWKS 주소로 자동 배포되어 서비스마다 키를 복사해 넣을 필요가 없다
- 대가: 서명·검증이 HS256보다 느리고 토큰이 길다(약 630자). 우리 규모에서는 문제가 아니다
- 다른 선택지: ES256(타원곡선)은 RS256보다 키·서명이 짧고 빠르다. RS256이 라이브러리·자료가 가장 많아 학습용으로 골랐다

## 서명 키 관리

- 개인키: platform-infra의 Ansible Vault `vault_auth_jwt_keys` (`{kid: PKCS#8 PEM}`) → `ansible-playbook auth.yml` → Secret `auth-jwt-keys` → 파드의 `/etc/auth/keys/<kid>.pem` (읽기 전용, 0440, 그룹 101 = 앱 사용자만)
- 활성 키 id: platform-infra `k8s/apps/auth/config.env`의 `AUTH_JWT_ACTIVE_KID` (비밀이 아니라 Git)
- 폴더의 모든 키의 공개키가 JWKS에 나가고, 활성 키로만 서명한다 (`security/JwtKeys.java`)
- 개발·테스트에서 키 폴더를 정하지 않으면 실행할 때마다 임시 키를 만든다

### 키 교체(로테이션) 절차

개인키가 새었거나 정기 교체(예: 1년)할 때. 토큰을 쓰는 사용자가 끊기지 않게 **두 키가 함께 있는 기간**을 둔다.

1. **새 키 추가** (아직 서명에 안 씀)
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out new.pem
   # vault_auth_jwt_keys에 "2027-10-20": | (new.pem 내용) 을 추가 (ansible-vault edit), new.pem 삭제
   cd ansible && ansible-playbook auth.yml      # Secret에 키 파일이 2개가 된다
   ```
   앱은 키 파일을 **시작할 때 한 번만** 읽는다 → platform-infra `k8s/apps/auth/deployment.yaml`의 파드 어노테이션
   `platform-infra/jwt-keys: "2026-10-20,2027-10-20"`로 바꿔 push → 파드가 다시 떠서 JWKS에 공개키 2개가 나간다
2. **기다림 ≥ JWKS 캐시 시간(5분)**: 쿠폰 서버 등이 새 공개키를 받아 둘 시간. (모르는 kid가 오면 바로 다시 받지만, 여유를 둔다)
3. **활성 키 전환**: `k8s/apps/auth/config.env`의 `AUTH_JWT_ACTIVE_KID=2027-10-20` → push → ArgoCD가 배포. 이때부터 새 키로 서명
4. **기다림 ≥ 액세스 토큰 수명(15분)**: 옛 키로 서명된 토큰이 모두 만료될 때까지 옛 공개키를 남겨 둔다
5. **옛 키 삭제**: vault에서 옛 키를 지우고 `ansible-playbook auth.yml`, 어노테이션을 `"2027-10-20"`으로 바꿔 push → JWKS에서 빠진다

- 개인키가 **새었을 때**는 2·4의 기다림을 생략하고 바로 옛 키를 지운다 (그 키로 서명된 토큰은 즉시 모두 무효 → 사용자는 다시 로그인)
- 리프레시 토큰은 JWT가 아니라 키 교체와 무관하다

## 리프레시 토큰

- 무작위 32바이트 문자열(JWT 아님). Redis에 **SHA-256 해시**를 키로 저장 → Redis 내용이 새어도 그 값으로 토큰을 쓸 수 없다
- 재발급할 때마다 **교체(rotation)**: `GETDEL`로 한 번에 꺼내고 지운다 → 같은 토큰으로 동시에 두 번 재발급되지 않는다
- **재사용 감지**: 교체된 토큰이 다시 오면 탈취(누군가 먼저 썼다)로 보고 그 사용자의 리프레시 토큰을 모두 지운다 → 둘 다 다시 로그인해야 한다
- 로그아웃은 리프레시 토큰만 지운다. 이미 나간 액세스 토큰은 만료(15분)까지 유효하다 → 수명을 짧게 둔 이유. 즉시 끊어야 하면 jti 차단 목록(denylist)이 필요하다 (다음 후보)
- Redis 키: `auth:refresh:{해시}`, `auth:refresh:used:{해시}`, `auth:refresh:user:{id}`, TTL 14일

## 로그인 보호

- **비밀번호**: BCrypt(강도 10, 사용자마다 다른 소금값). 규칙: 8~64자, 영문·숫자·특수문자 각 1개 이상, 공백 불가
- **잠금**: 이메일마다 실패를 Redis에 센다(서버 2대가 같이 본다). 15분 안에 5번 → 5분 잠금(429 + `Retry-After`). 잠금 중에는 맞는 비밀번호도 거부
- **가입 여부를 숨긴다**: 없는 이메일도 같은 메시지(401 `INVALID_CREDENTIALS`), 같은 잠금, 같은 BCrypt 비교 시간(가짜 해시와 비교)
- 이메일은 소문자로 저장, 중복은 DB의 UNIQUE 제약이 마지막으로 막는다(동시 가입 경쟁)
- **관리자**: 회원가입은 USER만. 첫 ADMIN은 설정(vault → Secret `auth-admin`)으로 시작할 때 만든다 (`admin@platform.local`)

## 테스트

- 단위(맥에서 바로): `JwtKeysTest`(PEM 읽기, 활성 키, 숨김 항목 무시), `TokenVerificationTest`(발급·검증, 위조·다른 키·만료·다른 발급자 거부), `StrongPasswordTest`
- 통합(Testcontainers MySQL 8.4 + Redis 7.4, Docker가 없으면 건너뜀, CI에서 실행): `AuthApiTest` 8개 — 전체 흐름, 리프레시 재사용 감지, 가입 검증, 5회 잠금, 가입 여부 숨김, 관리자, 401, JWKS
- 합계 23개 (CI 통과)

## 검증 기록 (2026-10-20, k3s)

- 전체 흐름: 관리자 로그인 → 쿠폰 생성 → 회원가입 201 → 로그인(expiresIn 900) → `/me` → 토큰으로 쿠폰 발급 202. 본문에 남의 userId를 넣어도 토큰의 사용자로 발급
- 거부: 토큰 없음·만료(진짜 키로 서명한 만료 토큰)·내용 변조·다른 키·다른 iss·다른 aud·`alg: none` → 모두 401. USER의 쿠폰 생성·남의 내역 조회 → 403
- 배포 중 부하(클러스터 안, Traefik 경유): 인증·쿠폰 서버를 동시에 롤링 업데이트하는 동안 발급 39,480건(202)·로그인 4,365건(200), **실패 0**, 재시작 0
- 메모리: RSS 최대 약 354MiB → 힙 160MB 고정 + limit 512Mi (platform-infra의 docs/memory-plan.md)
- 디스코드 알림: 인증 파드가 JVM 지표 덕분에 자동으로 "Java 앱" 규칙(90%)에 들어가고, 첫 배포의 재시작이 `PodRestarted`로 디스코드에 갔다

## 겪은 문제

- **`POST /logout`이 302**: Spring Security의 기본 로그아웃 필터가 컨트롤러보다 먼저 가로채 `/login?logout`으로 보냈다 → `.logout(disable)`. CI 통합 테스트가 잡았다
- **시작 실패 `AccessDeniedException: /etc/auth/keys/2026-10-20.pem`**: Secret 파일을 0400(주인 root만)으로 마운트했는데 앱은 uid 100 → `fsGroup: 101` + 0440
- **가짜 BCrypt 해시를 손으로 적었다가 바꿈**: 형식이 틀리면 BCrypt가 비교를 건너뛰어 응답 시간 차이(가입 여부 노출)가 생긴다 → 시작할 때 진짜 해시를 만든다
- **이미지 이름**: 멀티 모듈이라 `ghcr.io/diz99-art/platform-services-<모듈>`. 저장소가 public이라 패키지도 public(익명 pull 200)
