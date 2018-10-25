/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.db;

import gov.usgs.anss.util.Util;
import java.io.*;
import java.sql.*;
import java.io.InputStreamReader;

/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class UsgsUserMaint {
  static DBConnectionThread dbconnconfig=null,dbconnmeta=null,dbconnstatus=null;
  public static void main(String [] args) {
    Util.init("edge.prop");
    StringBuilder usersb = new StringBuilder(20);
    
    try {

      dbconnconfig = new DBConnectionThread(Util.getProperty("DBServer"),"rootconfig",
              "mysql",  true, false,"config", null);
      if(!DBConnectionThread.waitForConnection("config"))
        if(!DBConnectionThread.waitForConnection("config"))
          if(!DBConnectionThread.waitForConnection("config")) {
            Util.prta(" **** Could not connect to database "+Util.getProperty("DBServer"));
            System.exit(1);
          }

      dbconnmeta = new DBConnectionThread(Util.getProperty("MetaDBServer"),"rootmeta",
              "mysql",  true, false,"meta", null);
      if(!DBConnectionThread.waitForConnection("meta"))
        if(!DBConnectionThread.waitForConnection("meta"))
          if(!DBConnectionThread.waitForConnection("meta")) {
            Util.prta(" **** Could not connect to database "+Util.getProperty("MetaDBServer"));
            System.exit(1);
          }
      dbconnstatus = new DBConnectionThread(Util.getProperty("StatusDBServer"), "rootstatus",
              "mysql",  true, false,"status", null);
      if(!DBConnectionThread.waitForConnection("status"))
        if(!DBConnectionThread.waitForConnection("status"))
          if(!DBConnectionThread.waitForConnection("status")) {
            Util.prta(" **** Could not connect to database "+Util.getProperty("DBServer"));
            System.exit(1);
          }
    }
    catch(InstantiationException e) {
      Util.prta(" **** Impossible Instantiation exception e="+e);
      System.exit(0);
    }
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      for(;;) {
        Util.prt("1=add new user, 2=Grant all users in config and create in other DBs, 3=delete user, 4=reset user password");
        String line = in.readLine();
        if(line == null) break;
        if(line.length() <= 0) break;
        if(line.charAt(0) >= '9') break;
        if(line.equals("1")) {
          Util.prt("User name : ");
          String user=in.readLine();
          Util.prt("Password (def=nop240):");
          String pw = in.readLine();
          pw = Util.sqlEscape(pw).toString();
          if(pw.trim().equals("")) pw="nop240";
          Util.prt("Grant INV access (y or n) :");
          String inv = in.readLine();
          if(user.length() > 0) {
            user = Util.sqlEscape(user).toString();
            createUser(dbconnconfig, user, pw);
            createUser(dbconnmeta, user, pw);
            createUser(dbconnstatus, user,pw);      
            doGrant(user,inv);
          }
          else Util.prta("***** user name is empty");
        }
        else if(line.equals("2")) {
          Util.prt("User (Def=all users) : ");
          String user = in.readLine();
          Util.prt("Grant INV access (y or n) :");
          String inv = in.readLine();
          if(user.length() > 0) {
            user = Util.sqlEscape(user).toString();
            try {
              ResultSet rs = dbconnconfig.getNewStatement(false).executeQuery("SELECT DISTINCT user,password FROM mysql.user "+
                      (user.trim().equals("")?"":" WHERE user="+user+" ")+" ORDER BY user");
              while(rs.next()) {
                String pw = rs.getString(2);
                user = rs.getString(1);
                if(user.equals("root")) continue;   // do not do root.
                createUser(dbconnmeta, user,"nop240");
                createUser(dbconnstatus, user,"nop240");
                doGrant(user, inv);
                dbconnstatus.executeUpdate("SET PASSWORD FOR "+user+"@'%' = "+pw+"");
                dbconnstatus.executeUpdate("SET PASSWORD FOR "+user+"@'localhost' = "+pw+"");
                dbconnmeta.executeUpdate("SET PASSWORD FOR "+user+"@'%' = "+pw+"");
                dbconnmeta.executeUpdate("SET PASSWORD FOR "+user+"@'localhost' = "+pw+"");
              }
              rs.close();
            }
            catch(SQLException e) {
              e.printStackTrace();
            }
          }
          else Util.prta("**** username is empty!");
        }
        if(line.equals("3")) {
          Util.prt("User name : ");
          String user=in.readLine();
          if(user.length() > 0) {
              user = Util.sqlEscape(user).toString();
            try {
              dbconnconfig.executeUpdate("DROP USER "+user+"@localhost;");
            }   catch(SQLException e) { e.printStackTrace();}
            try {
              dbconnconfig.executeUpdate("DROP USER "+user+"@'%';");
            }   catch(SQLException e) { e.printStackTrace();}
            try {
              dbconnmeta.executeUpdate("DROP USER "+user+"@localhost;");
            }   catch(SQLException e) { e.printStackTrace();}
            try {
              dbconnmeta.executeUpdate("DROP USER "+user+"@'%';");
            }   catch(SQLException e) { e.printStackTrace();}
            try {
              dbconnmeta.executeUpdate("DELETE FROM inv.person where person='"+user.replaceAll("'","")+"'");
            }   catch(SQLException e) { e.printStackTrace();}
            try {
              dbconnstatus.executeUpdate("DROP USER "+user+"@localhost;");
            }   catch(SQLException e) { e.printStackTrace();}
            try {
              dbconnstatus.executeUpdate("DROP USER "+user+"@'%';");
            }   catch(SQLException e) { e.printStackTrace();}
            Util.prta("DROP USER "+user+"@localhost and '%' complete.");
          }
        }
        if(line.equals("4")) {
          Util.prt("User name : ");
          String user=in.readLine();
          Util.prt("Password (def=nop240):");
          String pw = in.readLine();
          pw = Util.sqlEscape(pw).toString();
          if(user.length() > 0) {
            try {
              user = Util.sqlEscape(user).toString();
              Util.prt("SET PASSWORD FOR "+user+"@'%' = "+pw);
              dbconnconfig.executeUpdate("SET PASSWORD FOR "+user+"@'%' = password("+pw+")");
              dbconnconfig.executeUpdate("SET PASSWORD FOR "+user+"@'localhost' = password("+pw+")");
              dbconnstatus.executeUpdate("SET PASSWORD FOR "+user+"@'%' = password("+pw+")");
              dbconnstatus.executeUpdate("SET PASSWORD FOR "+user+"@'localhost' = password("+pw+")");
              dbconnmeta.executeUpdate("SET PASSWORD FOR "+user+"@'%' = password("+pw+")");
              dbconnmeta.executeUpdate("SET PASSWORD FOR "+user+"@'localhost' = password("+pw+")");
            }
            catch(SQLException e) {
              e.printStackTrace();
            }
          }
        }
        try {
          dbconnconfig.executeUpdate("FLUSH PRIVILEGES");
          dbconnmeta.executeUpdate("FLUSH PRIVILEGES");
          dbconnstatus.executeUpdate("FLUSH PRIVILEGES");
        }
        catch(SQLException e) {
          e.printStackTrace();
        }
      }

    }
    catch(IOException e) {
      e.printStackTrace();
    }
  }
  private static void doGrant(String user, String inv) {
    try {
      if(user.equals("root")) return;   // do not process root
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on anss.* TO "+user+"@'%'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on anss.* TO "+user+"@'localhost'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on edge.* TO "+user+"@'%'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on edge.* TO "+user+"@'localhost'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on alarm.* TO "+user+"@'%'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on alarm.* TO "+user+"@'localhost'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on fetcher.* TO "+user+"@'%'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on fetcher.* TO "+user+"@'localhost'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on portables.* TO "+user+"@'%'");
      dbconnconfig.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on portables.* TO "+user+"@'localhost'");

      dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on metadata.* TO "+user+"@'%'");
      dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on metadata.* TO "+user+"@'localhost'");
      dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on irserver.* TO "+user+"@'%'");
      dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on irserver.* TO "+user+"@'localhost'");
      dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on qml.* TO "+user+"@'%'");
      dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on qml.* TO "+user+"@'localhost'");
      if(inv.equalsIgnoreCase("y")) {
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on inv.* TO "+user+"@'%'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on inv.* TO "+user+"@'localhost'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on invgs.* TO "+user+"@'%'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on invgs.* TO "+user+"@'localhost'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on invmisc.* TO "+user+"@'%'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on invmisc.* TO "+user+"@'localhost'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on invims.* TO "+user+"@'%'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on invims.* TO "+user+"@'localhost'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on digit.* TO "+user+"@'%'");
        dbconnmeta.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on digit.* TO "+user+"@'localhost'");  
        String ins = "INSERT INTO inv.person (person,master,password,role,name,updated,created_by,created) VALUES "+
                "("+user+",'Y','','M',"+user+",now(),1,now())";
        Util.prta(user+ "INS: "+ins);
        int ans = dbconnmeta.executeUpdate(ins);
        if(ans == -1) Util.prt("** User is duplicate in inv.person table");
        if(ans == -2) Util.prt("** User is updated is needing truncation in inv.person table");
        Util.prt("ans="+ans);
      }

      dbconnstatus.executeUpdate("GRANT SELECT on status.* TO "+user+"@'%'");
      dbconnstatus.executeUpdate("GRANT SELECT on status.* TO "+user+"@'localhost'");
    }
    catch(SQLException e) {
      Util.prt("Unexpected SQL error - e="+e);
      e.printStackTrace();
    }
  }
  private static void createUser(DBConnectionThread dbconn, String user, String pw) {
    try {
      if(user.equals("root")) return;   // do not process root
      if(pw.trim().equals("")) pw = "*D9DD25481346D02B50B630D258A1D9024D373A78";
      ResultSet rs = dbconn.executeQuery("SELECT * FROM user WHERE user="+user+" AND host='%'");
      if(!rs.next()) {
       dbconn.executeUpdate("CREATE USER "+user+"@'%' IDENTIFIED BY "+pw+";");
        Util.prt(dbconn.getTag()+" "+user+"@'%' created!");  
      }
      else Util.prt(dbconn.getTag()+" "+user+"'@'%' exists");
      rs.close();
      rs = dbconn.executeQuery("SELECT * FROM user WHERE user="+user+" AND host='localhost'");
      if(!rs.next()) {
       dbconn.executeUpdate("CREATE USER "+user+"@'localhost' IDENTIFIED BY "+pw+";");
        Util.prt(dbconn.getTag()+" "+user+"@'localhost' created!");  
      }
      else Util.prt(dbconn.getTag()+" "+user+"'@'localhost' exists");
      rs.close();
     }
     catch(SQLException e) {
       Util.prt("create user "+dbconn.getTag()+" failed - does user exist e="+e);
     }  
  }
}
