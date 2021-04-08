package osgi2gradle

import java.io.FileInputStream
import java.nio.file.Path
import java.util.jar.Manifest


class EclipseBundleGradleProject(
        val buildPropertiesPath: Path,
        val relativePath: Path,
        val gradleSubprojectName: String) : Comparable<EclipseBundleGradleProject?> {

    fun readManifest(): Manifest? {
        val manifestPath = buildPropertiesPath.parent.resolve("META-INF").resolve("MANIFEST.MF")
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
