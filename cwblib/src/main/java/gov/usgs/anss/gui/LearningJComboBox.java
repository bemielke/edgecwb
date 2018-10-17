/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.gui;
import java.util.Arrays;
import javax.swing.JComboBox;
/**
 *  The purpose of this class is to build a JComboBox of string dynamically.
 * You create one of these items and pair it with a JComboBox at creation.  
 * Call the learn function with potential things to add to the box.  If it is
 * a new item it is added to an internal list.  Periodically call buildJComboBox
 * to update the items on the list.  Remember that build will change and select
 * in the box and could execute action events more often than you want.  In the
 * "ChannelDisplay and ProcessDisplay" the doUpdate function of the action items
 * was disabled by flag during the update.
 *
 * The calls to build the list and change the JComboBox where separated because on
 * some version of the JRE (XP in particular) changing the JComboBox while in a routine
 * which might be caused changes in the JComboBox resulted in a dead lock in addItem().
 * So, you can learn() in the update function, but do the build outside.
 *
 * @author  ketchum
 */
public class LearningJComboBox
{ static int GROW_SIZE=20;
  private final JComboBox box;
  String [] list;
  int nlist;
  boolean changed;
  public void setVisible(boolean b) {box.setVisible(b);}
  /** Creates a new instance of LearningJComboBox 
   *@param b The JComboBox to associate with the learning with
   */
  public LearningJComboBox(JComboBox b)
  { box = b;
    list = new String[GROW_SIZE];
    for(int i=0; i< GROW_SIZE; i++) list[i]="zzzzzz";
    list[0]=" None";
    nlist=1;
    box.addItem(" None");
    //Util.prt("LCB: Create learnbox len="+box.getItemCount());
    changed=false;
  }
  /** Check a string to see if it is in or needs to be added to the learning box
   *@param s String to check or add
   */
  public synchronized void learn( String s) {
    if(s == null) return;
    int insertPoint = Arrays.binarySearch(list, s);
    if(insertPoint >=  0) return;
    
    // Insert a new one. increase list size if needed
    insertPoint = -insertPoint -1;
    if( nlist >= list.length-5) {
      String [] tmp = new String[list.length+GROW_SIZE];
      System.arraycopy(list, 0, tmp, 0, list.length);
      for(int i=0; i<GROW_SIZE; i++) tmp[list.length+i] = "zzzzzz";
      list = tmp;
    }
    //Util.prt(s+" LCB: move for insert at ="+insertPoint+" len ="+(list.length-insertPoint-1)+" len="+list.length);
    if(list.length - insertPoint-1 > 0)
      System.arraycopy(list, insertPoint, list, insertPoint+1, list.length-insertPoint-1);
    //Util.prta("LCB: Insert new item "+s+" at "+insertPoint+" listsize="+list.length+" jcombosize="+box.getItemCount());
    list[insertPoint] = s;
    nlist++;
    changed=true;
  }
  /** Update the Associated JComboBox to include everything learned so far and set the
   *box to the currently selected item
   */
  public void buildJComboBox() {
    synchronized(box) {
      if(!changed) return;
      //Util.prta("LCBbj: nlist="+nlist+" count="+box.getItemCount());
      String selected = (String) box.getSelectedItem();
      if(box.getItemCount() > 0) box.removeAllItems();
      //Util.prta("LCBbj: add none");
      for(int i=0; i<nlist; i++)  box.addItem(list[i]);
      // Make sure something is selected
      if(selected != null) box.setSelectedItem(selected);
      if(box.getSelectedIndex() == -1) box.setSelectedIndex(1);
      changed=false;
      //Util.prta("LCBbj: done");
    }

  }
}

