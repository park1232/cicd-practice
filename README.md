# cicd-practice

Jenkins 파이프라인에 보안 검사 단계를 붙여보려고 만든 연습용 저장소.
시크릿 탐지, SAST, SCA, 이미지 스캔, DAST, 권한 테스트, SBOM까지 단계별로 넣어봄.
앱 자체는 Spring Boot 기본 프로젝트에 컨트롤러 몇 개만 있음.

## 구성

- Spring Boot 4.1.2 / Java 17 / Maven Wrapper
- 인증·인가: Spring Security 7.1.2 (JWT는 `oauth2-resource-server`, nimbus-jose-jwt 10.9.1)
- Jenkins LTS (Docker, 로컬)
- 시크릿 탐지: gitleaks 8.21.2
- SAST: SpotBugs 4.8.6.6 + FindSecBugs 1.13.0
- SCA: OWASP Dependency-Check 12.2.2
- 이미지 스캔: Trivy 0.58.1
- DAST: OWASP ZAP baseline
- SBOM: CycloneDX Maven 플러그인 2.9.1 + Dependency-Track 4.14.6 (Docker)

Jenkinsfile 단계

```
Secret Scan (gitleaks) → Build & Test → SAST (SpotBugs) → SCA (Dependency-Check)
→ Image Scan (Trivy) → DAST (ZAP baseline) → SBOM (Dependency-Track) → Archive
```

- gitleaks는 코드와 커밋 이력에서 키가 나오면 실패
- SpotBugs는 Medium 이상 나오면 실패
- Dependency-Check는 CVSS 7.0 이상 나오면 실패
- Trivy는 HIGH, CRITICAL이 나오면 실패
- ZAP은 규칙 파일(`zap-rules.tsv`)에서 FAIL로 지정한 항목이 걸리면 실패
- 권한 테스트(BOLA)는 Build & Test 단계에서 같이 돌아감
- 앞 단계가 실패하면 뒤 단계(Archive 포함)를 건너뛰니까 jar가 안 남음
- 트리거는 Poll SCM 5분. Jenkins가 로컬에 있어서 GitHub webhook은 못 씀
- NVD API 키, Dependency-Track API 키는 Jenkins Credentials(Secret text, `nvd-api-key`, `dtrack-api-key`)에 넣고 `withCredentials`로 해당 단계에서만 꺼내 씀

## Jenkins 실행

Jenkins 안에서 Docker 명령을 쓰려고 Docker CLI를 넣은 이미지를 따로 만들어서 씀.

```
docker build -t my-jenkins:lts-jdk17 .
docker run -d --name jenkins -p 8081:8080 -p 50000:50000 --group-add 0 -v jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock my-jenkins:lts-jdk17
```

- 이미지는 `jenkins/jenkins:lts-jdk17` 위에 `docker-ce-cli`만 추가
- 8080은 다른 앱이 쓰고 있어서 8081로 띄움
- 호스트 Docker를 같이 쓰려고 `docker.sock`을 마운트함. 소켓이 root:root 660이라 `--group-add 0` 필요
- gitleaks, Trivy는 Jenkins 작업공간이나 소켓을 그대로 넘겨서 컨테이너로 실행

Dependency-Track은 별도로 띄움.

```
docker run -d --name dtrack-apiserver -p 8091:8080 -v dependency-track:/data dependencytrack/apiserver:4.14.6
docker run -d --name dtrack-frontend -p 8092:8080 -e API_BASE_URL=http://localhost:8091 dependencytrack/frontend:4.14.6
```

- 화면은 http://localhost:8092, API는 8091
- Jenkins 컨테이너 안에서는 `localhost`가 자기 자신이라 `host.docker.internal:8091`로 업로드함

## 테스트한 것

### SAST - 명령어 삽입

`PingController`에 사용자 입력을 그대로 붙여서 `Runtime.exec()`를 호출하는 코드를 넣음.

- #4 실패: `COMMAND_INJECTION` (Medium), PingController.java 13번째 줄
- 입력값을 정규식으로 거르고 `ProcessBuilder`에 인자를 따로 넘기도록 수정
- 그런데 이렇게 고쳐도 FindSecBugs가 계속 잡음 (오히려 High로 올라감). 검증 로직까지는 못 따라가는 것 같음
- `@SuppressFBWarnings`에 justification 적어서 예외 처리 → #5 통과

### SCA - Log4Shell

`log4j-core 2.14.1`을 직접 추가.

- 처음 돌릴 때 NVD 데이터 420,016건 받느라 6분 정도 걸림 (API 키 있을 때). 그 뒤로는 십몇 초
- #9 실패: CVE-2021-44228(10.0), CVE-2021-45046(9.0) 포함 9건
- CVE-2021-44832(6.6)도 리포트엔 나오지만 기준 미만이라 이것만으론 안 막힘
- 버전 지정을 지우고 Spring Boot가 관리하는 버전(2.25.5)을 쓰게 바꿈 → #10 통과

### 시크릿 - gitleaks

가짜 AWS 키(`AKIA...`)를 application.properties에 넣고 커밋.

- #13 실패: aws-access-token 1건
- 주석 처리해도 계속 잡힘(#14), 줄을 지워도 Git 이력에 남아서 계속 잡힘(#15)
- 진짜 조치는 키 폐기인데 가짜 키라 폐기는 안 함. `.gitleaksignore`에 fingerprint와 사유를 적어서 통과(#16)

### 이미지 - Trivy

오래된 베이스 이미지(`eclipse-temurin:17.0.1_12-jre-focal`)로 Dockerfile 작성.

- #17 실패: HIGH 9건 (openssl CVE-2022-0778, nghttp2 CVE-2023-44487 등), 이미지 318MB
- 최신 `17-jre` HIGH 2건, distroless 39건과 비교한 뒤 `17-jre-alpine`으로 교체 → #18 통과(0건, 206MB)

### DAST - ZAP baseline

앱을 컨테이너로 띄우고 같은 Docker 네트워크에서 ZAP을 돌림. 끝나면 컨테이너, 네트워크, 이미지를 정리함.

- #19 실패: 보안 헤더 4건 FAIL (10020, 10021, 10038, 10063)
- 필터로 X-Content-Type-Options, X-Frame-Options, CSP 등을 추가 → #20 통과 (FAIL 0, PASS 65, WARN 3)
- 이후 필터를 지우고 Spring Security `http.headers`로 같은 헤더를 옮김 (#32). ZAP 결과 FAIL 0, WARN 3 그대로. 헤더 4종은 단위 테스트(`SecurityHeadersTests`)로도 고정

### 권한 - BOLA

주문 조회 API(`/api/orders/{id}`)에 소유자 검증을 일부러 뺌. 사용자는 `X-User` 헤더로만 구분하는 단순한 모델.

- #21 실패: 다른 사용자의 주문 조회가 403이어야 하는데 200
- 소유자 검증을 추가 → #22 통과
- SAST/DAST는 이런 권한 문제를 정상 응답으로 봐서 못 잡는다고 알려져 있음. 이건 직접 스캐너로 돌려본 건 아님

### 인증·인가 - Spring Security

BOLA 예제의 `X-User` 헤더는 클라이언트가 마음대로 바꿀 수 있어서, 인증부터 단계별로 바꿈.

1. 약점 증명: 헤더만 `bob`으로 바꿔서 bob의 주문이 200으로 조회되는 걸 재현
2. Basic + BCrypt (#28): `SecurityFilterChain`, 메모리 사용자(alice, bob), 비밀번호는 BCrypt 해시로 비교
3. JWT (#29): `POST /login`에서 HS256 서명 토큰 발급(만료 15분). `Authorization: Bearer`를 `oauth2ResourceServer`가 검증
4. `@PostAuthorize` (#30): 컨트롤러의 `if`를 `OrderService`의 `@PostAuthorize`로 이동. `@EnableMethodSecurity`가 없으면 어노테이션이 조용히 무시되는 걸 확인
5. 테스트 (#31): 본인 200, 남의 주문 403, 토큰 없음·위조·만료·`alg=none`·틀린 비밀번호 401
6. 보안 헤더 (#32): 직접 만든 필터를 `http.headers`로 대체

- 서명 키는 코드에 안 넣음. 환경변수 `APP_JWT_SECRET`이 있으면 쓰고, 없으면 기동 때 무작위 생성(재시작하면 기존 토큰 무효)
- 디코더는 HS256만 허용해서 `alg=none` 위조 토큰을 거부
- SpotBugs가 `new SecureRandom().nextBytes(...)`를 `DMI_RANDOM_USED_ONLY_ONCE`로 잡아서 `KeyGenerator`로 교체
- 변이 실험: 방어를 일부러 빼고 테스트가 실패하는지 확인. `@EnableMethodSecurity` 제거, `@PostAuthorize` 제거 → 둘 다 `otherUserCannotReadOrder`가 403 대신 200으로 실패. `/api/**`를 `permitAll`로 바꿔도 `@PostAuthorize`가 막아서 통과(방어 2겹). 만료 검증을 끄면 `expiredTokenIsUnauthorized`가 실패
- 패키지는 `auth`, `order`, `config`, `system`으로 나눔

### SBOM - Dependency-Track

CycloneDX로 SBOM(42개 컴포넌트)을 만들어 Dependency-Track에 업로드 (#23~#25).

- 처음엔 `log4j-core 2.14.1`을 올려도 0건. purl만 있는 SBOM이라 NVD(CPE 기반)와 매칭이 안 돼서였음. CPE를 직접 넣은 실험용 SBOM은 Critical 2, Medium 8
- Google OSV(Maven)를 켜고 서버를 재시작하니 purl만 있는 SBOM도 8건(Critical 2) 탐지
- 실제 앱(log4j-core 2.25.5)은 OSV 적재 뒤에도 0건. 설정 전의 0건은 믿을 수 없던 값이었음
- 실험용 SBOM은 직접 만든 최소 SBOM이고, 실험 프로젝트는 끝나고 삭제함

## 하다가 막혔던 것

- 8080 포트 충돌 → 8081로 변경
- 처음 플러그인 설치할 때 Pipeline, Git 등이 실패. 로그 보니 미러 서버 타임아웃 2건 때문에 그걸 의존하는 플러그인까지 줄줄이 실패한 거였음. Retry 후 재시작
- IntelliJ에서 Maven 동기화가 안 됨. wrapper가 Maven 3.10.0이라 IDE가 못 읽음. IDE 설정만 Bundled Maven으로 바꿈
- `archiveArtifacts 'target/*.jar'`로 했더니 SpotBugs가 받아둔 findsecbugs 플러그인 jar까지 같이 보관됨 → `target/cicd-practice-*.jar`로 좁힘
- pom.xml에 플러그인 붙여넣다가 다른 플러그인 블록 안에 넣어버려서 Malformed POM 남
- gitleaks, Trivy를 컨테이너로 돌릴 때 `-v` 경로가 호스트 기준이라 작업공간이 안 보임 → `--volumes-from jenkins`로 해결
- Jenkins 컨테이너 안에서 `localhost:8091`로는 호스트의 Dependency-Track에 못 붙음. `host.docker.internal`로 바꿈
- Dependency-Track 관리자 비밀번호를 몰라 볼륨을 새로 만듦
- 권한 `+` 버튼에서 뜬 "Not Modified (304)"를 404로 착각. 이미 있는 권한이라 중복 추가한 것
- SpotBugs가 JWT 키 생성 코드(`new SecureRandom().nextBytes`)에서 실패. 일회용 난수 객체 패턴이라 `KeyGenerator`로 바꿈

## 아쉬운 점

- 예외 처리에 사유만 남겼는데, 실제로 쓰려면 누가 승인했는지랑 만료일도 같이 관리해야 할 듯
- 지금은 Jenkins 컨트롤러에서 바로 빌드함. 제대로 하려면 agent 분리
- Jenkins가 호스트의 `docker.sock`을 마운트해서 씀. 호스트 root급 권한이라 실무에선 에이전트 분리나 Kaniko 같은 방식이 필요함
- 인증은 Spring Security + JWT로 바꿨지만 단순화한 부분이 남음: 로그인 시도 횟수 제한 없음, 토큰 폐기(로그아웃) 불가, 사용자·비밀번호가 코드 안 메모리에 있음(실제로는 DB)
- ZAP이 BOLA를 못 잡는다는 건 여전히 직접 측정하지 않음. 권한 문제는 테스트 코드로 지키는 쪽으로 정리
- 서명 키가 대칭키(HS256)라 발급·검증이 같은 키를 씀. 서비스가 나뉘면 비대칭키(RS256)가 필요
- Dependency-Track 알림(Slack 등)이나 정책(Policy) 기반 게이트는 아직 안 해봄

## AWS 기초 학습

Jenkins를 로컬에서만 돌려서 클라우드 쪽은 안 해봤음. 계정 만들기 전에 개념부터 정리함. 아직 AWS 계정은 없고 직접 만들어 본 건 없음.

### IAM
- 사용자는 사람·외부 도구의 고정된 신원(영구 키). 역할은 필요할 때 빌려 쓰는 권한 묶음이고 임시 자격증명이 자동 발급됨
- 정책이 없으면 기본 거부
- 키 없이 AWS에 접근하려면 EC2(인스턴스 프로파일)나 ECS 태스크에 역할을 연결함. 환경변수나 Jenkins credentials에 넣은 키도 결국 영구 키
- 예를 들어 Jenkins가 ECR에 push하는 건 "특정 저장소 push 권한만" 가진 역할을 EC2에 붙이는 식 (최소 권한)

### VPC, 보안그룹, NACL
- 퍼블릭/프라이빗은 라우팅 테이블에 `0.0.0.0/0 → 인터넷 게이트웨이` 경로가 있느냐로 갈림
- 프라이빗 서버가 밖으로 나가려면 NAT 게이트웨이가 필요하고, NAT는 퍼블릭 서브넷에 둠. 서버가 아니라 NAT를 퍼블릭에 두는 것
- 보안그룹은 인스턴스 단위, Stateful, 허용만 가능. NACL은 서브넷 단위, Stateless, 거부도 가능(특정 IP 차단용)
- 보안그룹 규칙의 출처를 IP 대신 다른 보안그룹으로 지정할 수 있음

```
ALB  (퍼블릭 서브넷, 2개 AZ 이상)  인바운드 443  ← 0.0.0.0/0
앱   (프라이빗 서브넷)             인바운드 8080 ← ALB의 SG
DB   (프라이빗 서브넷)             인바운드 5432 ← 앱의 SG
```

- 인터넷에 노출되는 건 ALB뿐. 경로 차단과 SG가 이중으로 막음
- Dependency-Track에서 프런트만 포트를 열고 API는 안 연 것과 같은 발상이라고 이해함

### S3, RDS
- S3 유출 원인은 대부분 버킷 공개 설정 실수. 퍼블릭 액세스 차단을 켜 두고, 외부에 줄 땐 Presigned URL
- RDS는 Multi-AZ가 가용성(장애 시 자동 전환), 읽기 복제본이 성능(읽기 분산). 처음에 복제본을 가용성이라고 잘못 이해했었음
- S3 접근 권한은 IAM 역할, DB 비밀번호는 Secrets Manager. 앱은 역할로 Secrets Manager 읽기 권한을 받음
- RDS는 멈춰 둬도 스토리지·스냅샷 비용이 남아서 실습 후 삭제해야 함

### ALB, 모니터링
- ALB는 리스너 → 규칙 → 대상 그룹. 헬스체크로 죽은 대상을 빼고 복구되면 다시 넣음
- CloudWatch는 지금 상태(메트릭, 로그, 알람), CloudTrail은 누가 무엇을 했는지(API 감사). "누가 보안그룹을 열었나"는 CloudTrail

### 계정 보안
- 루트 MFA 먼저, 예산 알림은 리소스 만들기 전에, 평소엔 MFA 건 IAM 사용자로 작업, 루트 액세스 키는 안 만듦
- 예산 알림은 알려 줄 뿐 자동으로 끄지는 않음. NAT, RDS, ALB를 켜 두고 잊는 게 대표적인 과금 사고