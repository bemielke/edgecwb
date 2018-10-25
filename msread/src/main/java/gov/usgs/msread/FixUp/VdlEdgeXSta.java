/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * VdlEdgeXSta.java
 *
 * Created on October 31, 2007, 12:13 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.msread.FixUp;
import gov.usgs.anss.db.DBConnectionThread;
//import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author davidketchum
 */
public class VdlEdgeXSta {
  
  /** Creates a new instance of VdlEdgeXSta */
  public VdlEdgeXSta() {
  }
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setProcess("VdlEdgeXsta");
    Util.setNoInteractive(true);
    Util.setNoconsole(false);
    DBConnectionThread mysql;
    // To get here we are in "rawMode
    mysql = DBConnectionThread.getThread("edge");
    if(mysql == null) {
      try { 
        Util.prta("Start get connection");
        mysql = new DBConnectionThread(Util.getProperty("DBServer" ),"update", "edge",
            true,false,"edge", Util.getOutput());
        if(!DBConnectionThread.waitForConnection("edge"))
          if(!DBConnectionThread.waitForConnection("edge"))
            if(!DBConnectionThread.waitForConnection("edge")) {
            Util.prta("Could not connect to MySQL! exit");System.exit(0);}
      }
      catch(InstantiationException e) {
        Util.prta("InstantiationException opening edge database e="+e.getMessage());
        System.exit(1);
      }
    }
    try {
      String [] arglist = new String[8];
      arglist[0]="-s"; arglist[2]="-b"; arglist[4]="-d"; arglist[6]="-t"; arglist[7]="ms";
      // Use this statement for modifications
      long mask = (1<<11) | (1<<12) | (1<<13) |(1<<8);
      BufferedReader in = new BufferedReader(new FileReader("log/xsta_edge.dat"));
      StringBuilder sb = new StringBuilder(300000);
      StringBuilder sbout = new StringBuilder(3000);
      String line;
      int count=0;
      int count2=0;
      while( (line = in.readLine()) != null ) sb.append(line).append("\n");
      
      ResultSet rs = mysql.executeQuery("SELECT channel,rate FROM channel WHERE (sendto&"+mask+")!= 0 ORDER BY channel");
      Util.prt("#List of channels on Edge export list to VDL7, VDL8, ANS7 or ANS8 which are not in the xsta_edge.dat file");
      Util.prt("#stat chn ntll rate #");
      while(rs.next()) {
        String chan = (rs.getString("channel")+"    ").substring(0,12);
        String xsta = chan.substring(2,7)+" "+chan.substring(7,10)+" "+chan.substring(0,2)+chan.substring(10,12);
        int index = sb.indexOf(xsta);
        if(index < 0) {
          if(xsta.substring(8,9).equals("E")) {
            
            if(sb.indexOf(xsta.substring(0,8)+"2"+xsta.substring(9,14)) > 0 ) {
              Util.prt(xsta+" not found but '2' component is");
              continue;
            }
          }
          if(xsta.substring(8,9).equals("N")) {
            if(sb.indexOf(xsta.substring(0,8)+"1"+xsta.substring(9,14)) > 0 ) {
              Util.prt(xsta+" not found but '1' component is");
              continue;
            }
          }
          count++;
          Util.prt(xsta+" "+rs.getDouble("rate")+" "+count);
          sbout.append(xsta).append(" ").append(rs.getDouble("rate")).append("\n");
        }
        else {
          Util.prt(sb.substring(index+15, index+23));
          if(sb.substring(index+15, index+23).equals("        ")) {
            sbout.append(xsta).append(" ").append(rs.getDouble("rate")).append("\n");
            Util.prt(xsta+" "+rs.getDouble("rate")+" "+count+" blanks");           
          }
        }
      }
      
     // Now run list of XSTA_EDGE.dat stations that are not going to VMS
   
      if(count > 0 || count2 > 0) {
        Util.prta(count+" channels not found in xsta_edge.dat");
        FileWriter out = new FileWriter("log/xsta_edge.missing");
        out.write(sbout.toString());
        out.close();
      }
      
 
    }
    catch(FileNotFoundException e) {
      Util.prt("xsta_edge.dat file not found");
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"SQLError getting list or updating open");
      e.printStackTrace();
    }
    catch(IOException e) {
      Util.prt("IOException reading xsta_edge.dat "+e.getMessage());
      System.exit(0);
    }
    System.exit(0);
  }
}
