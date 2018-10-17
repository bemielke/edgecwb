# EdgeCWB

EdgeCWB is a software suite developed by the US Geological Survey (USGS) National Earthquake Information Center (NEIC) that supports multi-protocol waveform acquisition, short-term storage, and data product return. "Edge" refers to a special configuration of a Unix operating system to operate as a network node. "CWB" refers to a Continuous Waveform Buffer which is a method of long-term archival and access of seismic data. The EdgeCWB software allows a computer to collect seismic data and simultaneously serve as a redundant archive.

[Read more on the EdgeCWB Wiki](https://github.com/usgs/edgecwb/wiki)


## Developing

EdgeCWB uses the [Gradle Build Tool](https://gradle.org/)

Run
```
./gradlew build
```

## Using NetBeans IDE
This code may be edited using NetBeans IDE.  For best results a gradle plugin should be used.  

Such a plugin is available at http://plugins.netbeans.org/plugin/44510/gradle-support
It can added to netbeans directly from the Tools->plugins menu.
After loading the the project, there will be a tree item of Subprojects.  To view
the code within those subprojects you must open them individually which can be
done from the context sensitive right-click menu.
