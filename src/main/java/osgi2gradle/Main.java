package osgi2gradle;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        var subProjectPaths = Files
                .list(Paths.get("/home/lx/Dokumente/Projects/eclipse-workspace"))
                .filter(path -> !path.getName(path.getNameCount() - 1).toString().startsWith("."))
                .filter(path -> Files.isDirectory(path))
                .collect(Collectors.toList());

        makeProjectsGradle(subProjectPaths);
        makeSettingsGradle(subProjectPaths);
    }

    private static void makeProjectsGradle(List<Path> subProjectPaths) throws Exception {
        var availableProjects = extractProjectNames(subProjectPaths);
        try (var projectsGradle = new FileOutputStream("subprojects.gradle");
             var projectsGradleWriter = new OutputStreamWriter(projectsGradle)) {
            for (var path : subProjectPaths) {
                var name = path.getName(path.getNameCount() - 1);
                projectsGradleWriter
                        .append("project(':")
                        .append(name.toString())
                        .append("') {\r\n");

                var manifestPath = Paths.get(path.toString(), "META-INF", "MANIFEST.MF");
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
                                            .append("\t\timplementation project(':")
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

    private static List<String> extractProjectNames(List<Path> subProjectPaths) {
        return subProjectPaths
                .stream()
                .map(path -> path.getName(path.getNameCount() - 1).toString())
                .collect(Collectors.toList());
    }

    private static void makeSettingsGradle(List<Path> subProjectPaths) throws IOException {
        try (var settingsGradleOS = new FileOutputStream("settings.gradle");
             var settingsGradleWriter = new OutputStreamWriter(settingsGradleOS)) {
            extractProjectNames(subProjectPaths).forEach(path ->
            {
                try {
                    settingsGradleWriter
                            .append("include '")
                            .append(path.toString())
                            .append("'\r\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
