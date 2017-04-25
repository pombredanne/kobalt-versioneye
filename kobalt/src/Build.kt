
import com.beust.kobalt.buildScript
import com.beust.kobalt.file
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.autoGitTag
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.profile
import com.beust.kobalt.project
import net.thauvin.erik.kobalt.plugin.versioneye.*
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm

val bs = buildScript {
    repos(file("K:/maven/repository"))
    plugins("net.thauvin.erik:kobalt-maven-local:0.5.2", "net.thauvin.erik:kobalt-versioneye:0.4.5")
}

val dev by profile()
val kobaltDependency = if (dev) "kobalt" else "kobalt-plugin-api"

val p = project {

    name = "kobalt-versioneye"
    group = "net.thauvin.erik"
    artifactId = name
    version = "0.4.5"

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

    dependencies {
        compile("com.beust:$kobaltDependency:")
        compile("com.squareup.okhttp3:logging-interceptor:jar:3.7.0")
    }

    dependenciesTest {
        compile("org.testng:testng:")
    }

    assemble {
        jar {
            fatJar = true
        }

        mavenJars {
            fatJar = true
        }
    }

    autoGitTag {
        enabled = true
        message = "Version $version"
    }

    bintray {
        publish = true
        description = "Release version $version"
        vcsTag = version
    }

    versionEye {
        pom = true
    }
}