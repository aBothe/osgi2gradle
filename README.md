# osgi2gradle

Creates gradle-files around bundle-based Eclipse OSGI projects in order to get everything built not by PDE but direct gradle.


## Usage

Assume there's a eclipse workspace-like filetree featuring different plugins to be built:
```
projectroot/
  trunk1/
    pluginA/
      META-INF/MANIFEST.MF
      build.properties
    pluginB/
      META-INF/MANIFEST.MF
      build.properties
  trunk2/
    pluginC/
      META-INF/MANIFEST.MF
      build.properties
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

Now, run the following command:
```
gradle build
java -jar build/libs/osgi2gradle /path/to/workspace
```

Then reference the target P2 site by editing the `gradle.properties`:
Set 
* `eclipseRepository=https://my.update.site/somewhere` (or if local)
* `eclipseRepository=file://C:/dev/mylocaleclipse/eclipse`

Then you'll be able run a default `gradle build` in your projectroot.
