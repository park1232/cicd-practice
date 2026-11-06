pipeline {
    // 사용 가능한 아무 Jenkins 노드에서 실행
    agent any

    triggers {
        // 5분마다 GitHub 새 커밋 확인 후 있으면 자동 빌드
        pollSCM('H/5 * * * *')
    }

    stages {
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
        // 보관 대상을 앱 jar로 한정
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/cicd-practice-*.jar', fingerprint: true
            }
        }
    }
}
