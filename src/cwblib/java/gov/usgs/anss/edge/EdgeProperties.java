/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;

/**
 * This class load the standard properties form edge.prop and then EDGEMOM/edge_N#I.prop as part of
 * initializing and Edge/CWB process.
 *
 * @author davidketchum
 */
public final class EdgeProperties {

  /**
   * Creates a new instance of EdgeProperties
   */
  public static void init() {
    Util.addDefaultProperty("ndatapath", "1");
    Util.addDefaultProperty("datapath", "/data/");  // TODO: Do the slashes need to be replaced here?
    Util.addDefaultProperty("nday", "3");
    Util.addDefaultProperty("daysize", "2000000");
    Util.addDefaultProperty("extendsize", "4000");
    Util.addDefaultProperty("logfilepath", "./");
    Util.addDefaultProperty("AlarmIP", "localhost");
    Util.addDefaultProperty("ebqsize", "10000");
    Util.addDefaultProperty("emailTo", "ketchum@usgs.gov");
    Util.init("edge.prop");
    Util.init("edge_" + Util.getSystemName() + ".prop");
    if (EdgeThread.getInstance() != null) {
      Util.init("EDGEMOM/edge_" + EdgeThread.getInstance() + ".prop");
    }
    //Util.prt("Srt: DBServer="+Util.getProperty("DBServer")+" Status="+Util.getProperty("StatusDBServer")+
    //        " meta="+Util.getProperty("MetaDBServer")+" mysql="+Util.getProperty("MySQLServer"));    if(Util.getProperties().size() == 0) Util.saveProperties();
    //System.out.println("ListProperties in EdgeProperties");
    //Util.prtProperties();
    // For installations without any setup, make sure the new stuff is set
    if (Util.getProperty("DBServer") == null && Util.getProperty("MySQLServer") != null) {
      Util.setProperty("DBServer", Util.getProperty("MySQLServer").trim() + "/3306:edge:mysql:edge");
    } else if (Util.getProperty("DBServer") == null) {
      Util.setProperty("DBServer", "localhost/3306:edge:mysql:edge");
    }
    if (Util.getProperty("MetaDBServer") == null) {
      if (Util.getProperty("StatusDBServer") != null) {
        Util.setProperty("MetaDBServer", Util.getProperty("StatusDBServer").replaceAll("status", "metadata"));
      } else {
        Util.setProperty("MetaDBServer", Util.getProperty("DBServer").replaceAll("edge", "metadata"));
      }
    }
    if (Util.getProperty("StatusDBServer") == null) {
      Util.setProperty("StatusDBServer", Util.getProperty("DBServer").replaceAll("edge", "status"));
    }
    DBConnectionThread.init(Util.getProperty("DBServer"));
    //Util.prt("End: DBServer="+Util.getProperty("DBServer")+" Status="+Util.getProperty("StatusDBServer")+
    //        " meta="+Util.getProperty("MetaDBServer")+" mysql="+Util.getProperty("MySQLServer"));
  }

  public static void main(String[] args) {
    EdgeProperties.init();
  }

}
