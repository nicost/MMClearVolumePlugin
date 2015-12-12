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

import edu.ucsf.valelab.mmclearvolumeplugin.slider.RangeSlider;
import edu.ucsf.valelab.mmclearvolumeplugin.uielements.ScrollBarAnimateIcon;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.event.ChangeEvent;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.display.DataViewer;
import org.micromanager.display.InspectorPanel;

/**
 *
 * @author nico
 */
public final class CVInspectorPanel extends InspectorPanel {
   private Viewer viewer_;
   
   static public final int SLIDERRANGE = 256;
   static public final int SLIDERPIXELWIDTH = 256;
   static public final int XAXIS = 0;
   static public final int YAXIS = 1;
   static public final int ZAXIS = 2;
   
   private RangeSlider xSlider_, ySlider_, zSlider_;
   private JScrollBar timeAxisScrollBar_;
   private JPanel timeSliderPanel_;
   private boolean animating_ = false;
   
   public CVInspectorPanel() {
      super();
   }
   
   @Override
   public boolean getIsValid(DataViewer viewer) {
      if (! (viewer instanceof Viewer) )
         return false;
      viewer_ = (Viewer) viewer;
      
      return true;
      
   }
   
   /**
    * Called whenever we are attached to a new viewer 
    * @param viewer 
    */
   @Override
   public void setDataViewer(DataViewer viewer) {
      // although this should always be a valid viewer, check anyways
      if (! (viewer instanceof Viewer) )
         return;
      viewer_ = (Viewer) viewer;
      
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
      if (viewer_.getDatastore().getAxisLength(Coords.TIME) > 1) {
         int tp = viewer_.getDisplayedImages().get(0).getCoords().getTime();
         int max = viewer_.getDatastore().getAxisLength(Coords.TIME) - 1;
         timeAxisScrollBar_.setValues(tp, 1, 0, max);
         timeAxisScrollBar_.setSize(new Dimension(SLIDERPIXELWIDTH,
              timeAxisScrollBar_.getPreferredSize().height));
         for (AdjustmentListener aj : timeAxisScrollBar_.getAdjustmentListeners()) {
            timeAxisScrollBar_.removeAdjustmentListener(aj);
         }
         timeAxisScrollBar_.addAdjustmentListener((AdjustmentEvent e) -> {
            viewer_.drawVolume(timeAxisScrollBar_.getValue());
         });
         timeSliderPanel_.setVisible(true);
      } else {
         timeSliderPanel_.setVisible(false);
      }
      viewer_.updateHistograms();
   }
   
   private static int clipValToSliderVal (float clipVal) {
      return Math.round ( (clipVal + 1) / 2 * SLIDERRANGE );
   }
   
   @Override
   public void cleanup() {
      // Let's see when this is called to learn what we should be doing
      System.out.println("InspectorPanel.cleanup called in ClearVolume plugin");
   }
   
   public Viewer getViewer() {
      return viewer_;
   }
   
   public void buildPanelGUI() {
      // Decorate the panel here and not in the constructor to avoid
      // calling overridable methods in the constructor
      setLayout(new MigLayout("flowx"));
      JButton resetButton = new JButton("Reset");
      resetButton.setToolTipText("Resets rotation, and centers the complete volume");
      resetButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().resetRotationTranslation();
         }
      });
      add(resetButton, "span 4, split 4");
      
      
      JButton centerButton = new JButton("Center");
      centerButton.setToolTipText("Moves middle of visible part to the center");
      centerButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().center();
         }
      });
      add(centerButton, "");
      
      JButton straightButton = new JButton("Straighten");
      straightButton.setToolTipText("Rotates the object back onto the xyz axes");
      straightButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().straighten();
         }
      });
      add(straightButton, "wrap");
      
      JButton showBoxButton = new JButton("Toggle Box");
      showBoxButton.setToolTipText("Toggle visibility of the wireFrame Box");
      showBoxButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().toggleWireFrameBox();
         }
      });
      add(showBoxButton, "span 4, split 4");
      
      JButton parmsButton = new JButton("Toggle Parameters");
      parmsButton.setToolTipText("Toggle visibility of Parameters");
      parmsButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().toggleParametersListFrame();
         }
      });
      add(parmsButton, "wrap");
      
      /*
      Controls do not seem to do anything....????
      JButton controlsButton = new JButton("Toggle Controls");
      controlsButton.setToolTipText("Toggle visibility of Controls");
      controlsButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().toggleControlPanelDisplay();
         }
      });
      add(controlsButton, "wrap");
      */
      
      addLabel("X");
      xSlider_ = makeSlider(XAXIS);
      add(xSlider_, "");
      
      JButton fullSliderRangeButton = new JButton ("Full");
      fullSliderRangeButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            xSlider_.setValue(0); xSlider_.setUpperValue(SLIDERRANGE);
            ySlider_.setValue(0); ySlider_.setUpperValue(SLIDERRANGE);
            zSlider_.setValue(0); zSlider_.setUpperValue(SLIDERRANGE);
            // TODO: check that this triggers resetting the Viewer's ClipBox
         }
      });
      add(fullSliderRangeButton, "span y 3, wrap");
      
      addLabel("Y");
      ySlider_ = makeSlider(YAXIS);
      add(ySlider_, "wrap");
      
      addLabel("Z");
      zSlider_ = makeSlider(ZAXIS);
      add(zSlider_, "wrap");
      
      timeSliderPanel_ = new JPanel(new MigLayout("flowx"));
      ScrollBarAnimateIcon sbaIcon= new ScrollBarAnimateIcon("t", this);
      timeSliderPanel_.add(sbaIcon);
      timeAxisScrollBar_ = new JScrollBar(JScrollBar.HORIZONTAL, 1, 1, 0, 100);
      timeAxisScrollBar_.setSize(new Dimension(SLIDERPIXELWIDTH,
              timeAxisScrollBar_.getPreferredSize().height));
      timeSliderPanel_.add(timeAxisScrollBar_, "growx");
      add(timeSliderPanel_, "span x 4, growx, wrap");
      
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
      add(label, "");
    }
    
}