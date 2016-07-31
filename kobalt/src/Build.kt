import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.plugin.publish.bintray
import com.beust.kobalt.project
import com.beust.kobalt.repos
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm

val repos = repos()


val p = project {

    name = "kobalt-versioneye"
    group = "net.thauvin.erik"
    artifactId = name
    version = "0.4.2-beta"

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
        compile("com.beust:kobalt-plugin-api:0.878")
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
