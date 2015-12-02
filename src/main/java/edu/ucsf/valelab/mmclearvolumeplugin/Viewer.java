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
import clearvolume.transferf.TransferFunction1D;
import clearvolume.transferf.TransferFunctions;
import com.google.common.eventbus.EventBus;
import com.jogamp.newt.awt.NewtCanvasAWT;
import coremem.fragmented.FragmentedMemory;
import coremem.types.NativeTypeEnum;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.internal.events.DefaultDisplayAboutToShowEvent;
import org.micromanager.events.internal.DefaultEventManager;

/**
 * Micro-Manager DataViewer that shows 3D stack in the ClearVolume 3D Renderer
 * 
 * @author nico
 */
public class Viewer implements DisplayWindow {

   private DisplaySettings ds_;
   private final Studio studio_;
   private Datastore store_;
   private ClearVolumeRendererInterface clearVolumeRenderer_;
   private String name_;
   private final EventBus displayBus_;
   private final JFrame cvFrame_;
   private Coords.CoordsBuilder coordsBuilder_;
   private boolean open_ = false;
   private int maxValue_;

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
              randomImage.getHeight()));
      cvFrame_.add(container);
      SwingUtilities.invokeLater(() -> {
         cvFrame_.setVisible(true);
      });

      clearVolumeRenderer_.setTransferFunction(TransferFunctions.getDefault());
      clearVolumeRenderer_.setVisible(true);

      // create fragmented memory for each stack that needs sending to CV:
      final Metadata metadata = randomImage.getMetadata();
      final SummaryMetadata summary = store_.getSummaryMetadata();
      maxValue_ = 1 << store_.getAnyImage().getMetadata().getBitDepth();

      for (int ch = 0; ch < nrCh; ch++) {
         FragmentedMemory lFragmentedMemory = new FragmentedMemory();
         for (int i = 0; i < nrZ; i++) {
            coordsBuilder_ = coordsBuilder_.z(i).channel(ch).time(0).stagePosition(0);
            Coords coords = coordsBuilder_.build();

            // Bypass Micro-Manager api to get access to the ByteBuffers
            DefaultImage image = (DefaultImage) store_.getImage(coords);

            // add the contiguous memory as fragment:
            lFragmentedMemory.add(image.getPixelBuffer());
         }

         // pass data to renderer: (this calls takes a long time!)
         clearVolumeRenderer_.setVolumeDataBuffer(ch,
                 lFragmentedMemory,
                 randomImage.getWidth(),
                 randomImage.getHeight(),
                 nrZ);
         
         // TODO: correct x and y voxel sizes using aspect ratio
         clearVolumeRenderer_.setVoxelSize(ch, metadata.getPixelSizeUm(),
                 metadata.getPixelSizeUm(), summary.getZStepUm());

         // Set various display options:
         Color chColor = ds_.getChannelColors()[ch];
         clearVolumeRenderer_.setTransferFunction(ch, getGradientForColor(chColor));
         float max = (float) ds_.getChannelContrastSettings()[ch].getContrastMaxes()[0] / 
                         (float) maxValue_;
         float min = (float) ds_.getChannelContrastSettings()[ch].getContrastMins()[0] / 
                         (float) maxValue_;
         clearVolumeRenderer_.setTransferFunctionRange(ch, min, max);
         Double[] contrastGammas = ds_.getChannelContrastSettings()[ch].getContrastGammas();
         if (contrastGammas != null) {
            clearVolumeRenderer_.setGamma(ch, contrastGammas[0]);
         }
      }

      clearVolumeRenderer_.requestDisplay();
      cvFrame_.pack();
      open_ = true;

   }

   /**
    * Code that needs to register this instance with various managers and
    * listeners Could have been in the constructor, except that it is unsafe to
    * register our instance before it is completed. Needs to be called right
    * after the constructor.
    */
   public void register() {
      if (!open_) {
         return;
      }

      displayBus_.register(this);    
      studio_.getDisplayManager().addViewer(this);
      // Needed to initialize the histograms
      DefaultEventManager.getInstance().post(new DefaultDisplayAboutToShowEvent(this));
      // Ensure there are histograms for our display.
       studio_.getDisplayManager().updateHistogramDisplays(getDisplayedImages(), this);

      studio_.getDisplayManager().raisedToTop(this);
      // used to reference our instance within the listeners:
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
            System.out.println("Our window lost focus");
         }
      }
      );

      cvFrame_.addWindowListener(new WindowAdapter() {
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

   /**
    * There was an update to the display settings, so update the display
    * of the image to reflect the change.  Only change variables that actually
    * changed
    * @param ds New display settings 
    */
   @Override
   public void setDisplaySettings(DisplaySettings ds) {
      
      for (int ch = 0; ch < store_.getAxisLength(Coords.CHANNEL); ch++ ) {
         if ( !Objects.equals(ds_.getSafeIsVisible(ch, true), 
                 ds.getSafeIsVisible(ch, true)) ) {
            clearVolumeRenderer_.setLayerVisible(ch, ds.getSafeIsVisible(ch, true) );
         }
         if (ds_.getChannelColors()[ch] != ds.getChannelColors()[ch]) {
            Color chColor = ds.getChannelColors()[ch];
            clearVolumeRenderer_.setTransferFunction(ch, getGradientForColor(chColor));
         }
         if (!Objects.equals 
               (ds_.getChannelContrastSettings()[ch].getContrastMaxes()[0], 
               ds.getChannelContrastSettings()[ch].getContrastMaxes()[0])  ||
             !Objects.equals
               (ds_.getChannelContrastSettings()[ch].getContrastMins()[0], 
               ds.getChannelContrastSettings()[ch].getContrastMins()[0]) )  {
            float max = (float) ds.getChannelContrastSettings()[ch].getContrastMaxes()[0] / 
                         (float) maxValue_; 
            float min = (float) ds.getChannelContrastSettings()[ch].getContrastMins()[0] / 
                         (float) maxValue_;
            
            System.out.println("Max was: " + max);
            clearVolumeRenderer_.setTransferFunctionRange(ch, min, max);
         }
         if (!Objects.equals 
               (ds_.getChannelContrastSettings()[ch].getContrastGammas(), 
                ds.getChannelContrastSettings()[ch].getContrastGammas()) )  {
            clearVolumeRenderer_.setGamma(ch,
                    ds.getChannelContrastSettings()[ch].getContrastGammas()[0]);
         }
      }
      
      // replace our reference to the display settings with the new one
      ds_ = ds;
      
      // Needed to update the Inspector window
      displayBus_.post(new NewDisplaySettingsEvent(ds_, this));
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      System.out.println("getDisplaySettings called");
      return ds_;
   }

   @Override
   public void registerForEvents(Object o) {
      System.out.println("Registering for events");
      displayBus_.register(o);
   }

   @Override
   public void unregisterForEvents(Object o) {
      System.out.println("Unregistering for events");
      displayBus_.unregister(o);
   }

   @Override
   public void postEvent(Object o) {
      System.out.println("Posting event on the EventBus");
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
    * Assemble all images that are showing in our volume May need to be updated
    * for multiple time points in the future
    */
   public List<Image> getDisplayedImages() {
      System.out.println("getDisplayed Images called");
      List<Image> imageList = new ArrayList<>();
      final int nrZ = store_.getAxisLength(Coords.Z);
      final int nrCh = store_.getAxisLength(Coords.CHANNEL);
      for (int ch = 0; ch < nrCh; ch++) {
         /*
         // return the complete stack
         for (int i = 0; i < nrZ; i++) {
            coordsBuilder_ = coordsBuilder_.z(i).channel(ch).time(0).stagePosition(0);
            Coords coords = coordsBuilder_.build();
            imageList.add(store_.getImage(coords));
         }
         */
         
         // Only return the middle image
         coordsBuilder_ = coordsBuilder_.z(nrZ / 2).channel(ch).time(0).stagePosition(0);
         Coords coords = coordsBuilder_.build();
         imageList.add(store_.getImage(coords));
         
      }
      return imageList;
   }
   
      /**
    * This method ensures that the Inspector histograms have up-to-date data
    * to display.
    */
   private void updateHistograms() {
      studio_.displays().updateHistogramDisplays(getDisplayedImages(), this);
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

   /**
    * This function is in CV 1.1.2, replace when updating Returns a transfer a
    * simple transfer function that is a gradient from dark transparent to a
    * given color. The transparency of the given color is used.
    *
    * @param pColor color
    * @return 1D transfer function.
    */
   private TransferFunction1D getGradientForColor(Color pColor) {
      final TransferFunction1D lTransfertFunction = new TransferFunction1D();
      lTransfertFunction.addPoint(0, 0, 0, 0);
      float lNormaFactor = (float) (1.0 / 255);
      lTransfertFunction.addPoint(lNormaFactor * pColor.getRed(),
              lNormaFactor * pColor.getGreen(),
              lNormaFactor * pColor.getBlue(),
              lNormaFactor * pColor.getAlpha());
      return lTransfertFunction;
   }

   
   // Following functions are included since we need to be a DisplayWindow, not a DataViewer

   @Override
   public void displayStatusString(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void setMagnification(double d) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void adjustZoom(double d) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public double getMagnification() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void autostretch() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public ImagePlus getImagePlus() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public boolean requestToClose() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void forceClosed() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void toggleFullScreen() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public GraphicsConfiguration getScreenConfig() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public DisplayWindow duplicate() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void toFront() {
      cvFrame_.toFront();
   }

   @Override
   public ImageWindow getImageWindow() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public Window getAsWindow() {
      return cvFrame_.getOwner();
   }

   @Override
   public void setCustomTitle(String string) {
      
   }

}
