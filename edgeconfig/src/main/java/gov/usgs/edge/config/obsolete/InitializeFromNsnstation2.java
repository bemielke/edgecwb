/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config.obsolete;
/*
 * InitializeFromNsnstation2.java
 *
 * Created on July 11, 2006, 2:03 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;

/**
 *
 * @author davidketchum
 */
public class InitializeFromNsnstation2 {
  
  /** Creates a new instance of InitializeFromNsnstation2 */
  public InitializeFromNsnstation2() {
  }

  /** This main Updates the channel table in edge with rates, NSN triplets, and adds new channels.
   *@param args command line args ignored*/
  public static void main(String args[]) {
    try {
      Util.exit("Obsolete code");
      // Get a new connection to the edge database
      DBConnectionThread C  = new DBConnectionThread("gacqdb.cr.usgs.gov/3306:edge:mysql:edge", "update", "edge", true, false, "edge", Util.getOutput());
      String seedname;
      String line ;
      int net;
      int node;
      int chan;
      double rate;
      BufferedReader in = new BufferedReader(new FileReader("nsnstation2.dat"));
      DBObject obj;
      int nchanged=0;
      int nnew=0;
      while( (line = in.readLine()) != null) {
        if(line.substring(0,3).equals("Sta")) continue;
        seedname = line.substring(10,12)+line.substring(0,5)+line.substring(6,9)+line.substring(12,14);
        obj = new DBObject(C, "edge", "channel", "channel", seedname);
        Util.prt("seedname="+seedname+" new="+obj.isNew());
        net = Integer.parseInt(line.substring(15,17),16);
        node = Integer.parseInt(line.substring(18,20),16);
        chan = Integer.parseInt(line.substring(21,23),16);
        rate = Double.parseDouble(line.substring(24,33).trim());
        if(obj.isNew() || net != obj.getInt("nsnnet") || node != obj.getInt("nsnnode") ||
            chan != obj.getInt("nsnchan") || Math.abs(rate-obj.getDouble("rate"))>0.0001) {
          Util.prt(seedname+" "+rate+" "+obj.getDouble("rate")+" "+net+"-"+node+"-"+chan+" "+
              obj.getInt("nsnnet")+"-"+obj.getInt("nsnnode")+"-"+obj.getInt("nsnchan"));
          obj.setInt("nsnnet", net);
          obj.setInt("nsnnode", node);
          obj.setInt("nsnchan", chan);
          obj.setDouble("rate",rate);
          if(!obj.isNew()) {
            obj.setString("channel", seedname);
            nchanged++;
          }else {
            obj.setString("channel",seedname);
            nnew++;
          }
          obj.updateRecord();          
        }

        obj.close();
      }
      Util.prta("# new="+nnew+" #changed="+nchanged);
      
    } catch (SQLException e) {
      System.err.println("SQLException on getting Channel"+e.getMessage());
      Util.SQLErrorPrint(e, "SQLExcepion on gettning Channel");
    }
    catch(InstantiationException e) {
      Util.prt("Impossible instantiation exception e="+e);
    }
    //catch (JCJBLBadPassword E) {
    //  Util.prt("bad password");
    //}
    catch(FileNotFoundException e) {
      Util.prt("could not open nsnstation2.dat"+e.getMessage());
    }
    catch(IOException e) {
      Util.prt("IOException found "+e.getMessage());
    }

  }   
  
}
