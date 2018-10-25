/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * AllFlagsPanel.java
 *
 * Created on July 21, 2006, 12:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.edge.config;
import java.util.ArrayList;
import java.awt.Dimension;
/**
 *
 * @author davidketchum
 */
public class AllFlagsPanel extends javax.swing.JPanel{
  ArrayList subpanels;
  /** Creates a new instance of AllFlagsPanel. */
  public AllFlagsPanel(ArrayList subs) {
    subpanels = subs;
    ArrayList dims = new ArrayList(subpanels.size());
    double height =0;
    double  width = 0;
    Dimension size = null;
    for(int i=0; i<subpanels.size(); i++) {
      size = ((javax.swing.JPanel) subpanels.get(i)).getPreferredSize();
      if(size.getWidth() > width ) width = size.getWidth();
      height += size.getHeight()+4;
    }
    setPreferredSize(new Dimension((int) width, (int) height+20));
    setMinimumSize(new Dimension((int) width, (int) height+20));
    for(int i=0; i<subpanels.size() ; i++) {
      add((javax.swing.JPanel)subpanels.get (i));
    }
    UC.Look(this);
    
  }
  
}
