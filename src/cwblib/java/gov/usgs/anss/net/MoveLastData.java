/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.net;
//import java.io.*;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class MoveLastData {
  /**
   * @param args the command line arguments
   */
  public  static  void  main  (String  []  args)  {
    Util.setModeGMT();
    Util.init("edge.prop");
    String hostin = Util.getProperty("DBServer");
    String hostout = "cwb-pub/3306:edge:mysql:edge";
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-in")) hostin=args[i+1];
      if(args[i].equals("-out"))  hostout = args[i+1];
      if(args[i].equals("-?")) {
        Util.prt("MoveLastData [-in host][-out host2]");
        Util.prt("    -in host  database url  to read last data from channel table");
        //Util.prt("    -b yyyy-mm-dd The beginning date to include (records can lap this date)");
        Util.prt("    -out host database url to move the last data to ");
        System.exit(0);
      }
    }
    Util.init();
    Util.prta("\n******* "+Util.ascdate()+" move lastData from "+hostin+" to "+hostout);
    DBConnectionThread out=null;
    DBConnectionThread in =null;
    try {
      in = new DBConnectionThread(hostin,"update", "edge",true, false,"in", Util.getOutput());
      if(!DBConnectionThread.waitForConnection("in"))
        if(!DBConnectionThread.waitForConnection("in"))
          if(!DBConnectionThread.waitForConnection("in"))
            if(!DBConnectionThread.waitForConnection("in"))
              if(!DBConnectionThread.waitForConnection("in")) {
                Util.prta(" *** Could not connect to database abort run "+hostin);
                System.exit(1);
          }
      out = new DBConnectionThread(hostout,"update", "edge",true, false,"out", Util.getOutput());
      if(!DBConnectionThread.waitForConnection("out"))
        if(!DBConnectionThread.waitForConnection("out"))
          if(!DBConnectionThread.waitForConnection("out"))
            if(!DBConnectionThread.waitForConnection("out"))
              if(!DBConnectionThread.waitForConnection("out")) {
                Util.prta(" ***** Could not connect to database abort run "+hostout);
                System.exit(1);
          }
    }
    catch(InstantiationException e) {
      Util.prt("instantiation exception e="+e.getMessage());
      System.exit(0);
    }
    if(in == null || out == null) {
      SendEvent.edgeSMEEvent("MoveLastDB", "Did not open DB for  MoveLastData", "MoveLastData");
    }
    int count=0;
    int count2=0;
    try {
      Statement stmtout = out.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      ResultSet rsout = stmtout.executeQuery("SELECT ID,channel,lastdata,rate FROM channel ORDER BY channel");
      ResultSet rsin = in.executeQuery("SELECT channel,lastdata,rate FROM channel ORDER BY channel");
      while (rsout.next()) {
        count2++;
        String ch = rsout.getString("channel");
        Timestamp ld = rsout.getTimestamp("lastdata");
        double rate1 = rsout.getDouble("rate");
        while(rsin.next()) {
          String chin = rsin.getString("channel");
          if(chin.compareTo(ch) > 0) break;
          Timestamp ldin = rsin.getTimestamp("lastdata");
          double rate2 = rsin.getDouble("rate");
          if(chin.equals(ch)) {
            if(ld.getTime() < ldin.getTime() || rate1 != rate2) {
              rsout.updateTimestamp("lastdata", ldin);
              rsout.updateDouble("rate", rate2);
              rsout.updateRow();
              count++;
            }
            break;

          }
        }
      }
      rsout.close();
      rsin.close();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e, "Error updating");
    }
    DBConnectionThread.shutdown();
    Util.sleep(10000);
    Util.prta(Util.getThreadsString());
    Util.prta("Completed "+count+" updates of " +count2);
    System.exit(0);
  }
}
