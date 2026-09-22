plugins {
    kotlin("jvm") version "2.2.21"
}

group = "ru.tikhonov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.16")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}