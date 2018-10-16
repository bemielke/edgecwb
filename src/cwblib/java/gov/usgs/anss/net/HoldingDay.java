/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.net;

//import java.lang.Math;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import gov.usgs.alarm.SendEvent;
import java.sql.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
/**
 *
 * @author davidketchum
 */
public class HoldingDay implements Comparable {
  private static String table="holdingsday";      // this is the table to be use (holdings or holdingshist)
  private static final GregorianCalendar gc = new GregorianCalendar();
  private static int biggestID;
  private static long lookupMS;
  private static long insertMS;
  private static long readbackMS;
  private static long updateMS;
  private static long ninsert;
  private static long nupdate;
  private static long nreadback;
  private static long nlookup;
  private int ID;
  private String seedName;      // net/station/channel/location
  private String type;          // Two characters for source
  private long start;           // Start time in Timestamp Milliseconds
  private long finish;             // Time finish in Timestamp milliseconds
  private int yrdoy;            // THis is the key for each holding
  private int tolerance;        // In ms, depends on station channel
  private boolean deferUpdates; // If true, update only on command, else each change
  private boolean modified;     // Indicate if changes have been made
  private long lastUpdate; // How long since something has changed
  private long lastWritten;//  how long since something written to persistent storage
  private int createType;
  private boolean outOfScope;   // When a holdings is purged , this will get set at each update.
  // PreparedStatement stuff
  private static PreparedStatement doHolding;
  private static PreparedStatement manualWrite;
  private static PreparedStatement selectID;
  private static Timestamp tsend = new Timestamp(100000L);
  private static Timestamp tsstart = new Timestamp(100000L);
  private static Timestamp ts = new Timestamp(100000L);
  private static PreparedStatement updateStart,updateEnd;
  private static PreparedStatement deleteHolding;
  private static PreparedStatement updateHolding;
  private static PreparedStatement existsHolding;

  // Static
  private static boolean doNotAutoWrite;
  private StringBuilder scratch = new StringBuilder(100);
  //static Statement stmt;       // The statement we use to create and update this in MySQL
  private static DBConnectionThread C; // A permanently up MySQLConnection
  private static final Integer dbMutex = Util.nextMutex();
  public static int getYrdoy(long ms) {
    synchronized(gc) {
      gc.setTimeInMillis(ms);
      return gc.get(Calendar.YEAR)*1000+gc.get(Calendar.DAY_OF_YEAR);
    }
  }
  /** Creates a new instance of Holding */
  private boolean dbg=false;
  public static void setDoNotAutoWrite() {doNotAutoWrite=true;}
  public boolean getOutOfScope() {return outOfScope;}
  private void reopenDB() {
    synchronized(dbMutex) {
      C.reopen();
      updateStart=null;
      updateEnd=null;
      updateHolding=null;
      existsHolding = null;
      deleteHolding=null;
      deleteHolding=null;
      doHolding=null;
      manualWrite=null;
      selectID=null;
    }

  }
  public static void useHistory2() {table="holdingsdayhist2";}
  public static void useHistory() {table="holdingsdayhist";}
  public static void useHoldings() {table="holdingsday";}
  public static void useSummary() {table="holdingssummary";}
  public static String getHoldingStats() {return "lkup="+lookupMS+"/"+nlookup+" ins="+insertMS+"/"+ninsert+
          " update="+updateMS+"/"+nupdate+
          " read="+readbackMS+"/"+nreadback+" bigID="+biggestID;}
  /** set the holdings to use a particular database connection
   *@param Cin The database connection to use
   */
  public static void setHoldingsConnection(DBConnectionThread Cin) {
    C = Cin;
  }
  public void manualWrite() throws SQLException  {
    synchronized(dbMutex) {
      int ok=0; 
      while(ok<=1) {
        try {
          long now = System.currentTimeMillis();
          if(manualWrite == null) manualWrite = C.prepareStatement("INSERT INTO status."+table+
                    " (seedname,type,start,start_ms,ended,end_ms,yrdoy) VALUES (?,?,?,?,?,?,?)", true);
          manualWrite.setString(1,seedName);
          manualWrite.setString(2,type);
          tsstart.setTime(start/1000*1000);
          manualWrite.setTimestamp(3,tsstart);
          manualWrite.setShort(4,(short)(start%1000));
          tsend.setTime(finish/1000*1000);
          manualWrite.setTimestamp(5, tsend);
          manualWrite.setInt(6,(short)(finish%1000));
          manualWrite.setInt(7, yrdoy);
          manualWrite.executeUpdate();
          ID=C.getLastInsertID(table);
          ninsert++;
          insertMS += System.currentTimeMillis() - now;
          break;
        }
        catch(SQLException e) {
          if(ok > 0) {
            Util.SQLErrorPrint(e,"SQL Error manual write holding for "+seedName);
            SendEvent.edgeSMEEvent("HoldingFaile", "Manual "+toString2()+" e="+e, this);
            throw e;
          }
          reopenDB();
          ok++;          
        }
      }
    }

  }
  public static void setDefaultHoldingsConnection() {
    Util.prta("***** holdingsday is using the default connection "+C);
    DBConnectionThread  db = DBConnectionThread.getThread("HoldingStatusDay");
    if(db == null) {
      try {
        db = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update","status",
          true,false,"HoldingStatusDay",Util.getOutput());
        if(!db.waitForConnection()) 
          if(!db.waitForConnection()) 
             if(!db.waitForConnection()) Util.prt("Holdings: ******Open default holding connection failed!");
      }
      catch(InstantiationException e ) {
        Util.prt("Instantiation error on status db impossible");
        db = DBConnectionThread.getThread("HoldingStatusDay");
      }
    }
    setHoldingsConnection(db);
  }
  public HoldingDay (String name,String ty, long time,long ms,boolean lookup) {

    start=time;
    finish=time+ms;
    seedName=name;
    yrdoy = getYrdoy(time);
    type=ty;
    String tm="";
    modified=false;
    deferUpdates=false;
    setTolerance();
    createType=-1;

  }
  
  public HoldingDay(String name, String ty, long time, long ms)  {
    doHolding(name,ty,time,ms);
    createType=1;
    if(ID == 0 && !doNotAutoWrite) Util.prt("Create Holding type 1 ID is zero!! "+toString());
    //else Util.prt("Create Holding type 1 "+toString());
  }
  /** build a Holding from a Holding ID
   *@param IDin The ID of the result set to read/build
   * @throws java.lang.InstantiationException
   */
  public HoldingDay(int IDin) throws InstantiationException {
    createType=2;
    if(C == null) setDefaultHoldingsConnection();
    ID=IDin;
    int ok=0;
    while(ok <= 1) {
      synchronized(dbMutex) {
        try {
          long now = System.currentTimeMillis();
          if(selectID == null) {
            selectID= C.prepareStatement("SELECT * FROM status."+table+" WHERE ID=?",false);
          }
          if(ID==0) Util.prt("Attempt to make holding where IDin=0!!"+toString());
          /*String s = "SELECT * FROM status."+table+" WHERE ID=" + Util.sqlEscape(ID);
          ResultSet rs = C.executeQuery(s);*/
          selectID.setInt(1,ID);
          try (ResultSet rs = selectID.executeQuery()) {
            if(rs.next()) {
              doResultSet(rs);
            } else throw new InstantiationException("THere is no Holding in "+table+" with id="+IDin);
          }
          readbackMS = System.currentTimeMillis() - now;
          nreadback++;
          break;
        }
        catch(SQLException e) {
          if(ok > 0) {
            Util.SQLErrorPrint(e,"SQL Error Getting status."+table+" ID="+ID);
            SendEvent.edgeSMEEvent("HoldingFaile", "Const "+ toString2()+" e="+e, this);
          }
          reopenDB();        
          ok++;
        }
      }
    }
  }
  /** given a positioned resultSet build a holding
   *@param rs a positioned resultSet
   */
  public HoldingDay(ResultSet rs) {
    createType=3;
    doResultSet(rs);
  }
  /** given a Positioned result Set, build up the data we need
   */
  private void doResultSet(ResultSet rs) {
    try {
      if(C == null) setDefaultHoldingsConnection();
      ID= rs.getInt("ID");
      if(ID == 0) Util.prt("doResultSet with RS having a ID=0!!"+toString());
      if(dbg) Util.prta("Holding: Found existing="+ID+
              " srt="+rs.getTimestamp("start")+" "+rs.getTimestamp("start").getTime()+" "+rs.getShort("start_ms")+
              " end="+rs.getTimestamp("ended")+" "+rs.getShort("end_ms"));
      seedName=rs.getString("seedname");
      yrdoy = rs.getInt("ydrdoy");
      type=rs.getString("type");
      start=rs.getTimestamp("start").getTime();
      start += rs.getShort("start_ms");
      finish=rs.getTimestamp("ended").getTime();
      finish += rs.getShort("end_ms");
      lastWritten=System.currentTimeMillis();
      lastUpdate=lastWritten;
      modified=false;
      setTolerance();
      deferUpdates=false;
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"SQL Error Getting holding for ResultSet="+ID);
      if(!e.getMessage().contains("empty result set")) Util.exit(1);
    }
  }
  public void close() {}

  /** repopulate this holding with entirely new information (similar to creating
   *a new one, but it simply reuses an existing object
   * @param name The seedname for the holding
   * @param ty The type of holdings (2 char) like "CW"
   * @param time The time in millis for the first sample of the holdings
   * @param ms The duration of the holding in milliseconds
   */
  public void replaceHolding(String name, String ty, long time, long ms) {
    doHolding(name,ty,time,ms);
  }
    /** reload this holding with entirely new information (similar to creating
   *a new one, but it simply reuses an existing object
   * @param name The seedname for the holding
   * @param ty The type of holdings (2 char) like "CW"
   * @param time The time in millis for the first sample of the holdings
   * @param ms The duration of the holding in milliseconds
   */
  public void reload(String name, String ty, long time, long ms) {
    if(!Util.isValidSeedName(name)) {
      Util.prta("Holdings rejected bad seed="+name);
      throw new RuntimeException("Holdings rejected bad seed="+name);
    }
    // Times before 1970 and after 2050 are very bad
    if(time <= 0 || time >= 365*86400000L*70 ) {
      Util.prta("Holdings rejeact bad time="+time); 
      throw new RuntimeException("Holding rejected bad time="+time);
    }
    start=time;
    finish=time+ms;
    seedName=name;
    type=ty;
    yrdoy = getYrdoy(start);
    //Timestamp early = new Timestamp(time-604800);
    modified=false;
    setTolerance();
    lastUpdate=System.currentTimeMillis();
  }
  /** make a holdings
   *
   * @param name The seedname for the holding
   * @param ty The type of holdings (2 char) like "CW"
   * @param time The time in millis for the first sample of the holdings
   * @param ms The duration of the holding in milliseconds
   */
  private void doHolding(String name, String ty, long time, long ms) {
    if(!Util.isValidSeedName(name)) {
      Util.prta("Holdings rejected bad seed="+name);
      throw new RuntimeException("Holdings rejected bad seed="+name);
    }
    // Times before 1970 and after 2050 are very bad
    if(time <= 0 || time >= 365*86400000L*70 ) {
      Util.prta("Holdings rejeact bad time="+time); 
      throw new RuntimeException("Holding rejected bad time="+time);
    }

    start=time;
    finish=time+ms;
    seedName=name;
    yrdoy=getYrdoy(time);
    type=ty;
    //Timestamp early = new Timestamp(time-604800);
    modified=false;
    deferUpdates=false;
    setTolerance();
    lastUpdate=System.currentTimeMillis();
    long now;
    long now2;
    int ok=0;
    while(ok <= 1) {
      try {
        if(C == null) setDefaultHoldingsConnection();
        //ts = new Timestamp(time);
        //Timestamp ts2 = new Timestamp(time+ms);
        now2 = System.currentTimeMillis();
        if(!doNotAutoWrite) {
          synchronized(dbMutex) {

            if(dbg) Util.prta("Holding: new holding="+seedName+" str="+new Timestamp(time)+" ms="+ms);
            ts.setTime(time/1000*1000+1000);
            if(doHolding == null) 
              doHolding=C.prepareStatement("INSERT INTO status."+table+
                      " (seedname,type,start,start_ms,ended,end_ms,yrdoy) VALUES (?,?,?,?,?,?,?)",true);
            if(dbg) Util.prta("Holding: Create via="+seedName+" "+type+" "+start+" end="+finish);
            //C.executeUpdate(s);
            doHolding.setString(1,seedName);
            doHolding.setString(2,type);
            tsstart.setTime(time/1000*1000);
            doHolding.setTimestamp(3,tsstart);
            doHolding.setShort(4, (short)(time%1000));
            tsend.setTime(finish/1000*1000);
            doHolding.setTimestamp(5,tsend);
            doHolding.setShort(6,(short) (finish%1000));
            doHolding.setInt(7, yrdoy);
            doHolding.executeUpdate();
            ID=C.getLastInsertID(table);
            now = System.currentTimeMillis();
            insertMS += (now-now2);
            ninsert++;

            if(ID > biggestID) biggestID=ID;
            if(dbg) Util.prt("Holding:ID of new is "+ID);
            lastWritten=now;
            lastWritten = lastWritten+(ID % 100)*1000;// randomize the timers
            break;
          }
        }
        else break;
      }
      catch(SQLException e) {
        if(ok > 0) {
          Util.SQLErrorPrint(e,"SQL Error Getting holding for "+ts+" "+seedName);
          SendEvent.edgeSMEEvent("HoldingFaile", "Doholding "+toString2()+" e="+e, this);
        }
        e.printStackTrace();
        reopenDB();
        ok++;
      }
    }

  }
  

  /** Set state of defer Update flag
   *@param b State to set it to
   */
  public void setDeferUpdate(boolean b) { deferUpdates=b;}
  /** return state of defer Update flag
   *@return the defer udpate flag
   */
  public boolean getDeferUpdate() { return deferUpdates;}
  /** try to add this covered period to a holding
   *@param name The seed name to match
   *@param ty The seed type to match
   *@param time ms of time (per a Timestamp)
   *@param ms the length of the packet in ms
   *@return true if this is within or extends this holding
   */
  public boolean addOn(String name, String ty, long time, long ms) throws HoldingDayException {
    if(name.equals(seedName) && type.equals(ty)) {
      return addOn(time,ms);
      
    }
    return false;
  }
  public boolean completelyContains(Holding cmp) {
    return start <= cmp.getStart() && finish >= cmp.getEnd();
  }
  public boolean completelyContains(long time, int ms) {
    return start <=time && finish >= (time+ms);
  }
  /** try to add this covered period to a holding
   *@param time ms of time (per a Timestamp)
   *@param ms the length of the packet in ms
   *@return true if this is within or extends this holding
   */
  public boolean addOn(long time, long ms) throws HoldingDayException {
    // quick test, its out of range
    if(time > finish+tolerance) return false;
    if(time + ms < start-tolerance) return false;
    if(yrdoy != getYrdoy(time)) 
      return false;     // not the right day!
    boolean isTrue=false;     // assume it is not in this record
    //boolean dbg2=dbg;
    boolean dbg2=false;
    if(seedName.substring(0,10).equals("IMIL06 ZZZ"))
      dbg2=true;
    if(dbg2) Util.prt("   "+toString2());
    if(dbg2) Util.prt("   "+"start="+start+" time="+time+" w/ms="+(time+ms)+" end="+finish+" time="+new Timestamp(time));
      // Is it entirely within this record
      if( start <= time && finish >= (time+ms)) {
        if(dbg2) Util.prt("     Totally inside");
        return true;
      }
      
      // does it exactly extend beginning
      if( Math.abs(time+ms - start) < tolerance) {
        // If this laps the beginning of day, this need to bifurcate (its an error)
        if(getYrdoy(time) != yrdoy) {     // would this go to another day
          throw new HoldingDayException("*** Attempt to span beginning day boundary yrdoy(time)="+getYrdoy(time)+" vs "+yrdoy+" required");
          
        }
        start = time;
        if(dbg2) Util.prt("    exactly beginning");
        int ok=0;
        while(ok <= 1) {
          try {
            if(!deferUpdates && !doNotAutoWrite) {
              synchronized (dbMutex) {
                long now = System.currentTimeMillis();
                if(ID == 0) Util.prta("attempt to update with ID=0!! "+toString());
                if(updateStart == null) updateStart = C.prepareStatement(
                          "UPDATE status."+table+" SET start=?, start_ms=?  WHERE ID=?",true);
                
                tsstart.setTime(start/1000*1000);
                updateStart.setTimestamp(1, tsstart);
                updateStart.setShort(2, (short) (start%1000));
                updateStart.setInt(3, ID);
                updateStart.executeUpdate();
                lastWritten=System.currentTimeMillis();
                nupdate++;
                updateMS += (System.currentTimeMillis() - now);
              }
            } 
            else {lastUpdate=System.currentTimeMillis();modified=true;}
            return true;
          }
          catch(SQLException e) {
            if(ok > 0) {
              Util.SQLErrorPrint(e,"Error updating holding="+seedName+" "+new Timestamp(start).toString());
              SendEvent.edgeSMEEvent("HoldingFaile", "Addon2 "+toString2(), this);
            }
            reopenDB();
            ok++;
          }
        }
      }

      // Does it add exactly to finish
      if( Math.abs(finish - time) < tolerance) {
        finish = time+ms;
        if(dbg2) Util.prt("     Exactly end");
        int ok=0;
        while(ok <= 1) {
          try { 
            if(!deferUpdates && !doNotAutoWrite) {
              synchronized (dbMutex) {
                long now = System.currentTimeMillis();
                if(ID == 0) Util.prta("attempt to update2 with ID=0!!"+toString());
                if(updateEnd == null) updateEnd = 
                        C.prepareStatement("UPDATE status."+table+" SET ended=?,end_ms=? WHERE ID=?", true);
                
                tsend.setTime(finish/1000*1000);
                updateEnd.setTimestamp(1,tsend);
                updateEnd.setShort(2, (short) (finish % 1000));
                updateEnd.setInt(3, ID);
                updateEnd.executeUpdate();
                updateMS += System.currentTimeMillis() - now;
              }
              lastWritten=System.currentTimeMillis();
            } else {lastUpdate=System.currentTimeMillis();modified=true;}
            return true;
          }
          catch(SQLException e) {
            if(ok > 0) {
              Util.SQLErrorPrint(e,"Error updating holding="+seedName+" "+new Timestamp(start).toString());
              SendEvent.edgeSMEEvent("HoldingFaile", "Addon1 "+toString2(), this);
            }
            reopenDB();
            ok++;
          }        
        }
      }

      // Does this over lap the beginning
      if( time < start && (time+ms) >= start) {
        start = time;
        if(dbg2) Util.prt("     Overlap beginning");
        int ok=0;
        while(ok <= 1) {
          try { 
            if(!deferUpdates && !doNotAutoWrite) {
              synchronized (dbMutex) {
                long now = System.currentTimeMillis();
                if(ID == 0) Util.prta("attempt to update3 with ID=0!!"+toString());
                if(updateStart == null) updateStart = C.prepareStatement(
                          "UPDATE status."+table+" SET start=?, start_ms=? WHERE ID=?",true);
                tsstart.setTime(start/1000*1000);
                updateStart.setTimestamp(1, tsstart);
                updateStart.setShort(2, (short) (start%1000));
                updateStart.setInt(3, ID);
                updateStart.executeUpdate();  
                updateMS += System.currentTimeMillis() - now;
                nupdate++;
              }
              lastWritten=System.currentTimeMillis();
            } else {lastUpdate=System.currentTimeMillis();modified=true;}
            isTrue=true;
            break;
          }
          catch(SQLException e) {
            if(ok > 0)       Util.SQLErrorPrint(e,"Error updating holding="+seedName+" "+new Timestamp(start).toString());
            reopenDB();
            ok++;
          }        
        }
      }

      // Does this overlap finish
      if( time <= finish && (time+ms) > finish) {
        finish = time+ms;
        if(dbg2) Util.prt("     Overlap end");
        int ok=0;
        while(ok <= 1) {
          try {
            if(!deferUpdates && !doNotAutoWrite) {
              synchronized (dbMutex) {
                long now = System.currentTimeMillis();
                if(ID == 0) Util.prta("attempt to update4 with ID=0!!"+toString());
                if(updateEnd == null) updateEnd = C.prepareStatement("UPDATE status."+table+" SET ended=?,end_ms=? WHERE ID=?", true);
                tsend.setTime(finish/1000*1000);
                updateEnd.setTimestamp(1,tsend);
                updateEnd.setShort(2, (short) (finish % 1000));
                updateEnd.setInt(3, ID);
                updateEnd.executeUpdate();
                updateMS += System.currentTimeMillis() -now;
              }
              lastWritten=System.currentTimeMillis();
            } else {lastUpdate=System.currentTimeMillis();modified=true;}
            isTrue = true;
            break;
          }
          catch(SQLException e) {
            if(ok > 0) {
              Util.SQLErrorPrint(e,"Error updating holding="+seedName+" "+new Timestamp(start).toString());
              SendEvent.edgeSMEEvent("HoldingFaile", "Addon "+toString2(), this);
            }
            reopenDB();
            ok++;
          }        
       }
    }
    return isTrue;
  }
  
  private void setTolerance() {
    tolerance = 1500;
    char band = seedName.charAt(7);
    if(band == 'B') tolerance=100;
    else if(band == 'S') tolerance=100;
    else if(band == 'H') tolerance=50;
    else if(band == 'M') tolerance=125;
    else if(band == 'L') tolerance=1100;
    else if(band == 'V') tolerance=11000;
  }
  
  /** delete this holding from the file
   */
  public void delete() {
    if(C == null) return;       // not a database backed holding
    int ok=0;
    while(ok <= 1) {
      try {
        //Util.prt("Delete id="+ID);
        synchronized (dbMutex) {
          if(deleteHolding == null) deleteHolding = C.prepareStatement(
                  "DELETE FROM status."+table+" WHERE ID=?", true);
          deleteHolding.setInt(1, ID);
          deleteHolding.executeUpdate();
        }
        break;
      }
      catch (SQLException e) {
        if(ok > 0) Util.SQLErrorPrint(e,"Error deleting holding="+seedName+" "+new Timestamp(start).toString());
        reopenDB();
        ok++;
      }
    }
  }
  /** if this holding needs to be put in persistent storage, do it
   *@param ms Milliseconds old it should be to allow an update
   *@return Number of records modified
   */
  public boolean doUpdate(int ms) {
    if(dbg) Util.prt("Holding:Do update called with MS="+ms+" "+toString2()+" time="+
            Math.abs(System.currentTimeMillis() - lastUpdate));
    if(C == null) return false;
    if( (Math.abs(System.currentTimeMillis() - lastWritten) >= ms)) {
      //Util.prt("Holding: Timeout update "+toString2());
      return doUpdate();
    }
    return false;
    
  }
  public boolean doUpdate() { 
    if(dbg) 
      Util.prta("Holding: DoUpdate auto="+doNotAutoWrite+" "+toString2());
    if(modified) {
      int ok=0; 
      while(ok <=1) {
        try {
          if(ID == 0  && !doNotAutoWrite) 
            Util.prta("attempt to update5 with ID=0!!"+toString());
          if( !doNotAutoWrite) {
            synchronized(dbMutex) {
              if(updateHolding == null) updateHolding = C.prepareStatement(
                  "UPDATE status."+table+" SET start=?,start_ms=?,ended=?,end_ms=? WHERE id=?", true);
              //C.executeUpdate(s);
              tsstart.setTime(start/1000*1000);
              updateHolding.setTimestamp(1, tsstart);
              updateHolding.setShort(2, (short) (start%1000));
              tsend.setTime(finish/1000*1000);
              updateHolding.setTimestamp(3, tsend);
              updateHolding.setShort(4, (short) (finish % 1000));
              updateHolding.setInt(5, ID);
              int n = updateHolding.executeUpdate();
              if(n != 1) {
                // This did not update, check to see if its old and probably just deleted from holdings table
                if(existsHolding == null) existsHolding = C.prepareStatement("SELECT * FROM holding WHERE id=?",false);
                long now = System.currentTimeMillis();
                existsHolding.setInt(1, ID);
                try (ResultSet rs = existsHolding.executeQuery()) {
                  if(!rs.next()) {
                    long hours = (System.currentTimeMillis() - getEnd())/3600000;
                    if(hours > 72) {
                      outOfScope=true;
                      modified=false;
                      lastWritten = System.currentTimeMillis();
                      Util.prta("* Holding for ID="+ID+" "+tsend+" is not in holding table end is "+hours+" Hrs old + probably just purged");
                      return false;
                    }
                    else {
                      Util.prta("**** Holding for ID="+ID+" "+tsend+" is not in holding table end is "+hours+" Hrs old this is a problem");
                      Util.prta(" *** Update failed id="+ID+" to ended="+tsend);
                      SendEvent.edgeSMEEvent("HoldingFailed",toString2(),"Holdings");
                    }
                  }
                  else Util.prta("***** Found a existent ID="+ID+" did not update!!  How does this happen!");
                }
                readbackMS = System.currentTimeMillis() - now;
                nreadback++;
              }
            }
          }
          lastWritten=System.currentTimeMillis()+(ID % 100)*1000;
          modified=false;
          return true;
        }
        catch (SQLException e) {
          if(e.toString().contains("doesn't exist")) {
            Util.prta("**** ID="+ID+" does not exist - drop this holding? "+toString2());
            long hours = (System.currentTimeMillis() - getEnd())/3600000;
            outOfScope=true;
            modified=false;
            lastWritten = System.currentTimeMillis();
            Util.prta("* Holding for ID="+ID+" "+tsend+" does not exist. End is "+hours+" Hrs old + probably just purged");
            return false;
          }
          Util.SQLErrorPrint(e," *** Error doUpdate() holding="+seedName+" "+new Timestamp(start).toString()); 
          Util.prta("*** error in doUpdate e="+e);
          if(ok == 1) SendEvent.edgeSMEEvent("HoldingFaile", "Update "+toString2(), this);
          ok++;
          reopenDB();
          //Util.exit(1);
        }
      }
    }
    return false;
  }
  /** get the length of this holding in millis
   *@return THe linength of this in millis 
   */
  public long getLengthInMillis() {return finish - start;}
  /** get the start time of holding
   *@return The start time as a Timestamp().getTimeInMillis()
   */
  public long getStart() {return start;}
  /** get the finish time of holding
   *@return The finish time as a Timestamp().getTimeInMillis()
   */
  public long getEnd() {return finish;}
  /** get the Seed name 
   *@return the network/station/channel/location code as string
   */
  public String getSeedName() {return seedName;}
  /** return the type of holding (that is the source of this data)
   *@return The type as a two character string *
   */
  public String getType() {return type;}
  /** return the tolerance in ms for this record
   *@return the tolerance in ms
   */
  public int getTolerance() {return tolerance;}
  /** return age since this last updated in seconds
   *@return Time difference between system clock on last  update time
   */
  public long getAgeWritten() {
    /*Util.prt("now="+Util.now().toString()+" ms="+new GregorianCalendar().getTimeInMillis()+
            "  upd="+lastUpdate.toString()+" ms="+lastUpdate.getTime()+" age="+
            (new GregorianCalendar().getTimeInMillis() - lastUpdate.getTime())/1000L);*/
    if(lastWritten == 0) return 999999999999L;
    return (System.currentTimeMillis() - lastWritten)/1000L;}
  /** return age since this last updated in seconds
   *@return Time difference between system clock on last  update time
   */
  public long getAge() {
    /*Util.prt("now="+Util.now().toString()+" ms="+new GregorianCalendar().getTimeInMillis()+
            "  upd="+lastUpdate.toString()+" ms="+lastUpdate.getTime()+" age="+
            (new GregorianCalendar().getTimeInMillis() - lastUpdate.getTime())/1000L);*/
    if(lastUpdate == 0) return 999999999999999L;
    return (System.currentTimeMillis() - lastUpdate)/1000L;}
  public int getID() {return ID;}
  /** Get the yyyyddd key field
   * 
   * @return  yyyyddd
   */
  public int getYRDOY() {return yrdoy;}
  public StringBuilder toStringBuilder(StringBuilder use) {
    StringBuilder sb;
    if(use == null) sb = scratch;
    else sb = use;
    Util.clear(sb);
    sb.append(seedName).append(" ").append(type).append(" ").
            append(Util.ascdatetime2(start)).append(" ").
            append(" yrody=").append(yrdoy).append(" end=").append(Util.ascdatetime2(finish)).
                    append("cr=").append(createType).append(" ID=").append(ID);
    return sb;
  }
  @Override
  public final String toString() {return seedName+" "+type+" "+
          Util.ascdate(start)+" "+Util.asctime2(start)+" yrody="+yrdoy+" end="+
          Util.ascdate(finish)+" "+Util.asctime2(finish)+"cr="+createType+" ID="+ID;}
  public String toString2() {return seedName+" "+type+" "+
          Util.ascdatetime2(start)+" yrdoy="+yrdoy+" end="+
          
          Util.ascdatetime2(finish)+
          " md="+modified+" ID="+ID+
          " age(ms)="+(System.currentTimeMillis() - lastUpdate)+
          " agewr(ms)="+(System.currentTimeMillis() - lastWritten)+
          " defer="+deferUpdates;}
  /** is this holding modified since last write to database
   *@return Modified flag
   */
   public boolean isModified() {return modified;}
    /** Implements a sort order for channels in key order (network/station/channel/location). */
  @Override
  public int compareTo(Object o)
  { HoldingDay h = (HoldingDay) o;
    String key = h.getSeedName()+h.getType();
    String mykey = seedName+type;
    int comp = mykey.compareTo(key);
    
    if(comp != 0) return comp;
    
    // equal by seed name and type, now compare start times
    if(yrdoy < h.getYRDOY()) return -1;
    else if(yrdoy > h.getYRDOY()) return 1;
    return 0;
  }

  public  static  void  main  (String  []  args)  {
    DBConnectionThread  db;
    Connection  C2;
    //User user=new User("dkt");
    try  {
      db  =  new  DBConnectionThread("localhost/3306:status:mysql:status","update","status", true, false,"status", Util.getOutput());
      if(!db.waitForConnection()) 
        if(!db.waitForConnection()) {
          Util.prt("connection did not open"); 
          System.exit(1);
        }
      Holding.setHoldingsConnection(db);
      GregorianCalendar g = new GregorianCalendar(2005,1,1,10,0,0);
      g.setTimeInMillis(g.getTimeInMillis()+101);
      Holding a=new Holding("USAAA  BHZ00","AA",g.getTimeInMillis(),10000);
      Holding c=new Holding("USAAA  BHZ00","AA",g.getTimeInMillis()+30000,10000);
      Util.prt("HoldingTest id="+a.getID()+"="+a.toString());
      //b=new Holding("USBBB  BHZ00","AA",g.getTimeInMillis(),10000);
      //c=new Holding("USCCC  BHZ00","AA",g.getTimeInMillis(),10000);
      //d=new Holding("USDDD  BHZ00","AA",g.getTimeInMillis(),10000);
      a.setDeferUpdate(false);
      Util.prt("testAddOn");
    
      a.setDeferUpdate(false);
       a.addOn(g.getTimeInMillis()+10001,10000);// should double it
       Util.prt("Badjust");
       a.manualWrite();
       //b.addOn(g.getTimeInMillis()+10001,10000);
       //c.addOn(g.getTimeInMillis()+10001,10000);
       //a.addOn(g.getTimeInMillis()-10000,10000); // exact add to beginning
       //b.addOn(g.getTimeInMillis()-5000,10000);  // add 5 to beginning
       //c.addOn(g.getTimeInMillis()+15000,10000);  // add 5 to finish
       Util.prt("adjustments made");
       //b.doUpdate();
       Util.prt(a.getID()+" a1="+a);
       //Util.prt("b="+b);
       //Util.prt("c="+c);
     Util.assertEquals("addon+20.001",a.toString(),
             "USAAA  BHZ00 AA 2005-02-01 10:00:00.0 end=2005-02-01 10:00:20.001");
     a.addOn(g.getTimeInMillis()-10000,10000);
       Util.prt(a.getID()+" a2="+a);
     Util.assertEquals("addon-10, +20.001",a.toString(),
             "USAAA  BHZ00 AA 2005-02-01 09:59:50.0 end=2005-02-01 10:00:20.001");
     a.doUpdate();
     a.addOn(g.getTimeInMillis(),10000);
       Util.prt(a.getID()+" a3="+a);
     Util.assertEquals("complete overlap",a.toString(),
             "USAAA  BHZ00 AA 2005-02-01 09:59:50.0 end=2005-02-01 10:00:20.001");
     a.addOn(g.getTimeInMillis()+15000,10000);
       Util.prt(a.getID()+" a4="+a);
     Util.assertEquals("add overlap +15 to +25",a.toString(),
             "USAAA  BHZ00 AA 2005-02-01 09:59:50.0 end=2005-02-01 10:00:25.0");
     a.doUpdate();
     Holding  b=new Holding("USAAA  BHZ00","AA",g.getTimeInMillis(),10000);
       Util.prt(a.getID()+" b1="+b);
     Util.assertEquals("Read back latest",b.toString(),
             "USAAA  BHZ00 AA 2005-02-01 09:59:50.0 end=2005-02-01 10:00:25.0");
     c.addOn(g.getTimeInMillis()+20001,10002);
     c.addOn(g.getTimeInMillis()+30000,20003);
     Util.assertEquals("Read on C=",c.toString(),
            "USAAA  BHZ00 AA 2005-02-01 10:00:20.001 end=2005-02-01 10:00:50.003" );
     c.addOn(g.getTimeInMillis()-20000, 20000);
     c.doUpdate();
     c.addOn(g.getTimeInMillis(),40000);
     c.doUpdate();
     c.addOn(g.getTimeInMillis()-40000, 100000);
     c.doUpdate();
     c.addOn(g.getTimeInMillis(), 100000);
     c.doUpdate();
     c.delete();
     
     Holding d = new Holding(c.getID());
     Util.prt(d.toString());
      //user=new User(C,"dkt","karen");

    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e," Main SQL unhandled=");
      e.printStackTrace();
      System.err.println("SQLException  on  getting test Holdings");
    }
    catch(InstantiationException e) {
      Util.prt("Instantiation error");
    }
  }
}

