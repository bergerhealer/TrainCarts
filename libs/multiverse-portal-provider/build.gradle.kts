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
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.6.2")
    compileOnly("org.mvplugins.multiverse.portals:multiverse-portals:5.2.2")
}

java {
    withSourcesJar()
}
