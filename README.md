# cicd-practice

Jenkins 파이프라인에 SAST, SCA 검사를 붙여보려고 만든 연습용 저장소.
앱 자체는 Spring Boot 기본 프로젝트에 컨트롤러 두 개만 있음.

## 구성

- Spring Boot 4.1.2 / Java 17 / Maven Wrapper
- Jenkins LTS (Docker, 로컬)
- SAST: SpotBugs 4.8.6.6 + FindSecBugs 1.13.0
- SCA: OWASP Dependency-Check 12.2.2

Jenkinsfile 단계

```
Checkout → Build & Test → SAST (SpotBugs) → SCA (Dependency-Check) → Archive
```

- SpotBugs는 Medium 이상 나오면 실패
- Dependency-Check는 CVSS 7.0 이상 나오면 실패
- 앞 단계가 실패하면 Archive를 건너뛰니까 jar가 안 남음
- 트리거는 Poll SCM 5분. Jenkins가 로컬에 있어서 GitHub webhook은 못 씀
- NVD API 키는 Jenkins Credentials(Secret text, `nvd-api-key`)에 넣고 `withCredentials`로 SCA 단계에서만 꺼내 씀

## Jenkins 실행

```
docker run -d --name jenkins -p 8081:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home jenkins/jenkins:lts-jdk17
```

8080은 다른 앱이 쓰고 있어서 8081로 띄움.

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

## 하다가 막혔던 것

- 8080 포트 충돌 → 8081로 변경
- 처음 플러그인 설치할 때 Pipeline, Git 등이 실패. 로그 보니 미러 서버 타임아웃 2건 때문에 그걸 의존하는 플러그인까지 줄줄이 실패한 거였음. Retry 후 재시작
- IntelliJ에서 Maven 동기화가 안 됨. wrapper가 Maven 3.10.0이라 IDE가 못 읽음. IDE 설정만 Bundled Maven으로 바꿈
- `archiveArtifacts 'target/*.jar'`로 했더니 SpotBugs가 받아둔 findsecbugs 플러그인 jar까지 같이 보관됨 → `target/cicd-practice-*.jar`로 좁힘
- pom.xml에 플러그인 붙여넣다가 다른 플러그인 블록 안에 넣어버려서 Malformed POM 남

## 아쉬운 점

- 예외 처리에 사유만 남겼는데, 실제로 쓰려면 누가 승인했는지랑 만료일도 같이 관리해야 할 듯
- 지금은 Jenkins 컨트롤러에서 바로 빌드함. 제대로 하려면 agent 분리
- DAST, SBOM은 아직 안 해봄