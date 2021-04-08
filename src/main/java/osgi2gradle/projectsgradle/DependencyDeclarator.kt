package osgi2gradle.projectsgradle

import osgi2gradle.Bundle
import osgi2gradle.EclipseBundleGradleProject
import osgi2gradle.model.P2BundleReference
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

fun Bundle.declareProjectDependencies(availableProjects: List<EclipseBundleGradleProject>,
                                      projectsGradleWriter: OutputStreamWriter) {
    if (hasNoDependency()) {
        return
    }
    projectsGradleWriter.append("\r\n\tdependencies {\r\n")
    declareProjectImplementationDependencies(availableProjects, projectsGradleWriter)
    declareInlineJarImplementationDependencies(projectsGradleWriter)
    projectsGradleWriter.append("\t}\r\n")
}

private fun Bundle.collectProjectDependencies(availableProjects: List<EclipseBundleGradleProject>): List<P2BundleReference> {
    return requiredBundles
    /*val bundlesListAttribute = bundleManifest.mainAttributes.getValue("Require-Bundle")
    val requiredBundles = parseManifestBundleReferences(bundlesListAttribute)

    val importPackageAttribute = bundleManifest.mainAttributes.getValue("Import-Package")
    val importPackages = Arrays.stream(importPackageAttribute?.split(",")?.toTypedArray() ?: emptyArray())
            .map { obj: String -> obj.trim { it <= ' ' } }

    importPackages.forEach { importPackage ->
        val packageAsPath = Paths.get()
        availableProjects.filter { prj ->
            prj.
        }
    }*/
}

@Throws(IOException::class)
private fun Bundle.declareProjectImplementationDependencies(
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
    return this.requiredBundles.isEmpty() && extractJarsOnClasspath().isEmpty()
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
