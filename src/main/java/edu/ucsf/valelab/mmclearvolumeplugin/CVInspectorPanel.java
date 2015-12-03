


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
   
   @Override
   public void setDataViewer(DataViewer viewer) {
      if (! (viewer instanceof Viewer) )
         return;
      viewer_ = (Viewer) viewer;
      
      // TODO: update range sliders with clipped region of current viewer
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
      resetButton.addActionListener( (ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().resetRotationTranslation();
         }
      });
      add(resetButton, "");
      
      JButton showBoxButton = new JButton("Toggle Box");
      showBoxButton.addActionListener((ActionEvent e) -> {
         if (getViewer() != null) {
            getViewer().toggleWireFrameBox();
         }
      });
      add(showBoxButton, "wrap");
      
      addLabel("X");
      xSlider_ = makeSlider(XAXIS);
      add(xSlider_, "wrap");
      
      addLabel("Y");
      ySlider_ = makeSlider(YAXIS);
      add(ySlider_, "wrap");
      
      addLabel("Z");
      zSlider_ = makeSlider(ZAXIS);
      add(zSlider_, "wrap");

   }
   
    private RangeSlider makeSlider(final int axis) {
      final RangeSlider slider = new RangeSlider();
      slider.setPreferredSize(new Dimension(240,
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
