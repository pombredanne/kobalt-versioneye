import com.beust.kobalt.*
import com.beust.kobalt.plugin.packaging.*
import com.beust.kobalt.plugin.application.*
import com.beust.kobalt.plugin.kotlin.*
import net.thauvin.erik.kobalt.plugin.versioneye.*

val repos = repos()

//val pl = plugins(file("../kobaltBuild/libs/kobalt-versioneye-0.4.2-beta.jar"))
val pl = plugins("net.thauvin.erik:kobalt-versioneye:")

val p = project {

    name = "example"
    group = "com.example"
    artifactId = name
    version = "0.1"

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
        //compile("com.beust:kobalt-plugin-api:0.878")
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

        //failOn(Fail.securityCheck)
    }
}
