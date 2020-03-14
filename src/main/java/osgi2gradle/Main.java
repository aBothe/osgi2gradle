package osgi2gradle;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        final var projectRootPath = Paths.get("/home/lx/Dokumente/Projects/eclipse-workspace");
        var projects = findSubProjects(projectRootPath);

        var subProjectsGradlePath = projectRootPath.resolve("subprojects.gradle");
        Files.write(subProjectsGradlePath,
                Main.class.getResourceAsStream("/bundlebuilder.gradle").readAllBytes());
        makeProjectsGradle(projects, subProjectsGradlePath);
        makeSettingsGradle(projects, projectRootPath.resolve("settings.gradle"));
        var buildGradlePath = projectRootPath.resolve("build.gradle");
        if (!buildGradlePath.toFile().exists()) {
            Files.write(buildGradlePath, Main.class.getResourceAsStream("/build.default.gradle").readAllBytes());
        }
    }

    private static List<EclipseBundleGradleProject> findSubProjects(Path projectRootPath) throws IOException {
        return Files.walk(projectRootPath, 3)
                .filter(path -> "build.properties".equals(path.getName(path.getNameCount() - 1).toString()))
                .map(path -> {
                    var relativePath = projectRootPath.relativize(path).getParent();
                    return new EclipseBundleGradleProject(path, relativePath, relativePath.toString()
                            .replace(FileSystems.getDefault().getSeparator(), ":"));
                })
                .sorted((a, b) -> a.gradleSubprojectName.compareToIgnoreCase(b.gradleSubprojectName))
                .collect(Collectors.toList());
    }

    private static void makeProjectsGradle(List<EclipseBundleGradleProject> eclipseBundleGradleProjects, Path toGradleFile) throws Exception {
        try (var projectsGradle = new FileOutputStream(toGradleFile.toFile(), true);
             var projectsGradleWriter = new OutputStreamWriter(projectsGradle)) {
            for (var bundle : eclipseBundleGradleProjects) {
                bundle.declareProjectSignature(projectsGradleWriter);
                bundle.declareProjectSourceSets(projectsGradleWriter);

                new EclipseBundleManifest(bundle.readBundleManifest())
                        .declareProjectDependencies(eclipseBundleGradleProjects, projectsGradleWriter);

                bundle.declareProjectEnd(projectsGradleWriter);
            }
        }
    }

    private static void makeSettingsGradle(List<EclipseBundleGradleProject> subProjectPaths, Path settingsFile) throws IOException {
        try (var settingsGradleOS = new FileOutputStream(settingsFile.toFile());
             var settingsGradleWriter = new OutputStreamWriter(settingsGradleOS)) {
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
}
