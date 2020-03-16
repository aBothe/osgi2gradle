package osgi2gradle;

import java.io.*;
import java.nio.file.*;
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
}
