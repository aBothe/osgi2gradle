# osgi2gradle

Creates gradle-files around bundle-based Eclipse OSGI projects in order to get everything built not by PDE but direct gradle.

## Goals

* Build OSGI plugins (featuring dependency lookup, unittests, maybe sonarqube analyses)
* Zip them
* Ship them

## Prerequisites

Assume there's a eclipse workspace-like filetree featuring different plugins to be built:
```
workspaceroot/
  trunk1/
    pluginA/
      META-INF/MANIFEST.MF
      build.properties
      src/com/myenterprise/MyType.java
      plugin.xml
    pluginB/
      META-INF/MANIFEST.MF
      build.properties
      src/com/myenterprise/MyOtherType.java
      plugin.xml
  trunk2/
    pluginC/
      META-INF/MANIFEST.MF
      build.properties
      ...
```
Also, there's an external P2-based UpdateSite located either in the local Filesystem or somewhere online:
```
https://my.update.site/somewhere
  artifacts.jar (or artifacts.xml)
  plugins/
    org.eclipse.core.runtime_1.5.0.jar
    myplugin_2.0.0.jar
  ...
```

## Usage

Now, run the following command to first build osgi2gradle, then execute it:
```
gradle build
java -jar build/libs/osgi2gradle /path/to/workspaceroot
```

Running osgi2gradle will generate a set of gradle files in your workspace:
* `build.gradle` - the main file which may be modified. References `subprojects.gradle`. This file will not be overwritten if osgi2gradle is executed again.
* `subprojects.gradle` - auto-generated build script that contains both P2-bundle referencing infrastructure and information retrieved from all the plugin projects' MANIFEST.MF / build.properties

Then reference the target P2 site by editing the `gradle.properties`:
Set 
* `eclipseRepository=https://my.update.site/somewhere` (or if local)
* `eclipseRepository=file://C:/dev/mylocaleclipse/eclipse`

Then you'll be able run a default `gradle build` in your `/path/to/workspaceroot`.

## Concepts
This build-tool is a by-hand corner-cutting approach to build eclipse RDP projects in eclipse-less environments.

## Limitations
As this project is just cutting corners to get code built, many features of P2 or Equinox are not reimplemented.

What's implemented:
* Reading artifacts.jar
* Reading artifacts.xml to build a metainfo cache about what external bundles there are available
* Reading a plugin's META-INF/MANIFEST.MF
  * Required-Bundle
  * Bundle-Classpath
  * Bundle-SymbolicName

What's not implemented:
* Recognizing Imported/Exported packages
* Bundle versions as part of Required-Bundle definitions

## Contribute
If you want to enrichen my simple approach, feel free to open issues or PRs!
