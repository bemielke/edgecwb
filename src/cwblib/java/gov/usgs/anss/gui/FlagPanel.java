/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * FlagPanel.java
 *
 * Created on July 19, 2006, 4:36 PM
 *
 * This panel represents a series of flags based on the items in the JComboBox given
 * at start up.  These normally represent the values in some table containing the
 * names of the flags.  The "toString()" on each item in the JComboBox is used to 
 * build an "on" and "off" JRadioButon on this panel.  The event handlers insure one
 * one of the buttons is on at a time (turns the other off when user sets on on).
 * The neither one selected represents the "do nothing" option and is the initial state.
 *
 *
 */

package gov.usgs.anss.gui;
import gov.usgs.anss.gui.UC;
import gov.usgs.anss.util.*;
import java.util.ArrayList;
import javax.swing.JRadioButton;
import javax.swing.JComboBox;


/**
 *
 * @author davidketchum
 */
public class FlagPanel extends javax.swing.JPanel {
  ArrayList<Object> list;
  ArrayList<JRadioButton> radio;
  JComboBox combo;
  int width;                // User preferred width
  /** Return the list of items used to build and control this panel.  These came from
   * the JComboBox used at creation
   * @return  The list
   */
  public ArrayList getList() {return list;}
  public ArrayList getRadio() {return radio;}

  public void setAllClear() {
    for(int i=0; i<list.size(); i++) {
      ((JRadioButton) radio.get(i)).setSelected(false);
    }
  }

  /** Set the two button group to the "on" state
   * @param i turn on the ith one
   */
  public void setOn(int i) {
    ((JRadioButton) radio.get(i)).setSelected(true);
  }
  
  /** set the ith two button group to the "off" state
   * @param i turn off the ith one
   */
  public void setOff(int i) {
    ((JRadioButton) radio.get(i)).setSelected(false);   
  }  
  /** set the On radio selected to the given state.  Note this does not insure the 
   * off button is in the opposite state.  Use of setOn() is preferred.
   * @param i which button
   * @param val to this value
   */
  public void setradioSelected(int i, boolean val) { ((JRadioButton) radio.get(i)).setSelected(val);}
  /** return state of ith on button
   * @param i which button
   * @return  its value
   */
  public boolean isradioSelected(int i) { return ((JRadioButton) radio.get(i)).isSelected();}
 
  /** Creates a new instance of FlagPanel
   * @param comb The JCombo to use
   * @param wid width in pixels*/
  public FlagPanel(JComboBox comb, int wid) {
    combo = comb; 
    width = wid;
    list = new ArrayList<>(); 
    radio= new ArrayList<>(); 
    /** THe JPanel for sento and the sendTo and sendtoRadio ArrayLists need to be build from 
   *the SendtoPanel derived JComboBox
   */
    buildFlagPanel(combo);
  }
  public final void buildFlagPanel(JComboBox comb) {
    combo = comb;
    removeAll();
    list.clear();
    radio.clear();
    setLayout(new java.awt.GridLayout( 0, 4));    // Note this guarantees exactly 4 columns
    for(int i=0; i<combo.getItemCount(); i++) {
      Object s = combo.getItemAt(i);
      //Util.prt("item i="+i+" is "+s.toString());
      if(s.toString().contains("_unused") || s.toString().contains("_Available") ) continue;
      list.add(s);
      //if(s != null) {
        // create on button and add to lists
        JRadioButton tem = new JRadioButton(s.toString());
        UC.Look(tem);
        add(tem);
        radio.add(tem);
      //}
    }

    setBorder(new javax.swing.border.EtchedBorder());
    setPreferredSize(new java.awt.Dimension(width,(list.size()+3)/4*20));
    setMinimumSize(new java.awt.Dimension(width,(list.size()+3)/4*20));
  }

}
