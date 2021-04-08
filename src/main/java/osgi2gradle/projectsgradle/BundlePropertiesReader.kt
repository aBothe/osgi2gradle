package osgi2gradle.projectsgradle

import osgi2gradle.EclipseBundleGradleProject
import osgi2gradle.model.BundleProperties
import java.io.FileInputStream
import java.util.*

fun EclipseBundleGradleProject.readBundleProperties(): BundleProperties {
    val bundleProperties = readRawBundleProperties()

    val sourceDirectories = if (bundleProperties.containsKey("source..")) {
        bundleProperties
                .getProperty("source..")
                .split(",")
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { include: String -> "." != include }
    } else {
        emptyList()
    }

    val includedJars: List<String>
    val includedNonJars: List<String>
    if (bundleProperties.containsKey("bin.includes")) {
        includedJars = bundleProperties
                .getProperty("bin.includes")
                .split(",")
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { include: String -> include.toLowerCase().endsWith(".jar") }

        includedNonJars = bundleProperties
                .getProperty("bin.includes")
                .split(",")
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { include: String -> "." != include && !include.toLowerCase().endsWith(".jar") }
    } else {
        includedJars = emptyList()
        includedNonJars = emptyList()
    }

    return BundleProperties(sourceDirectories, includedJars, includedNonJars)
}

private fun EclipseBundleGradleProject.readRawBundleProperties(): Properties {
    val bundleProperties = Properties()
    FileInputStream(buildPropertiesPath.toFile()).use { fin -> bundleProperties.load(fin) }
    return bundleProperties
}