# MMClearVolumePlugin
[Micro-Manager](http://micro-manager.org) 2.0 plugin that opens datasets in the ClearVolume 3D viewer.  

[ClearVolume](http://fiji.sc/ClearVolume) is a volume renderer developed at the MPI-CBG in Dresden.  The MMClearVolume plugin 
lets you open a ClearVolume viewer on a dataset in Micro-Manager.  It only works in Micro-Manager 2.0, not in Micro-Manager 1.4

![](http://valelab.ucsf.edu/~nstuurman/CV-MM-Desktop.png)

Open a dataset in Micro-Manager.  Select ```ClearVolume``` from the plugins menu.  After a while, the 3D view will appear.  
Change brightness and contrast using the usual Micro-Manager controls (changing the gamma by changing the histogram line
from straight to curved is especially useful).  Note the ClearVolume entry in the Inspector Window.  When you reduce the volume view by changing the x, y, or z slider, you can grab the active area in the slider and move it around (which will 
cause the visible volume area to change).  

For developers:  MMClearVolumePlugin is set-up as a Maven project.  To build it, run ```mvn install```.  Copy the file  ```MMClearVolumePlugin_-0.1-SNAPSHOT.jar``` in the folder 
```target``` to ```Micro-Manager2.0/plugins```.  Download all the jars needed by ClearColume by running ```mvn dependency:copy-dependencies``` which copies all dependent JARs into ```$target/cv_dependencies```. These files can 
then be copied to ```Micro-Manager-2.0/jars/```.  Look for any duplicate jars by checking the contents of ```plugins``` and ```mmplugins``` (I am not sure how important it is to remove duplicates).

Non-developers can download a recent version of the [plugin](https://valelab.ucsf.edu/~nstuurman/images/MMClearVolumePlugin_0.1-SNAPSHOT.jar) (copy to the ```mmplugins``` directory) and the needed [jars](https://valelab.ucsf.edu/~nstuurman/images/jars.zip) (extract in the ```Micro-Manager-2.0``` directory, it will create a ```jars``` directory).

Launch Micro-Manager by running ```java -cp jars/*:ij.jar ij.ImagaJ``` (in the Micro-Manager 2.0 directory).  

Make sure that you launch Java 1.8 (ClearVolume does not work in Java 1.6, which is usually used by Micro-Manager).  On Windows, you can replace the ImageJ.cfg file with something like the following:

```
.
..\Java\jre1.8.0_91\bin\javaw.exe
-Xmx4000m -cp .\jars\*;ij.jar ij.ImageJ
```



Hopefully, installation and deployment will be much simpler in the near future!


TODOs:
* Easy deployment
* Video capture


