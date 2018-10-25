/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.alarm;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.edge.config.UC;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;

/**
 * 
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "schedule" in the initial form
 * The table name and key name should match and start lower case (schedule).
 * The Class here will be that same name but with upper case at beginning(Schedule).
 * <br> 1)  Rename the location JComboBox to the "key" (schedule) value.  This should start Lower
 *      case.
 * <br>  2)  The table name should match this key name and be all lower case.
 * <br>  3)  Change all Schedule to ClassName of underlying data (case sensitive!)
 * <br>  4)  Change all schedule key value (the JComboBox above)
 * <br>  5)  clearScreen should update swing components to new defaults
 * <br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * <br>  7) newone() should be updated to create a new instance of the underlying data class.
 * <br>  8) oldone() get data from database and update all swing elements to correct values
 * <br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 * <br> 10) SchedulePanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 * 
 * <br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 * <br>
 * ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initialized with 
 * <br>
 * <br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("alarm"), "tcpstation","rtsport1");
 * <br> Put this in "CodeGeneration"  "CustomCreateCode" of the form editor for each Enum.
 * <br>
 * <br> In  oldone() get Enum fields with something like :
 * <br>  fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 * <br>  (This sets the JComboBox to the Item matching the string)
 * <br>
 * data class should have code like :
 * <br><br>  import java.util.ArrayList;          /// Import for Enum manipulation
 * <br>    .
 * <br>  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *  <br>      .
 * <br>       .
 * <br>    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("alarm"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br>
 * 
 * @author D.C. Ketchum
 */
public final class SchedulePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector schedules" is used for main Comboboz
  static ArrayList<Schedule> schedules;             // Vector containing objects of this Schedule Type
  private  DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  private String scheduleVal;
  private String aliasIDVal;
  private int aliasIDInt;
  private int onlineIDInt;
  private int julianInt;
  private String whyjulianVal;
  private int dayOfWeekInt;
  private Timestamp st, et, st2, et2;
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   UC.Look(error);
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm Schedule");


//   aliasIDInt = FUtil.chkInt(aliasID,err);
//   aliasIDInt = FUtil.chkInt(aliasID,err);
//   onlineIDInt = FUtil.chkInt(onlineID,err);
   julianInt = FUtil.chkInt(julian,err,true);
//   dayOfWeekInt = FUtil.chkInt(dayOfWeek,err);
   whyjulianVal=" ";
   if (julianInt>0){    // If julian is set then ignore dayOfWeek
     dayOfWeekInt =0;
     String t = whyjulian.getText();
     if (t.length()>20)t = t.substring(0,19);
     if (t.length()==0)t = " ";
     whyjulianVal=t;
   }
   else {
     dayOfWeekInt=0;
     if (Sunday.isSelected())dayOfWeekInt = dayOfWeekInt + 1;
     if (Monday.isSelected())dayOfWeekInt = dayOfWeekInt + 2;
     if (Tuesday.isSelected())dayOfWeekInt = dayOfWeekInt + 4;
     if (Wednesday.isSelected())dayOfWeekInt = dayOfWeekInt + 8;
     if (Thursday.isSelected())dayOfWeekInt = dayOfWeekInt + 16;
     if (Friday.isSelected())dayOfWeekInt = dayOfWeekInt + 32;
     if (Saturday.isSelected())dayOfWeekInt = dayOfWeekInt + 64;
   }
   if (julianInt==0 & dayOfWeekInt==0) {
     err.set(true);
     err.appendText("Must set Day of Week or Julian Date");
   }
   String sst, set;
   sst = startTime.getText();
   set = endTime.getText();
   startTime.setText("2007-01-01 "+startTime.getText());
   endTime.setText("2007-01-01 "+endTime.getText());
   st = FUtil.chkTimestamp(startTime,err);
   et = FUtil.chkTimestamp(endTime,err);
   startTime.setText(sst);
   endTime.setText(set);

    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    }
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    schedule.setSelectedIndex(-1);
    ID.setText("");
    UC.Look(error);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Schedule");
    delete.setEnabled(false);
      
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    aliasID.setSelectedIndex(-1);
    onlineID.setSelectedIndex(-1);
    otherwiseID.setSelectedIndex(-1);
    whyjulian.setText("");
    julian.setText("");
    Sunday.setSelected(false);
    Monday.setSelected(false);
    Tuesday.setSelected(false);
    Wednesday.setSelected(false);
    Thursday.setSelected(false);
    Friday.setSelected(false);
    Saturday.setSelected(false);
    WorkWeek.setSelected(false);
    WeekEnd.setSelected(false);
    startTime.setText("00:00:00");
    endTime.setText("00:00:00");
  }
 
  
  private Schedule newOne() {
       Timestamp t = new Timestamp(0l);

     
    return new Schedule(0,"New Schedule",  // ID
       0, // aliasID
       0, // onlineID
       0, // otherwiseID
       0, // julian
       "",// whyjulian
       0, // dayOfWeek
       t, // StartTime
       t  // endtime        
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
    "alarm", "schedule","schedule",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      schedule.setText(obj.getString("Schedule"));
      // Example : description.setText(obj.getString("description"));
      SchedulePanel.setJComboBoxToID(schedule,obj.getInt("ID"));      
      TargetPanel.setJComboBoxToID(aliasID,obj.getInt("aliasID"));      
      TargetPanel.setJComboBoxToID(onlineID,obj.getInt("onlineID"));      
      TargetPanel.setJComboBoxToID(otherwiseID,obj.getInt("otherwiseID"));
      julianInt = obj.getInt("julian");
      if (julianInt==0) julian.setText("");
      else {
        julian.setText(julianInt+"");
        whyjulian.setText(obj.getString("whyjulian"));
      }
      int dow=obj.getInt("dayOfWeek");
      Sunday.setSelected(((dow & (1)) != 0));
      Monday.setSelected(((dow & (1 << (1) )) != 0));
      Tuesday.setSelected(((dow & (1 << (2) )) != 0));
      Wednesday.setSelected(((dow & (1 << (3) )) != 0));
      Thursday.setSelected(((dow & (1 << (4) )) != 0));
      Friday.setSelected(((dow & (1 << (5) )) != 0));
      Saturday.setSelected(((dow & (1 << (6) )) != 0));
      if (Sunday.isSelected() & Saturday.isSelected()) 
        WeekEnd.setSelected(true);
      else
        WeekEnd.setSelected(false);
      if (Monday.isSelected() & Tuesday.isSelected() & Wednesday.isSelected() & Thursday.isSelected() & Friday.isSelected())
        WorkWeek.setSelected(true);
      else
        WorkWeek.setSelected(false);
      startTime.setText(obj.getTimestamp("startTime").toString().substring(11,19));
      endTime.setText(obj.getTimestamp("endTime").toString().substring(11,19));


        
        
    }           // End else isNew() - processing to form
  }
  
  private String getScheduleName() {
    
    String sttmp;
    sttmp=""+ID;
    // Get Alias using aliasID
    try {
     Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
     String s = "SELECT alias FROM alarm.alias where aliasID = "+aliasID+";";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Schedule loc = new Schedule(rs);
//        Util.prt("MakeSchedule() i="+schedules.size()+" is "+loc.getSchedule());
        schedules.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSchedules() on table SQL failed");
      return null;
    }      
    return sttmp;
  }
  
  
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    gridBagLayout1 = new java.awt.GridBagLayout();
    labSchedule = new javax.swing.JLabel();
    schedule = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    labID = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labAlias = new javax.swing.JLabel();
    labOnlineID = new javax.swing.JLabel();
    labJulianDate = new javax.swing.JLabel();
    labDayOfWeek = new javax.swing.JLabel();
    labStartTime = new javax.swing.JLabel();
    labEndTime = new javax.swing.JLabel();
    julian = new javax.swing.JTextField();
    startTime = new javax.swing.JTextField();
    endTime = new javax.swing.JTextField();
    aliasID = TargetPanel.getJComboBox();
    labOtherwiseID = new javax.swing.JLabel();
    dowPanel = new javax.swing.JPanel();
    Sunday = new javax.swing.JRadioButton();
    Monday = new javax.swing.JRadioButton();
    Tuesday = new javax.swing.JRadioButton();
    Wednesday = new javax.swing.JRadioButton();
    Thursday = new javax.swing.JRadioButton();
    Friday = new javax.swing.JRadioButton();
    Saturday = new javax.swing.JRadioButton();
    WorkWeek = new javax.swing.JRadioButton();
    WeekEnd = new javax.swing.JRadioButton();
    onlineID = TargetPanel.getJComboBox();
    otherwiseID = TargetPanel.getJComboBox();
    delete = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    julianDP = new org.jdesktop.swingx.JXDatePicker();
    labWhy = new javax.swing.JLabel();
    whyjulian = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    labSchedule.setText("Schedule : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSchedule, gridBagConstraints);

    schedule.setEditable(true);
    schedule.setMaximumSize(new java.awt.Dimension(129, 25));
    schedule.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        scheduleActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(schedule, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setColumns(8);
    ID.setEditable(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    labID.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    add(labID, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labAlias.setText("Alias : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labAlias, gridBagConstraints);

    labOnlineID.setText("Online : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labOnlineID, gridBagConstraints);

    labJulianDate.setText("Single Day : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labJulianDate, gridBagConstraints);

    labDayOfWeek.setText("Day of Week : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDayOfWeek, gridBagConstraints);

    labStartTime.setText("StartTime : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labStartTime, gridBagConstraints);

    labEndTime.setText("EndTime : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labEndTime, gridBagConstraints);

    julian.setPreferredSize(new java.awt.Dimension(100, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    add(julian, gridBagConstraints);

    startTime.setMaximumSize(new java.awt.Dimension(200, 20));
    startTime.setMinimumSize(new java.awt.Dimension(200, 20));
    startTime.setPreferredSize(new java.awt.Dimension(200, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(startTime, gridBagConstraints);

    endTime.setMaximumSize(new java.awt.Dimension(200, 20));
    endTime.setMinimumSize(new java.awt.Dimension(200, 20));
    endTime.setPreferredSize(new java.awt.Dimension(200, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(endTime, gridBagConstraints);

    aliasID.setEditable(true);
    aliasID.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(aliasID, gridBagConstraints);

    labOtherwiseID.setText("Otherwise : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labOtherwiseID, gridBagConstraints);

    dowPanel.setLayout(new java.awt.GridBagLayout());

    dowPanel.setMaximumSize(new java.awt.Dimension(200, 120));
    dowPanel.setMinimumSize(new java.awt.Dimension(200, 120));
    dowPanel.setPreferredSize(new java.awt.Dimension(200, 120));
    Sunday.setText("Sunday");
    Sunday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Sunday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Sunday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        SundayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Sunday, gridBagConstraints);

    Monday.setText("Monday");
    Monday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Monday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Monday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        MondayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Monday, gridBagConstraints);

    Tuesday.setText("Tuesday");
    Tuesday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Tuesday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Tuesday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        TuesdayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Tuesday, gridBagConstraints);

    Wednesday.setText("Wednesday");
    Wednesday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Wednesday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Wednesday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        WednesdayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Wednesday, gridBagConstraints);

    Thursday.setText("Thursday");
    Thursday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Thursday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Thursday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        ThursdayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Thursday, gridBagConstraints);

    Friday.setText("Friday");
    Friday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Friday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Friday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        FridayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Friday, gridBagConstraints);

    Saturday.setText("Saturday");
    Saturday.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    Saturday.setMargin(new java.awt.Insets(0, 0, 0, 0));
    Saturday.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        SaturdayActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(Saturday, gridBagConstraints);

    WorkWeek.setText("M-F");
    WorkWeek.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    WorkWeek.setMargin(new java.awt.Insets(0, 0, 0, 0));
    WorkWeek.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        WorkWeekActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(WorkWeek, gridBagConstraints);

    WeekEnd.setText("WeekEnd");
    WeekEnd.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    WeekEnd.setMargin(new java.awt.Insets(0, 0, 0, 0));
    WeekEnd.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        WeekEndActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    dowPanel.add(WeekEnd, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(dowPanel, gridBagConstraints);

    onlineID.setEditable(true);
    onlineID.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(onlineID, gridBagConstraints);

    otherwiseID.setEditable(true);
    otherwiseID.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(otherwiseID, gridBagConstraints);

    delete.setText("Delete");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 18;
    add(delete, gridBagConstraints);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(reload, gridBagConstraints);

    julianDP.setMaximumSize(new java.awt.Dimension(133, 25));
    julianDP.setMinimumSize(new java.awt.Dimension(133, 25));
    julianDP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        julianDPActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(julianDP, gridBagConstraints);

    labWhy.setText("Why? : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labWhy, gridBagConstraints);

    whyjulian.setMaximumSize(new java.awt.Dimension(129, 25));
    whyjulian.setMinimumSize(new java.awt.Dimension(129, 25));
    whyjulian.setPreferredSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(whyjulian, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void julianDPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_julianDPActionPerformed
    julian.setText(SeedUtil.toJulian(julianDP.getDate())+"");
    WorkWeek.setSelected(false);
    WeekEnd.setSelected(false);
    Sunday.setSelected(false);
    Monday.setSelected(false);
    Tuesday.setSelected(false);
    Wednesday.setSelected(false);
    Thursday.setSelected(false);
    Friday.setSelected(false);
    Saturday.setSelected(false);
    startTime.setText("00:00:00");
    endTime.setText("00:00:00");
  }//GEN-LAST:event_julianDPActionPerformed

  private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
// TODO add your handling code here:
      SchedulePanel.getJComboBox();      
      TargetPanel.getJComboBox();   
      schedule.setSelectedIndex(-1);
      clearScreen();
  }//GEN-LAST:event_reloadActionPerformed

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
      try {
        obj.deleteRecord();
        SchedulePanel.reload();
        SchedulePanel.getJComboBox(schedule);
        schedule.setSelectedIndex(-1);
        clearScreen();
      }
      catch(SQLException e) {Util.SQLErrorPrint(e,"Error deleting record");}
  }//GEN-LAST:event_deleteActionPerformed

  private void SaturdayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaturdayActionPerformed
    julian.setText("");
    if (Saturday.isSelected()){
      Saturday.setSelected(true);
    }
    else {
      Saturday.setSelected(false);
    }
  }//GEN-LAST:event_SaturdayActionPerformed

  private void FridayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_FridayActionPerformed
    julian.setText("");
    if (Friday.isSelected()){
      Friday.setSelected(true);
    }
    else {
      Friday.setSelected(false);
    }
  }//GEN-LAST:event_FridayActionPerformed

  private void ThursdayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ThursdayActionPerformed
    julian.setText("");
    if (Thursday.isSelected()){
      Thursday.setSelected(true);
    }
    else {
      Thursday.setSelected(false);
    }
  }//GEN-LAST:event_ThursdayActionPerformed

  private void WednesdayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_WednesdayActionPerformed
    julian.setText("");
    if (Wednesday.isSelected()){
      Wednesday.setSelected(true);
    }
    else {
      Wednesday.setSelected(false);
    }
  }//GEN-LAST:event_WednesdayActionPerformed

  private void TuesdayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TuesdayActionPerformed
    julian.setText("");
    if (Tuesday.isSelected()){
      Tuesday.setSelected(true);
    }
    else {
      Tuesday.setSelected(false);
    }
  }//GEN-LAST:event_TuesdayActionPerformed

  private void MondayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MondayActionPerformed
    julian.setText("");
    if (Monday.isSelected()){
      Monday.setSelected(true);
    }
    else {
      Monday.setSelected(false);
    }
  }//GEN-LAST:event_MondayActionPerformed

  private void SundayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SundayActionPerformed
// TODO add your handling code here:
    julian.setText("");
    if (Sunday.isSelected()){
      Sunday.setSelected(true);
    }
    else {
      Sunday.setSelected(false);
    }
  }//GEN-LAST:event_SundayActionPerformed

  private void WeekEndActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_WeekEndActionPerformed
// TODO add your handling code here:
    julian.setText("");
    if (WeekEnd.isSelected()){
      Sunday.setSelected(true);
      Saturday.setSelected(true);
    }
    else {
      Sunday.setSelected(false);
      Saturday.setSelected(false);      
    }
  }//GEN-LAST:event_WeekEndActionPerformed

  private void WorkWeekActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_WorkWeekActionPerformed
// TODO add your handling code here:
    julian.setText("");
    if (WorkWeek.isSelected()){
      Monday.setSelected(true);
      Tuesday.setSelected(true);
      Wednesday.setSelected(true);
      Thursday.setSelected(true);
      Friday.setSelected(true);
    } 
    else {
      Monday.setSelected(false);
      Tuesday.setSelected(false);
      Wednesday.setSelected(false);
      Thursday.setSelected(false);
      Friday.setSelected(false);      
    }
  }//GEN-LAST:event_WorkWeekActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
//      String p = schedule.getSelectedItem().toString();
//      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
//      obj.setString("schedule",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      if(aliasID.getSelectedIndex() != -1) obj.setInt("aliasID", ((Target) aliasID.getSelectedItem()).getID());
      else obj.setInt("aliasID",0);
      if(onlineID.getSelectedIndex() != -1) obj.setInt("onlineID", ((Target) onlineID.getSelectedItem()).getID());
      else obj.setInt("onlineID",0);
      if(otherwiseID.getSelectedIndex() != -1) obj.setInt("otherwiseID", ((Target) otherwiseID.getSelectedItem()).getID());
      else obj.setInt("otherwiseID",0);
      String dow = "";
      if (Sunday.isSelected())dow = dow+"Su";
      if (Monday.isSelected())dow = dow+"Mo";
      if (Tuesday.isSelected())dow = dow+"Tu";
      if (Wednesday.isSelected())dow = dow+"We";
      if (Thursday.isSelected())dow = dow+"Th";
      if (Friday.isSelected())dow = dow+"Fr";
      if (Saturday.isSelected())dow = dow+"Sa";
      String p = aliasID.getSelectedItem()+"|"+whyjulianVal+"|"+dow+"|"+st.toString().substring(11,19)+"|"+et.toString().substring(11,19);


      obj.setString("schedule",p);
      obj.setInt("julian",julianInt);
      obj.setString("whyjulian",whyjulianVal);
      obj.setInt("dayOfWeek",dayOfWeekInt);
      obj.setTimestamp("startTime",st);
      obj.setTimestamp("endTime",et);


      
      // Do not change
      obj.updateRecord();
      schedules=null;       // force reload of combo box
      getJComboBox(schedule);
      clearScreen();
      schedule.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"schedule: update failed partno="+schedule.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void scheduleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scheduleActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_scheduleActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JRadioButton Friday;
  private javax.swing.JTextField ID;
  private javax.swing.JRadioButton Monday;
  private javax.swing.JRadioButton Saturday;
  private javax.swing.JRadioButton Sunday;
  private javax.swing.JRadioButton Thursday;
  private javax.swing.JRadioButton Tuesday;
  private javax.swing.JRadioButton Wednesday;
  private javax.swing.JRadioButton WeekEnd;
  private javax.swing.JRadioButton WorkWeek;
  private javax.swing.JButton addUpdate;
  private javax.swing.JComboBox aliasID;
  private javax.swing.JButton delete;
  private javax.swing.JPanel dowPanel;
  private javax.swing.JTextField endTime;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField julian;
  private org.jdesktop.swingx.JXDatePicker julianDP;
  private javax.swing.JLabel labAlias;
  private javax.swing.JLabel labDayOfWeek;
  private javax.swing.JLabel labEndTime;
  private javax.swing.JLabel labID;
  private javax.swing.JLabel labJulianDate;
  private javax.swing.JLabel labOnlineID;
  private javax.swing.JLabel labOtherwiseID;
  private javax.swing.JLabel labSchedule;
  private javax.swing.JLabel labStartTime;
  private javax.swing.JLabel labWhy;
  private javax.swing.JComboBox onlineID;
  private javax.swing.JComboBox otherwiseID;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox schedule;
  private javax.swing.JTextField startTime;
  private javax.swing.JTextField whyjulian;
  // End of variables declaration//GEN-END:variables

  /**
   * Creates new form SchedulePanel
   */
  public SchedulePanel() {
    initiating=true;
    initComponents();
    getJComboBox(schedule);                // set up the key JComboBox
    schedule.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    UC.Look(dowPanel);
    UC.Look(WeekEnd);
    UC.Look(WorkWeek);
    UC.Look(Sunday);
    UC.Look(Monday);
    UC.Look(Tuesday);
    UC.Look(Wednesday);
    UC.Look(Thursday);
    UC.Look(Friday);
    UC.Look(Saturday);
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
    clearScreen();                    // Start with empty screen
  }
  
  private void Look() {
    UC.Look(this);                    // Set color background
  }

  
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /** Create a JComboBox of all of the items in the table represented by this panel
   *@return A New JComboBox filled with all row keys from the table
   */
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  /** Update a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeSchedules();
    for (int i=0; i< schedules.size(); i++) {
      b.addItem(schedules.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SchedulePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Schedule) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SchedulePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(schedules == null) makeSchedules();
    for(int i=0; i<schedules.size(); i++) if( ID == ((Schedule) schedules.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    schedules = null;
    makeSchedules();
  }
    /* return a vector with all of the Schedule
   * @return The vector with the schedule
   */
  public static ArrayList<Schedule> getScheduleVector() {
    if(schedules == null) makeSchedules();
    return schedules;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Schedule row with this ID
   */
  public static Schedule getItemWithID(int ID) {
    if(schedules == null) makeSchedules();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Schedule) schedules.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSchedules() {
    if (schedules != null) return;
    schedules=new ArrayList<>(100);
    try {
       Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
     String s = "SELECT * FROM alarm.schedule ORDER BY schedule;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Schedule loc = new Schedule(rs);
//        Util.prt("MakeSchedule() i="+schedules.size()+" is "+loc.getSchedule());
        schedules.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSchedules() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(schedule == null) return;
    if(initiating) return;
    Schedule l;
    if(schedule.getSelectedIndex() == -1) {
      if(schedule.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Schedule) schedule.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSchedule();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      delete.setEnabled(false);
      addUpdate.setText("Enter a Schedule!");
    }
//    p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
      clearScreen();          // set screen to known state
      oldOne(p);
      
      // set add/Update button to indicate an update will happen
      if(obj.isNew()) {
          clearScreen();
          ID.setText("NEW!");

          // Set up for "NEW" - clear fields etc.
          error.setText("NOT found - assume NEW");
          error.setBackground(UC.yellow);
          addUpdate.setText("Add "+p);
          delete.setEnabled(false);
          addUpdate.setEnabled(true);
      }
      else {
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        delete.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Schedule: SQL error getting Schedule="+p);
    }
    
  }
  /** This main displays the form Pane by itself
   *@param args command line args ignored*/
  public static void main(String args[]) {
        DBConnectionThread jcjbl;
    Util.init(UC.getPropertyFilename());
    UC.init();
    try {
        // Make test DBconnection for form
      jcjbl = new DBConnectionThread(DBConnectionThread.getDBServer(),DBConnectionThread.getDBCatalog(),
              UC.defaultUser(),UC.defaultPassword(), true, false, DBConnectionThread.getDBSchema(), DBConnectionThread.getDBVendor());
      if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
        if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          {Util.prt("Could not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new SchedulePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
}
