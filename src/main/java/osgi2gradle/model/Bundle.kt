package osgi2gradle

import osgi2gradle.model.P2BundleReference

data class Bundle(
        val symbolicName: String,
        val version: String,
        val requiredBundles: List<P2BundleReference>,
        val importedPackages: Set<String>,
        val classPath: List<String>
)