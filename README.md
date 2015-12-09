# MMClearVolumePlugin
Micro-Manager 2.0 plugin that opens datasets in the ClearVolume 3D viewer.  

[ClearVolume](http://fiji.sc/ClearVolume) is a volume renderer developed at the MPI-CBG in Dresden.  Th MMClearVolume plugin 
lets you open a ClearVolume viewer on a dataset in Micro-Manager.  It only works in Micro-Manager 2.0, not in Micro-Manager 1.4

![](http://valelab.ucsf.edu/~nstuurman/CV-MM-Desktop.png)

For developers:  MMClearVolumePlugin is set-up as a Maven project.  To build it, run ```mvn install```.  Copy the jar in the folder 
```target``` to Micro-Manager2.0/plugins.  Download all the jars needed by ClearColume by running  
```mvn dependency:copy-dependencies``` which copies all dependent JARs into`` `$target/cv_dependencies```. These files can 
then be copied in ```jars/``` dir of MM2.  Look for any duplicate jars by checing the contents of ```plugins``` and ```mmplugins```.
Launch Micro-Manager by running ```java -cp jars/*:ij.jar ij.ImagaJ``` (in the Micro-Manager 2.0 directory).  Make sure that 
you launch Java 1.8 (ClearVolume does not work in Java 1.6, which is usually used by Micro-Manager).  Hopefully, installation 
and deployment will be much simpler in the near future!



