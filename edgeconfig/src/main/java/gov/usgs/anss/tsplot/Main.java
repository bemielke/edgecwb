/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import gov.usgs.anss.util.Util;
import java.awt.Dimension;
import javax.swing.JFrame;

/*
 * Main.java
 *
 * Created on July 3, 2006, 4:33 PM
 * By Jeremy Powell
 */
public final class Main {

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    JFrame mainFrame = new JFrame();
    Util.setModeGMT();

    // get the screen size and set the app to be half of it
    Dimension dim = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
    //dim.setSize(dim.getWidth()/2, dim.getHeight()/2);
    dim.setSize(750, 600);
    mainFrame.setSize(dim);
    mainFrame.setLocation(0, 0);

    mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // add the panel and make visible
    //mainFrame.add(new TSPlotPanel(dim));
    mainFrame.getContentPane().add(new TSPlotPanel(dim));
    mainFrame.setVisible(true);
  }
}
