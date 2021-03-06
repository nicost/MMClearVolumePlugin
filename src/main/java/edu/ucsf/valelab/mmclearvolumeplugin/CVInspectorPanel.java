/**
 * Binding to ClearVolume 3D viewer View Micro-Manager datasets in 3D
 *
 * AUTHOR: Nico Stuurman COPYRIGHT: Regents of the University of California,
 * 2015 
 * LICENSE: This file is distributed under the BSD license. License text is
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

import edu.ucsf.valelab.mmclearvolumeplugin.recorder.CVSnapshot;
import com.google.common.eventbus.Subscribe;
import edu.ucsf.valelab.mmclearvolumeplugin.recorder.CVVideoRecorder;

import edu.ucsf.valelab.mmclearvolumeplugin.slider.RangeSlider;
import edu.ucsf.valelab.mmclearvolumeplugin.uielements.ScrollerPanel;
import java.awt.Color;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.events.AcquisitionStartedEvent;

/**
 *
 * @author nico
 */
public final class CVInspectorPanel extends InspectorPanel {

   private static final long serialVersionUID = 2048658391111857080L;

   private CVViewer viewer_;
   
   static public final int SLIDERRANGE = 256;
   static public final int SLIDERPIXELWIDTH = 296;
   static public final int XAXIS = 0;
   static public final int YAXIS = 1;
   static public final int ZAXIS = 2;
   
   private final String USE_FOR_ALL = "Use for all";
   
   private final Studio studio_;
   private RangeSlider xSlider_, ySlider_, zSlider_;
   private ScrollerPanel sp_;
   private boolean animating_ = false;
   private final AtomicBoolean attachToNew_ = new AtomicBoolean(false);
   private final CVVideoRecorder recorder_;
   
   public CVInspectorPanel(Studio studio) {
      super();
      
      studio_ = studio;
      super.setLayout(new MigLayout("flowx"));
      
      final JCheckBox attachToNewCheckBox = new JCheckBox ("Use for all");
      attachToNewCheckBox.setToolTipText("Open all new data in ClearVolume");
      attachToNew_.set(studio.profile().getBoolean(this.getClass(), 
              USE_FOR_ALL, false));
      attachToNewCheckBox.setSelected(attachToNew_.get());
      attachToNewCheckBox.addActionListener((ActionEvent e) -> {
         attachToNew_.set(attachToNewCheckBox.isSelected());
         studio.profile().setBoolean(this.getClass(), USE_FOR_ALL, 
                 attachToNewCheckBox.isSelected());
      });
      super.add(attachToNewCheckBox, "span 4, wrap");
      
      super.add(new JSeparator(SwingConstants.HORIZONTAL), "span 4, growx, pushx, wrap");
           
      JButton resetButton = new JButton("Reset");
      resetButton.setToolTipText("Resets rotation, and centers the complete volume");
      resetButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().resetRotationTranslation();
         }
      });
      super.add(resetButton, "span 4, split 4, center");
      
      
      JButton centerButton = new JButton("Center");
      centerButton.setToolTipText("Moves middle of visible part to the center");
      centerButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().center();
         }
      });
      super.add(centerButton, "");
      
      JButton straightButton = new JButton("Straighten");
      straightButton.setToolTipText("Rotates the object back onto the xyz axes");
      straightButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().straighten();
         }
      });
      super.add(straightButton);
      
      JButton showBoxButton = new JButton("Toggle Box");
      showBoxButton.setToolTipText("Toggle visibility of the wireFrame Box");
      showBoxButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().toggleWireFrameBox();
         }
      });
      super.add(showBoxButton, "wrap");
     
      addLabel("X");
      xSlider_ = makeSlider(XAXIS);
      super.add(xSlider_, "");
      
      JButton fullSliderRangeButton = new JButton ("Full");
      fullSliderRangeButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            xSlider_.setValue(0); xSlider_.setUpperValue(SLIDERRANGE);
            ySlider_.setValue(0); ySlider_.setUpperValue(SLIDERRANGE);
            zSlider_.setValue(0); zSlider_.setUpperValue(SLIDERRANGE);
            // TODO: check that this triggers resetting the Viewer's ClipBox
         }
      });
      super.add(fullSliderRangeButton, "span y 3, wrap");
      
      addLabel("Y");
      ySlider_ = makeSlider(YAXIS);
      super.add(ySlider_, "wrap");
      
      addLabel("Z");
      zSlider_ = makeSlider(ZAXIS);
      super.add(zSlider_, "wrap");
      
      if (viewer_ != null) {
         sp_ = new ScrollerPanel(studio_, viewer_.getDatastore(), viewer_);
         super.add(sp_, "span x 4, growx, wrap");
      }
      
      final JButton snapButton = new JButton("Snap 3D"); 
      snapButton.setToolTipText("Snapshot of 3D viewer");
      snapButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            CVSnapshot snapper = new CVSnapshot();
            getViewer().attachRecorder(snapper);
         }
      });
      super.add(snapButton, "span 4, split 2, center");
      
      final JButton recordButton = new JButton("Record"); 
      recordButton.setToolTipText("Record 3D viewer");
      recordButton.setContentAreaFilled(false);
      recordButton.setOpaque(true);
      recordButton.setBackground(Color.green);
      recorder_ = new CVVideoRecorder();
      recordButton.addActionListener((ActionEvent e) -> {
         if (recordButton.getText().equals("Record")) {
            snapButton.setEnabled(false);
            recordButton.setBackground(Color.red);
            recordButton.setText("Stop Recording");
            if (getViewer() != null) {
               getViewer().attachRecorder(recorder_);
            }
         } else {
            snapButton.setEnabled(true);
            recordButton.setText("Record");
            recordButton.setBackground(Color.green);
            recorder_.stopRecording();
         }
      });
      super.add(recordButton, "wrap");
      
   }
   
   @Override
   public boolean getIsValid(DataViewer viewer) {
      if (! (viewer instanceof CVViewer) )
         return false;
      viewer_ = (CVViewer) viewer;
      
      return true;
      
   }
   
   /**
    * Called whenever we are attached to a new viewer 
    * @param viewer 
    */
   @Override
   public void setDataViewer(DataViewer viewer) {
      // although this should always be a valid viewer, check anyways
      if (! (viewer instanceof CVViewer) )
         return;
      
      // TODO: do we need to unregister the old viewer???
      
      viewer_ = (CVViewer) viewer;
      
      // update range sliders with clipped region of current viewer
      float[] clipBox = viewer_.getClipBox();
      if (clipBox != null) {
         xSlider_.setValue(clipValToSliderVal(clipBox[0]));
         xSlider_.setUpperValue(clipValToSliderVal(clipBox[1]));
         ySlider_.setValue(clipValToSliderVal(clipBox[2]));
         ySlider_.setUpperValue(clipValToSliderVal(clipBox[3]));
         zSlider_.setValue(clipValToSliderVal(clipBox[4]));
         zSlider_.setUpperValue(clipValToSliderVal(clipBox[5]));
      }
      Coords intendedDimensions = viewer_.getDatastore().getSummaryMetadata().getIntendedDimensions();
      if (intendedDimensions != null) {
         if (sp_ != null) {
            sp_.stopUpdateThread();
            this.remove(sp_);
         }
         sp_ = new ScrollerPanel(studio_, viewer_.getDatastore(), viewer_);
         add(sp_, "span x 4, growx, wrap");
      } 
      super.revalidate();
      super.repaint();
      viewer_.updateHistograms();
      viewer_.registerForEvents(this);
   }
   
   @Subscribe
   public void OnNewDisplaySettinsgEvent(NewDisplaySettingsEvent ndse) {
      if (viewer_.equals(ndse.getDisplay())) {
         DisplaySettings displaySettings = ndse.getDisplaySettings();
         recorder_.setTargetFrameRate(displaySettings.getAnimationFPS());
      }
   }
   
   private static int clipValToSliderVal (float clipVal) {
      return Math.round ( (clipVal + 1) / 2 * SLIDERRANGE );
   }
   
   @Override
   public void cleanup() {
      // Let's see when this is called to learn what we should be doing
      System.out.println("InspectorPanel.cleanup called in ClearVolume plugin");
   }
   
   public CVViewer getViewer() {
      return viewer_;
   }
     
   public void toggleAnimation() {
      animating_ = !animating_;
      toggleAnimation(animating_);
   }
   
   private void toggleAnimation(boolean start) {
      if (start) {
         
      }
   }
   
   private RangeSlider makeSlider(final int axis) {
      final RangeSlider slider = new RangeSlider();
      slider.setPreferredSize(new Dimension(SLIDERPIXELWIDTH,
              slider.getPreferredSize().height));
      slider.setMinimum(0);
      slider.setMaximum(SLIDERRANGE);
      slider.setValue(0);
      slider.setUpperValue(SLIDERRANGE);
      slider.addChangeListener((ChangeEvent e) -> {
         if (getViewer() != null) {
            getViewer().setClip(axis, slider.getValue(), slider.getUpperValue());
         }
      });
      return slider;
   }
    
    private void addLabel(String labelText) {
      JLabel label = new JLabel(labelText);
      add(label, "span 3, split 2");
    }
    
    
    @Subscribe
    public void onAcquisitionStartedEvent(AcquisitionStartedEvent ase) {
       if (attachToNew_.get()) {
          try {
             CVViewer viewer = new CVViewer(studio_, ase.getDatastore());
             viewer.register();
          } catch (Exception ex) {
             studio_.logs().logError(ex);
          }
       }
    }
   

}
