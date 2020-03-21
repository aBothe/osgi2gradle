package osgi2gradle;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        final Path projectRootPath = Paths.get("/home/lx/Dokumente/Projects/eclipse-workspace");
        List<EclipseBundleGradleProject> projects = findSubProjects(projectRootPath);

        Path subProjectsGradlePath = projectRootPath.resolve("subprojects.gradle");
        try(InputStream stream = Main.class.getResourceAsStream("/bundlebuilder.gradle")) {
            Files.copy(stream, subProjectsGradlePath, StandardCopyOption.REPLACE_EXISTING);
        }
        makeProjectsGradle(projects, subProjectsGradlePath);
        makeSettingsGradle(projects, projectRootPath.resolve("settings.gradle"));
        Path buildGradlePath = projectRootPath.resolve("build.gradle");
        if (!buildGradlePath.toFile().exists()) {
            try(InputStream stream = Main.class.getResourceAsStream("/build.default.gradle")) {
                Files.copy(stream, buildGradlePath);
            }
        }

        makeDevPropertiesFile(projects, projectRootPath
                .resolve(".metadata/.plugins/org.eclipse.pde.core/New_configuration/dev.properties"));
        makePlatformXmlSiteDefinition(projectRootPath, projects, projectRootPath
                .resolve(".metadata/.plugins/org.eclipse.pde.core/New_configuration/org.eclipse.update/platform.xml"));
    }

    private static List<EclipseBundleGradleProject> findSubProjects(Path projectRootPath) throws IOException {
        return Files.walk(projectRootPath, 3)
                .filter(path -> "build.properties".equals(path.getName(path.getNameCount() - 1).toString()))
                .map(path -> {
                    Path relativePath = projectRootPath.relativize(path).getParent();
                    return new EclipseBundleGradleProject(path, relativePath, relativePath.toString()
                            .replace(FileSystems.getDefault().getSeparator(), ":"));
                })
                .sorted((a, b) -> a.gradleSubprojectName.compareToIgnoreCase(b.gradleSubprojectName))
                .collect(Collectors.toList());
    }

    private static void makeProjectsGradle(List<EclipseBundleGradleProject> eclipseBundleGradleProjects, Path toGradleFile) throws Exception {
        try (OutputStream projectsGradle = new FileOutputStream(toGradleFile.toFile(), true);
             OutputStreamWriter projectsGradleWriter = new OutputStreamWriter(projectsGradle)) {
            for (EclipseBundleGradleProject bundle : eclipseBundleGradleProjects) {
                bundle.declareProjectSignature(projectsGradleWriter);
                bundle.declareProjectSourceSets(projectsGradleWriter);

                new EclipseBundleManifest(bundle.readBundleManifest())
                        .declareProjectDependencies(eclipseBundleGradleProjects, projectsGradleWriter);

                bundle.declareProjectEnd(projectsGradleWriter);
            }
        }
    }

    private static void makeSettingsGradle(List<EclipseBundleGradleProject> subProjectPaths, Path settingsFile) throws IOException {
        try (OutputStream settingsGradleOS = new FileOutputStream(settingsFile.toFile());
             OutputStreamWriter settingsGradleWriter = new OutputStreamWriter(settingsGradleOS)) {
            subProjectPaths.forEach(eclipseBundleGradleProject ->
            {
                try {
                    settingsGradleWriter
                            .append("include '")
                            .append(eclipseBundleGradleProject.gradleSubprojectName)
                            .append("'\r\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void makeDevPropertiesFile(List<EclipseBundleGradleProject> subProjects, Path devPropertiesPath) throws IOException {
        Properties devProperties = new Properties();
        devProperties.setProperty("@ignoredot@", "true");

        subProjects.stream()
                .map(EclipseBundleGradleProject::readBundleManifest)
                .forEach(bundleManifest -> {
                    String symbolicBundleName = bundleManifest.getMainAttributes().getValue("Bundle-SymbolicName");
                    if (symbolicBundleName == null || (symbolicBundleName = symbolicBundleName.trim()).isEmpty()) {
                        return;
                    }
                    int firstAttributeIndex = symbolicBundleName.indexOf(';');
                    if (firstAttributeIndex > -1) {
                        symbolicBundleName = symbolicBundleName.substring(0, firstAttributeIndex);
                    }

                    String classPath = bundleManifest.getMainAttributes().getValue("Bundle-ClassPath");
                    List<String> cleanedClassPath = new ArrayList<>();
                    cleanedClassPath.add("build/classes/java/main");
                    cleanedClassPath.add("build/resources/main");
                    Arrays.stream(classPath != null ? classPath.split(",") : new String[0])
                            .map(String::trim)
                            .filter(path -> !".".equals(path))
                            .forEach(cleanedClassPath::add);
                    devProperties.setProperty(symbolicBundleName, String.join(",", cleanedClassPath));
                });

        File devPropertiesFile = devPropertiesPath.toFile();
        devPropertiesFile.getParentFile().mkdirs();
        try (OutputStream outputStream = new FileOutputStream(devPropertiesFile);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            devProperties.store(writer, "Auto-generated by osgi2gradle");
        }
    }

    private static void makePlatformXmlSiteDefinition(
            Path projectRootPath,
            List<EclipseBundleGradleProject> subProjects,
            Path platformXmlPath) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element config = document.createElement("config");
        document.appendChild(config);
        config.setAttribute("date", String.valueOf(Instant.now().toEpochMilli()));
        config.setAttribute("transient", "true");
        config.setAttribute("version", "3.0");

        Element site = document.createElement("site");
        config.appendChild(site);
        site.setAttribute("enabled", "true");
        site.setAttribute("list", subProjects.stream()
                .map(bundle -> bundle.relativePath.toString())
                .collect(Collectors.joining(",")));
        site.setAttribute("policy", "USER-INCLUDE");
        site.setAttribute("updateable", "true");
        site.setAttribute("url", projectRootPath.toUri().toString());

        File targetFile = platformXmlPath.toFile();
        targetFile.getParentFile().mkdirs();
        try (OutputStream outputStream = new FileOutputStream(targetFile);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(document), new StreamResult(writer));
        }
    }
}
