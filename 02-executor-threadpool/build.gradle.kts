plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":study-support"))
}

application {
    mainClass = "executor.MainKt"
}
