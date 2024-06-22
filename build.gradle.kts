plugins {
    kotlin("jvm") version "1.9.23"
//    id("io.github.kota65535.dependency-report") version "2.0.1"
      id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/terl/lazysodium-maven")
    }
}

dependencies {
    implementation("io.javalin:javalin-bundle:6.1.3")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.1")
    implementation("commons-codec:commons-codec:1.13")
    implementation("org.slf4j:slf4j-simple:1.7.26")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.glassfish:javax.json:1.1.4")
    implementation("net.sf.jopt-simple:jopt-simple:4.9")
    implementation("com.muquit.libsodiumjna:libsodium-jna:1.0.4")
    implementation(kotlin("stdlib-jdk8"))
}

group = "org.ronsmits"
version = "1.0-SNAPSHOT"
description = "owntrack"

tasks.jar {
    manifest.attributes["Main-Class"] = "org.ronsmits.owntrackserver.MainKt"
}

kotlin {
    jvmToolchain(17)
}
