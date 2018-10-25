/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * TagPanel.java
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

package gov.usgs.edge.config;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;

/**
 *
 * @author davidketchum
 */
public class TagSubPanel extends javax.swing.JPanel {
  ArrayList<Object> list;
  ArrayList<JRadioButton> onRadio;
  ArrayList<JRadioButton> offRadio;
  JComboBox combo;
  /** return the list of items used to build and control this panel.  These came from
   * the JComboBox used at creation */
  public ArrayList<Object> getList() {return list;}
  public ArrayList<JRadioButton> getOnRadio() {return onRadio;}
  public ArrayList<JRadioButton> getOffRadio() {return offRadio;}
  public void setAllClear() {
    for(int i=0; i<list.size(); i++) {
      ((JRadioButton) onRadio.get(i)).setSelected(false);
      ((JRadioButton) offRadio.get(i)).setSelected(false);
    }
  }
  
  /** set the two button group to the "on" state. */
  public void setOn(int i) {
    ((JRadioButton) onRadio.get(i)).setSelected(true);
    ((JRadioButton) offRadio.get(i)).setSelected(false);
    
  }
  
  /** set the ith two button group to the "off" state. */
  public void setOff(int i) {
    ((JRadioButton) onRadio.get(i)).setSelected(false);
    ((JRadioButton) offRadio.get(i)).setSelected(true);
    
  }  
  /** set the ith button group to the "both off" state. */
  public void setBothOff(int i) {
    ((JRadioButton) onRadio.get(i)).setSelected(false);
    ((JRadioButton) offRadio.get(i)).setSelected(false);
  }
  /** set the On radio selected to the given state.  Note this does not insure the 
   * off button is in the opposite state.  Use of setOn() is preferred.
   */
  public void setOnRadioSelected(int i, boolean val) { ((JRadioButton) onRadio.get(i)).setSelected(val);}
  /** set the Off radio selected to the given state.  Note this does not insure the 
   * on button is in the opposite state.  Use of setOff() is preferred.
   */
  public void setOffRadioSelected(int i, boolean val) { ((JRadioButton) offRadio.get(i)).setSelected(val);}
  /** return state of ith on button. */
  public boolean isOnRadioSelected(int i) { return ((JRadioButton) onRadio.get(i)).isSelected();}
  /** return state of ith off button. */
  public boolean isOffRadioSelected(int i) { return ((JRadioButton) offRadio.get(i)).isSelected();}
 
  /** Creates a new instance of TagPanel. */
  public TagSubPanel(JComboBox comb) {
    combo = comb; 
    list = new ArrayList<Object>(); 
    onRadio= new ArrayList<JRadioButton>(); 
    offRadio = new ArrayList<JRadioButton>();
  /** THe JPanel for sento and the sendTo and sendtoRadio ArrayLists need to be build from 
   *the SendtoPanel derived JComboBox.
   */
    buildTagPanel(combo);
  }
  public final void buildTagPanel(JComboBox comb) {
    combo = comb;
    removeAll();
    list.clear();
    onRadio.clear();
    offRadio.clear();
    setLayout(new java.awt.GridLayout( 0, 4));    // Note this guarantees exactly 4 columns
    for(int i=0; i<combo.getItemCount(); i++) {
      Object s = combo.getItemAt(i);
      //Util.prt("item i="+i+" is "+s.toString()+" class="+s.getClass().getName());
      if(s.toString().indexOf("_unused") >=0  || s.toString().indexOf("_Available") >= 0) continue;
      list.add( s);
      if(s != null) {
        // create on button and add to lists
        JRadioButton tem = new JRadioButton(s.toString()+" On");
        UC.Look(tem);
        add(tem);
        onRadio.add(tem);
        tem.addActionListener(new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            for(int i=0; i<list.size(); i++) {
              //Util.prt("On match found"+evt.getActionCommand()+" "+list.get(i).toString());
              if(evt.getActionCommand().equals( list.get(i).toString()+" On")) {
                //Util.prt("ON match found");
                ((JRadioButton) offRadio.get(i)).setSelected( !((JRadioButton) onRadio.get(i)).isSelected());
                return;
              }
            }
            Util.prt("Action ON NOT FOUND! command="+evt.getActionCommand()+" mod="+evt.getModifiers()+
                " param="+evt.paramString());
          }
        });
        // create off button and add to lists
        tem = new JRadioButton(s.toString()+" Off");
        UC.Look(tem);
        offRadio.add(tem);
        add(tem);
        tem.addActionListener(new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            for(int i=0; i<list.size(); i++) {
              //Util.prt("Off match found"+evt.getActionCommand()+" "+list.get(i).toString());
               if(evt.getActionCommand().equals( list.get(i).toString()+" Off")) {
                //Util.prt("Off match found");
                ((JRadioButton) onRadio.get(i)).setSelected( !((JRadioButton) offRadio.get(i)).isSelected());
                return;
               }
            }
            Util.prt("Action OFF NOT FOUND! command="+evt.getActionCommand()+" mod="+evt.getModifiers()+
                " param="+evt.paramString());
          }
        });
      }
    }
    /*if((combo.getItemCount() % 2) == 1) {
      javax.swing.JLabel z = new javax.swing.JLabel(" ");
      UC.Look(z);
      add(z);
      javax.swing.JLabel x = new javax.swing.JLabel(" ");
      UC.Look(x);
      add(x);
    }*/
    setBorder(new javax.swing.border.EtchedBorder());
    setPreferredSize(new java.awt.Dimension(725,(list.size()+1)/2*20));
    setMinimumSize(new java.awt.Dimension(725,(list.size()+1)/2*20));
  }

}
