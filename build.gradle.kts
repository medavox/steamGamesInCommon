plugins {
    kotlin("jvm") version "1.6.10"
    java
    kotlin("plugin.serialization") version "1.6.10"
    id ("maven-publish")
    id ("org.jetbrains.dokka") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.41.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://m2.dv8tion.net/releases")
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
    implementation ("net.dv8tion:JDA:4.4.0_352")
    implementation ("redis.clients:jedis:4.1.0")
    //a logging library is strongly recommended by JDA
    implementation ("org.apache.logging.log4j:log4j-api:2.17.1")
    implementation ("org.apache.logging.log4j:log4j-core:2.17.1")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
}

/*val run by tasks.creating(JavaExec::class) {
    group = "application"
    main = "com.jetbrains.handson.introMpp.MainKt"
    kotlin {
        val main = targets["jvm"].compilations["main"]
        dependsOn(main.compileAllTaskName)
        classpath(
            { main.output.allOutputs.files },
            { configurations["jvmRuntimeClasspath"] }
        )
    }
    ///disable app icon on macOS
    systemProperty("java.awt.headless", "true")
}*/
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    minimize()
    manifest {
        attributes["Implementation-Version"] = version
        attributes["Main-Class"] = "DiscordBotKt"
    }
}
