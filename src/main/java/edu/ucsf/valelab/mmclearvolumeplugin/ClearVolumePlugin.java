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

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class ClearVolumePlugin implements MenuPlugin, SciJavaPlugin {

   private Studio studio_;
   static public final String VERSION_INFO = "1.5.0";
   static private final String COPYRIGHT_NOTICE = "Copyright by UCSF, 2015";
   static private final String DESCRIPTION = "View Micro-Manager data in the ClearVolume viewer";
   static private final String NAME = "ClearVolume";

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected() {
      Viewer viewer = null;
      try {
         viewer = new Viewer(studio_);
      } catch (Exception ex) {}
      viewer.register();
      studio_.getDisplayManager().addViewer(viewer);
   
      /*
      while (lClearVolumeRenderer.isShowing()) {
         try {
            Thread.sleep(100);
         } catch (InterruptedException ex) {
            Logger.getLogger(ClearVolumePlugin.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
*/

     // lClearVolumeRenderer.close();

   }

   @Override
   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   @Override
   public String getHelpText() {
      return DESCRIPTION;
   }

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public String getVersion() {
      return VERSION_INFO;
   }

}
