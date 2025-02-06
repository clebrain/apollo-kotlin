import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.net.HttpURLConnection
import java.net.URI

fun Project.configurePublishing() {
  apply {
    plugin("maven-publish")
  }
  pluginManager.withPlugin("com.android.library") {
    extensions.findByType(LibraryExtension::class.java)!!.publishing {
      singleVariant("release")
    }
  }
  pluginManager.withPlugin("maven-publish") {
    extensions.configure(PublishingExtension::class.java) {
      publications {
        when {
          plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {}
          plugins.hasPlugin("com.gradle.plugin-publish") -> {}
          plugins.hasPlugin("java-gradle-plugin") -> {}
          extensions.findByName("android") != null -> {
            create("default", MavenPublication::class.java) {
              afterEvaluate {
                // afterEvaluate is required for Android
                from(components.findByName("release"))
              }
              artifactId = project.name
            }
          }

          extensions.findByName("java") != null -> {
            create("default", MavenPublication::class.java) {
              from(components.findByName("java"))
              artifactId = project.name
            }
          }

          else -> {
            create("default", MavenPublication::class.java) {
              artifactId = project.name
            }
          }
        }
        withType(MavenPublication::class.java).configureEach {
          setDefaultPomFields(this)
        }
      }
      repositories {
        maven {
          name = "GitHub"
          url = uri("https://maven.pkg.github.com/clebrain/apollo-kotlin")
          credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
          }
        }
      }
    }

    tasks.withType(PublishToMavenRepository::class.java).configureEach {
      onlyIf { !publishedToGitHub(project, repository, publication) }
    }
  }
}

private fun publishedToGitHub(
    project: Project,
    repository: MavenArtifactRepository,
    publication: MavenPublication,
): Boolean {
  val url = URI(StringBuilder().apply {
    append(repository.url)
    append('/')
    append(publication.groupId.replace('.', '/'))
    append('/')
    append(publication.artifactId)
    append("/${project.version}")
    append("/${publication.artifactId}-${project.version}.pom")
  }.toString()).toURL()

  val connection = url.openConnection() as HttpURLConnection
  val githubToken = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
  connection.setRequestProperty("Authorization", "Bearer $githubToken")
  connection.requestMethod = "GET"
  connection.connect()

  return connection.responseCode in 200..299
}

/**
 * Set fields which are common to all project, either KMP or non-KMP
 */
private fun Project.setDefaultPomFields(mavenPublication: MavenPublication) {
  mavenPublication.groupId = findProperty("GROUP") as String?
  mavenPublication.version = findProperty("VERSION_NAME") as String?

  mavenPublication.pom {
    name.set(findProperty("POM_NAME") as String?)
    (findProperty("POM_PACKAGING") as String?)?.let {
      // Do not overwrite packaging if already set by the multiplatform plugin
      packaging = it
    }

    description.set(findProperty("POM_DESCRIPTION") as String?)
    url.set(findProperty("POM_URL") as String?)

    scm {
      url.set(findProperty("POM_SCM_URL") as String?)
      connection.set(findProperty("POM_SCM_CONNECTION") as String?)
      developerConnection.set(findProperty("POM_SCM_DEV_CONNECTION") as String?)
    }

    licenses {
      license {
        name.set(findProperty("POM_LICENCE_NAME") as String?)
      }
    }

    developers {
      developer {
        id.set(findProperty("POM_DEVELOPER_ID") as String?)
        name.set(findProperty("POM_DEVELOPER_NAME") as String?)
      }
    }
  }
}