package osgi2gradle.projectsgradle

import osgi2gradle.Bundle
import osgi2gradle.EclipseBundleGradleProject
import osgi2gradle.model.P2BundleReference
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

fun EclipseBundleGradleProject.declareProjectDependencies(availableProjects: List<EclipseBundleGradleProject>,
                                      projectsGradleWriter: OutputStreamWriter) {
    if (bundle == null || bundle.hasNoDependency()) {
        return
    }
    projectsGradleWriter.append("\r\n\tdependencies {\r\n")
    declareProjectImplementationDependencies(availableProjects, projectsGradleWriter)
    bundle.declareInlineJarImplementationDependencies(projectsGradleWriter)
    projectsGradleWriter.append("\t}\r\n")
}

private fun EclipseBundleGradleProject.collectProjectDependencies(availableProjects: List<EclipseBundleGradleProject>): List<P2BundleReference> {
    val bundles = mutableListOf<P2BundleReference>()
    bundles.addAll(bundle!!.requiredBundles)

    bundle.importedPackages.forEach { importPackage ->
        availableProjects.forEach { otherProject ->
            if (otherProject.bundle != null && importPackage.contains(otherProject.bundle.symbolicName)) {
                bundles.add(P2BundleReference().also {
                    it.name = otherProject.bundle.symbolicName
                    it.version = otherProject.bundle.version
                })
            }
        }
    }

    return bundles.makeReferencesUnique()
}

private fun List<P2BundleReference>.makeReferencesUnique(): List<P2BundleReference> =
        this.groupBy { it.name }.flatMap {
            it.value.sortedByDescending { reference -> reference.version }
        }.distinctBy { it.name }

@Throws(IOException::class)
private fun EclipseBundleGradleProject.declareProjectImplementationDependencies(
        availableProjects: List<EclipseBundleGradleProject>,
        projectsGradleWriter: OutputStreamWriter) {
    val referencedBundles = collectProjectDependencies(availableProjects)
    findProjectIncludes(availableProjects, referencedBundles).forEach(Consumer { referencedBundle: EclipseBundleGradleProject ->
        try {
            projectsGradleWriter
                    .append("\t\t").append("implementation").append(" project(':")
                    .append(referencedBundle.gradleSubprojectName)
                    .append("')\r\n")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    })
    projectsGradleWriter.append("\r\n")
    findNonProjectIncludes(availableProjects, referencedBundles).forEach(Consumer { referencedBundle: P2BundleReference ->
        try {
            projectsGradleWriter
                    .append("\t\t").append("implementation").append(" ")
            referencedBundle.declareP2BundleCall(projectsGradleWriter)
            projectsGradleWriter.append("\r\n")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    })
    projectsGradleWriter.append("\r\n")
}

private fun Bundle.declareInlineJarImplementationDependencies(projectsGradleWriter: OutputStreamWriter) {
    val includedEntries = extractJarsOnClasspath().joinToString("', '")
    if (includedEntries.isNotEmpty()) {
        try {
            projectsGradleWriter
                    .append("\t\t").append("compileOnly files('")
                    .append(includedEntries)
                    .append("')\r\n")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}

private fun Bundle.extractJarsOnClasspath(): List<String> {
    return this.classPath.filter { pathEntry: String -> pathEntry.toLowerCase().endsWith(".jar") }
}

private fun Bundle.hasNoDependency(): Boolean {
    return this.requiredBundles.isEmpty()
            && this.importedPackages.isEmpty()
            && extractJarsOnClasspath().isEmpty()
}


private fun findProjectIncludes(
        availableProjects: List<EclipseBundleGradleProject>,
        requiredBundles: List<P2BundleReference>): List<EclipseBundleGradleProject> {
    return requiredBundles.stream().map { matchResult: P2BundleReference ->
        availableProjects.stream().filter { bundle: EclipseBundleGradleProject? ->
            matchResult.name
                    .equals(bundle!!.relativePath.getName(bundle.relativePath.nameCount - 1).toString(), ignoreCase = true)
        }
                .findFirst().orElse(null)
    }
            .filter { obj: EclipseBundleGradleProject? -> Objects.nonNull(obj) }
            .collect(Collectors.toList())
}

private fun findNonProjectIncludes(
        availableProjects: List<EclipseBundleGradleProject?>,
        requiredBundles: List<P2BundleReference>): List<P2BundleReference> {
    return requiredBundles
            .stream()
            .filter { p2BundleReference: P2BundleReference ->
                availableProjects.stream()
                        .noneMatch { bundle: EclipseBundleGradleProject? ->
                            p2BundleReference.name
                                    .equals(bundle!!.relativePath
                                            .getName(bundle.relativePath.nameCount - 1).toString(), ignoreCase = true)
                        }
            }
            .collect(Collectors.toList())
}
