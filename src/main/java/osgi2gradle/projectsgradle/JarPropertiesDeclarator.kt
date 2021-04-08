package osgi2gradle.projectsgradle

import osgi2gradle.Bundle
import java.io.OutputStreamWriter

fun Bundle.declareArchiveOutputNames(projectsGradleWriter: OutputStreamWriter) {
    val bundleSymbolicName = symbolicName
    projectsGradleWriter
            .append("\tjar.archiveBaseName = '")
            .append(bundleSymbolicName)
            .append("'\r\n")
    val bundleVersion = version
    projectsGradleWriter
            .append("\tjar.archiveVersion = '")
            .append((bundleVersion ?: "").trim { it <= ' ' })
            .append("'\r\n")
    projectsGradleWriter
            .append("\tjar.archiveFileName = '")
            .append(bundleSymbolicName)
            .append('_')
            .append((bundleVersion ?: "").trim { it <= ' ' })
            .append(".jar'\r\n\r\n")
}