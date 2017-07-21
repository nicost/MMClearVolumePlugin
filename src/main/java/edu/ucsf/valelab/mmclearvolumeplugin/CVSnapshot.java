
package edu.ucsf.valelab.mmclearvolumeplugin;

import clearvolume.renderer.cleargl.recorder.VideoRecorderBase;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAutoDrawable;
import ij.ImagePlus;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author nico
 * 
 * Use the recently added ClearVolume VideRecorder interface to grab a single 
 * image and convert it into a Java AWT RGBA image.  Display as an ImageJ RGB image.
 *  
 */
public class CVSnapshot extends VideoRecorderBase {
   


   @Override
   public boolean screenshot(GLAutoDrawable pGLAutoDrawable,
           boolean pAsynchronous) {
      if (!super.screenshot(pGLAutoDrawable, pAsynchronous)) {
         return false;
      }
      
      super.toggleActive();

      new Thread  (new Runnable() {
         @Override
         public void run() {
            
      final int lWidth = pGLAutoDrawable.getSurfaceWidth();
      final int lHeight = pGLAutoDrawable.getSurfaceHeight();

      ByteBuffer lByteBuffer
              = ByteBuffer.allocateDirect(lWidth * lHeight * 3)
              .order(ByteOrder.nativeOrder());

      final GL lGL = pGLAutoDrawable.getGL();

      lGL.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
      lGL.glReadPixels(0, // GLint x
              0, // GLint y
              lWidth, // GLsizei width
              lHeight, // GLsizei height
              GL.GL_RGB, // GLenum format
              GL.GL_UNSIGNED_BYTE, // GLenum type
              lByteBuffer); // GLvoid *pixels
      mLastImageTimePoint = System.nanoTime();
      
      BufferedImage lBufferedImage = getBufferedImage(lWidth, lHeight, lByteBuffer);

      ImagePlus ip = new ImagePlus("3D", lBufferedImage);
      ip.show();
         };
      }).start();

      return true;
   }
   
   private BufferedImage getBufferedImage(
                                 int pWidth,
                                 int pHeight,
                                 ByteBuffer pByteBuffer)
  {
    try
    {
      int[] lPixelInts = new int[pWidth * pHeight];
      

      // Convert RGB bytes to ARGB ints with no transparency. Flip image
      // vertically by reading the
      // rows of pixels in the byte buffer in reverse - (0,0) is at bottom
      // left
      // in
      // OpenGL.

      int p = pWidth * pHeight * 3; // Points to first byte (red) in each row
      int q; // Index into ByteBuffer
      int i = 0; // Index into target int[]
      final int w3 = pWidth * 3; // Number of bytes in each row

      for (int row = 0; row < pHeight; row++)
      {
        p -= w3;
        q = p;
        for (int col = 0; col < pWidth; col++)
        {
          final int iR = pByteBuffer.get(q++);
          final int iG = pByteBuffer.get(q++);
          final int iB = pByteBuffer.get(q++);

          lPixelInts[i++] = 0xFF000000 | ((iR & 0x000000FF) << 16)
                            | ((iG & 0x000000FF) << 8)
                            | (iB & 0x000000FF);
        }

      }

      BufferedImage lBufferedImage =
                       new BufferedImage(pWidth,
                                         pHeight,
                                         BufferedImage.TYPE_INT_ARGB);
    
      lBufferedImage.setRGB(0,
                            0,
                            pWidth,
                            pHeight,
                            lPixelInts,
                            0,
                            pWidth);
      
      return lBufferedImage;
      
   } catch (final Throwable e)
   {
      e.printStackTrace();
   }
    
   return null;
 }


}
