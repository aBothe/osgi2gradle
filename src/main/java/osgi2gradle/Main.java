package osgi2gradle;

import java.io.*;
import java.nio.file.*;
import java.util.*;
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
                    bundle.gradleSubprojectName = bundle.relativePath.toString()
                            .replace(FileSystems.getDefault().getSeparator(), ":");
                    return bundle;
                })
                .sorted((a, b) -> a.gradleSubprojectName.compareToIgnoreCase(b.gradleSubprojectName))
                .collect(Collectors.toList());

        makeProjectsGradle(subProjectPaths, projectRootPath.resolve("subprojects.gradle"));
        makeSettingsGradle(subProjectPaths, projectRootPath.resolve("settings.gradle"));
    }

    private static void makeProjectsGradle(List<EclipseBundle> eclipseBundles, Path toGradleFile) throws Exception {
        try (var projectsGradle = new FileOutputStream(toGradleFile.toFile());
             var projectsGradleWriter = new OutputStreamWriter(projectsGradle)) {
            for (var bundle : eclipseBundles) {
                declareProjectSignature(projectsGradleWriter, bundle);

                var bundleProperties = new Properties();
                try (var fin = new FileInputStream(bundle.buildPropertiesPath.toFile())) {
                    bundleProperties.load(fin);
                }

                if (bundleProperties.containsKey("source..")) {
                    projectsGradleWriter
                            .append("\tsourceSets.main.java.srcDirs = [")
                            .append(Arrays
                                    .stream(bundleProperties.getProperty("source..").split(","))
                                    .map(source -> "'" + source + "'")
                                    .collect(Collectors.joining(",")))
                            .append("]\r\n");
                }
                if (bundleProperties.containsKey("bin.includes")) {
                    projectsGradleWriter
                            .append("\tsourceSets.main.resources {\r\n")
                            .append("\t\tsrcDirs = ['.']\r\n")
                            .append("\t\tincludes += [")
                            .append(Arrays
                                    .stream(bundleProperties.getProperty("bin.includes").split(","))
                                    .map(String::trim)
                                    .filter(include -> !".".equals(include))
                                    .map(source -> "'" + source + "'")
                                    .collect(Collectors.joining(",")))
                            .append("]\r\n")
                            .append("\t}\r\n");
                }

                var manifest = getBundleManifest(bundle);
                declareProjectDependencies(eclipseBundles, projectsGradleWriter, manifest);

                projectsGradleWriter.append("}\r\n\r\n");
            }
        }
    }

    private static void declareProjectSignature(OutputStreamWriter projectsGradleWriter, EclipseBundle bundle)
            throws IOException {
        projectsGradleWriter
                .append("project(':")
                .append(bundle.gradleSubprojectName)
                .append("') {\r\n");
    }

    private static void declareProjectDependencies(List<EclipseBundle> eclipseBundles,
                                                   OutputStreamWriter projectsGradleWriter,
                                                   Manifest manifest) throws IOException {
        var requiredBundlesAttribute = manifest.getMainAttributes().getValue("Require-Bundle");
        if (requiredBundlesAttribute == null) {
            return;
        }

        projectsGradleWriter.append("\r\n\tdependencies {\r\n");
        var requiredBundles = new Scanner(requiredBundlesAttribute)
                .findAll(Pattern.compile("([^;,]+)(;[^,]+)?(,|$)"))
                .collect(Collectors.toList());

        requiredBundles.stream().map(matchResult -> matchResult.group(1))
                .map(bundleName -> eclipseBundles.stream().filter(bundle -> bundleName
                        .equalsIgnoreCase(bundle.relativePath.getName(bundle.relativePath.getNameCount() - 1).toString()))
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .forEach(bundle -> {
                    try {
                        projectsGradleWriter
                                .append("\t\timplementation project(':")
                                .append(bundle.gradleSubprojectName)
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
