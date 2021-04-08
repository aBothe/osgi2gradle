package osgi2gradle.projectsgradle

import osgi2gradle.EclipseBundleGradleProject
import osgi2gradle.parseBundle
import java.io.IOException
import java.io.OutputStreamWriter

fun EclipseBundleGradleProject.declareGradleProject(w: OutputStreamWriter, projects: List<EclipseBundleGradleProject>) {
    declareProjectSignature(w)
    declareProjectSourceSets(w)
    readManifest()?.parseBundle(this)?.also { bundle ->
        try {
            bundle.declareArchiveOutputNames(w)
            bundle.declareProjectDependencies(projects, w)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
    declareProjectEnd(w)
}