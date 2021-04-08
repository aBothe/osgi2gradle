package osgi2gradle

import java.io.IOException
import java.io.OutputStreamWriter
import java.util.*
import java.util.function.Consumer
import java.util.jar.Manifest
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

private class P2BundleReference : Comparable<P2BundleReference?> {
    var name: String? = null
    var version: String? = null

    @Throws(IOException::class)
    fun declareP2BundleCall(writer: OutputStreamWriter) {
        writer.append("p2bundle('").append(name).append("'")
        if (version != null) {
            writer.append(", '").append(version).append("'")
        }
        writer.append(")")
    }

    override fun compareTo(p2BundleReference: P2BundleReference?): Int {
        return name!!.compareTo(p2BundleReference!!.name!!)
    }
}

internal class EclipseBundleManifest(private val bundleManifest: Manifest) {

    @Throws(IOException::class)
    fun declareArchiveOutputNames(project: EclipseBundleGradleProject,
                                  projectsGradleWriter: OutputStreamWriter) {
        var bundleSymbolicName = bundleManifest.mainAttributes.getValue("Bundle-SymbolicName")
        if (bundleSymbolicName == null) {
            bundleSymbolicName = project.gradleSubprojectName
        }
        bundleSymbolicName = bundleSymbolicName.trim { it <= ' ' }
        val firstAttributeSemicolonIndex = bundleSymbolicName.indexOf(';')
        if (firstAttributeSemicolonIndex > -1) {
            bundleSymbolicName = bundleSymbolicName.substring(0, firstAttributeSemicolonIndex)
        }
        projectsGradleWriter
                .append("\tjar.archiveBaseName = '")
                .append(bundleSymbolicName)
                .append("'\r\n")
        val bundleVersion = bundleManifest.mainAttributes.getValue("Bundle-Version")
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

    @Throws(IOException::class)
    fun declareProjectDependencies(availableProjects: List<EclipseBundleGradleProject>,
                                   projectsGradleWriter: OutputStreamWriter) {
        if (hasNoDependency()) {
            return
        }
        projectsGradleWriter.append("\r\n\tdependencies {\r\n")
        declareProjectImplementationDependencies(availableProjects, projectsGradleWriter)
        declareInlineJarImplementationDependencies(projectsGradleWriter)
        projectsGradleWriter.append("\t}\r\n")
    }

    @Throws(IOException::class)
    private fun declareProjectImplementationDependencies(
            availableProjects: List<EclipseBundleGradleProject>,
            projectsGradleWriter: OutputStreamWriter) {
        val bundlesListAttribute = bundleManifest.mainAttributes.getValue("Require-Bundle")
        val referencedBundles = parseManifestBundleReferences(bundlesListAttribute)
        findProjectIncludes(availableProjects, referencedBundles).forEach(Consumer { bundle: EclipseBundleGradleProject ->
            try {
                projectsGradleWriter
                        .append("\t\t").append("implementation").append(" project(':")
                        .append(bundle.gradleSubprojectName)
                        .append("')\r\n")
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        })
        projectsGradleWriter.append("\r\n")
        findNonProjectIncludes(availableProjects, referencedBundles).forEach(Consumer { bundle: P2BundleReference ->
            try {
                projectsGradleWriter
                        .append("\t\t").append("implementation").append(" ")
                bundle.declareP2BundleCall(projectsGradleWriter)
                projectsGradleWriter.append("\r\n")
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        })
        projectsGradleWriter.append("\r\n")
    }

    private fun declareInlineJarImplementationDependencies(projectsGradleWriter: OutputStreamWriter) {
        val includedEntries = extractJarsOnClasspath().collect(Collectors.joining("', '"))
        if (!includedEntries.isEmpty()) {
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

    private fun extractJarsOnClasspath(): Stream<String> {
        val classPath = bundleManifest.mainAttributes.getValue("Bundle-ClassPath")
        return Arrays.stream(classPath?.split(",")?.toTypedArray() ?: emptyArray())
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { pathEntry: String -> pathEntry.toLowerCase().endsWith(".jar") }
    }

    private fun hasNoDependency(): Boolean {
        val requiredBundlesAttribute = bundleManifest.mainAttributes.getValue("Require-Bundle")
        return (requiredBundlesAttribute == null || requiredBundlesAttribute.trim { it <= ' ' }.isEmpty()) &&
                !extractJarsOnClasspath().findFirst().isPresent
    }

    private fun parseManifestBundleReferences(bundlesListAttribute: String?): List<P2BundleReference> {
        val matcher = bundlesListAttributeFormat.matcher(bundlesListAttribute ?: "")
        val references: MutableList<P2BundleReference> = ArrayList()
        var startIndex = 0
        while (matcher.find(startIndex)) {
            val p2BundleRef = P2BundleReference()
            p2BundleRef.name = matcher.group(1)
            var versionString = matcher.group(3)
            if (versionString != null && versionString.startsWith("\"")) {
                versionString = versionString.substring(1, versionString.length - 1)
            }
            p2BundleRef.version = versionString
            references.add(p2BundleRef)
            startIndex = matcher.end()
        }
        return references
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

    companion object {
        private val bundlesListAttributeFormat = Pattern
                .compile("([^;,]+)(;bundle-version=(\"[^\"]+\"|[^,;]+))?(;[\\w-]+:?=(\"[^\"]+\"|[^,;]+))*")
    }
}
