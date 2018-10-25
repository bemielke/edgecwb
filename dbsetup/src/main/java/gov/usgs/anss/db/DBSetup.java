/*
 * Copyright 2012, United States Geological Survey or
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
package gov.usgs.anss.db;

import gov.usgs.anss.util.Util;
import java.io.*;
import java.sql.*;
import javax.swing.JOptionPane;
/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class DBSetup {
  /*public static void main(String [] args) {
    String a = encrypt("D!vqI@Pa?","ansis42");    // test encrypt
    System.out.println(a);
    System.out.println(decrypt(edgeMask, "ansis42"));// test decrypt
  }*/
  public static void  main(String [] args) {
    try {
      String vendor=null;
      String host;
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      boolean ok =false;
      for(int i=0; i<args.length; i++) {
        if(args[i].equals("-q")) {
          quickMake(args[i+1],args[i+2]);
          System.exit(1);
        }
        if(args[i].equals("-dump")) {
          String randomSeed = getRandomSeed();
          DBContainer container = new DBContainer(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
                System.getProperty("file.separator")+
                    args[i+1], randomSeed); 
          Util.prt(container.read());
          System.exit(0);
        }
        if(args[i].equals("-gui")) {
          DBSetupGUI.main(args);
          for(;;) Util.sleep(10000);
        }
        if(args[i].equals("-usgs")) {
          UsgsUserMaint.main(args);
          System.exit(0);
        }
      }
      while (!ok) {
        Util.prt("Enter the database vendor 0=MySQL, 1=PostgreSQL, 2=Oracle");

        String line = in.readLine();
        if(line.length() ==1 ) {
          char c = line.charAt(0);
          ok=true;
          if(c == '0') vendor = "mysql";
          else if( c == '1') vendor ="postgres";
          else if( c == '2') vendor = "oracle";
          else ok=false;
        }
        else if(line.equals("usgs")) {
          Util.prt("Enter the local encrypt key");
          String encrypt = in.readLine();
          if(encrypt.length() <=0) encrypt="becky123";
          Util.prt("Enter host 136.177.24.92/3306");
          host = in.readLine();
          if(host.length() <=0) host="136.177.24.92/3306";
          vendor="mysql";
          String saveEncrypt=encrypt;
          encrypt = DBContainer.encrypt(encrypt, DBContainer.getMask());
          File f = new File(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn");
          if(!f.exists()) f.mkdir();
          PrintStream key = new PrintStream(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
                  System.getProperty("file.separator")+"dbconn.conf");
          key.println(encrypt);
          key.close();
          DBContainer e = new DBContainer(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
                  System.getProperty("file.separator")+"dbconn_"+vendor+"_"+host.replaceAll("/","_")+".conf",saveEncrypt);
          e.add("readonly","ro=readonly");
          e.add("update","vdl=nop240");
          try {
            DBConnectionThread db = new DBConnectionThread(Util.getProperty("DBServer"),"update", "edge", false, false,"name", Util.getOutput());
            if(!db.waitForConnection()) 
              if(!db.waitForConnection()) Util.prt("DID not connect");
            ResultSet rs = db.executeQuery("Select * from version");
            if(rs.next()) {
              Util.prt("Got version");
            }
          }
          catch(InstantiationException e2) {
                        Util.prt("SQLE="+e2);

          }
          catch(SQLException e2) {
            Util.prt("SQLE="+e2);
          }
          
          System.exit(0);
        }
        
      }
      File f = new File(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn");
      if(!f.exists()) f.mkdir();
      Util.prt("Enter the database host this must be a host name or IP address followed by / and port number e.g. '136.177.24.92/3306'");
      host = in.readLine();
      Util.prt("Enter the encrypt key");
      String encrypt = in.readLine();
      String saveEncrypt=encrypt;
      encrypt = DBContainer.encrypt(encrypt, DBContainer.getMask());
      PrintStream key = new PrintStream(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
              System.getProperty("file.separator")+"dbconn.conf");
      key.println(encrypt);
      key.close();
      DBContainer e = new DBContainer(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+System.getProperty("file.separator")+
              "dbconn_"+vendor+"_"+host.replaceAll("/","_")+".conf",saveEncrypt);
      ok=false;
      String schema="edge";
      while(!ok) {
        Util.prt("Enter the user name for the read only user (empty  line=skip this tag)");
        String ro = in.readLine();
        if(ro.length() <= 0) break;
        Util.prt("Enter the password for the read only user ");
        String ropw = in.readLine();
        Util.prt("Enter the schema to use for testing - default is 'edge'");
        schema = in.readLine();
        if(schema.equals("")) schema="edge";
        ok = testUser(host,vendor, ro,ropw, schema);
        if(!ok) Util.prt(" **** the user name and password could not connect to "+host+" as a "+vendor+" connection!");
        else 
          e.add("readonly",ro+"="+ropw);
      }
      ok=false;
      while(!ok) {

        Util.prt("Enter the user name for the update user (empty  line=skip this tag)");
        String vdl = in.readLine();
        if(vdl.length() <= 0) break;
        Util.prt("Enter the password for the update user ");
        String vdlpw = in.readLine();
        ok = testUser(host,vendor, vdl,vdlpw, schema);
        if(!ok) Util.prt(" **** the user name and password could not connect to "+host+" as a "+vendor+" connection!");
        else e.add("update",vdl+"="+vdlpw);
      }
      ok=false;
      while (!ok) {
        Util.prt("Do you want to enter any other database urls? (Hit return for No or 'y' for yes)");
        String line = in.readLine();
        if(line.length() <= 0) break; 
        if(line.substring(0,1).equalsIgnoreCase("y")) {
          Util.prt("Enter tag name for this URL - return=exit (this cannot be 'readonly' or 'update')");
          String url = in.readLine();
          if(url.length() <= 0) break;
          Util.prt("Enter the user name to associate with this tag");
          String u = in.readLine();
          if(u.length() <=0) continue;
          Util.prt("Enter the password for this user");
          String p = in.readLine();
          if(p.length() <=0) continue;
          e.add(url, u+"="+p);
          Util.prt("URL "+url+" has been added");
        }
        else break;
      }
      System.exit(0);
    }
    catch(IOException e) {
      Util.prt("IOError e="+e);
      e.printStackTrace();
    }
  }    
  public static boolean testUser(String h, String v, String u, String p, String schema) {
    boolean ok=true;
    try {
      DBConnectionThread db = new DBConnectionThread(h, schema, u, p, false, false, u, v);
      if(!db.waitForConnection()) 
        if(!db.waitForConnection()) 
          if(!db.waitForConnection()) ok=false;
      if(ok) Util.prt("Link to "+h+" vendor="+v+" user="+u+"edge  tests o.k");
      else {
        Util.prt(" ****** Link to "+h+" vendor="+v+" user="+u+" tests as NOT o.k.");
      }
      db.close();
        
    }
    catch(InstantiationException e) {
      ok=false;
    }
    return ok;
  }
  public static boolean checkEmpty() {
    File dir = new File(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn");
    if(!dir.exists()) {
      if(Util.isNEICHost()) { 
        if(Util.getProperty("DBServer") != null) quickMake(Util.getProperty("DBServer"),"user:ro=readonly");
        if(Util.getProperty("MetaDBServer") != null) quickMake(Util.getProperty("MetaDBServer"),"user:ro=readonly");
        if(Util.getProperty("StatusDBServer") != null) quickMake(Util.getProperty("StatusDBServer"),"user:ro=readonly");
        quickMake("localhost/3306:edge:mysql:edge","user:ro=readonly");
        //DCK removed code to create a connection to contingency
        return true;
      }
      else {
        quickMake("localhost/3306:edge:mysql:edge","user:ro=readonly");
        return true;
      }
    }    
    return false;
  }
  public static String [] quickGet(String dbstring, String key) {
    String [] parts =dbstring.split(":");
    String [] ips = parts[0].split("/");
    DBContainer container = new DBContainer(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
            System.getProperty("file.separator")+"dbconn_mysql_"+
                    ips[0]+"_"+ips[1]+".conf", DBSetup.getRandomSeed());
    try {
      return container.getKey(key).split("=");    
    }
    catch(IOException e) {
      Util.prt("IOError trying to get user key from .dbconn/dbconn_mysql...conf");
    }    
    return null;
  }
  public static void quickMake(String dbstring, String s) {
    File dir = new File(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn");
    if(!dir.exists()) {
      dir.mkdir();
    }
    String key="";
    File dbconf = new File(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
            System.getProperty("file.separator")+"dbconn.conf");
            
    if(!dbconf.exists()) {
      String ans ="";
      while(ans == null || ans.equals("") || ans.length() < 10) {
        ans = JOptionPane.showInputDialog(null, "Please enter a local encryption key.  You need not remember this so random is fine!");
        if(ans.equals("")) ans = DBSetupGUI.randomPassword();
      }
      String saveEncrypt=ans;
      if(ans.trim().equals("")) 
        key=ans;
      ans = DBContainer.encrypt(ans, DBContainer.getMask());
      File f = new File(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn");
      if(!f.exists()) f.mkdir();
      try {
        PrintStream keyout = new PrintStream(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
                System.getProperty("file.separator")+"dbconn.conf");
        keyout.println(ans);
        keyout.close();  
      }
      catch(FileNotFoundException e) {
        Util.prt("Could not create .dbconn/dbconn.conf file");
      }
    }
    else {
    }
    String randomSeed=getRandomSeed();
    String parts[]=dbstring.split(":");
    String ips[] = parts[0].split("/");
    if(ips.length >=2) {
        DBContainer container = new DBContainer(System.getProperty("user.home")+System.getProperty("file.separator")+".dbconn"+
                System.getProperty("file.separator")+"dbconn_mysql_"+
                    ips[0]+"_"+ips[1]+".conf", randomSeed);
      try {
        parts = s.split(":");
        //Util.prt("Set key="+parts[0]+" to "+parts[1]);
        container.add(parts[0], parts[1]);
        //Util.prt("user="+container.getKey("user")+" ro="+container.getKey("readonly")+" update="+container.getKey("update"));
        
        container.close();
      }
      catch(IOException e) {
        
      }
    }
  }
  public static String getRandomSeed() {
    String randomSeed="";
    try {
      BufferedReader dbconn = new BufferedReader(new FileReader(
              System.getProperty("user.home")+System.getProperty("file.separator")+
              ".dbconn"+System.getProperty("file.separator")+"dbconn.conf"));
      String line = dbconn.readLine();
      randomSeed = DBContainer.decrypt(line, DBContainer.getMask());
    }
    catch(IOException e) {
      Util.prt("Error reading .dbconn/dbconn.conf e="+e);
    }
    return randomSeed;
  }
}
