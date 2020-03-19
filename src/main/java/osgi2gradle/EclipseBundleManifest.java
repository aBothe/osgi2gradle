package osgi2gradle;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class EclipseBundleManifest {
    private final Manifest bundleManifest;

    EclipseBundleManifest(Manifest bundleManifest) {
        this.bundleManifest = bundleManifest;
    }

    private static class P2BundleReference implements Comparable<P2BundleReference> {
        String name;
        String version;

        void declareP2BundleCall(OutputStreamWriter writer) throws IOException {
            writer.append("p2bundle('").append(name).append("'");
            if (version != null) {
                writer.append(", '").append(version).append("'");
            }
            writer.append(")");
        }

        @Override
        public int compareTo(P2BundleReference p2BundleReference) {
            return name.compareTo(p2BundleReference.name);
        }
    }

    void declareProjectDependencies(List<EclipseBundleGradleProject> availableProjects,
                                    OutputStreamWriter projectsGradleWriter) throws IOException {
        if (hasNoDependency()) {
            return;
        }

        projectsGradleWriter.append("\r\n\tdependencies {\r\n");
        declareProjectImplementationDependencies(availableProjects, projectsGradleWriter);
        declareInlineJarImplementationDependencies(projectsGradleWriter);
        projectsGradleWriter.append("\t}\r\n");
    }

    private void declareProjectImplementationDependencies(
            List<EclipseBundleGradleProject> availableProjects,
            OutputStreamWriter projectsGradleWriter) throws IOException {
        String bundlesListAttribute = bundleManifest.getMainAttributes().getValue("Require-Bundle");
        List<P2BundleReference> referencedBundles = parseManifestBundleReferences(bundlesListAttribute);

        findProjectIncludes(availableProjects, referencedBundles).forEach(bundle -> {
            try {
                projectsGradleWriter
                        .append("\t\t").append("implementation").append(" project(':")
                        .append(bundle.gradleSubprojectName)
                        .append("')\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        projectsGradleWriter.append("\r\n");

        findNonProjectIncludes(availableProjects, referencedBundles).forEach(bundle -> {
            try {
                projectsGradleWriter
                        .append("\t\t").append("implementation").append(" ");
                bundle.declareP2BundleCall(projectsGradleWriter);
                projectsGradleWriter.append("\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        projectsGradleWriter.append("\r\n");
    }

    private void declareInlineJarImplementationDependencies(OutputStreamWriter projectsGradleWriter) {
        String includedEntries = extractJarsOnClasspath().collect(Collectors.joining("', '"));
        if (!includedEntries.isEmpty()) {
            try {
                projectsGradleWriter
                        .append("\t\t").append("compileOnly files('")
                        .append(includedEntries)
                        .append("')\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Stream<String> extractJarsOnClasspath() {
        String classPath = bundleManifest.getMainAttributes().getValue("Bundle-ClassPath");
        return Arrays.stream(classPath != null ? classPath.split(",") : new String[0])
                .map(String::trim)
                .filter(pathEntry -> pathEntry.toLowerCase().endsWith(".jar"));
    }

    private boolean hasNoDependency() {
        String requiredBundlesAttribute = bundleManifest.getMainAttributes().getValue("Require-Bundle");
        return (requiredBundlesAttribute == null || requiredBundlesAttribute.trim().isEmpty()) &&
                !extractJarsOnClasspath().findFirst().isPresent();
    }

    private static final Pattern bundlesListAttributeFormat = Pattern
            .compile("([^;,]+)(;bundle-version=(\"[^\"]+\"|[^,;]+))?(;[\\w-]+:?=(\"[^\"]+\"|[^,;]+))*");

    private List<P2BundleReference> parseManifestBundleReferences(String bundlesListAttribute) {
        Matcher matcher = bundlesListAttributeFormat.matcher(bundlesListAttribute != null ? bundlesListAttribute : "");
        List<P2BundleReference> references = new ArrayList<>();
        for (int startIndex = 0; matcher.find(startIndex); startIndex = matcher.end()) {
            P2BundleReference p2BundleRef = new P2BundleReference();
            p2BundleRef.name = matcher.group(1);
            String versionString = matcher.group(3);
            if (versionString != null && versionString.startsWith("\"")) {
                versionString = versionString.substring(1, versionString.length() - 1);
            }
            p2BundleRef.version = versionString;
            references.add(p2BundleRef);
        }
        return references;
    }

    private List<EclipseBundleGradleProject> findProjectIncludes(
            List<EclipseBundleGradleProject> availableProjects,
            List<P2BundleReference> requiredBundles) {
        return requiredBundles.stream().map(matchResult -> availableProjects.stream().filter(bundle -> matchResult.name
                .equalsIgnoreCase(bundle.relativePath.getName(bundle.relativePath.getNameCount() - 1).toString()))
                .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<P2BundleReference> findNonProjectIncludes(
            List<EclipseBundleGradleProject> availableProjects,
            List<P2BundleReference> requiredBundles) {
        return requiredBundles
                .stream()
                .filter(p2BundleReference -> availableProjects.stream()
                        .noneMatch(bundle -> p2BundleReference.name
                                .equalsIgnoreCase(bundle.relativePath
                                        .getName(bundle.relativePath.getNameCount() - 1).toString()))
                )
                .collect(Collectors.toList());
    }
}
