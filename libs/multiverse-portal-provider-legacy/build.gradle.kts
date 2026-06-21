plugins {
    id("java-library")
}

group = "com.bergerkiller.bukkit"
version = rootProject.version

repositories {
    mavenLocal()
    maven("https://ci.mg-dev.eu/plugin/repository/everything/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":"))
    compileOnly(libs.spigot.api)
    compileOnly(libs.bkcommonlib)
    compileOnly("com.onarandombox.multiversecore:Multiverse-Core:4.2.0")
    compileOnly("com.onarandombox.multiverseportals:Multiverse-Portals:4.2.0")
}

java {
    withSourcesJar()
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}