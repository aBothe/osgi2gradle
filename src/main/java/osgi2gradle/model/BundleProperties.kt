package osgi2gradle.model

data class BundleProperties(
        val sourceDirectories: List<String>,
        val jarsToInclude: List<String>,
        val nonJarsToInclude: List<String>
)
