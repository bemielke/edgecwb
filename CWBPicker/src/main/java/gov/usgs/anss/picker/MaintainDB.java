/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import static java.lang.Thread.sleep;
import gov.usgs.anss.edgethread.EdgeThread;

/**
 * This class insures that a DBConnectionThread to a given property name is
 * constantly maintained with the DBConnectionThread name set to the property
 * name. If it detects that the DBConnectionThread has a problem, it closes it
 * and recreates it. It is especially useful for other threads to delegate the
 * maintenance of the connedction and simply have a null returned if the
 * connection is bad.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class MaintainDB extends Thread {

  DBConnectionThread dbconn;
  String propertyName;
  String db;
  private final EdgeThread par;

  /**
   *
   * @return Get the DBConnectionThread maintained by this process, it is null
   * if something is wrong
   */
  public DBConnectionThread getDBThread() {
    return dbconn;
  }

  /**
   * The user does not like this thread, force it to reconnect.
   */
  public void setBad() {   // User got a problem, rebuild connection and leave bad until then.
    if (dbconn != null) {
      dbconn.close();
    }
    dbconn = null;          // we do not have a connection build it again
  }

  /**
   *
   * @param propname The property name to use to get the connection specifics
   * and set the name of the thread.
   * @param parent
   */
  public MaintainDB(String propname, EdgeThread parent) {
    par = parent;
    propertyName = propname;
    db = Util.getProperty(propname).substring(Util.getProperty(propname).lastIndexOf(":") + 1);
    if (DBConnectionThread.getThread(propertyName) != null) {
      parent.prta("MaintainDB : * curious, something else has created a thread by " + propertyName);
    }
    setDaemon(true);
    par.prta("MaintainDB: init for db=" + db + " prop=" + propname + "->" + Util.getProperty(propname));
    if (Util.getProperty(propname) == null) {
      return;
    }
    if (Util.getProperty(propname).equalsIgnoreCase("NoDB")) {
      return;
    }
    start();

  }

  @Override
  public void run() {
    while (true) {
      // while the thread is down, create it.
      while ((dbconn = DBConnectionThread.getThread(propertyName)) == null) {
        par.prta("MaintainDB: try to connect to " + Util.getProperty(propertyName));
        if (Util.getProperty(propertyName) != null) {
          try {
            // create a new DBConnThr
            dbconn = new DBConnectionThread(Util.getProperty(propertyName), "update", db,
                    false, false, propertyName, par.getPrintStream());
            par.addLog(dbconn);
            if (!DBConnectionThread.waitForConnection(propertyName)) {
              if (!DBConnectionThread.waitForConnection(propertyName)) {
                if (!DBConnectionThread.waitForConnection(propertyName)) {
                  if (!DBConnectionThread.waitForConnection(propertyName)) {
                    if (!DBConnectionThread.waitForConnection(propertyName)) {
                      if (!DBConnectionThread.waitForConnection(propertyName)) {
                        if (!DBConnectionThread.waitForConnection(propertyName)) {
                          par.prt("MaintainDB: *** Failed to get database access for " + propertyName + ". try in 30 s");
                          dbconn = null;
                          try {
                            sleep(30000);
                          } catch (InterruptedException e) {
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          } catch (InstantiationException e) {
            par.prta("InstantiationException: should be impossible");
            dbconn = DBConnectionThread.getThread(propertyName);
          }
        }
      }
      // while we have a connection, periodically ask if its still OK and close and cause it to be remade if not.
      while (dbconn != null) {
        if (!dbconn.isOK()) {
          par.prta("MaintainDB: connection has gone down " + propertyName);
          if (dbconn != null) {
            dbconn.close();
          }
          dbconn = null;
          break;
        } else {
          try {
            sleep(10000);
          } catch (InterruptedException e) {
          }  // wait 10 and try again
        }
      }
    }
  }
}
