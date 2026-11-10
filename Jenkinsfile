pipeline {
    // 사용 가능한 아무 Jenkins 노드에서 실행
    agent any

    triggers {
        // 5분마다 GitHub 새 커밋 확인 후 있으면 자동 빌드
        pollSCM('H/5 * * * *')
    }

    stages {
        // 시크릿 탐지: 코드와 커밋 이력에서 키·비밀번호 노출을 찾으면 빌드 실패
        stage('Secret Scan (gitleaks)') {
            steps {
                // Jenkins 작업공간을 gitleaks 컨테이너에 그대로 연결(--volumes-from)해 검사
                sh 'docker run --rm --volumes-from jenkins zricethezav/gitleaks:v8.21.2 git --redact --no-banner "$WORKSPACE"'
            }
        }
        // 컴파일 + 단위 테스트, 결과를 Jenkins 테스트 리포트로 등록
        stage('Build & Test') {
            steps {
                sh './mvnw -B clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        // SAST 게이트: 보안 취약점(Medium 이상) 발견 시 빌드 실패
        stage('SAST (SpotBugs)') {
            steps {
                sh './mvnw -B spotbugs:check'
            }
            post {
                always {
                    // 성공·실패와 관계없이 분석 리포트 보관
                    archiveArtifacts artifacts: 'target/spotbugsXml.xml', allowEmptyArchive: true
                }
            }
        }
        // SCA 게이트: 라이브러리에 CVSS 7 이상 취약점이 있으면 빌드 실패
                stage('SCA (Dependency-Check)') {
                    steps {
                        // Jenkins에 저장된 nvd-api-key를 이 단계 안에서만 환경변수로 꺼내 씀
                        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                            sh './mvnw -B dependency-check:check'
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                        }
                    }
                }
                // 이미지 스캔: 앱을 Docker 이미지로 만들고 OS 패키지·라이브러리의 HIGH/CRITICAL 취약점이 있으면 빌드 실패
                stage('Image Scan (Trivy)') {
                    steps {
                        sh 'docker build -t cicd-practice:$BUILD_NUMBER .'
                        // 취약점 DB는 trivy-cache 볼륨에 저장해 다음 빌드부터 다시 받지 않음
                        sh 'docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache/ aquasec/trivy:0.58.1 image --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --no-progress cicd-practice:$BUILD_NUMBER'
                    }
                }
                // DAST: 앱을 실제로 띄우고 ZAP baseline으로 응답을 점검, 규칙 파일의 FAIL 항목이 걸리면 빌드 실패
                stage('DAST (ZAP baseline)') {
                    steps {
                        sh '''
                            docker network create dast-$BUILD_NUMBER
                            docker run -d --name app-$BUILD_NUMBER --network dast-$BUILD_NUMBER cicd-practice:$BUILD_NUMBER
                            # 앱이 응답할 때까지 최대 60초 대기
                            docker run --rm --network dast-$BUILD_NUMBER curlimages/curl:8.10.1 \
                                --retry 30 --retry-delay 2 --retry-all-errors -s -o /dev/null http://app-$BUILD_NUMBER:8080/
                            # 스캔 (규칙 파일 적용, 리포트는 jenkins_home 볼륨의 작업공간에 저장)
                            docker run --rm --network dast-$BUILD_NUMBER -v jenkins_home:/zap/wrk \
                                ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
                                -t http://app-$BUILD_NUMBER:8080/ \
                                -c workspace/cicd-practice/zap-rules.tsv \
                                -r workspace/cicd-practice/zap-report.html -I
                        '''
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'zap-report.html', allowEmptyArchive: true
                            // 앱 컨테이너·네트워크·이번 빌드 이미지 정리
                            sh '''
                                docker rm -f app-$BUILD_NUMBER || true
                                docker network rm dast-$BUILD_NUMBER || true
                                docker rmi cicd-practice:$BUILD_NUMBER || true
                            '''
                        }
                    }
                }
        // 보관 대상을 앱 jar로 한정
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/cicd-practice-*.jar', fingerprint: true
            }
        }
    }
}
