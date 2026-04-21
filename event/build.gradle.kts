plugins {
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.dmfs:lib-recur:0.17.1")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.8")
}
