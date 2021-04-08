package osgi2gradle

import osgi2gradle.projectsgradle.*
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.file.*
import java.time.Instant
import java.util.*
import java.util.function.Consumer
import java.util.jar.Manifest
import java.util.stream.Collectors
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


fun main(args: Array<String>) = Main().main(args)

class Main {
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printManpage()
            return
        }
        val projectRootPath = extractProjectRootPath(args)
        val projects = findSubProjects(projectRootPath)
        val subProjectsGradlePath = projectRootPath.resolve("subprojects.gradle")
        Main::class.java.getResourceAsStream("/bundlebuilder.gradle").use { stream -> Files.copy(stream, subProjectsGradlePath, StandardCopyOption.REPLACE_EXISTING) }
        makeProjectsGradle(projects, subProjectsGradlePath)
        makeSettingsGradle(projects, projectRootPath.resolve("settings.gradle"))
        val buildGradlePath = projectRootPath.resolve("build.gradle")
        if (!buildGradlePath.toFile().exists()) {
            Main::class.java.getResourceAsStream("/build.default.gradle").use { stream -> Files.copy(stream, buildGradlePath) }
        }
        val gradlePropertiesPath = projectRootPath.resolve("gradle.properties")
        if (!gradlePropertiesPath.toFile().exists()) {
            Main::class.java.getResourceAsStream("/gradle.default.properties").use { stream -> Files.copy(stream, gradlePropertiesPath) }
        }
        if (generateEclipseRunConfiguration(args)) {
            val configurationName = extractEclipseRunConfigurationName(args)
            makeDevPropertiesFile(projects, projectRootPath
                    .resolve(".metadata/.plugins/org.eclipse.pde.core/" + configurationName
                            + "/dev.properties"))
            makePlatformXmlSiteDefinition(projectRootPath, projects, projectRootPath
                    .resolve(".metadata/.plugins/org.eclipse.pde.core/" + configurationName
                            + "/org.eclipse.update/platform.xml"))
        }
    }

    private fun generateEclipseRunConfiguration(args: Array<String>): Boolean {
        return Arrays.stream(args, 0, args.size - 1)
                .anyMatch { arg: String -> arg.startsWith("-eclipse") }
    }

    private fun extractEclipseRunConfigurationName(args: Array<String>): String {
        return Arrays.stream(args, 0, args.size - 1)
                .filter { arg: String -> arg.startsWith("-eclipse=") }
                .map { arg: String -> arg.substring("-eclipse=".length) }
                .findFirst().orElse("New_configuration")
    }

    private fun extractProjectRootPath(args: Array<String>): Path {
        return Paths.get(args[args.size - 1])
    }

    @Throws(IOException::class)
    private fun printManpage() {
        Main::class.java.getResourceAsStream("/manpage.txt").use { stream ->
            val buf = ByteArray(512)
            var readb: Int
            while (stream.read(buf).also { readb = it } > 0) {
                System.out.write(buf, 0, readb)
            }
        }
    }

    @Throws(IOException::class)
    private fun findSubProjects(projectRootPath: Path): List<EclipseBundleGradleProject> {
        return Files.walk(projectRootPath, 3)
                .filter { path: Path -> "build.properties" == path.getName(path.nameCount - 1).toString() }
                .map { path: Path? ->
                    val relativePath = projectRootPath.relativize(path).parent
                    EclipseBundleGradleProject(path!!, relativePath, relativePath.toString()
                            .replace(FileSystems.getDefault().separator, ":"))
                }
                .sorted { a: EclipseBundleGradleProject, b: EclipseBundleGradleProject -> a.gradleSubprojectName.compareTo(b.gradleSubprojectName, ignoreCase = true) }
                .collect(Collectors.toList())
    }

    @Throws(Exception::class)
    private fun makeProjectsGradle(projects: List<EclipseBundleGradleProject>,
                                   toGradleFile: Path) {
        FileOutputStream(toGradleFile.toFile(), true).use { projectsGradle ->
            OutputStreamWriter(projectsGradle).use { projectsGradleWriter ->
                projects.forEach { it.declareGradleProject(projectsGradleWriter, projects) }
            }
        }
    }

    @Throws(IOException::class)
    private fun makeSettingsGradle(subProjectPaths: List<EclipseBundleGradleProject>,
                                   settingsFile: Path) {
        FileOutputStream(settingsFile.toFile()).use { settingsGradleOS ->
            OutputStreamWriter(settingsGradleOS).use { settingsGradleWriter ->
                subProjectPaths.forEach(Consumer { eclipseBundleGradleProject: EclipseBundleGradleProject ->
                    try {
                        settingsGradleWriter
                                .append("include '")
                                .append(eclipseBundleGradleProject.gradleSubprojectName)
                                .append("'\r\n")
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                })
            }
        }
    }

    @Throws(IOException::class)
    private fun makeDevPropertiesFile(subProjects: List<EclipseBundleGradleProject>,
                                      devPropertiesPath: Path) {
        val devProperties = Properties()
        devProperties.setProperty("@ignoredot@", "true")
        subProjects.stream()
                .map { obj: EclipseBundleGradleProject -> obj.readManifest() }
                .filter { obj: Manifest? -> obj != null }
                .map { obj: Manifest? -> obj!! }
                .forEach { bundleManifest ->
                    var symbolicBundleName: String? = bundleManifest
                            .mainAttributes.getValue("Bundle-SymbolicName")
                    if (symbolicBundleName == null || symbolicBundleName.trim { it <= ' ' }.also { symbolicBundleName = it }.isEmpty()) {
                        return@forEach
                    }
                    val firstAttributeIndex = symbolicBundleName!!.indexOf(';')
                    if (firstAttributeIndex > -1) {
                        symbolicBundleName = symbolicBundleName!!.substring(0, firstAttributeIndex)
                    }
                    val classPath: String? = bundleManifest.mainAttributes.getValue("Bundle-ClassPath")
                    val cleanedClassPath: MutableList<String> = ArrayList()
                    cleanedClassPath.add("build/classes/java/main")
                    cleanedClassPath.add("build/resources/main")
                    Arrays.stream(classPath?.split(",")?.toTypedArray() ?: emptyArray())
                            .map { obj: String -> obj.trim { it <= ' ' } }
                            .filter { path: String -> "." != path }
                            .forEach { e: String -> cleanedClassPath.add(e) }
                    devProperties.setProperty(symbolicBundleName, java.lang.String.join(",", cleanedClassPath))
                }
        val devPropertiesFile = devPropertiesPath.toFile()
        devPropertiesFile.parentFile.mkdirs()
        FileOutputStream(devPropertiesFile).use { outputStream -> OutputStreamWriter(outputStream).use { writer -> devProperties.store(writer, "Auto-generated by osgi2gradle") } }
    }

    @Throws(Exception::class)
    private fun makePlatformXmlSiteDefinition(
            projectRootPath: Path,
            subProjects: List<EclipseBundleGradleProject>,
            platformXmlPath: Path) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val config = document.createElement("config")
        document.appendChild(config)
        config.setAttribute("date", Instant.now().toEpochMilli().toString())
        config.setAttribute("transient", "true")
        config.setAttribute("version", "3.0")
        val site = document.createElement("site")
        config.appendChild(site)
        site.setAttribute("enabled", "true")
        site.setAttribute("list", subProjects.stream()
                .map { bundle: EclipseBundleGradleProject -> bundle.relativePath.toString() }
                .collect(Collectors.joining(",")))
        site.setAttribute("policy", "USER-INCLUDE")
        site.setAttribute("updateable", "true")
        site.setAttribute("url", projectRootPath.toUri().toString())
        val targetFile = platformXmlPath.toFile()
        targetFile.parentFile.mkdirs()
        FileOutputStream(targetFile).use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                TransformerFactory.newInstance().newTransformer()
                        .transform(DOMSource(document), StreamResult(writer))
            }
        }
    }
}
