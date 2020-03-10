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
        public Path buildPropertiesPath;
        public Path relativePath;
        public String gradleSubprojectName;
    }

    public static void main(String[] args) throws Exception {
        final var projectRootPath = Paths.get("/home/lx/Dokumente/Projects/eclipse-workspace");
        var subProjectPaths = Files.walk(projectRootPath, 3)
                .filter(path -> "build.properties".equals(path.getName(path.getNameCount() - 1).toString()))
                .map(path -> {
                    var bundle = new EclipseBundle();
                    bundle.buildPropertiesPath = path;
                    bundle.relativePath = projectRootPath.relativize(path).getParent();
                    bundle.gradleSubprojectName = bundle.relativePath.toString()
                            .replace(FileSystems.getDefault().getSeparator(),"-");
                    return bundle;
                })
                .collect(Collectors.toList());

        makeProjectsGradle(subProjectPaths);
        makeSettingsGradle(subProjectPaths);
    }

    private static void makeProjectsGradle(List<EclipseBundle> eclipseBundles) throws Exception {
        var availableProjects = extractProjectNames(eclipseBundles);
        try (var projectsGradle = new FileOutputStream("subprojects.gradle");
             var projectsGradleWriter = new OutputStreamWriter(projectsGradle)) {
            for (var bundle : eclipseBundles) {
                projectsGradleWriter
                        .append("project(':")
                        .append(bundle.gradleSubprojectName)
                        .append("') {\r\n")
                        .append("\tprojectDir = new File('")
                        .append(bundle.relativePath.toString().replace(FileSystems.getDefault().getSeparator(),"/"))
                        .append("')\r\n");

                var manifestPath = bundle.buildPropertiesPath.getParent().resolve("META-INF").resolve("MANIFEST.MF");
                Manifest manifest;
                try (var manifestStream = new FileInputStream(manifestPath.toFile())) {
                    manifest = new Manifest(manifestStream);
                }

                var requiredBundlesAttribute = manifest.getMainAttributes().getValue("Require-Bundle");
                if (requiredBundlesAttribute != null) {
                    projectsGradleWriter.append("\tdependencies {\r\n");
                    var requiredBundles = new Scanner(requiredBundlesAttribute)
                            .findAll(Pattern.compile("([^;,]+)(;[^,]+)?(,|$)"))
                            .collect(Collectors.toList());

                    requiredBundles.stream().map(matchResult -> matchResult.group(1))
                            .filter(availableProjects::contains)
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

                projectsGradleWriter.append("}\r\n\r\n");
            }
        }
    }

    private static List<String> extractProjectNames(List<EclipseBundle> subProjectPaths) {
        return subProjectPaths
                .stream()
                .map(path -> path.gradleSubprojectName)
                .collect(Collectors.toList());
    }

    private static void makeSettingsGradle(List<EclipseBundle> subProjectPaths) throws IOException {
        try (var settingsGradleOS = new FileOutputStream("settings.gradle");
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
