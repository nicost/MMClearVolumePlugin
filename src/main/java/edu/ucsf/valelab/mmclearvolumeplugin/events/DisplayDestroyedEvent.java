
package edu.ucsf.valelab.mmclearvolumeplugin.events;

import org.micromanager.display.DisplayWindow;

/**
 *
 * @author nico
 */
public class DisplayDestroyedEvent implements org.micromanager.display.DisplayDestroyedEvent {
   private final DisplayWindow dw_;
   
   public DisplayDestroyedEvent(DisplayWindow dw) {
      dw_ = dw;
   }
   
   @Override
   public DisplayWindow getDisplay() {
      return dw_;
   }
   
}
