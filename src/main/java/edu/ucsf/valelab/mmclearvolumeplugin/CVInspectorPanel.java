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
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import net.miginfocom.swing.MigLayout;
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
      // although this should always be a valid viewwer, check anyways
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
      add(resetButton, "");
      
      JButton showBoxButton = new JButton("Toggle Box");
      showBoxButton.setToolTipText("Toggle visibility of the wireFrame Box");
      showBoxButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().toggleWireFrameBox();
         }
      });
      add(showBoxButton, "");
      
      JButton centerButton = new JButton("Center");
      centerButton.setToolTipText("Moves middle of visible part to the center");
      centerButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().center();
         }
      });
      add(centerButton, "wrap");
      
      
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