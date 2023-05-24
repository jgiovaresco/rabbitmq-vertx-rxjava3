plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:4.4.2"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-rx-java3")
    implementation("io.vertx:vertx-rabbitmq-client")

    testImplementation("io.vertx:vertx-junit5")
    testImplementation("io.vertx:vertx-junit5-rx-java3")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.18.1"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}
