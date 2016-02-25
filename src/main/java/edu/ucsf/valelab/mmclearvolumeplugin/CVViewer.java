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
import com.google.common.eventbus.Subscribe;
import com.jogamp.newt.NewtFactory;

import com.jogamp.newt.awt.NewtCanvasAWT;

import coremem.fragmented.FragmentedMemory;
import coremem.types.NativeTypeEnum;

import edu.ucsf.valelab.mmclearvolumeplugin.events.CanvasDrawCompleteEvent;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.micromanager.SequenceSettings;

import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.HistogramData;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewHistogramsEvent;



/**
 * Micro-Manager DataViewer that shows 3D stack in the ClearVolume 3D Renderer
 * 
 * @author nico
 */
public class CVViewer implements DataViewer {

   private DisplaySettings displaySettings_;
   private final Studio studio_;
   private Datastore store_;
   private DisplayWindow clonedDisplay_;
   private ClearVolumeRendererInterface clearVolumeRenderer_;
   private String name_;
   private final EventBus displayBus_;
   private final JFrame cvFrame_;
   private Coords.CoordsBuilder coordsBuilder_;
   private boolean open_ = false;
   private int maxValue_;
   private int currentlyShownTimePoint_;
   private final String XLOC = "XLocation";
   private final String YLOC = "YLocation";
   private final Class<?> ourClass_;
   private int imgCounter_ = 0;
   private final Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA,
            Color.PINK, Color.CYAN, Color.YELLOW, Color.ORANGE};
   
   public CVViewer(Studio studio) {
      this(studio, null, null);
   }

   public CVViewer(Studio studio, Datastore store, SequenceSettings sequenceSettings) {
      // first make sure that our app's icon will not change:
      // This call still seems to generate a null pointer exception, at 
      // at jogamp.newt.driver.windows.DisplayDriver.<clinit>(DisplayDriver.java:70)
      // which is ugly but seems harmless
      NewtFactory.setWindowIcons(null);
      
      ourClass_ = this.getClass();
      studio_ = studio;
      UserProfile profile = studio_.getUserProfile();
      if (store == null) {
         clonedDisplay_ = studio_.displays().getCurrentWindow();
         store_ = clonedDisplay_.getDatastore();
         name_ = clonedDisplay_.getName() + "-ClearVolume";
      } else {
         store_ = store;
         clonedDisplay_ = getDisplay(store_);
         if (clonedDisplay_ != null)
            name_ = clonedDisplay_.getName() + "-ClearVolume";

      }
      displayBus_ = new EventBus();
      cvFrame_ = new JFrame();
      int xLoc = profile.getInt(ourClass_, XLOC, 100);
      int yLoc = profile.getInt(ourClass_, YLOC, 100);
      cvFrame_.setLocation(xLoc, yLoc);
      coordsBuilder_ = studio_.data().getCoordsBuilder();
      
      if (store_ == null) {
         ij.IJ.error("No data set open");
         return;
      }
      
      if (name_ == null) {
         name_ = store_.getSummaryMetadata().getPrefix();
      }

      // check if we have all z slices in the first time point
      // if not, rely on the onNewImage function to initialize the renderer
      Coords zStackCoords = studio_.data().getCoordsBuilder().time(0).build();
      final int nrImages = store_.getImagesMatching(zStackCoords).size();
      currentlyShownTimePoint_ = -1; // set to make sure the first volume will be drawn
      Coords intendedDimensions = store_.getSummaryMetadata().getIntendedDimensions();
      if (nrImages == intendedDimensions.getChannel() * intendedDimensions.getZ()) {
         initializeRenderer(0);
      }


   }
   
   private void initializeRenderer(int timePoint) {

      if (clonedDisplay_ == null) {
         // There could be a display attached to the store now
         clonedDisplay_ = getDisplay(store_);
      }
      
      if (clonedDisplay_ != null) { 
         name_ = clonedDisplay_.getName() + "-ClearVolume";
         displaySettings_ = clonedDisplay_.getDisplaySettings().copy().build();
      } else {
         displaySettings_ = studio_.displays().getStandardDisplaySettings();
      }
      
      maxValue_ = 1 << store_.getAnyImage().getMetadata().getBitDepth();
      
      final int nrCh = store_.getAxisLength(Coords.CHANNEL);
      
      // clean up ContrastSettings
      DisplaySettings.ContrastSettings[] contrastSettings
              = displaySettings_.getChannelContrastSettings();
      if (contrastSettings == null || contrastSettings.length != nrCh) {
         contrastSettings = new DisplaySettings.ContrastSettings[nrCh];
         for (int ch = 0; ch < store_.getAxisLength(Coords.CHANNEL); ch++) {
            contrastSettings[ch] = studio_.displays().
                    getContrastSettings(0, maxValue_, 1.0, true);
         }
      } else {
         for (int ch = 0; ch < contrastSettings.length; ch++) {
            if (contrastSettings[ch] == null || 
                    contrastSettings[ch].getContrastMaxes() == null || 
                    contrastSettings[ch].getContrastMaxes()[0] == null ||
                    contrastSettings[ch].getContrastMaxes()[0] == 0 ||
                    contrastSettings[ch].getContrastMins() == null ||
                    contrastSettings[ch].getContrastMins()[0] == null) {
               contrastSettings[ch] = studio_.displays().
                       getContrastSettings(0, maxValue_, 1.0, true);
            }
            if (contrastSettings[ch].getContrastGammas() == null || 
                    contrastSettings[ch].getContrastGammas()[0] == null) {
               contrastSettings[ch] = studio_.displays().getContrastSettings(
                       contrastSettings[ch].getContrastMins()[0],
                       contrastSettings[ch].getContrastMaxes()[0], 
                       1.0, 
                       contrastSettings[ch].getIsVisible());
            }
            if (contrastSettings[ch].getIsVisible() == null) {
               contrastSettings[ch] = studio_.displays().getContrastSettings(
                       contrastSettings[ch].getContrastMins()[0],
                       contrastSettings[ch].getContrastMaxes()[0],
                       contrastSettings[ch].getContrastGammas()[0], 
                       true);
            }
         }
      }
      
      // and clean up Colors
      Color[] channelColors = displaySettings_.getChannelColors();
      if (channelColors == null || channelColors.length != nrCh) {
         channelColors = new Color[nrCh];
         System.arraycopy(colors, 0, channelColors, 0, nrCh);
      } else {
         for (int ch = 0; ch < nrCh; ch++) {
            if (channelColors[ch] == null) {
               channelColors[ch] = colors[ch];
            }
         }
      }
      
      displaySettings_ = displaySettings_.copy().
              channelColors(channelColors).
              channelContrastSettings(contrastSettings).
              build();

      Image randomImage = store_.getAnyImage();
            // creates renderer:
      NativeTypeEnum nte = NativeTypeEnum.UnsignedShort;
      if (randomImage.getBytesPerPixel() == 1) {
         nte = NativeTypeEnum.UnsignedByte;
      }
      
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

             
      drawVolume(timePoint);
      displayBus_.post(new CanvasDrawCompleteEvent());
      
      currentlyShownTimePoint_ = timePoint;

      clearVolumeRenderer_.setVisible(true);
      clearVolumeRenderer_.requestDisplay();
      clearVolumeRenderer_.toggleControlPanelDisplay();
      cvFrame_.pack();
      studio_.getDisplayManager().raisedToTop(this);

      open_ = true;
   }

   /**
    * Code that needs to register this instance with various managers and
    * listeners. Could have been in the constructor, except that it is unsafe to
    * register our instance before it is completed. Needs to be called right
    * after the constructor.
    */
   public void register() {
     // if (!open_) {
      //   return;
      //}

      displayBus_.register(this);
      store_.registerForEvents(this);
      studio_.getDisplayManager().addViewer(this);
                  
      // Ensure there are histograms for our display.
      if (open_)
         updateHistograms();

      
      // used to reference our instance within the listeners:
      final CVViewer ourViewer = this;

      // the WindowFocusListener should go into the WindowAdapter, but there it
      // does not work, so add both a WindowListener and WindowFocusListener
      cvFrame_.addWindowFocusListener(new WindowFocusListener() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            studio_.getDisplayManager().raisedToTop(ourViewer);
         }

         @Override
         public void windowLostFocus(WindowEvent e) {
            // nothing to do
         }
      }
      );

      cvFrame_.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            UserProfile profile = studio_.profile();
            profile.setInt(ourClass_, XLOC, cvFrame_.getX());
            profile.setInt(ourClass_, YLOC, cvFrame_.getY());
            studio_.getDisplayManager().removeViewer(ourViewer);
            // studio_.events().post(new DisplayDestroyedEvent(ourViewer) {});
            displayBus_.unregister(ourViewer);
            store_.unregisterForEvents(ourViewer);
            clearVolumeRenderer_.close();
            cvFrame_.dispose();
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
         if ( !Objects.equals(displaySettings_.getSafeIsVisible(ch, true), 
                 ds.getSafeIsVisible(ch, true)) ) {
            clearVolumeRenderer_.setLayerVisible(ch, ds.getSafeIsVisible(ch, true) );
         }
         if (displaySettings_.getChannelColors()[ch] != ds.getChannelColors()[ch]) {
            Color chColor = ds.getChannelColors()[ch];
            clearVolumeRenderer_.setTransferFunction(ch, getGradientForColor(chColor));
         }
         if (!Objects.equals 
               (displaySettings_.getChannelContrastSettings()[ch].getContrastMaxes()[0], 
               ds.getChannelContrastSettings()[ch].getContrastMaxes()[0])  ||
             !Objects.equals
               (displaySettings_.getChannelContrastSettings()[ch].getContrastMins()[0], 
               ds.getChannelContrastSettings()[ch].getContrastMins()[0]) )  {
            float max = (float) ds.getChannelContrastSettings()[ch].getContrastMaxes()[0] / 
                         (float) maxValue_; 
            float min = (float) ds.getChannelContrastSettings()[ch].getContrastMins()[0] / 
                         (float) maxValue_;
            
            // System.out.println("Max was: " + max);
            clearVolumeRenderer_.setTransferFunctionRange(ch, min, max);
         }
         if (!Objects.equals 
               (displaySettings_.getChannelContrastSettings()[ch].getContrastGammas(), 
                ds.getChannelContrastSettings()[ch].getContrastGammas()) )  {
            clearVolumeRenderer_.setGamma(ch,
                    ds.getChannelContrastSettings()[ch].getContrastGammas()[0]);
         }
      }
      
      // Autostretch if set
      if (! Objects.equals( ds.getShouldAutostretch(), 
              displaySettings_.getShouldAutostretch()) || 
          ! Objects.equals( ds.getExtremaPercentage(), 
                  displaySettings_.getExtremaPercentage()) ) {
         if (ds.getShouldAutostretch()) {
            autostretch();
            drawVolume(currentlyShownTimePoint_);
            displayBus_.post(new CanvasDrawCompleteEvent());
         }
      }
      
      // replace our reference to the display settings with the new one
      displaySettings_ = ds;
      
      // Needed to update the Inspector window
      displayBus_.post(new NewDisplaySettingsEvent(displaySettings_, this));
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   @Override
   public void registerForEvents(Object o) {
      System.out.println("Registering for event: " + o.toString());
      displayBus_.register(o);
   }

   @Override
   public void unregisterForEvents(Object o) {
      System.out.println("Unregistering for event: " + o.toString());
      displayBus_.unregister(o);
   }

   @Override
   public void postEvent(Object o) {
      // System.out.println("Posting event on the EventBus");
      displayBus_.post(o);
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }
   
   @Override
   public void setDisplayedImageTo(Coords coords) {
      // ClearVolume uses commands and keystrokes that work on a given channel
      // Make sure that the channel that ClearVolume works on is synced with the
      // Channel slider position in the ClearVolume panel in the Image Inspector
       clearVolumeRenderer_.setCurrentRenderLayer(coords.getChannel());
       drawVolume(coords.getTime());
       displayBus_.post(new CanvasDrawCompleteEvent());
   }

   @Override
   /**
    * Assemble all images that are showing in our volume May need to be updated
    * for multiple time points in the future
    */
   public List<Image> getDisplayedImages() {
      // System.out.println("getDisplayed Images called");
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
    * This method ensures that the Inspector histograms have up-to-date data to
    * display.
    */
   public void updateHistograms() {
      // Needed to initialize the histograms
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

   public final void drawVolume(final int timePoint) {
      //if (timePoint == currentlyShownTimePoint_)
      //   return; // nothing to do, already showing requested timepoint
      // create fragmented memory for each stack that needs sending to CV:
      Image randomImage = store_.getAnyImage();
      final Metadata metadata = randomImage.getMetadata();
      final SummaryMetadata summary = store_.getSummaryMetadata();
      final int nrZ = store_.getAxisLength(Coords.Z);
      final int nrCh = store_.getAxisLength(Coords.CHANNEL);

      // long startTime = System.currentTimeMillis();
      clearVolumeRenderer_.setVolumeDataUpdateAllowed(false);
      if (displaySettings_.getShouldAutostretch())
         autostretch();
      for (int ch = 0; ch < nrCh; ch++) {
         FragmentedMemory fragmentedMemory = new FragmentedMemory();
         for (int i = 0; i < nrZ; i++) {
            coordsBuilder_ = coordsBuilder_.z(i).channel(ch).time(timePoint).stagePosition(0);
            Coords coords = coordsBuilder_.build();

            // Bypass Micro-Manager api to get access to the ByteBuffers
            DefaultImage image = (DefaultImage) store_.getImage(coords);

            // add the contiguous memory as fragment:
            if (image != null)
               fragmentedMemory.add(image.getPixelBuffer());
         }

         // TODO: correct x and y voxel sizes using aspect ratio
         double pixelSizeUm = metadata.getPixelSizeUm();
         if (pixelSizeUm == 0.0)
            pixelSizeUm = 1.0;
         double stepSizeUm = summary.getZStepUm();
         if (stepSizeUm == 0.0)
            stepSizeUm = 1.0;
         
         // pass data to renderer: (this call takes a long time!)
         clearVolumeRenderer_.setVolumeDataBuffer(0, 
                 TimeUnit.SECONDS, 
                 ch,
                 fragmentedMemory,
                 randomImage.getWidth(),
                 randomImage.getHeight(),
                 nrZ, 
                 pixelSizeUm,
                 pixelSizeUm, 
                 stepSizeUm);


         //clearVolumeRenderer_.setVoxelSize(ch, pixelSizeUm,
         //        pixelSizeUm, stepSizeUm);

         // Set various display options:
         // HACK: on occassion we get null colors, correct that problem here
         Color chColor = displaySettings_.getChannelColors()[ch];
         if (chColor == null) {
            chColor = colors[ch];
            Color[] chColors = displaySettings_.getChannelColors();
            chColors[ch] = chColor;
            displaySettings_ = displaySettings_.copy().channelColors(chColors).build();
         }
         clearVolumeRenderer_.setTransferFunction(ch, getGradientForColor(chColor));
         try {
            float max = (float) displaySettings_.getChannelContrastSettings()[ch].getContrastMaxes()[0]
                    / (float) maxValue_;
            float min = (float) displaySettings_.getChannelContrastSettings()[ch].getContrastMins()[0]
                    / (float) maxValue_;
            clearVolumeRenderer_.setTransferFunctionRange(ch, min, max);
            Double[] contrastGammas = displaySettings_.getChannelContrastSettings()[ch].getContrastGammas();
            if (contrastGammas != null) {
               clearVolumeRenderer_.setGamma(ch, contrastGammas[0]);
            }
         } catch (NullPointerException ex) {
            studio_.logs().showError(ex);
         }
         // System.out.println("Finished assembling ch: " + ch + " after " + (System.currentTimeMillis() - startTime) + " ms");
      }
      currentlyShownTimePoint_ = timePoint;
      clearVolumeRenderer_.setVolumeDataUpdateAllowed(true);
      // System.out.println("Start finishing after " + (System.currentTimeMillis() - startTime) + " ms");
      
      // We should be waiting here for the renderer to finish, however that call times out!
      // clearVolumeRenderer_.waitToFinishAllDataBufferCopy(0, TimeUnit.SECONDS);
      
      // System.out.println("Ended finishing after " + (System.currentTimeMillis() - startTime) + " ms");
     
   }

   /*
    * Series of functions that are merely pass through to the underlying 
    * clearVolumeRenderer
   */
   
   /**
    * I would have liked an on/off control here, but the ClearVolume api
    * only has a toggle function
    */
   public void toggleWireFrameBox() {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleBoxDisplay();
      }
   }
   
   public void toggleControlPanelDisplay() {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleControlPanelDisplay();
      }
   }
   
   public void toggleParametersListFrame() {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleParametersListFrame();
      }
   }
   
   
   public void resetRotationTranslation () {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.resetRotationTranslation();
         float x = clearVolumeRenderer_.getTranslationX();
         float y = clearVolumeRenderer_.getTranslationY();
         float z = clearVolumeRenderer_.getTranslationZ();
         studio_.logs().logMessage("Rotation now is: " + x + ", " + y + ", " + z);
      }
   }
   
   /**
    * Centers the visible part of the ClipBox
    * It seems that 0, 0 is in the center of the full volume, and 
    * that -1 and 1 are at the edges of the volume
    */
   public void center() {
      if (clearVolumeRenderer_ != null) {
         float[] clipBox = clearVolumeRenderer_.getClipBox();
         clearVolumeRenderer_.setTranslationX( -(clipBox[1] + clipBox[0]) / 2.0f);
         clearVolumeRenderer_.setTranslationY( -(clipBox[3] + clipBox[2]) / 2.0f);
         // do not change TRanslationZ, since that mainly changes how close we are 
         // to the object, not really the rotation point
         // clearVolumeRenderer_.setTranslationZ( -5);
      }
   }
   
   /**
    * Resets the rotation so that the object ligns up with the xyz axis.
    */
   public void straighten() {
      if (clearVolumeRenderer_ != null) {
         // Convoluted way to reset the rotation
         // I probably should use rotationControllers...
         float x = clearVolumeRenderer_.getTranslationX();
         float y = clearVolumeRenderer_.getTranslationY();
         float z = clearVolumeRenderer_.getTranslationZ();
         clearVolumeRenderer_.resetRotationTranslation();
         clearVolumeRenderer_.setTranslationX(x);
         clearVolumeRenderer_.setTranslationY(y);
         clearVolumeRenderer_.setTranslationZ(z);
      }
   }
   
   /**
    * Print statements to learn about the renderer.  Should be removed before release
    */
   private void printRendererInfo() {
      float x = clearVolumeRenderer_.getTranslationX();
      float y = clearVolumeRenderer_.getTranslationY();
      float z = clearVolumeRenderer_.getTranslationZ();
      studio_.logs().logMessage("Translation now is: " + x + ", " + y + ", " + z);
      String clipBoxString = "Clipbox: ";
      for (int i = 0; i < 6; i++) {
         clipBoxString += ", " + clearVolumeRenderer_.getClipBox()[i];
      }
      studio_.logs().logMessage(clipBoxString);
   }
   
   
   /**
    * It appears that the clip range in the ClearVolume Renderer goes from 
    * -1 to 1
    * @param axis desired axies (X=0, Y=1, Z=2, defined in ClearVolumePlugin)
    * @param minVal minimum value form the slider
    * @param maxVal maxmimum value form the slider
    */
   public void setClip(int axis, int minVal, int maxVal) {
      if (clearVolumeRenderer_ != null) {
         float min = ( (float) minVal / (float) CVInspectorPanel.SLIDERRANGE ) * 2 - 1;
         float max = ( (float) maxVal / (float) CVInspectorPanel.SLIDERRANGE ) * 2 - 1;
         float[] clipBox = clearVolumeRenderer_.getClipBox();
         switch (axis) {
                 case CVInspectorPanel.XAXIS : 
                    clipBox[0] = min;  clipBox[1] = max;
                    break;
                 case CVInspectorPanel.YAXIS :
                    clipBox[2] = min;  clipBox[3] = max;
                    break;
                  case CVInspectorPanel.ZAXIS :
                    clipBox[4] = min;  clipBox[5] = max;
                    break;
         }
         clearVolumeRenderer_.setClipBox(clipBox);         
      }
   }
   
   public float[] getClipBox() {
      if (clearVolumeRenderer_ != null)
         return clearVolumeRenderer_.getClipBox();
      return null;
   }

   /**
    * Find the first DisplayWindow attached to this datastore
    *
    * @param store first DisplayWindow or null if not found
    */
   private DisplayWindow getDisplay(Datastore store) {
      List<DisplayWindow> dataWindows = studio_.displays().getAllImageWindows();
      for (DisplayWindow dv : dataWindows) {
         if (store == dv.getDatastore()) {
            return dv;
         }
      }
      return null;
   }
   
   // Following functions are included since we need to be a DisplayWindow, not a DataViewer
/*
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
      // Not sure if this should be implemented...
   }

   @Override
   public double getMagnification() {
      // Not sure if this should be implemented
      return 1.0;
   }

  

   @Override
   public ImagePlus getImagePlus() {
      throw new UnsupportedOperationException("What ImagePlus do you want?"); //To change body of generated methods, choose Tools | Templates.
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
   if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleFullScreen();
      }
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
   
    @Override
    */
   public void autostretch() {
      Coords baseCoords = getDisplayedImages().get(0).getCoords();
      Double extremaPercentage = displaySettings_.getExtremaPercentage();
      if (extremaPercentage == null) {
         extremaPercentage = 0.0;
      }
      DisplaySettings.DisplaySettingsBuilder builder = 
              displaySettings_.copy();
      for (int ch = 0; ch < store_.getAxisLength(Coords.CHANNEL); ++ch) {
         Image image = store_.getImage(baseCoords.copy().channel(ch).build());
         if (image != null) {
            int numComponents = image.getNumComponents();
            Integer[] mins = new Integer[numComponents];
            Integer[] maxes = new Integer[numComponents];
            Double[] gammas = new Double[numComponents];
            final ArrayList<HistogramData> datas = new ArrayList(image.getNumComponents());
            for (int j = 0; j < image.getNumComponents(); ++j) {
               gammas[j] = displaySettings_.getSafeContrastGamma(ch, j, 1.0);
               HistogramData data = studio_.displays().calculateHistogram(
                       getDisplayedImages().get(ch),
                       j,
                       store_.getAnyImage().getMetadata().getBitDepth(),
                       store_.getAnyImage().getMetadata().getBitDepth(),
                       extremaPercentage);
               mins[j] = data.getMinIgnoringOutliers();
               maxes[j] = data.getMaxIgnoringOutliers();
               datas.add(j, data);
            }
            displaySettings_ = builder.safeUpdateContrastSettings(
                    studio_.displays().getContrastSettings(
                            mins, maxes, gammas, true), ch).build();
            final int tmpCh = ch;
            if (SwingUtilities.isEventDispatchThread()) {
               postEvent(new NewHistogramsEvent(tmpCh, datas));
            } else {
               SwingUtilities.invokeLater(() -> {
                  postEvent(new NewHistogramsEvent(tmpCh, datas));
               });
            }
         }
      }
       postEvent(new NewDisplaySettingsEvent(displaySettings_, this));
   }
   
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      Coords coords = event.getCoords();
      int t = coords.getTime();
      if (t != currentlyShownTimePoint_) { // we are not yes showing this time point
         // check if we have all z planes, if so draw the volume
         /** The following code is exact, but very slow
         Coords zStackCoords = studio_.data().getCoordsBuilder().time(t).build();
         final int nrImages = store_.getImagesMatching(zStackCoords).size();
         */
         Coords intendedDimensions = store_.getSummaryMetadata().getIntendedDimensions();
         // instead, keep our own counter, this assumes that all time points arrive in order
         // TODO: work around this by keeping a list of counters
         imgCounter_++;
         if (imgCounter_ == intendedDimensions.getChannel() * intendedDimensions.getZ()) {
            if (!open_) {
               initializeRenderer(0);
            } else {
               // we are complete, so now draw the image
               setDisplayedImageTo(coords);
            }
            imgCounter_ = 0;
         }
      }
   }
}
