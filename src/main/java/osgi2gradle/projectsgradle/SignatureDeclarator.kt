package osgi2gradle.projectsgradle

import osgi2gradle.EclipseBundleGradleProject
import java.io.OutputStreamWriter

fun EclipseBundleGradleProject.declareProjectSignature(projectsGradleWriter: OutputStreamWriter) {
    projectsGradleWriter
            .append("project(':")
            .append(gradleSubprojectName)
            .append("') {\r\n")
}

fun EclipseBundleGradleProject.declareProjectEnd(projectsGradleWriter: OutputStreamWriter) {
    projectsGradleWriter.append("}\r\n\r\n")
}
