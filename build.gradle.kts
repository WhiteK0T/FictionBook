plugins {
    id("java")
    `java-library`
    `maven-publish`
}

group = "org.tehlab.whitek0t"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // Целимся в Java 21
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {

    // Source: https://mvnrepository.com/artifact/io.freefair.lombok/io.freefair.lombok.gradle.plugin
    implementation("io.freefair.lombok:io.freefair.lombok.gradle.plugin:9.5.0")

    // 1. Jackson XML для парсинга <description> и метаданных FB3
    // Он автоматически подтягивает Woodstox (самый быстрый StAX парсер на Java).
    // Это даст нам буст скорости и для нашего ручного StAX парсинга тела книги!
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.21.3")

    // Source: https://mvnrepository.com/artifact/com.googlecode.juniversalchardet/juniversalchardet
    implementation("com.googlecode.juniversalchardet:juniversalchardet:1.0.3")

    // 2. Тестирование
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("net.jqwik:jqwik:1.8.4")  // Property-based testing

    // 3. SLF4J для логгирования (API без реализации, чтобы не навязывать зависимость юзерам)
    implementation("org.slf4j:slf4j-api:2.0.13")
    // SLF4J простая реализация для тестов
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}

/*
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.tehlab.whitek0t"
            artifactId = "fictionbook"
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set("FictionBook Library")
                description.set("Java library for reading and writing FB2/FB3 files")
                url.set("https://github.com/WhiteK0T/fictionbook")
            }
        }
    }
}*/
