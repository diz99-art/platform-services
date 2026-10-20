# platform-services

[![CI](https://github.com/diz99-art/platform-services/actions/workflows/ci.yml/badge.svg)](https://github.com/diz99-art/platform-services/actions/workflows/ci.yml)

k3s 학습용 사내 플랫폼의 서비스 모음 (Java 21, Spring Boot 4, Gradle 멀티 모듈).

- 인프라·배포: [platform-infra](https://github.com/diz99-art/platform-infra) (k3s, MySQL·Redis·Kafka, ArgoCD, 모니터링)
- 쿠폰 서버: [coupon-system](https://github.com/diz99-art/coupon-system) (이 저장소의 인증 서버가 발급한 토큰으로 사용자를 확인)

## 모듈

- `auth` 인증 서버: 회원가입·로그인·토큰 재발급·로그아웃·내 정보, RS256 JWT + JWKS 공개키 → [docs/auth.md](docs/auth.md)
- (예정) `gateway`, `notification`

## 인증 서버 한눈에

```
클라이언트 ── POST /auth/login ──▶ 인증 서버 ──▶ MySQL(auth DB: users) / Redis(리프레시 토큰, 로그인 잠금)
          ◀── 액세스 토큰(JWT, RS256, 15분) + 리프레시 토큰(14일) ──

클라이언트 ── POST /coupons/{id}/issue (Authorization: Bearer 액세스 토큰) ──▶ 쿠폰 서버
                                          쿠폰 서버 ── GET /auth/.well-known/jwks.json ──▶ 인증 서버 (공개키, 5분 캐시)
                                          공개키로 서명·iss·aud·exp 확인 → sub = 사용자 id
```

- **비대칭 키(RS256)**: 개인키는 인증 서버에만(Ansible Vault → Secret), 공개키는 JWKS로 공개. 다른 서비스는 검증만 하고 위조는 못 한다
- **리프레시 토큰**: Redis에 해시로 저장, 재발급할 때마다 교체, 이미 쓴 토큰이 다시 오면 탈취로 보고 전부 폐기
- **로그인 보호**: BCrypt, 15분 안에 5번 실패하면 5분 잠금, 없는 이메일도 같은 응답·같은 시간
- **키 교체**: 키 두 개를 함께 두는 기간을 거쳐 끊김 없이 바꾼다 (절차: [docs/auth.md](docs/auth.md#키-교체로테이션-절차))

## 실행·배포

```bash
./gradlew test                    # 단위 테스트 (통합 테스트는 Docker가 있을 때, CI에서는 항상)
docker compose up -d && ./gradlew :auth:bootRun   # 로컬: http://localhost:8080/auth/swagger-ui.html
```

- main에 push → GitHub Actions: 테스트 → ARM64 이미지(`ghcr.io/diz99-art/platform-services-auth:<커밋 해시>`) → platform-infra의 이미지 태그 변경 → ArgoCD가 배포
- 클러스터: http://192.168.252.2/auth/swagger-ui.html

## 검증 결과 (2026-10-20)

- 테스트 23개 (단위 15 + Testcontainers 통합 8) CI 통과
- k3s: 회원가입 → 로그인 → 토큰으로 쿠폰 발급까지 동작. 토큰 없음·만료·변조·다른 키·`alg: none` → 401, USER의 쿠폰 생성 → 403
- 인증·쿠폰 서버 동시 롤링 업데이트 중 로그인 4,365건·발급 39,480건 실패 0
- 메모리 실측 RSS 약 354MiB → 힙 160MB + limit 512Mi

## 겪은 문제

- `POST /logout`이 302: Spring Security 기본 로그아웃 필터가 가로챔 → 끔 (CI 통합 테스트가 발견)
- 키 파일 `AccessDeniedException`: Secret을 0400으로 마운트, 앱은 uid 100 → `fsGroup: 101` + 0440
