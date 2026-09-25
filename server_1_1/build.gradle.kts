plugins {
    kotlin("jvm") version "2.2.21"
    id("com.google.protobuf") version "0.9.4"
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

    // gRPC
    implementation("io.grpc:grpc-protobuf:1.66.0")
    implementation("io.grpc:grpc-netty-shaded:1.66.0")
    implementation("io.grpc:grpc-stub:1.66.0")

    // Protobuf
    implementation("com.google.protobuf:protobuf-kotlin:4.28.2")

    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

kotlin {
    jvmToolchain(17)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}