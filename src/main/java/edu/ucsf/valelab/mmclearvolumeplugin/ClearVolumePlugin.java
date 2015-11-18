/**
 * Binding to ClearVolume 3D viewer View Micro-Manager datasets in 3D
 *
 * AUTHOR: Nico Stuurman COPYRIGHT: Regents of the University of California,
 * 2015 LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.clearvolume;

import clearvolume.renderer.ClearVolumeRendererInterface;
import clearvolume.renderer.factory.ClearVolumeRendererFactory;
import clearvolume.transferf.TransferFunctions;
import coremem.fragmented.FragmentedMemory;
import coremem.offheap.OffHeapMemory;
import coremem.types.NativeTypeEnum;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class ClearVolumePlugin implements MenuPlugin, SciJavaPlugin {

   private Studio studio_;
   static public final String VERSION_INFO = "1.5.0";
   static private final String COPYRIGHT_NOTICE = "Copyright by UCSF, 2015";
   static private final String DESCRIPTION = "View Micro-Manager data in the ClearVolume viewer";
   static private final String NAME = "ClearVolume";

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected() {
      DisplayWindow theDisplay = studio_.displays().getCurrentWindow();
      if (theDisplay == null) {
         ij.IJ.error("No data set open");
         return;
      }
      Datastore theDatastore = theDisplay.getDatastore();
      List<String> axes = theDatastore.getAxes();
      final int nrZ = theDatastore.getAxisLength(Coords.Z);

      Image randomImage = theDatastore.getAnyImage();

      // creates renderer:
      NativeTypeEnum nte = NativeTypeEnum.UnsignedShort;
      if (randomImage.getBytesPerPixel() == 1) {
         nte = NativeTypeEnum.UnsignedByte;
      }
      final ClearVolumeRendererInterface lClearVolumeRenderer
              = ClearVolumeRendererFactory.newBestRenderer(
                      theDisplay.getName() + "-ClearVolume",
                      randomImage.getWidth(),
                      randomImage.getHeight(),
                      nte,
                      false);
      lClearVolumeRenderer.setTransferFunction(TransferFunctions.getDefault());
      lClearVolumeRenderer.setVisible(true);

      final int nrBytesPerImage = randomImage.getWidth() * randomImage.getHeight() *
              randomImage.getBytesPerPixel();
      
      final int lResolutionX = randomImage.getWidth();
      final int lResolutionY = randomImage.getHeight();
      final int lResolutionZ = nrZ;

      // create fragmented memory for each stack that needs sending to CV:
      FragmentedMemory lFragmentedMemory = new FragmentedMemory();
      Coords.CoordsBuilder builder = studio_.data().getCoordsBuilder();
      
      for (int i = 0; i < nrZ; i++) {
         //For each image in the stack you build a offheap memory object:
         OffHeapMemory lOffHeapMemory = OffHeapMemory.allocateBytes(nrBytesPerImage);
         // copy the array contents to it, we really can’t avoid that copy unfortunately
         builder = builder.z(i).channel(0).time(0).stagePosition(0);
         Coords coords = builder.build();
         Image image = theDatastore.getImage(coords);
         short[] pix = (short[]) image.getRawPixels();
         lOffHeapMemory.copyFrom( pix );
      
         // add the contiguous memory as fragment:
         lFragmentedMemory.add(lOffHeapMemory);
      }

      // pass data to renderer:
      lClearVolumeRenderer.setVolumeDataBuffer(0,
              lFragmentedMemory,
              lResolutionX,
              lResolutionY,
              lResolutionZ);
      lClearVolumeRenderer.requestDisplay();

      while (lClearVolumeRenderer.isShowing()) {
         try {
            Thread.sleep(100);
         } catch (InterruptedException ex) {
            Logger.getLogger(ClearVolumePlugin.class.getName()).log(Level.SEVERE, null, ex);
         }
      }

      lClearVolumeRenderer.close();

   }

   @Override
   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   @Override
   public String getHelpText() {
      return DESCRIPTION;
   }

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public String getVersion() {
      return VERSION_INFO;
   }

}
