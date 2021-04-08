package osgi2gradle.projectsgradle

import osgi2gradle.EclipseBundleGradleProject
import osgi2gradle.parseBundle
import java.io.IOException
import java.io.OutputStreamWriter

fun EclipseBundleGradleProject.declareGradleProject(w: OutputStreamWriter, projects: List<EclipseBundleGradleProject>) {
    declareProjectSignature(w)
    declareProjectSourceSets(w)
    bundle?.declareArchiveOutputNames(w)
    declareProjectDependencies(projects, w)
    declareProjectEnd(w)
}