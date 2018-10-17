/*
 * Copyright 2010, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
/*
 * MakeEconDump.java - This code makes a chan_rate.dat file which contains variances
 * between the Edge.channel rates and metadata rates. It is apparently not used any longer
 * though for a time in 2008 it was used to track changes in data rates.
 *
 * Created on March 5, 2008, 1:54 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.net;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
/**
 *
 * @author davidketchum
 */
public class MakeEconDump extends Thread {
  DBConnectionThread dbconnedge;
  DBConnectionThread dbconnmeta;
  boolean terminate;
  DecimalFormat df= new DecimalFormat("0.00000");
  public void terminate() {terminate=true; interrupt();}
  /** Creates a new instance of MakeEconDump */
  public MakeEconDump() {
    dbconnedge = DBConnectionThread.getThread("MED");
    if(dbconnedge == null) {
      try {
        dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", true, false, "MED", Util.getOutput());
        if(!DBConnectionThread.waitForConnection("MED"))
          if(!DBConnectionThread.waitForConnection("MED")) 
            if(!DBConnectionThread.waitForConnection("MED")) 
              if(!DBConnectionThread.waitForConnection("MED")) 
                Util.prta("MakeEconDump: Did not promptly connect to MED!");
      }
      catch(InstantiationException e ) {
        Util.prt("Instantiation error on edge db impossible");
        dbconnedge = DBConnectionThread.getThread("TCPHstatus");
      }
    } 
    dbconnmeta = DBConnectionThread.getThread("MEDmeta");
    if(dbconnmeta == null) {
      try {
        dbconnmeta = new DBConnectionThread(Util.getProperty("MetaDBServer"), "readonly","metadata",true,false,"MEDmeta", Util.getOutput());
        if(!DBConnectionThread.waitForConnection("MEDmeta"))
          if(!DBConnectionThread.waitForConnection("MEDmeta")) Util.prta("MakeEconDump: Did not promptly connect to metadata!");
      }
      catch(InstantiationException e ) {
        Util.prt("MakeEconDump: Instantiation error on meta db impossible");
        dbconnmeta = DBConnectionThread.getThread("MEDmeta");
      }
    } 
    Runtime.getRuntime().addShutdownHook(new ShutdownMakeEconDump());
    start();
  }
  @Override
  public void run() {
    double ratio;
    boolean addon;
    boolean addZero;
    double mdrate;
    StringBuilder sb = new StringBuilder(10000);
    while(!terminate) {
      try {
        Util.prta("report start");
        if(sb.length() > 0) sb.delete(0,sb.length());
        ResultSet rs = dbconnedge.executeQuery("SELECT ID,channel,rate FROM channel ORDER BY channel");
        PrintStream out = new PrintStream("chan_rate.dat");
        Statement delstmt = DBConnectionThread.getConnection("MED").createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_UPDATABLE);
        while(rs.next()) {
          if(!Util.isValidSeedName( (rs.getString("channel")+"           ").substring(0,12))) {
            Util.prta("MakeEconDump: got invalid seedname="+rs.getString("channel"));
            delstmt.executeUpdate("DELETE FROM channel WHERE id="+rs.getInt("ID"));
            continue;
          }
          if(rs.getString("channel").substring(0,1).equals("X")) continue;
          ResultSet rs2 = dbconnmeta.executeQuery("SELECT rate FROM channelmeta where channel='"+rs.getString("channel")+"'");
          addon=false;
          addZero=false;
          mdrate=0.;
          if(rs2.next()) {
            mdrate = rs2.getDouble("rate");
            ratio = rs.getDouble("rate")/rs2.getDouble("rate");
            if(ratio < 0.98 || ratio > 1.02) 
              addon=true;
          }
          else 
            addZero=true;
          out.println( (rs.getString("channel")+"     ").substring(0,12)+
              Util.leftPad(df.format(rs.getDouble("rate")), 12)+
              Util.leftPad(df.format(mdrate),12)+
              (addon?" ***" :"")+(addZero?" Not in MDS":""));
          if(addon) sb.append((rs.getString("channel")+"     ").substring(0,12)).
                  append(Util.leftPad(df.format(rs.getDouble("rate")), 12)).
                  append(Util.leftPad(df.format(mdrate),12)).append( "\n");
          try{sleep(10);} catch(InterruptedException e) {}
        }
        out.close();
        Util.prta("MakeEconDump: Report done");
        if(System.currentTimeMillis() % 86400000 < 7200000) {
          if(Util.getProperty("emailTo") != null) {
            SimpleSMTPThread.email(Util.getProperty("emailTo"),"_EdgeConfig to MDS rate mismatches",
              Util.ascdate()+" "+Util.asctime()+" This email comes from POCMySQLServer once per day for all channels \n"+
              "whose rates are inconsistent\n\nChannnel      EdgeConf        MDS\n"+sb.toString());
          }
        }
      }
      catch(SQLException e) {
        Util.SQLErrorPrint(e,"MakeEconDump: getting channel/rate");
      }
      catch(FileNotFoundException e) {
        Util.IOErrorPrint(e,"MakeEconDump: Opening chan_rate.dat or writing to it");
      }
      try{sleep(System.currentTimeMillis() % 7200000L+2100000);} catch(InterruptedException e) {}
    }
    Util.prta("MakeEconDump: is exiting");
  }
  private class ShutdownMakeEconDump extends Thread {
    public ShutdownMakeEconDump() {
      
    }
    
    /** this is called by the Runtime.shutdown during the shutdown sequence
     *cause all cleanup actions to occur
     */
    
    @Override
    public void run() {
      terminate();
      Util.prta("MakeEconDump: Shutdown started");
    }
  }
  public static void main(String [] args) {
    Util.init("edge.prop");
    MakeEconDump dump = new MakeEconDump();
    for(;;) 
      Util.sleep(100000);
  }
}
