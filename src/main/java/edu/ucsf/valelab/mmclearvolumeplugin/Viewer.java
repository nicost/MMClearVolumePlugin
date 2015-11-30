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

package edu.ucsf.valelab.mmclearvolumeplugin;

import clearvolume.renderer.ClearVolumeRendererInterface;
import clearvolume.renderer.factory.ClearVolumeRendererFactory;
import clearvolume.transferf.TransferFunctions;
import com.google.common.eventbus.EventBus;
import coremem.fragmented.FragmentedMemory;
import coremem.offheap.OffHeapMemory;
import coremem.types.NativeTypeEnum;
import java.util.List;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

/**
 *
 * @author nico
 */
public class Viewer implements DataViewer {
   private DisplaySettings ds_;
   private final Studio studio_;
   private Datastore store_;
   private ClearVolumeRendererInterface lClearVolumeRenderer_;
   private String name_;
   private final EventBus displayBus_;
   
   public Viewer(Studio studio) {
      studio_ = studio;
      DisplayWindow theDisplay = studio_.displays().getCurrentWindow();
      displayBus_ = new EventBus();
      if (theDisplay == null) {
         ij.IJ.error("No data set open");
         return;
      }
      store_ = theDisplay.getDatastore();
      // List<String> axes = theDatastore.getAxes();
      final int nrZ = store_.getAxisLength(Coords.Z);
      final int nrCh = store_.getAxisLength(Coords.CHANNEL);
      Image randomImage = store_.getAnyImage();

      // nrCh > 1 does not work yet. Remove once it works and make nrCh final
      // nrCh = 1;
      
      // creates renderer:
      NativeTypeEnum nte = NativeTypeEnum.UnsignedShort;
      if (randomImage.getBytesPerPixel() == 1) {
         nte = NativeTypeEnum.UnsignedByte;
      }
      name_ = theDisplay.getName() + "-ClearVolume";
      lClearVolumeRenderer_
              = ClearVolumeRendererFactory.newBestRenderer(
                      name_,
                      randomImage.getWidth(),
                      randomImage.getHeight(),
                      nte,
                      768,
                      768,
                      nrCh,
                      false);
      lClearVolumeRenderer_.setTransferFunction(TransferFunctions.getDefault());
      lClearVolumeRenderer_.setVisible(true);

      final int nrBytesPerImage = randomImage.getWidth() * randomImage.getHeight() *
              randomImage.getBytesPerPixel();

      // create fragmented memory for each stack that needs sending to CV:
      Coords.CoordsBuilder builder = studio_.data().getCoordsBuilder();
      final Metadata metadata = randomImage.getMetadata();
      final SummaryMetadata summary = store_.getSummaryMetadata();
      
      for (int ch = 0; ch < nrCh; ch++) {
         FragmentedMemory lFragmentedMemory = new FragmentedMemory();
         for (int i = 0; i < nrZ; i++) {
            // For each image in the stack build an offheap memory object:
            OffHeapMemory lOffHeapMemory = OffHeapMemory.allocateBytes(nrBytesPerImage);
            // copy the array contents to it, we really can’t avoid that copy unfortunately
            builder = builder.z(i).channel(ch).time(0).stagePosition(0);
            Coords coords = builder.build();
            Image image = store_.getImage(coords);
            short[] pix = (short[]) image.getRawPixels();
            lOffHeapMemory.copyFrom(pix);

            // add the contiguous memory as fragment:
            lFragmentedMemory.add(lOffHeapMemory);
         }

         // pass data to renderer:
         lClearVolumeRenderer_.setVolumeDataBuffer(ch,
                 lFragmentedMemory,
                 randomImage.getWidth(),
                 randomImage.getHeight(),
                 nrZ);
         // TODO: correct x and y voxel sizes using aspect ratio
         lClearVolumeRenderer_.setVoxelSize(ch, metadata.getPixelSizeUm(),
                 metadata.getPixelSizeUm(), summary.getZStepUm());
      }

      lClearVolumeRenderer_.requestDisplay();

   }
   
   public void register() {
      displayBus_.register(this);
   }
   
   @Override
   public void setDisplaySettings(DisplaySettings ds) {
      ds_ = ds;}

   @Override
   public DisplaySettings getDisplaySettings() {
      return ds_;
   }

   @Override
   public void registerForEvents(Object o) {
      displayBus_.register(o);
   }

   @Override
   public void unregisterForEvents(Object o) {
      displayBus_.unregister(o);
   }

   @Override
   public void postEvent(Object o) {
      displayBus_.post(o);
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public void setDisplayedImageTo(Coords coords) {
   }

   @Override
   public List<Image> getDisplayedImages() {
      return null;
   }

   @Override
   public void requestRedraw() {
        }

   @Override
   public boolean getIsClosed() {
      return lClearVolumeRenderer_.isShowing();
   }

   @Override
   public String getName() {
      return name_;
   }
   
}
