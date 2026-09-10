plugins {
    // 공통 빌드 설정을 convention 플러그인에서 가져온다.
    // (Kotlin JVM, jvmToolchain(25), 테스트 로깅, 콘솔 UTF-8 출력)
    // 실제 내용은 buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts 에 있다.
    id("buildsrc.convention.kotlin-jvm")

    // 예제 실행기를 실행하기 위한 Application 플러그인.
    application
}

group = "org.example"
version = "unspecified"

dependencies {
    implementation(project(":study-support"))
}

application {
    // 예제 실행기. ./gradlew :01-concurrency-basics:run --args="03 07"
    mainClass = "concurrency.MainKt"
}
