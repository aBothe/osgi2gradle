package osgi2gradle.projectsgradle

import osgi2gradle.EclipseBundleGradleProject
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.stream.Collectors

fun EclipseBundleGradleProject.declareProjectSourceSets(projectsGradleWriter: OutputStreamWriter) {
    if (bundleProperties.sourceDirectories.isNotEmpty()) {
        projectsGradleWriter
                .append("\tsourceSets.main.java.srcDirs = [")
                .append(bundleProperties.sourceDirectories.joinToString("','", "'", "'"))
                .append("]\r\n")
    }
    projectsGradleWriter
            .append("\tsourceSets.main.resources.srcDirs = sourceSets.main.java.srcDirs\r\n")

    if (bundleProperties.jarsToInclude.isNotEmpty()) {
        projectsGradleWriter
                .append("\tjar.from files(").append(bundleProperties.jarsToInclude.joinToString("','", "'", "'"))
                .append(").collect { zipTree(it) }\r\n")
    }

    if (bundleProperties.nonJarsToInclude.isNotEmpty()) {
        projectsGradleWriter
                .append("\tjar.from fileTree(projectDir) { includes = [")
                .append(bundleProperties.nonJarsToInclude.joinToString("','", "'", "'"))
                .append("] }\r\n")
    }
}
