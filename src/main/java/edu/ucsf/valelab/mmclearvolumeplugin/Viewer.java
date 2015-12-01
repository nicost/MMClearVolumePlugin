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
import com.jogamp.newt.awt.NewtCanvasAWT;
import coremem.fragmented.FragmentedMemory;
import coremem.types.NativeTypeEnum;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
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
   private ClearVolumeRendererInterface clearVolumeRenderer_;
   private String name_;
   private final EventBus displayBus_;
   private final JFrame cvFrame_;
   private Coords.CoordsBuilder coordsBuilder_;
   private boolean open_ = false;
   
   public Viewer(Studio studio) {
      studio_ = studio;
      DisplayWindow theDisplay = studio_.displays().getCurrentWindow();
      displayBus_ = new EventBus();
      cvFrame_ = new JFrame();
      coordsBuilder_ = studio_.data().getCoordsBuilder();
      if (theDisplay == null) {
         ij.IJ.error("No data set open");
         return;
      }
      ds_ = theDisplay.getDisplaySettings().copy().build();
      store_ = theDisplay.getDatastore();
      final int nrZ = store_.getAxisLength(Coords.Z);
      final int nrCh = store_.getAxisLength(Coords.CHANNEL);
      Image randomImage = store_.getAnyImage();
      
      // creates renderer:
      NativeTypeEnum nte = NativeTypeEnum.UnsignedShort;
      if (randomImage.getBytesPerPixel() == 1) {
         nte = NativeTypeEnum.UnsignedByte;
      }
      name_ = theDisplay.getName() + "-ClearVolume";

      clearVolumeRenderer_
              = ClearVolumeRendererFactory.newOpenCLRenderer(
                      name_,
                      randomImage.getWidth(),
                      randomImage.getHeight(),
                      nte,
                      768,
                      768,
                      nrCh,
                      true);
      
      final NewtCanvasAWT lNCWAWT = clearVolumeRenderer_.getNewtCanvasAWT();
      cvFrame_.setTitle(name_);
      cvFrame_.setLayout(new BorderLayout());
      final Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(lNCWAWT, BorderLayout.CENTER);
      cvFrame_.setSize(new Dimension(randomImage.getWidth(), 
              randomImage.getHeight()) );
      cvFrame_.add(container);
      SwingUtilities.invokeLater(() -> {
         cvFrame_.setVisible(true);
      });
            
      clearVolumeRenderer_.setTransferFunction(TransferFunctions.getDefault());
      clearVolumeRenderer_.setVisible(true);

      // create fragmented memory for each stack that needs sending to CV:
      final Metadata metadata = randomImage.getMetadata();
      final SummaryMetadata summary = store_.getSummaryMetadata();
      final int maxValue = 1 << store_.getAnyImage().getMetadata().getBitDepth();
      
      for (int ch = 0; ch < nrCh; ch++) {
         FragmentedMemory lFragmentedMemory = new FragmentedMemory();
         for (int i = 0; i < nrZ; i++) {
            // For each image in the stack build an offheap memory object:
            // OffHeapMemory lOffHeapMemory = OffHeapMemory.allocateBytes(nrBytesPerImage);
            // copy the array contents to it, we really can’t avoid that copy unfortunately
            coordsBuilder_ = coordsBuilder_.z(i).channel(ch).time(0).stagePosition(0);
            Coords coords = coordsBuilder_.build();
            // Bypass Micro-Manager api to get access to the ByteBuffers
            DefaultImage image = (DefaultImage) store_.getImage(coords);
            // short[] pix = (short[]) image.getRawPixels();
            // lOffHeapMemory.copyFrom(pix);

            // add the contiguous memory as fragment:
            lFragmentedMemory.add(image.getPixelBuffer());
         }

         // pass data to renderer:
         clearVolumeRenderer_.setVolumeDataBuffer(ch,
                 lFragmentedMemory,
                 randomImage.getWidth(),
                 randomImage.getHeight(),
                 nrZ);
         // TODO: correct x and y voxel sizes using aspect ratio
         clearVolumeRenderer_.setVoxelSize(ch, metadata.getPixelSizeUm(),
                 metadata.getPixelSizeUm(), summary.getZStepUm());
         Color chColor = ds_.getChannelColors()[ch];
         // clearVolumeRenderer_.setTransferFunction(ch, 
         //        TransferFunctions.getGradientForColor(chColor.getRGB()));
         //clearVolumeRenderer_.setBrightness(ch, 
         //        ds_.getChannelContrastSettings()[ch].getContrastMaxes()[0] / maxValue );
         clearVolumeRenderer_.setGamma(ch,  
                 ds_.getChannelContrastSettings()[ch].getContrastGammas()[0]);
      }

      clearVolumeRenderer_.requestDisplay();
      open_ = true;

   }
   
   /**
    * Code that needs to register this instance with various managers and listeners
    * Could have been in the constructor, except that it is unsafe to register
    * our instance before it is completed.  Needs to be called right after the 
    * constructor.
    */
   public void register() {
      if (!open_)
         return;
      displayBus_.register(this);
      studio_.getDisplayManager().addViewer(this);
      studio_.getDisplayManager().raisedToTop(this);
      final DataViewer ourViewer = this;

      // the WindowFocusListener should go into the WindowAdapter, but there it
      // does not work, so add both a WindowListener and WindowFocusListener
      cvFrame_.addWindowFocusListener(new WindowFocusListener() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            System.out.println("Our window got focus");
            studio_.getDisplayManager().raisedToTop(ourViewer);
         }

         @Override
         public void windowLostFocus(WindowEvent e) {
            System.out.println("Our window lost focus"); }
      }
      );
      
      cvFrame_.addWindowListener(new WindowAdapter(){
         @Override
         public void windowClosing(WindowEvent e) {
            clearVolumeRenderer_.close();
            cvFrame_.dispose();
            studio_.getDisplayManager().removeViewer(ourViewer);
            open_ = false;
         }
         @Override
         public void windowClosed(WindowEvent e) {
         }
      });
      
   }
   
   @Override
   public void setDisplaySettings(DisplaySettings ds) {
      System.out.println("setDisplaySettings called");
      ds_ = ds;
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      System.out.println("getDisplaySettings called");
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
      System.out.println("getDatastore called");
      return store_;
   }

   @Override
   public void setDisplayedImageTo(Coords coords) {
      System.out.println("setDisplayedImageTo called and ignored");
   }

   @Override
   /**
    * Assemble all images that are showing in our volume
    * May need to be updated for multiple time points in the future
    */
   public List<Image> getDisplayedImages() {
      System.out.println("getDisplayed Images called");
      List<Image> imageList = new ArrayList<>();
      final int nrZ = store_.getAxisLength(Coords.Z);
      final int nrCh = store_.getAxisLength(Coords.CHANNEL);
      for (int ch = 0; ch < nrCh; ch++) {
         /*
         for (int i = 0; i < nrZ; i++) {
            coordsBuilder_ = coordsBuilder_.z(i).channel(ch).time(0).stagePosition(0);
            Coords coords = coordsBuilder_.build();
            imageList.add(store_.getImage(coords));
         }
         */
         // Only return the middle image
         coordsBuilder_ = coordsBuilder_.z(nrZ/2).channel(ch).time(0).stagePosition(0);
         Coords coords = coordsBuilder_.build();
         imageList.add(store_.getImage(coords));
      }
      return imageList;
   }

   @Override
   public void requestRedraw() {
      System.out.println("Redraw request received");
   }

   @Override
   public boolean getIsClosed() {
      System.out.println("getIsClosed called, answered: " + !clearVolumeRenderer_.isShowing());
      return !clearVolumeRenderer_.isShowing();
   }

   @Override
   public String getName() {
      System.out.println("Name requested, gave: " + name_);
      return name_;
   }
   
}
