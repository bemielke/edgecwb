/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.k2;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
/** This class loads coordinates into invgs for stations in a text file from mark Meremonte
 *
 * @author U.S. Geological Survey  ketchum at usgs.gov
 */
public class LoadCoordinateText {
  public static void main(String [] args) {
    DBConnectionThread dbconn=null;
    try {
      String server = "gacqdb";
      dbconn = DBConnectionThread.getThread("edgeMAIN");
      if(dbconn == null) {
        dbconn = new DBConnectionThread(server, "update", "invgs", true, false,"edgeMAIN", Util.getOutput());

        if(!DBConnectionThread.waitForConnection("edgeMAIN"))
          if(!DBConnectionThread.waitForConnection("edgeMAIN"))
            if(!DBConnectionThread.waitForConnection("edgeMAIN")) {
              Util.prt(" **** Could not connect to database "+server);
              System.exit(1);
            }
      }
    }
    catch(InstantiationException e) {}
    String line;
    String station="";
    String [] parts;
    for(int i=0; i<args.length; i++) {
      try {
        BufferedReader in = new BufferedReader(new FileReader(args[i]));
        while( (line = in.readLine()) != null) {
          if(line.indexOf("STNID") == 0) {
            station=line.substring(6).trim();
            Util.prt("Station="+station);
          }
          else if(line.indexOf("COORD") == 0) {
            parts = line.split("\\s");
            try {
              ResultSet rs = dbconn.executeQuery("SELECT * FROM invgs.location WHERE location='"+station+"'");
              if(rs.next()) {
                Util.prt("Station exists "+station+" "+line+" "+rs.getDouble("latitude")+" "+rs.getDouble("longitude")+" "+rs.getDouble("elevation"));
                rs.close();
              }
              else {
                rs.close();
                String s = "INSERT INTO invgs.location (location,isStation,description,latitude,longitude,elevation,updated,installed,created_by,created)"+
                        " VALUES ('"+station+"',1,'"+station+"',"+parts[1]+","+parts[2]+","+parts[3].replaceAll("m","")+",now(),'1970-01-02',1,now())";
                Util.prt(s);
                dbconn.executeUpdate(s);
                int locID=dbconn.getLastInsertID("location");
                //
                s = "INSERT INTO invgs.item (item,person_id,agency,g_no,date_acquired,equiptypeID,subtableID,locationid,"+
                        "location,description,input_power,operating_status,updated,created_by,created)"+
                        " VALUES ('"+station+"',7,'NSMP','','2000-01-01',12,0,"+locID+",'"+station+"','','12 VDC','Spare',now(),6,now())";
                dbconn.executeUpdate(s);
                /*s = "INSERT INTO item (item,person_id,agency,g_no,date_acquired,equiptypeID,subtableID,locationid,"+
                        "location,description,input_power,operating_status,updated,created_by,created)"+
                        " VALUES ('"+station+"',7,'NSMP','','2000-01-01',13,0,"+locID+",'"+station+"','','12 VDC','Spare',now(),6,now())";
                dbconn.executeUpdate(s);
                 */
                s = "INSERT INTO invgs.locationconfig (location,digitizer,text,seednetwork,effective,updated,created_by,created) "+
                        "VALUES ('"+station+"','','K2DAS:100HZ-NONCAUSAL-HN','','2000-01-01',now(), 2, now())";
                dbconn.executeUpdate(s);
              }
            }
            catch(SQLException e) {
              Util.prt("SQLException e="+e);
              e.printStackTrace();
            }
          }
        }
      }
      catch(IOException e) {
        Util.prt("IOEerr="+e);
        e.printStackTrace();
      }
    }
    DBConnectionThread.shutdown();
    System.exit(0);
  }

}
