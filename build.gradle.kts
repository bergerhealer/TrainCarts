plugins {
    id("java-library")
    id("maven-publish")
    alias(libs.plugins.shadow)
}

val buildNumber = System.getenv("BUILD_NUMBER") ?: "NO-CI"

group = "com.bergerkiller.bukkit"
version = "1.20.2-v1-SNAPSHOT"

repositories {
    mavenLocal {
        // Used to access a server JAR for testing
        // TODO Use Paperclip instead
        content {
            includeGroup("org.spigotmc")
            includeGroup("com.mojang")
        }
    }
    maven("https://ci.mg-dev.eu/plugin/repository/everything/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnlyApi(libs.bkcommonlib) {
        exclude("com.bergerkiller.bukkit.softdependency", "SoftDependency")
    }
    api(libs.smoothcoasters.api)

    // Internal dependencies
    compileOnly(libs.spigot.api) {
        exclude("net.md-5", "bungeecord-chat")
    }
    compileOnly(libs.bundles.cloud) // Reused from BKCommonLib
    implementation(libs.preloader)
    implementation(libs.softdependency)

    // Optional dependencies for integrations with other plugins
    compileOnly(libs.lightapi.fork)
    compileOnly(libs.lightapi.v5)
    compileOnly(libs.multiverse.core)
    compileOnly(libs.multiverse.portals)
    compileOnly(libs.myworlds)
    implementation(libs.neznamy.tab.hider)
    compileOnly(libs.signlink)
    compileOnly(libs.vault.api)
    compileOnly(libs.worldedit.bukkit)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.spigot)
    testImplementation(libs.bkcommonlib)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven("https://ci.mg-dev.eu/plugin/repository/everything") {
            name = "MGDev"
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    javadoc {
        options.encoding = "UTF-8"
        isFailOnError = false // TODO Fix those errors
        val options = options as StandardJavadocDocletOptions
        options.addStringOption("Xdoclint:all,-missing", "-quiet")
    }

    processResources {
        val properties = mapOf(
            "version" to version,
            "build" to buildNumber
        )
        inputs.properties(properties)
        filesMatching("*.yml") {
            expand(properties)
        }
    }

    shadowJar {
        val commonPrefix = "com.bergerkiller.bukkit.common.dep"
        relocate("me.m56738", "com.bergerkiller.bukkit.tc.dep.me.m56738")
        relocate("com.bergerkiller.bukkit.preloader", "com.bergerkiller.bukkit.tc")
        relocate("cloud.commandframework", "$commonPrefix.cloud")
        relocate("io.leangen.geantyref", "$commonPrefix.typetoken")
        relocate("me.lucko.commodore", "$commonPrefix.me.lucko.commodore")
        relocate("net.kyori", "$commonPrefix.net.kyori")
        relocate("org.objectweb.asm", "com.bergerkiller.mountiplex.dep.org.objectweb.asm")
        relocate("com.bergerkiller.bukkit.neznamytabnametaghider", "com.bergerkiller.bukkit.tc.dep.neznamytabnametaghider")
        relocate("com.bergerkiller.bukkit.common.softdependency", "com.bergerkiller.bukkit.tc.dep.softdependency")

        destinationDirectory.set(layout.buildDirectory)
        archiveFileName.set("${project.name}-${project.version}-$buildNumber.jar")

        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
