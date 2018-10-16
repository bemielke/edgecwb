/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.util;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
/** Show.java
  * Tool for displaying Swing demos.  This class takes a JPanel, width and height
 *and Displays it on the screen in a created JFrame.  The Title of the JFrame is
 *the class of the JPanel.  A window listener is added so that appropriate clicks
 *make the JFrame exit.
 */
public class Show {
  static JFrame frame;
  static JPanel jp;
  /** this static method takes an JPanel puts it on a new JFrame, sets the
   *title of the JFrame, adds a WindowListener to do exit handling on mouse clicks,
   *and makes the JFrame visible.  Only on JPanel can be associated with a Show
   * so this this is mainly used to unit test JPanels within the GUI by doing a
   * <br>     Show.inFrame(new LocationPanel(), 600,450);
   *@param jpin JPanel to display
   *@param width Width of the desired JFrame in pixels
   *@param height Height of the desired JFrame in pixels.
   */
  public static void inFrame(JPanel jpin, int width, int height) {
    jp = jpin;
    String title = jp.getClass().toString();
    // Remove the word "class":
    if(title.contains("class"))
      title = title.substring(6);
    frame = new JFrame(title);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e){
        //if(jp instanceof InvTree) InvTree.exitCleanup();
        System.exit(0);
      }
    });
    frame.getContentPane().add(
      jp, BorderLayout.CENTER);
    frame.setSize(width, height);
    frame.setVisible(true);
  }
  /** get the JFrame associated with this Show object.
   *@return The JFrame associated with this instance.*/
  public static JFrame getJFrame() {return frame;}
} ///:~