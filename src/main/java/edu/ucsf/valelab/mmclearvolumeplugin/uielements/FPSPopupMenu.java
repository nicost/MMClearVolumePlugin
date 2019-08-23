///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package edu.ucsf.valelab.mmclearvolumeplugin.uielements;


import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Hashtable; // needed because of JSlider

import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;

import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;

/**
 * This class provides an interface for selecting the FPS at which to display
 * animations for the ScrollerPanel.
 */
public class FPSPopupMenu extends JPopupMenu {

   private static final long serialVersionUID = 4641161742032383931L;
   /**
    * Implementation adapted from
    * http://www.onjava.com/pub/a/onjava/excerpt/swing_14/index6.html?page=2
    */
   private class FPSSlider extends JSlider implements MenuElement {

      private static final long serialVersionUID = 5545890245575026993L;
      
      public FPSSlider() {
         super(1, 30);
      }
      
      public void initialize() {
         setBorder(new TitledBorder("Animation FPS"));
         setMajorTickSpacing(20);
         // Set up custom labels, because otherwise we end up with ticks at
         // 21, 41, etc.
         Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
         labels.put(1, new JLabel("1"));
         for (int i = 5; i < 31; i += 5) {
            labels.put(i, new JLabel(String.valueOf(i)));
         }
         setLabelTable(labels);
         setPaintLabels(true);
      }
      
      @Override
      public MenuElement[] getSubElements() {
         return new MenuElement[0];
      }

      @Override
      public Component getComponent() {
         return this;
      }

      /**
       * Forward mouse motion events to our slider. Well actually we forward
       * *all* mouse events to our slider, but it seems to work okay.
       */
      @Override
      public void processMouseEvent(MouseEvent e, MenuElement[] path,
            MenuSelectionManager manager) {
         super.processMouseMotionEvent(e);
      }
      @Override
      public void processKeyEvent(KeyEvent e, MenuElement[] path,
            MenuSelectionManager manager) {}
      @Override
      public void menuSelectionChanged(boolean isIncluded) {}
   }

   public FPSPopupMenu(final DataViewer display, double initialVal) {
      final FPSSlider slider = new FPSSlider();
      slider.initialize();
      final JTextField field = new JTextField(3);
      slider.setValue((int) initialVal);
      field.setText(Integer.toString((int) initialVal));
      slider.addChangeListener((ChangeEvent event) -> {
         field.setText(Integer.toString(slider.getValue()));
         DisplaySettings settings = display.getDisplaySettings();
         settings = settings.copy().animationFPS((double) slider.getValue()).build();
         display.setDisplaySettings(settings);
      });
      super.add(slider);

      field.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent event) {
            try {
               int newVal = Integer.parseInt(field.getText());
               slider.setValue(newVal);
               slider.repaint();
               DisplaySettings settings = display.getDisplaySettings();
               settings = settings.copy().animationFPS((double) newVal).build();
               display.setDisplaySettings(settings);
            }
            catch (NumberFormatException e) {
               // Ignore it
            }
         }
      });
      super.add(field);
   }
}
