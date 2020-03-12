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
    }

    void declareProjectDependencies(List<EclipseBundleGradleProject> availableProjects,
                                    OutputStreamWriter projectsGradleWriter) throws IOException {
        if (!hasAnyDependencies(bundleManifest)) {
            return;
        }

        projectsGradleWriter.append("\r\n\tdependencies {\r\n");
        declareProjectImplementationDependencies(availableProjects, projectsGradleWriter);

        projectsGradleWriter.append("\t}\r\n");
    }

    private void declareProjectImplementationDependencies(
            List<EclipseBundleGradleProject> availableProjects,
            OutputStreamWriter projectsGradleWriter) {
        var requiredBundlesAttribute = bundleManifest.getMainAttributes().getValue("Require-Bundle");
        var requiredBundles = parseManifestBundleReferences(requiredBundlesAttribute);

        findProjectIncludes(availableProjects, requiredBundles).forEach(bundle -> {
            try {
                projectsGradleWriter
                        .append("\t\timplementation project(':")
                        .append(bundle.gradleSubprojectName)
                        .append("')\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        findNonProjectIncludes(availableProjects, requiredBundles).forEach(bundle -> {
            try {
                projectsGradleWriter
                        .append("\t\timplementation p2bundle('")
                        .append(bundle.name)
                        .append("'");
                if (bundle.version != null) {
                    projectsGradleWriter.append(", '").append(bundle.version).append("'");
                }
                projectsGradleWriter.append(")\r\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean hasAnyDependencies(Manifest bundleManifest) {
        var requiredBundlesAttribute = bundleManifest.getMainAttributes().getValue("Require-Bundle");
        var importPackageAttribute = bundleManifest.getMainAttributes().getValue("Import-Package");
        return requiredBundlesAttribute != null && !requiredBundlesAttribute.isBlank() &&
                importPackageAttribute != null && !importPackageAttribute.isBlank();
    }

    private List<P2BundleReference> parseManifestBundleReferences(String requiredBundlesAttribute) {
        return new Scanner(requiredBundlesAttribute)
                .findAll(Pattern.compile("([^;,]+)(;[^,]+)?(,|$)"))
                .map(matchResult -> {
                    var p2BundleRef = new P2BundleReference();
                    p2BundleRef.name = matchResult.group(1);
                    p2BundleRef.version = matchResult.group(2);
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
