# 학습 모듈의 역할과 의존 방향

[전체 문서 지도](README.md)

```text
01-concurrency-basics ──→ study-support
02-executor-threadpool ─→ study-support

buildSrc: 각 모듈에 Kotlin/JDK/출력 등의 빌드 설정 제공
```

학습 모듈끼리는 의존하지 않습니다. 공통 코드도 학습 모듈을 참조하지 않습니다.
`buildSrc`는 빌드 로직이고 `study-support`는 실행 코드입니다.

| 위치 | 책임 |
| --- | --- |
| 학습 모듈 `src/` | 실행 가능한 실험, 학습 대상 설정과 도메인 |
| 학습 모듈 `docs/` | 실험 원리, 관찰 포인트, 적용 범위 |
| `study-support` | 로깅, 이름 생성, 제한 시간 대기와 정리 |
| `docs/kotlin` | 언어를 읽는 법, 여러 모듈에서 쓰는 패턴 |

공통화 기준은 반복 여부뿐 아니라 학습 대상인지도 포함합니다.
큐·core·max·거절 정책을 공통 API 뒤에 숨기면 02에서 배울 내용이 보이지 않으므로 해당 모듈에 둡니다.

## 새 학습 모듈 추가

1. 주제를 나타내는 모듈 디렉터리와 `build.gradle.kts`를 만든다.
2. `buildsrc.convention.kotlin-jvm`을 적용하고, 실행기가 필요하면 `application`을 적용한다.
3. 공통 코드가 필요하면 아래 의존성을 선언한다.
4. 루트 `settings.gradle.kts`에 모듈을 등록한다.
5. 루트 학습 목차와 모듈 README에 실행·문서 링크를 추가한다.

```kotlin
dependencies {
    implementation(project(":study-support"))
}
```

이는 Gradle의 프로젝트 의존성입니다. Spring 객체 주입이나 별도 DI 프레임워크가 필요하지 않습니다.
현재 구조의 실례: [02 빌드 설정](../02-executor-threadpool/build.gradle.kts), [모듈 등록](../settings.gradle.kts).
공통 함수의 사용법·제약은 [study-support README](../study-support/README.md)에서 관리합니다.
