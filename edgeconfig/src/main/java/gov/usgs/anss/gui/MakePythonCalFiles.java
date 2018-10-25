/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.util.*;
import gov.usgs.anss.db.*;
import java.io.*;
import java.sql.*;

/**
 *
 * @author davidketchum
 */
public class MakePythonCalFiles {

  public static void main(String[] args) {
    Util.setProcess("MakePythonCalFiles");
    Util.init("edge.prop");
    User user = new User("dkt");
    Util.setNoInteractive(true);
    DBConnectionThread dbanss = null;
    try {
      dbanss = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss", true, false, "anss", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("anss")) {
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              if (!DBConnectionThread.waitForConnection("anss")) {
                Util.prta("Could not connect to DB abort run");
              }
            }
          }
        }
      }
      UC.setConnection(DBConnectionThread.getConnection("anss"));
    } catch (InstantiationException e) {
      Util.prt("instantiation exception e=" + e.getMessage());
      System.exit(0);
    }
    try {
      ResultSet rs = dbanss.executeQuery("SELECT * FROM anss.tcpstation WHERE q330!='' ORDER BY tcpstation");
      while (rs.next()) {
        TCPStation station = new TCPStation(rs);
        if (station.getNQ330s() > 0) {
          try (PrintStream out = new PrintStream("Python/Stations/" + station.getTCPStation() + ".config")) {
            for (int i = 0; i < station.getNQ330s(); i++) {
              out.println("Q330." + (i + 1) + ".IP = " + station.getQ330InetAddresses()[i].toString().substring(1));
              out.println("Q330." + (i + 1) + ".Serial = " + station.getHexSerials()[i].replaceAll("0x1", "01").replaceAll("0X1", "01"));
              out.println("Q330." + (i + 1) + ".BasePort = " + station.getQ330Ports()[i]);
              //Util.print("Q330 "+station.getTCPStation()+" authcode="+station.getAuthCode(i, 1)+" "+station.getAuthCode(i,2)+" "+station.getAuthCode(i,3));
              String auth = station.getAuthCode(i, 1);
              if (auth.equals("0x0")) {
                auth = "0";
              } else if (auth.endsWith("d1") || auth.endsWith("D1")) {
                auth = (auth.substring(0, auth.length() - 2) + "C").replaceAll("x", "").replaceAll("X", "");
              }
              out.println("Q330." + (i + 1) + ".AuthCode = " + auth + "\n");
            }
          }
          File autocal = new File("Python" + Util.fs + "Stations" + Util.fs + "autocal.config." + station.getTCPStation());
          if (!autocal.exists()) {
            try (PrintStream auto = new PrintStream("Python/Stations/autocal.config." + station.getTCPStation())) {
              auto.println("Empty autocal");
            }
          }

        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "SQL getting tcpstations");
    } catch (IOException e) {
      Util.prt("Got error trying to write out Q330 config files e=" + e);
      e.printStackTrace();
    }
    Util.prta("Done");
    System.exit(0);
  }
}
