/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.edge.snw.SNWRule;
import gov.usgs.edge.snw.SNWStation;
import gov.usgs.edge.snw.SNWGroup;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.db.JDBConnection;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.snw.*;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import jcexceptions.JCJBLBadPassword;

/**
 * DCK: I think this is obsolete.
 *
 * @author rjackson
 */
public final class SNWQ330GenConfigFile {

  /**
   * Creates a new instance of SNWQ330GenConfigFile.
   */
  public SNWQ330GenConfigFile() {
  }

  public static void main(String args[]) {
    JDBConnection jcjbl;
    Connection C;
//		User u = new User("test");
    //new User("dkt");
    try {
      // Get user from the Inventory database user file
      jcjbl = new JDBConnection("gacqdb.cr.usgs.gov", "inv", "ro", "readonly", true);
      C = JDBConnection.getConnection();
      JDBConnection.addConnection(C, "inv");
      User u = new User(C, "dkt", "karen");

      // Get a new connection to the ANSS database
      jcjbl = new JDBConnection("gacqdb.cr.usgs.gov", "edge", "ro", "readonly", true);
      C = JDBConnection.getConnection();
      JDBConnection.addConnection(C, "edge");
      UC.setConnection(C);
    } catch (SQLException e) {
      System.err.println("SQLException on getting SNWStation" + e.getMessage());
      Util.SQLErrorPrint(e, "SQLExcepion on gettning SNWStation");
    } catch (JCJBLBadPassword E) {
      Util.prt("bad password");
    }
    //JComboBox snwstationpanel = SNWStationPanel.getJComboBox();
    //JComboBox snwrulepanel = SNWRulePanel.getJComboBox();
    //JComboBox snwgrouppanel = SNWGroupPanel.getJComboBox();
    SNWStation snwstation;
    SNWRule snwrule;
    SNWGroup snwgroup;
    DBObject obj;
    String filename = "myfile.txt";
    if (args.length >= 1) {
      if (args[0].equals("")) {
        filename = "myfile.txt";
      } else {
        filename = args[0];
      }
    }
    PrintStream fileout;
    try {

      fileout = new PrintStream(new FileOutputStream(filename));
    } catch (FileNotFoundException e) {
      System.err.println("Error writing to file" + e.getMessage());
      return;
    }
    ArrayList snwstationvector = SNWStationPanel.getSNWStationVector();
    int itemcnt = snwstationvector.size();
    for (int i = 0; i < itemcnt; i++) {
      snwstation = (SNWStation) snwstationvector.get(i);
      Util.prt(i + " " + snwstation.toString());
      if (snwstation.getDisable() > 0) {
        // set to default group and add to group 'disabled'
        fileout.println("[" + snwstation.getNetwork() + "-" + snwstation.getSNWStation() + "]");
        String longName = snwstation.getDescription();
        if (longName.equals("")) {
          longName = "x";
        }
        fileout.println("longName = \"" + longName + "\"");
        // get RuleSet
        snwrule = (SNWRule) SNWRulePanel.getItemWithID(snwstation.getSNWRuleID());
        String rule = snwrule.getSNWRule();
        rule = "ruleSet = \"GoldenUnknownRuleset\"";
        fileout.println(rule);
        // Output group with process and CPU
        fileout.println("group = \"" + "disabled" + "\"");
        fileout.println("usagelevel = 3");
        fileout.println("\"Location Description\" = \"" + snwstation.getDescription() + "\"");
        fileout.println("\"Station Latitude\" = " + snwstation.getLatitude());
        fileout.println("\"Station Longitude\" = " + snwstation.getLongitude());
        fileout.println("\"Station Elevation\" = " + snwstation.getElevation());
        fileout.println("helpString = \"" + snwstation.getHelpstring() + "\"");
        fileout.println("");
      } else {
        fileout.println("[" + snwstation.getNetwork() + "-" + snwstation.getSNWStation() + "]");
        String longName = snwstation.getDescription();
        if (longName.equals("")) {
          longName = "x";
        }
        fileout.println("longName = \"" + longName + "\"");
        // get RuleSet
        snwrule = (SNWRule) SNWRulePanel.getItemWithID(snwstation.getSNWRuleID());
        String rule = snwrule.getSNWRule();
        if (rule.equals("")) {
          rule = "ruleSet = \"GoldenUnknownRuleset\"";
        } else {
          rule = "ruleSet = \"" + rule + "RuleSet\"";
        }
        fileout.println(rule);
        //fileout.println ("ruleSet = \" \"");
        // Output group with process and CPU
        String process = snwstation.getProcess();
        String cpu = snwstation.getCpu();
        if (process.equals("") == false) {
          fileout.println("group = \"" + process + "\"");
        }
        if (cpu.equals("") == false) {
          fileout.println("group = \"" + cpu + "\"");
        }
        // get groups
        long groupmask = snwstation.getGroupmask();
        // Check if any groups are selected
        if (groupmask > 0L) {
          for (int j = 0; j < 64; j++) {
            if ((groupmask & (1L << (j))) != 0) {
              snwgroup = (SNWGroup) SNWGroupPanel.getItemWithID(j + 1);
              fileout.println("group = \"" + snwgroup + "\"");
            }
          }
        }
        fileout.println("usagelevel = 3");
        fileout.println("\"Location Description\" = \"" + snwstation.getDescription() + "\"");
        fileout.println("\"Station Latitude\" = " + snwstation.getLatitude());
        fileout.println("\"Station Longitude\" = " + snwstation.getLongitude());
        fileout.println("\"Station Elevation\" = " + snwstation.getElevation());
        fileout.println("helpString = \"" + snwstation.getHelpstring() + "\"");
        fileout.println("");
      }

    }

    //fileout.println ("test");
    fileout.close();

  }
}
