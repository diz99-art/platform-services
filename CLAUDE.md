# 플랫폼 서비스 (platform-services)

k3s 학습용 사내 플랫폼의 서비스 모음: 인증(auth), 이후 Gateway·알림(notification). Gradle 멀티 모듈.
인프라(클러스터, MySQL·Redis·Kafka, 배포 매니페스트)는 [platform-infra](https://github.com/diz99-art/platform-infra), 쿠폰 서버는 coupon-system.

## 사용자와 목적

- 사용자는 Java/Spring·리눅스·쿠버네티스 초보다. 목적은 학습과 백엔드·데브옵스 취업 대비다.
- 설명은 한국어로 한다.

## 작업 방식

- 완성된 코드와 설정을 바로 작성한다. 그 안에 쓰인 개념과 왜 그렇게 했는지를 함께 설명한다.
- 사용자가 직접 한 작업은 리뷰하고 개선점을 설명한다.
- 유료 서비스는 쓰지 않는다.

## 보고 방식

- 작업 중 설명은 짧게 한다. 자세한 설명(개념, 이유, 측정값, 겪은 문제)은 `docs/` 문서에 남기고 경로만 알려준다.
- 마지막 "Claude 전달용" 보고(형식은 사용자 전역 CLAUDE.md)에 아래 세 항목을 더한다. 표는 쓰지 않는다.
  사용자는 이 보고를 다른 Claude에게 가져가 설명을 듣는다 → 이 보고만 읽어도 맥락이 통하게, 용어는 정확한 이름으로 쓴다.
  - [설계 결정] 결정마다 한 줄씩: 무엇을 / 어떻게 / 왜 / 다른 선택지
  - [새 개념] 이번 작업에서 처음 나온 기술 용어 목록
  - [문서] 관련 docs 경로와 GitHub 링크

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle(Groovy) 멀티 모듈 (`settings.gradle`에 모듈 추가)
- auth: Spring Web MVC, Spring Security(OAuth2 Resource Server, JwtEncoder), Spring Data JPA(MySQL 8.4), Spring Data Redis, springdoc

## 명령어

- 단위 테스트: `./gradlew test` (통합 테스트는 Testcontainers라 Docker가 없으면 건너뛴다. CI에서는 모두 돈다)
- 로컬 실행: `docker compose up -d` (MySQL·Redis) → `./gradlew :auth:bootRun` → http://localhost:8080/auth/swagger-ui.html
  (맥에서 k3s를 쓸 때는 Docker Desktop을 끄므로 보통은 CI와 클러스터로 확인한다)
- 배포: main에 push → GitHub Actions(test → image(ARM64) → deploy)가 platform-infra의 `k8s/apps/<서비스>/kustomization.yaml` 태그를 바꾸고 ArgoCD가 배포한다. **main push가 곧 배포다**
- 클러스터 주소: http://192.168.252.2/auth/swagger-ui.html

## 인증 서버 핵심 결정 (자세한 이유는 docs/auth.md)

- 액세스 토큰: JWT, RS256(비대칭 키) 서명, 15분. 공개키는 `/auth/.well-known/jwks.json`(JWKS). 다른 서비스는 개인키 없이 검증한다
- 서명 개인키는 platform-infra의 Ansible Vault → Secret(`auth-jwt-keys`) → `/etc/auth/keys/<kid>.pem`. 활성 키 id는 ConfigMap(`AUTH_JWT_ACTIVE_KID`)
- 리프레시 토큰: 무작위 문자열, Redis에 해시로 저장, 14일, 재발급할 때마다 교체, 재사용이면 그 사용자의 토큰 전부 폐기
- 로그인 실패 15분 안에 5번 → 5분 잠금 (Redis, 없는 이메일도 똑같이)
- 공통 에러 응답 `{code, message, errors}` (쿠폰 서버와 같은 모양)
- 토큰 클레임: iss=platform-auth, aud=platform, sub=사용자 id, roles=["USER"|"ADMIN"]
