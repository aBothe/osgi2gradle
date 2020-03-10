package osgi2gradle;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    private static class EclipseBundle {
        Path buildPropertiesPath;
        Path relativePath;
        String gradleSubprojectName;
    }

    public static void main(String[] args) throws Exception {
        final var projectRootPath = Paths.get("/home/lx/Dokumente/Projects/eclipse-workspace");
        var subProjectPaths = Files.walk(projectRootPath, 3)
                .filter(path -> "build.properties".equals(path.getName(path.getNameCount() - 1).toString()))
                .map(path -> {
                    var bundle = new EclipseBundle();
                    bundle.buildPropertiesPath = path;
                    bundle.relativePath = projectRootPath.relativize(path).getParent();
                    bundle.gradleSubprojectName = bundle.relativePath
                            .getName(bundle.relativePath.getNameCount() - 1).toString();
                    return bundle;
                })
                .collect(Collectors.toList());

        makeProjectsGradle(subProjectPaths, projectRootPath.resolve("subprojects.gradle"));
        makeSettingsGradle(subProjectPaths, projectRootPath.resolve("settings.gradle"));
    }

    private static void makeProjectsGradle(List<EclipseBundle> eclipseBundles, Path toGradleFile) throws Exception {
        try (var projectsGradle = new FileOutputStream(toGradleFile.toFile());
             var projectsGradleWriter = new OutputStreamWriter(projectsGradle)) {
            for (var bundle : eclipseBundles) {
                declareProjectHead(projectsGradleWriter, bundle);

                var manifest = getBundleManifest(bundle);
                declareProjectDependencies(eclipseBundles, projectsGradleWriter, manifest);

                projectsGradleWriter.append("}\r\n\r\n");
            }
        }
    }

    private static void declareProjectHead(OutputStreamWriter projectsGradleWriter, EclipseBundle bundle)
            throws IOException {
        projectsGradleWriter
                .append("project(':")
                .append(bundle.gradleSubprojectName)
                .append("') {\r\n");

        projectsGradleWriter.append("\tprojectDir = new File('")
                .append(bundle.relativePath.toString().replace(FileSystems.getDefault().getSeparator(),"/"))
                .append("')\r\n");
    }

    private static void declareProjectDependencies(List<EclipseBundle> eclipseBundles,
                                                   OutputStreamWriter projectsGradleWriter,
                                                   Manifest manifest) throws IOException {
        var requiredBundlesAttribute = manifest.getMainAttributes().getValue("Require-Bundle");
        if (requiredBundlesAttribute == null) {
            return;
        }

        projectsGradleWriter.append("\tdependencies {\r\n");
        var requiredBundles = new Scanner(requiredBundlesAttribute)
                .findAll(Pattern.compile("([^;,]+)(;[^,]+)?(,|$)"))
                .collect(Collectors.toList());

        final var projectNames = extractProjectNames(eclipseBundles);
        requiredBundles.stream().map(matchResult -> matchResult.group(1))
                .filter(projectNames::contains)
                .forEach(bundleName -> {
                    try {
                        projectsGradleWriter
                                .append("\t\timplementation bundle(':")
                                .append(bundleName)
                                .append("')\r\n");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        projectsGradleWriter.append("\t}\r\n");
    }

    private static Manifest getBundleManifest(EclipseBundle bundle) throws IOException {
        var manifestPath = bundle.buildPropertiesPath.getParent().resolve("META-INF").resolve("MANIFEST.MF");
        Manifest manifest;
        try (var manifestStream = new FileInputStream(manifestPath.toFile())) {
            manifest = new Manifest(manifestStream);
        }
        return manifest;
    }

    private static List<String> extractProjectNames(List<EclipseBundle> subProjectPaths) {
        return subProjectPaths
                .stream()
                .map(path -> path.gradleSubprojectName)
                .collect(Collectors.toList());
    }

    private static void makeSettingsGradle(List<EclipseBundle> subProjectPaths, Path settingsFile) throws IOException {
        try (var settingsGradleOS = new FileOutputStream(settingsFile.toFile());
             var settingsGradleWriter = new OutputStreamWriter(settingsGradleOS)) {
            subProjectPaths.forEach(eclipseBundle ->
            {
                try {
                    settingsGradleWriter
                            .append("include '")
                            .append(eclipseBundle.gradleSubprojectName)
                            .append("'\r\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
