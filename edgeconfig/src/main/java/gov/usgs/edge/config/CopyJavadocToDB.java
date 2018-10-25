/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
//import javax.net.ssl.*;

/**
 * CopyJavadocToDB.java
 *
 * @author  Rick Jackson
 */
public class CopyJavadocToDB extends Thread {   //implements Comparator
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passed data 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void CopyJavadocToDB()   {
  }
  
  public static void main(String args[]) {
    String endhtml="</HTML>";
    String quote ="\\\"";
    DBObject obj=null;
    String classhtml="gov.usgs.anss.edgemom\"";
    boolean complete=false;
    String line;
    boolean debug=true;
    DBConnectionThread jcjbl;
    Util.init("edge.prop");
    Util.prt(""+args.length);
    Util.setNoInteractive(true);
//		User u = new User("test");
    try {
        // Get user from the Inventory database user file
      /*jcjbl = new DBConnectionThread(Util.getProperty("MetaDBServer"),"readonly", "inv", false, false, "invJava", Util.getOutput());
      if(!jcjbl.waitForConnection()) 
        if(!jcjbl.waitForConnection()) 
          if(!jcjbl.waitForConnection()) 
            Util.prt(" ***** Did not connect to Inv ");*/
       
      // Get a new connection to the ANSS database
      if(args.length > 0) {Util.setProperty("DBServer", args[0]); Util.prt("Run against "+args[0]);}
      jcjbl = new DBConnectionThread(Util.getProperty("DBServer"),"update", "edge", true, false, "edgeJava", Util.getOutput());
      if(!jcjbl.waitForConnection()) 
        if(!jcjbl.waitForConnection()) 
          if(!jcjbl.waitForConnection()) 
            Util.prt(" ***** Did not connect to edge ");
    } catch (InstantiationException e) {
      System.err.println("Instantiation on getting DB connections"+e.getMessage());
      System.exit(1);
    }
    if (debug) Util.prt("Number of args = "+args.length);
    //String edgeroot = "http://gldsurvey/documentManagementSystem/edge/gov/usgs/anss/edgemom";
    //String edgeroot = "http://gldwatch.cr.usgs.gov/edge/";
    String edgeroot = "http://usgs.github.io/edgecwb/javadoc/";
    String [] packages = {"gov"+Util.fs+"usgs"+Util.fs+"cwbquery","gov"+Util.fs+"usgs"+Util.fs+"anss"+Util.fs+"edgemom",
      "gov"+Util.fs+"usgs"+Util.fs+"anss"+Util.fs+"fk","gov"+Util.fs+"usgs"+Util.fs+"anss"+Util.fs+"waveserver",
      "gov"+Util.fs+"usgs"+Util.fs+"anss"+Util.fs+"filterpicker","gov"+Util.fs+"usgs"+Util.fs+"anss"+Util.fs+"picker"};
    //String edgeroot = "http://localhost/edge/gov/usgs/anss/edgemom";
    if(args.length > 1) edgeroot = args[1];
    if(!edgeroot.endsWith("/")) edgeroot+="/";
   
    for(String pack : packages) {
      URL edgemomurl=null;
      URL classurl=null;
      BufferedReader edgemomreader = null;

      BufferedReader classreader = null;
      // First try a URL for Edgemom Package List
      try { 
        edgemomurl = new URL(edgeroot+pack+"/package-frame.html");
      }
      catch (MalformedURLException e) {
        if (debug) Util.prt ("Bad URL="+edgeroot+pack+"/package-frame.html");
        e.printStackTrace();
      }
      // create new reader to get file names for Edgemom classes
      try {

        edgemomreader = new BufferedReader(new InputStreamReader(edgemomurl.openStream()));
      }
        catch (Exception e){
          if (debug) Util.prt("Error"+e+" "+e.getMessage());
          e.printStackTrace();
      } 

      try {
        // Read in items from package-frame.html to find Edgemom Classes
        complete=false;
        while (complete==false){
          line = edgemomreader.readLine();
          if(line.contains("picker")) {
            Util.prt("Picker "+line);
          }
          //if(line.contains("gov.usgs.")) 
          // Util.prt(line);
          if (line.equalsIgnoreCase(endhtml)) complete=true;
          else if (line.contains(classhtml) || 
                line.contains("gov.usgs.anss.picker\"") && line.contains(">PickerManager<") ||
                (line.contains("gov.usgs.anss.picker\"") && line.contains(">CWBSubspaceManager<")) ||
                (line.contains("gov.usgs.cwbquery\"") && line.contains(">EdgeQueryServer<")) ||
                (line.contains("gov.usgs.anss.fk\"") && line.contains(">FKManager<")) ||
                (line.contains("gov.usgs.anss.waveserver\"") && line.contains(">CWBWaveServer<")) ||
                (line.contains("gov.usgs.cwbquery\"") && line.contains(">DataLinkToQueryServer<")) ||
                (line.contains("gov.usgs.cwbquery\"") && line.contains(">QuerySpanCollection<")) ||
                (line.contains("gov.usgs.cwbquery\"") && line.contains(">MiniSeedCollection<")) ||
                (line.contains("gov.usgs.anss.waveserver\"") && line.contains(">TrinetServer<")) 
                  ) {
            // Get reader for Edge Class
            int firstquote = line.indexOf('"');
            int secondquote = line.indexOf('"',firstquote+1);
            String edgeclass = line.substring(firstquote+1,secondquote);

            try { 
              Util.prt("Doing "+edgeroot+pack+"/"+edgeclass);
              classurl = new URL(edgeroot+pack+"/"+edgeclass);
            }
            catch (MalformedURLException e) {
              if (debug) Util.prt ("Bad URL");
              e.printStackTrace();
            }
            // create new reader to get file names for Edgemom classes
            try {

              classreader = new BufferedReader(new InputStreamReader(classurl.openStream()));
            }
              catch (Exception e){
                if (debug) Util.prt("Error"+e+" "+e.getMessage());
                e.printStackTrace();
            }
            // find Edgemom class in DB
            try {
              //Util.prt("start thread="+edgeclass.substring(0, edgeclass.indexOf('.')));
              obj = new DBObject(DBConnectionThread.getThread("edgeJava"), "edge", "edgethread","classname",edgeclass.substring(0, edgeclass.indexOf('.')));         
            } 
            catch (SQLException e) {
              System.err.println("SQLException on getting Edgethread"+e.getMessage());
              Util.SQLErrorPrint(e, "SQLExcepion on gettning Edgethread");
              System.exit(1);
            }       
            // pull description from Javadoc and write to DB
            StringBuilder sb = new StringBuilder(50);
            try {
              int rulesfound=0;
              String starting="<div class";
              String ending="</div>";
              String rule="<hr>";
              boolean start=false;
              boolean done=false;
              String line2;

              while (done==false && (line2 = classreader.readLine()) != null){
                //Util.prt(line2);
                /*if (line2.equalsIgnoreCase(rule)) {
                  rulesfound++;
                  if (rulesfound==2) start=true;
                  if (rulesfound>2) done=true;
                }*/
                if (start) {
                  sb.append(line2).append("\n");
                  //Util.prt(line2);
                }
                if(line2.startsWith(rule)) 
                  start=true;
                if(start && line2.indexOf(ending) == 0) 
                  done=true;

              }
            }
            catch (IOException e){
              if (debug) Util.prt("End of File");
              e.printStackTrace();
            }
            try {
              obj.setString("package", pack);
              obj.setString("classname",edgeclass.substring(0, edgeclass.indexOf('.')));
              obj.setString("help", sb.toString());
              obj.updateRecord();
              //Util.prt("Update class="+edgeclass.substring(0,edgeclass.indexOf('.')));
            }
            catch (SQLException e) {
              System.err.println("SQLException on setting help"+e.getMessage());
              Util.SQLErrorPrint(e, "SQLExcepion on setting help");
              System.exit(1);
            }  
          }
        }
      }
      catch (IOException e){
        if (debug) Util.prt("End of File");
        e.printStackTrace();
      }
    }
    Util.prta(Util.ascdate()+" Leaving CopyJavaDocToDB");
    System.exit(0);
  }
}
  

