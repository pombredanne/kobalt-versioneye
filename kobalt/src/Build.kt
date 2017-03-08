import com.beust.kobalt.buildScript
import com.beust.kobalt.file
import com.beust.kobalt.misc.kobaltLog
import com.beust.kobalt.plugin.application.application
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.project
import net.thauvin.erik.kobalt.plugin.versioneye.versionEye
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm

val semver = "0.4.4"

val bs = buildScript {
    val f = java.io.File("kobaltBuild/libs/kobalt-versioneye-$semver.jar")
    val p = if (f.exists()) {
        kobaltLog(1, "  >>> Using: ${f.path}")
        file(f.path)
    } else {
        "net.thauvin.erik:kobalt-versioneye:"
    }
    plugins(p)
}

val p = project {

    name = "kobalt-versioneye"
    group = "net.thauvin.erik"
    artifactId = name
    version = semver

    pom = Model().apply {
        description = "VersionEye plug-in for the Kobalt build system."
        url = "https://github.com/ethauvin/kobalt-versioneye"
        licenses = listOf(License().apply {
            name = "BSD 3-Clause"
            url = "https://opensource.org/licenses/BSD-3-Clause"
        })
        scm = Scm().apply {
            url = "https://github.com/ethauvin/kobalt-versioneye"
            connection = "https://github.com/ethauvin/kobalt-versioneye.git"
            developerConnection = "git@github.com:ethauvin/kobalt-versioneye.git"
        }
        developers = listOf(Developer().apply {
            id = "ethauvin"
            name = "Erik C. Thauvin"
            email = "erik@thauvin.net"
        })
    }

    sourceDirectories {
        path("src/main/kotlin")
    }

    sourceDirectoriesTest {
        path("src/test/kotlin")
    }

    dependencies {
        compile("com.beust:kobalt-plugin-api:")
    }

    dependenciesTest {
        compile("org.testng:testng:")

    }

    assemble {
        mavenJars {}
    }

    bintray {
        publish = true
    }
}

val example = project(p) {

    name = "example"
    group = "com.example"
    artifactId = name
    version = "0.1"
    directory = "example"

    sourceDirectories {
        path("src/main/kotlin")
    }

    sourceDirectoriesTest {
        path("src/test/kotlin")
    }

    dependencies {
        compile("com.beust:jcommander:1.47")
        //compile("org.slf4j:slf4j-api:")
        compile("ch.qos.logback:logback-core:0.5")
        compile("ch.qos.logback:logback-classic:1.1.7")
        compile("commons-httpclient:commons-httpclient:jar:3.1")
        compile("com.beust:kobalt-plugin-api:0.878")
    }



    dependenciesTest {
        compile("org.testng:testng:")
    }

    assemble {
        jar {
        }
    }

    application {
        mainClass = "com.example.MainKt"
    }

    versionEye {
        // baseUrl = "https://www.versioneye.com/"
        // colors = true
        // name = ""
        // org = ""
        // quiet = false
        // team = ""
        // verbose = true
        // visibility = "public"

        //failOn(Fail.licensesUnknownCheck, Fail.licensesCheck, Fail.securityCheck, Fail.dependenciesCheck)
    }
}

