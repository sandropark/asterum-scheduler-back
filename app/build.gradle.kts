plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":event"))
    implementation(project(":user"))
    implementation(project(":location"))
    implementation("org.springframework.boot:spring-boot-starter")
}
