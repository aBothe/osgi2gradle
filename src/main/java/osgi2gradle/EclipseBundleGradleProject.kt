package osgi2gradle

import osgi2gradle.projectsgradle.readBundleProperties
import java.io.FileInputStream
import java.nio.file.Path
import java.util.jar.Manifest


class EclipseBundleGradleProject(
        val buildPropertiesPath: Path,
        val relativePath: Path,
        val gradleSubprojectName: String) : Comparable<EclipseBundleGradleProject?> {

    val basePath: Path = buildPropertiesPath.parent.toAbsolutePath()
    val bundleProperties = readBundleProperties()
    val bundle = readManifest()?.parseBundle(this)

    private fun readManifest(): Manifest? {
        val manifestPath = basePath.resolve("META-INF").resolve("MANIFEST.MF")
        if (!manifestPath.toFile().exists()) {
            return null
        }
        try {
            FileInputStream(manifestPath.toFile()).use { manifestStream -> return Manifest(manifestStream) }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun compareTo(eclipseBundleGradleProject: EclipseBundleGradleProject?): Int {
        return gradleSubprojectName.compareTo(eclipseBundleGradleProject!!.gradleSubprojectName)
    }
}
