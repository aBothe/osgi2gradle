package osgi2gradle;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

class EclipseBundleGradleProject {
    final Path buildPropertiesPath;
    final Path relativePath;
    final String gradleSubprojectName;

    EclipseBundleGradleProject(Path buildPropertiesPath, Path relativePath, String gradleSubprojectName) {
        this.buildPropertiesPath = buildPropertiesPath;
        this.relativePath = relativePath;
        this.gradleSubprojectName = gradleSubprojectName;
    }

    void declareProjectSignature(OutputStreamWriter projectsGradleWriter) throws IOException {
        projectsGradleWriter
                .append("project(':")
                .append(gradleSubprojectName)
                .append("') {\r\n");
    }

    void declareProjectEnd(OutputStreamWriter projectsGradleWriter) throws IOException {
        projectsGradleWriter.append("}\r\n\r\n");
    }

    void declareProjectSourceSets(OutputStreamWriter projectsGradleWriter) throws IOException {
        var bundleProperties = readBundleProperties();

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
    }

    private Properties readBundleProperties() throws IOException {
        var bundleProperties = new Properties();
        try (var fin = new FileInputStream(buildPropertiesPath.toFile())) {
            bundleProperties.load(fin);
        }
        return bundleProperties;
    }

    Manifest readBundleManifest() throws IOException {
        var manifestPath = buildPropertiesPath.getParent().resolve("META-INF").resolve("MANIFEST.MF");
        try (var manifestStream = new FileInputStream(manifestPath.toFile())) {
            return new Manifest(manifestStream);
        }
    }
}
