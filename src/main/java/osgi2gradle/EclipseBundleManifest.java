package osgi2gradle;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class EclipseBundleManifest {
    private final Manifest bundleManifest;

    EclipseBundleManifest(Manifest bundleManifest) {
        this.bundleManifest = bundleManifest;
    }

    private static class P2BundleReference {
        String name;
        String version;

        void declareP2BundleCall(OutputStreamWriter writer) throws IOException {
            writer.append("p2bundle('").append(name).append("'");
            if (version != null) {
                writer.append(", '").append(version).append("'");
            }
            writer.append(")");
        }
    }

    void declareProjectDependencies(List<EclipseBundleGradleProject> availableProjects,
                                    OutputStreamWriter projectsGradleWriter) throws IOException {
        if (hasNoDependency()) {
            return;
        }

        projectsGradleWriter.append("\r\n\tdependencies {\r\n");
        declareProjectImplementationDependencies(availableProjects, projectsGradleWriter);

        projectsGradleWriter.append("\t}\r\n");
    }

    private void declareProjectImplementationDependencies(
            List<EclipseBundleGradleProject> availableProjects,
            OutputStreamWriter projectsGradleWriter) {
        var bundlesListAttribute = bundleManifest.getMainAttributes().getValue("Require-Bundle");
        var referencedBundles = parseManifestBundleReferences(bundlesListAttribute);

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
    }

    private boolean hasNoDependency() {
        var requiredBundlesAttribute = bundleManifest.getMainAttributes().getValue("Require-Bundle");
        return (requiredBundlesAttribute == null || requiredBundlesAttribute.isBlank());
    }

    private static final Pattern bundlesListAttributeFormat = Pattern.compile("([^;,]+)(;bundle-version=(\"[^\"]+\"|[^,;]+))?(;[\\w-]+=(\"[^\"]+\"|[^,;]+))*");

    private List<P2BundleReference> parseManifestBundleReferences(String bundlesListAttribute) {
        return new Scanner(bundlesListAttribute != null ? bundlesListAttribute : "")
                .findAll(bundlesListAttributeFormat)
                .map(matchResult -> {
                    var p2BundleRef = new P2BundleReference();
                    p2BundleRef.name = matchResult.group(1);
                    var versionString = matchResult.group(3);
                    if (versionString != null && versionString.startsWith("\"")) {
                        versionString = versionString.substring(1, versionString.length() - 1);
                    }
                    p2BundleRef.version = versionString;
                    return p2BundleRef;
                })
                .collect(Collectors.toList());
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
