// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(25)
}

// 콘솔 한글 깨짐 방지.
//
// Java 18+ 부터 file.encoding 기본값은 UTF-8 이지만, System.out 은 그것과 별개로
// stdout.encoding (Windows 에서는 콘솔 코드페이지 = cp949) 을 따른다.
// JVM 은 cp949 로 쓰는데 IntelliJ 콘솔은 UTF-8 로 읽기 때문에 한글만 깨진다.
// (ASCII 는 두 인코딩이 같아서 멀쩡하게 보인다.)
val utf8JvmArgs = listOf(
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
)

tasks.withType<JavaExec>().configureEach {
    jvmArgs(utf8JvmArgs)
}

tasks.withType<Test>().configureEach {
    // 테스트 출력도 동일하게 UTF-8 로 맞춘다.
    jvmArgs(utf8JvmArgs)

    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
