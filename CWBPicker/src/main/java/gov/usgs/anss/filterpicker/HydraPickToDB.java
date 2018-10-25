/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.filterpicker;
import java.io.*;
import java.sql.*;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;

/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class HydraPickToDB {
  public static void main(String [] args) {
    String line;
    Util.setModeGMT();
    Util.init("edge.prop");
    // Open DB connection
    // this will keep a connection up to anss
    DBConnectionThread dbconn = DBConnectionThread.getThread("ECSedge");
    while( dbconn == null) {
      Util.setProperty("DBServer","localhost/3306:status:mysql:status");
      try { 
        Util.prta("Open ECSedge connection to "+Util.getProperty("DBServer"));
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"),"update","edge",
            true,false,"ECSedge", null);
        if(!DBConnectionThread.waitForConnection("ECSedge"))   // Wait twice
          if(!DBConnectionThread.waitForConnection("ECSedge")) 
            Util.prta("ECS: db connection did not open promptly");
        Util.prta("ECS: open db connection");

      }
      catch(InstantiationException e) {
        Util.prta("InstantiationException opening edge database in EdgeChannelServer e="+e.getMessage());
        Util.exit(1);
      }
    } 
    
    try {
      PreparedStatement insert = dbconn.prepareStatement("INSERT INTO status.hpick (channel,time,timems,associated,repicked,raypick) VALUES"+
            " (?,?,?,?,?,?)", true);
      Timestamp time = new Timestamp(0L);
      for (String arg : args) {
        try {
          BufferedReader in = new BufferedReader(new FileReader(arg));
          in.readLine();
          in.readLine();
          while ( (line = in.readLine()) != null) {
            String chan = line.substring(0,2)+line.substring(10,15)+line.substring(18,21)+line.substring(28,30);
            double dtime = Double.parseDouble(line.substring(38,52).trim());
            long ms = (long) (dtime*1000.+0.1);
            
            time.setTime(ms/1000*1000);
            ms = ms%1000;
            int assoc = (line.substring(72,75).equalsIgnoreCase("yes")? 1:0);
            int repick =(line.substring(76,79).equalsIgnoreCase("yes")? 1:0);
            int raypick =(line.substring(80).trim().equalsIgnoreCase("yes")? 1:0);
            insert.setString(1, chan);
            insert.setTimestamp(2, time);
            insert.setInt(3,(short) ms);
            insert.setInt(4, assoc);
            insert.setInt(5, repick);
            insert.setInt(6, raypick);
            insert.executeUpdate();
            
          }
        }catch(IOException e) {
          e.printStackTrace();
        }
      }
    }
    catch(SQLException e) {
      e.printStackTrace();
    }
  }
}
