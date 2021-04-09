package osgi2gradle

import osgi2gradle.model.P2BundleReference
import java.util.*
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.regex.Pattern

fun Manifest.parseBundle(project: EclipseBundleGradleProject): Bundle {
    var bundleSymbolicName = this.mainAttributes.getValue("Bundle-SymbolicName")
    if (bundleSymbolicName == null) {
        bundleSymbolicName = project.gradleSubprojectName
    }
    bundleSymbolicName = bundleSymbolicName.trim { it <= ' ' }
    val firstAttributeSemicolonIndex = bundleSymbolicName.indexOf(';')
    if (firstAttributeSemicolonIndex > -1) {
        bundleSymbolicName = bundleSymbolicName.substring(0, firstAttributeSemicolonIndex)
    }

    val bundleVersion = (this.mainAttributes.getValue("Bundle-Version") ?: "").trim { it <= ' ' }

    val bundlesListAttribute = this.mainAttributes.getValue("Require-Bundle")
    val requiredBundles = parseManifestBundleReferences(bundlesListAttribute)

    val importPackages = this.mainAttributes.getCommaSplitValue("Import-Package").toSet()
    val exportPackages = this.mainAttributes.getCommaSplitValue("Export-Package").toSet()

    val classPathItems = this.mainAttributes.getCommaSplitValue("Bundle-ClassPath")

    return Bundle(bundleSymbolicName, bundleVersion, requiredBundles, importPackages, exportPackages, classPathItems)
}

private fun Attributes.getCommaSplitValue(name: String) = (getValue(name)?.split(",") ?: emptyList())
        .map { obj: String -> obj.trim { it <= ' ' } }

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

private val bundlesListAttributeFormat = Pattern
        .compile("([^;,]+)(;bundle-version=(\"[^\"]+\"|[^,;]+))?(;[\\w-]+:?=(\"[^\"]+\"|[^,;]+))*")
