package gov.usgs.adslserverdb;
//import gov.usgs.adslserverdb.ADSLEpoch;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.util.JDBConnectionOld;
import gov.usgs.anss.util.QueryOld;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class ADSLEpochs extends Thread {

  /**
   * This class reads data from the epochs table in the metadata database and maintains a cache of
   * all of the epochs. It sole switch is "-fdsnonly" which means only agency "FDSN" is included.
   * The SQL used to build the epochs table is kept in the reports sections
   *
   */
  private final TreeMap<String, ArrayList<ADSLEpoch>> epochs = new TreeMap<String, ArrayList<ADSLEpoch>>();
  private String[] keys;     // This is kept with list of all keys in TreeMap epochs so it does hot have to be repeatly rebuilt
  private final Integer mysqlmutex = Util.nextMutex();
  private DBConnectionThread dbconn;
  private static ADSLEpochs obj;
  private EdgeThread par;
  private long lastRead;
  private boolean terminate;
  private boolean fdsnonly;       // when set, then the only agency in list is FDSN
  private final ShutdownIREpochs shutdown;
  private static boolean forceUpdate;

  /*  private String buildEpochs = "TRUNCATE epochs;"+
      "INSERT INTO epochs (A,Aeff,Aend,D,Deff,Dend,S,Seff,Send,L,Leff,Lend,"+
      "latitude,longitude,elevation,depthofburial,overridelatitude,overridelongitude,"+
      "overrideelevation,localx,localy,localz,localdatum,seedchannels,seedrates,"+
      "sitename,fdsnnetworkcode,iscoverridesitename) "+
      "SELECT "+
      "agency.agency as A,agency.effective as Aeff,agency.enddate as Aend,"+
      "deploymentepoch.deployment as D,deploymentepoch.effective as Deff,deploymentepoch.enddate as Dend,"+
      "stationepoch.stationcode as S, stationepoch.effective as Seff, stationepoch.enddate as Send,"+
      "locationepoch.locationcode as L,locationepoch.effective as Leff, locationepoch.enddate as Lend,"+
      "locationepoch.latitude as latitude,locationepoch.longitude as longitude,"+
      "locationepoch.elevation as elevation, "+
      "locationepoch.depthofburial as depthofburial,"+
      "locationepoch.overridelatitude,locationepoch.overridelongitude,"+
      "locationepoch.overrideelevation,"+
      "locationepoch.localx,locationepoch.localy,locationepoch.localz,locationepoch.localdatum,"+
      "locationepoch.SEEDChannels,locationepoch.SEEDRates,"+
      "stationepoch.sitename as sitename,stationepoch.fdsnnetworkcode,"+
      "stationepoch.ISCOverrideSitename"+
      "FROM agency JOIN deploymentepoch ON (deploymentepoch.agencyid=agency.id)"+
      "JOIN deployment ON (deployment.id=deploymentepoch.deploymentid) "+
      "JOIN station ON (station.deploymentid=deployment.id) "+
      "JOIN stationepoch ON (stationepoch.stationid=station.id) "+
      "JOIN location ON (location.stationid=station.id)"+
      "JOIN locationepoch ON (locationepoch.locationid=location.id) "+
      "ORDER BY agency.agency,deploymentepoch.deployment,stationepoch.stationcode,locationepoch.locationcode;"+

      "INSERT INTO epochs (A,Aeff,Aend,D,Deff,Dend,S,Seff,Send,L,Leff,Lend,"+
      "latitude,longitude,elevation,depthofburial,overridelatitude,overridelongitude,"+
      "overrideelevation,localx,localy,localz,localdatum,seedchannels,seedrates,"+
      "sitename,fdsnnetworkcode,iscoverridesitename) "+
      "SELECT "+
      "A,agency.effective as Aeff,agency.enddate as Aend,"+
      "D,deploymentepoch.effective as Deff,deploymentepoch.enddate as Dend,"+
      "S, stationepoch.effective as Seff, stationepoch.enddate as Send,"+
      "locationepoch.locationcode as L,locationepoch.effective as Leff, locationepoch.enddate as Lend,"+
      "locationepoch.latitude as latitude,locationepoch.longitude as longitude,"+
      "locationepoch.elevation as elevation, "+
      "locationepoch.depthofburial as depthofburial,"+
      "locationepoch.overridelatitude,locationepoch.overridelongitude,"+
      "locationepoch.overrideelevation,"+
      "locationepoch.localx,locationepoch.localy,locationepoch.localz,locationepoch.localdatum,"+
      "locationepoch.SEEDChannels,locationepoch.SEEDRates,"+
      "stationepoch.sitename as sitename,stationepoch.fdsnnetworkcode,"+
      "stationepoch.ISCOverrideSitename "+
      "FROM "+

      "(SELECT agency.agency as A,deploymentepoch.deployment as D,alias.effective as Aeff,"+
      "alias.enddate as Aend,alias.station as S,alias.aliasedstationid as asid"+
      "FROM alias JOIN agency ON (alias.agencyepochid=agency.id) "+
      "JOIN deploymentepoch ON (deploymentepoch.id=alias.deploymentepochid)"+
      "JOIN station ON (station.id=alias.aliasedstationid)"+
      ") AS als"+

      "JOIN station ON (als.asid=station.id) "+
      "JOIN stationepoch on (stationepoch.stationid=station.id) "+
      "JOIN deployment ON (station.deploymentid=deployment.id) "+
      "JOIN deploymentepoch on (deployment.id=deploymentepoch.deploymentid)"+
      "JOIN agency ON (deploymentepoch.agencyid=agency.id) "+
      "JOIN location ON (location.stationid=station.id) "+
      "JOIN locationepoch ON (locationepoch.locationid=location.id) "+
      "ORDER BY A,D,S;";
   */
  public static void setForceUpdate() {
    forceUpdate = true;
  }

  /**
   * Return the epochs object for doing queries on this derived data
   *
   * @return The epochs object (IREpochs)
   */
  public static ADSLEpochs getADSLEpochsObject() {
    return obj;
  }

  public boolean isPopulated() {
    return keys != null;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * build a mapped structure for retrieving A.D.S and A.D.S.L by epoch
   *
   * @param argline The argument line used to start the parent (no uses yet)
   * @param parent The parent thread to use for logging
   */
  public ADSLEpochs(String argline, EdgeThread parent) {
    par = parent;
    if (obj != null) {
      prta("IRE: ****Recreation of IREepochs object - normally this is an error!");
    }
    prta("IRE: Starting ADSLEpochs thread " + parent);
    dbconn = DBConnectionThread.getThread("IRSMeta");
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("MetaDBServer"),
                "readonly", "irserver", true, false, "IRSMeta", parent.getPrintStream());
        if (!DBConnectionThread.waitForConnection("IRSMeta")) {
          if (!DBConnectionThread.waitForConnection("IRSMeta")) {
            if (!DBConnectionThread.waitForConnection("IRSMeta")) {
              if (!DBConnectionThread.waitForConnection("IRSMeta")) {
                if (!DBConnectionThread.waitForConnection("IRSMeta")) {
                  if (!DBConnectionThread.waitForConnection("IRSMeta")) {
                    prta("MDRun: Did not connect to DB promptly IRSMeta");
                  }
                }
              }
            }
          }
        }
      } catch (InstantiationException e) {
        prta("IRE: InstantiationException opening edge database in IRepochs e=" + e.getMessage());
        System.exit(1);
      }
    }
    if (argline.contains("-fdsnonly")) {
      fdsnonly = true;
    }
    reload();
    shutdown = new ShutdownIREpochs(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    prta(Util.ascdate() + " IRE: Start up ADSLEpochs");
    start();
  }

  /**
   * the run() checks for changes in the epochs table, and reloads it if one is found
   *
   */
  @Override
  public void run() {
    obj = this;
    while (!terminate) {
      try {
        sleep(900000);
      } catch (InterruptedException e) {
      }
      if (terminate) {
        break;
      }
      dbconn.setLogPrintStream(par.getPrintStream());
      reload();
    }
    prta("IRE: terminated reload loop");
  }

  public void forceUpdate() {
    synchronized (mysqlmutex) {
      try {
        prta("IRE: staring a force update");
        ResultSet rs = dbconn.executeQuery("SELECT * from report where report='Epochs Table Build'");
        if (rs.next()) {
          String s = rs.getString("sql");
          String[] commands = s.split(";");
          JDBConnectionOld jdbc = new JDBConnectionOld(Util.getProperty("MySQLServer"), "irserver", "vdl", "nop240", false, "IRSupdate");
          Statement stmt = JDBConnectionOld.getConnection("IRSupdate").
                  createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
          for (String command : commands) {
            if (command.trim().equals("")) {
              continue;
            }
            prta("IRE: Start force update command=" + command);
            stmt.executeUpdate(command);
          }
        }
        JDBConnectionOld.close("IRSupdate");
      } catch (SQLException e) {

      }
    }
    reload();
  }

  public final void reload() {
    boolean ok = false;
    synchronized (mysqlmutex) {
      while (!ok) {
        int nrows = 0;
        String key = "NONE";
        try {
          // When the table is built all updated fields get the current time,
          // use this fact to prevent reading the table again if it has not been changed
          if (forceUpdate) {
            prta("IRE: force update detected - do it now");
            forceUpdate = false;
          } else {
            String sql = "SELECT max(updated) FROM epochs";
            try (ResultSet rs = dbconn.executeQuery(sql)) {
              if (rs.next()) {
                Timestamp max = rs.getTimestamp(1);
                if (max == null) {
                  prta("IRE: attempt to reload() skipped - not  max(updated!)");
                  rs.close();
                  break;
                }
                long update = rs.getTimestamp(1).getTime();
                if (update < lastRead) {
                  prta("IRE: attempt to reload() skipped - no change");
                  //ok=true;
                  rs.close();
                  break;
                }
              } else {
                prta("IRE: ***** should be impossible not to get a data from a epoch");
              }
            } catch (SQLException e) {
              prta("IRE: **** cannot get max timestamp from epochs! e=" + e);
            }
          }
          // The table has been rebuilt since last read, build everything again.
          prta("IRE: start query");
          String sql = "SELECT * FROM epochs where a!='' and s != '' order by a,d,s,l,leff";
          if (fdsnonly) {
            sql = "SELECT * FROM epochs WHERE a='FDSN' and s != '' order by a,d,s,l,leff";
          }
          try (ResultSet rs = dbconn.executeQuery(sql)) {
            prta("IRE: process query results");

            //synchronized(this) {
            epochs.clear();
            while (rs.next()) {
              nrows++;
              if (nrows % 10000 == 0) {
                prta("IRE: rows now=" + nrows);
              }
              key = makeADSKey(rs.getString("a"), rs.getString("d"), rs.getString("s"));
              ArrayList<ADSLEpoch> ads = epochs.get(key);
              if (ads == null) {
                ads = new ArrayList<ADSLEpoch>(10);
                epochs.put(key, ads);
              }
              ADSLEpoch e = new ADSLEpoch(rs);
              ads.add(e);
            }
          } catch (SQLException e) {
            if (e.toString().contains("can not be represented")) {
              prta(key + " contains a time field that cannot be represented.  continuing");
              if (par == null) {
                e.printStackTrace();
              } else {
                e.printStackTrace(par.getPrintStream());
              }
            } else {
              Util.SQLErrorPrint(e, "IRE: Trying to reload epochs nrow=" + nrows + " last key=" + key);
              dbconn = DBConnectionThread.getThread("IRSmeta");
              if (dbconn != null) {
                dbconn.terminate();
              }
              try {
                dbconn = new DBConnectionThread(Util.getProperty("MetaDBServer"),
                        "readonly", "irserver", true, false, "IRSMeta", null);
                if (!DBConnectionThread.waitForConnection("IRSmeta")) {
                  if (!DBConnectionThread.waitForConnection("IRSmeta")) {
                    if (!DBConnectionThread.waitForConnection("IRSmeta")) {
                      if (!DBConnectionThread.waitForConnection("IRSmeta")) {
                        prta("IRE: reload() Did not connect to MySQL promptly IRSMeta");
                      }
                    }
                  }
                }
              } catch (InstantiationException e2) {
                prta("InstantiationException opening edge database in IREpochs e=" + e.getMessage());
                System.exit(1);
              }
            }

            ok = true;
            lastRead = System.currentTimeMillis();
            keys = new String[epochs.size()];
            keys = epochs.keySet().toArray(keys);
            //}
            prta("IRE: processing complete nrows=" + nrows);
          }
        } catch (RuntimeException e) {
          prta("ADSLEpoch reload Runtime error =" + e);
          if (par == null) {
            e.printStackTrace();
          } else {
            e.printStackTrace(par.getPrintStream());
          }
          if (e.toString().contains("can not be represented")) {
            prta(key + " has a timestamp that connot be represented!");

          }
        }
      } // while !ok
    } // synchronize
    prta("IRE: leaving reload");
  }

  /**
   * return a report listing to a query select statement
   *
   * @param line The select statement
   * @return String with results
   * @throws SQLException
   */
  public String doSelect(String line) throws SQLException {
    synchronized (mysqlmutex) {    // Make sure the statement is not is use by reloader
      QueryOld q = new QueryOld(dbconn.getConnection(), line);
      return q.getText("|");
    }
  }

  private String makeADSKey(String a, String d, String s) {
    return (a + "     ").substring(0, 5)
            + (d + "        ").substring(0, 8) + (s + "     ").substring(0, 5);
  }

  /**
   * Translate a user regexp like AAA.DEP.A to a dot padded regular expression for the fix field
   * width used by the indices
   *
   * @param regin The user A.D.S
   * @return The fixed field IDS
   */
  private String[] translateRegexp(String regin) {
    String[] parts = regin.split("\\.");
    if (parts.length >= 4) {
      parts[3] = parts[3].replaceAll(" ", "-");
    }
    String[] regexp = new String[4];
    for (int i = 0; i < regexp.length; i++) {
      regexp[i] = "..........";
    }
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].contains(".") || parts[i].contains("[") || parts[i].contains("*")) {
        regexp[i] = (parts[i].replaceAll("\\*", "")) + "..........";// * must be at end, so ... is same thing
      } else {
        regexp[i] = parts[i] + "          ";
      }
    }
    String[] ret = new String[2];
    ret[0] = makeADSKey(regexp[0], regexp[1], regexp[2]);
    ret[1] = regexp[3].substring(0, 4);
    return ret;
  }

  /**
   * Get a String list of stations matching a A.D.S regexp
   *
   * @param adsRegExp The user regular expression A.D.S with dots and []
   * @return An array list of the ADS keys which match
   */
  public synchronized ArrayList<String> getADSs(String adsRegExp) {
    String[] regexp = translateRegexp(adsRegExp);
    Pattern p = Pattern.compile(regexp[0]);
    ArrayList<String> stats = new ArrayList<String>(10);
    // Find all of the stations which match the ADS
    for (String key : keys) {
      if (p.matcher(key).matches()) {
        stats.add(key);
      }
    }
    return stats;
  }

  /**
   * Get a String list of stations/location matching a A.D.S.L regexp
   *
   * @param adsRegExp The user regular expression A.D.S with dots and []
   * @return An array list of the ADS keys + location code which match
   */
  public synchronized ArrayList<String> getADSLs(String adsRegExp) {
    String[] regexp = translateRegexp(adsRegExp);
    Pattern p = Pattern.compile(regexp[0]);
    ArrayList<String> stats = new ArrayList<String>(10);
    // Find all of the stations which match the ADS
    for (String key : keys) {
      if (p.matcher(key).matches()) {
        ArrayList<ADSLEpoch> eps = epochs.get(key);
        for (int j = 0; j < eps.size(); j++) {
          if (eps.get(j).getLocation().matches(regexp[1])) {
            stats.add(key + eps.get(j).getLocation());
          }
        }
      }
    }
    return stats;
  }

  /**
   * Get a list of A.D.S epochs (all of the locations at a station) matching a user regexp
   *
   * @param adsRegExp The user regular expression A.D.S with dots and []
   * @return An arrays list of Epochs for all locations matching the A.D.S regexp
   */
  public synchronized ArrayList<ADSLEpoch> getADSEpochs(String adsRegExp) {
    ArrayList<String> adsl = getADSs(adsRegExp);
    ArrayList<ADSLEpoch> ret = new ArrayList<ADSLEpoch>(200);
    for (int i = 0; i < adsl.size(); i++) {
      ArrayList<ADSLEpoch> eps = epochs.get(adsl.get(i));
      for (int j = 0; j < eps.size(); j++) {
        ret.add(eps.get(j));
      }
    }
    return ret;
  }

  /**
   * Get a list of A.D.S epochs (all of the locations at a station) matching a user regexp
   *
   * @param adslRegExp The user regular expression A.D.S with dots and []
   * @return An arrays list of Epochs for all locations matching the A.D.S regexp
   */
  public synchronized ArrayList<ADSLEpoch> getADSLEpochs(String adslRegExp) {
    ArrayList<String> adsl = getADSLs(adslRegExp);
    ArrayList<ADSLEpoch> ret = new ArrayList<ADSLEpoch>(200);
    for (int i = 0; i < adsl.size(); i++) {
      ArrayList<ADSLEpoch> eps = epochs.get(adsl.get(i).substring(0, 18));
      for (int j = 0; j < eps.size(); j++) {
        if (eps.get(j).getLocation().equals(adsl.get(i).substring(18))) {
          ret.add(eps.get(j));
        }
      }
    }
    return ret;
  }

  /**
   * get a single epoch from each station matching A.D.S regexp
   *
   * @param adsRegExp Get a list of one epoch for each A.D.S in regexp
   * @return The ArrayList of matching A.D.S Epochs (one per matching station)
   */
  public synchronized ArrayList<ADSLEpoch> getADSLSingleEpoch(String adsRegExp) {
    ArrayList<String> adsl = getADSs(adsRegExp);
    ArrayList<ADSLEpoch> ret = new ArrayList<ADSLEpoch>(200);
    for (int i = 0; i < adsl.size(); i++) {
      ArrayList<ADSLEpoch> eps = epochs.get(adsl.get(i));
      ret.add(eps.get(0));
    }
    return ret;
  }

  /**
   * retun a list of a single epoch from each station matching a A.D.S regexp and within a certain
   * distance of the given latitude and longitude
   *
   * @param adsRegExp An A.D.S regexp to match
   * @param mindeg The minimum distance
   * @param within The distance in degrees from lat and long to include
   * @param lat Latitude in degrees (+/- 90)
   * @param lng Longitude in degrees (+/- 180)
   * @return Array list with a single Epoch at each matching station
   */
  public synchronized ArrayList<ADSLEpoch> getADSEpochByDelaz(String adsRegExp, double mindeg, double within, double lat, double lng) {
    ArrayList<ADSLEpoch> stats = getADSLSingleEpoch(adsRegExp);
    int ndel = 0;
    //Util.prt("do mindeg="+mindeg+" maxdeg="+within+" from "+lat+","+lng);
    for (int i = 0; i < stats.size(); i++) {
      double[] del = Distaz.distaz2(lat, lng, stats.get(i).getLatitude(), stats.get(i).getLongitude());
      if (del[1] > within || del[1] < mindeg) {
        stats.set(i, null); // drop station from returned set
        ndel++;
      }
      /*else {
          Util.prt("Keep it del="+del[1]);
      }*/
    }

    ArrayList<ADSLEpoch> ret = new ArrayList<ADSLEpoch>(stats.size() - ndel);
    int j = 0;
    for (int i = 0; i < stats.size(); i++) {
      if (stats.get(i) != null) {
        ret.add(stats.get(i));
        j++;
      }
    }
    return ret;
  }

  /**
   * return a list of ADSLEpochs which match the stationID in the given epoch. Note : exclusion by
   * dates must be done by caller
   *
   * @param matchEpoch An ADSL Epoch to match the station ID for
   * @return A list of such
   */
  public synchronized ArrayList<ADSLEpoch> getADSLAliasStationID(ADSLEpoch matchEpoch) {
    return getADSLAliasStationID(matchEpoch.getStationID());
  }

  /**
   * return a list of ADSLEpoch which point to a give station ID Note : exclusion by dates must be
   * done by caller
   *
   * @param stationID The station ID to match
   * @return The list of matching epochs
   */
  public synchronized ArrayList<ADSLEpoch> getADSLAliasStationID(int stationID) {
    ArrayList<ADSLEpoch> ads = new ArrayList<ADSLEpoch>(10);
    // We are trying to find all ADSLEpochs which reference the same stationID
    for (String key : keys) {
      ArrayList<ADSLEpoch> eps = epochs.get(key);
      for (int j = 0; j < eps.size(); j++) {
        if (eps.get(j) != null) {
          if (eps.get(j).getStationID() == stationID) {
            ads.add(eps.get(j));
          }
        }
      }
    }
    return ads;
  }

  /**
   * return the best epoch for an FDSN networ.station. This is defined as the lowest location code
   * lexicographically which is normally the blank location code if it exists, or the lowest one
   * that does exist in the epoch.
   *
   * @param net The FDSN 2 character network code
   * @param station Station code (5 characters max)
   * @param g Epochal date desired
   * @return The epoch matching or null of the A.D.S.L does not exist or no epoch exists for the
   * date
   */
  public ADSLEpoch getEpochFDSN(String net, String station, GregorianCalendar g) {
    return getEpoch("FDSN", net, station, g.getTimeInMillis());

  }

  /**
   * return the best epoch for an FDSN network.station.location.
   *
   * @param net The FDSN 2 character network code
   * @param station Station code (5 characters max)
   * @param location The location code (2 characters max)
   * @param g Epochal date desired
   * @return The epoch matching or null of the A.D.S.L does not exist or no epoch exists for the
   * date
   */
  public ADSLEpoch getEpochFDSN(String net, String station, String location, GregorianCalendar g) {
    return getEpoch("FDSN", net, station, location, g.getTimeInMillis());
  }

  /**
   * return the best epoch for an FDSN network.station. THis returns the lowest location code
   * lexicographically which is normally the blank one, but might be some other if the blank one
   * does not exist.
   *
   * @param net The FDSN 2 character network code
   * @param station Station code (5 characters max)
   * @param g Epochal date desired
   * @return The epoch matching or null of the A.D.S.L does not exist or no epoch exists for the
   * date
   */
  public ADSLEpoch getEpochFDSN(String net, String station, long g) {
    return getEpoch("FDSN", net, station, g);

  }

  /**
   * return the best epoch for an FDSN network.station.location. T
   *
   * @param net The FDSN 2 character network code
   * @param station Station code (5 characters max)
   * @param location The location code (2 characters max)
   * @param g Epochal date desired
   * @return The epoch matching or null of the A.D.S.L does not exist or no epoch exists for the
   * date
   */
  public ADSLEpoch getEpochFDSN(String net, String station, String location, long g) {
    return getEpoch("FDSN", net, station, location, g);
  }

  /**
   * return the best epoch for an A.D.S. This is defined as the lowest location code
   * lexicographically
   *
   * @param a Agency
   * @param d Deployment
   * @param s Station
   * @param g Epochal date desired
   * @return The epoch matching or null of the A.D.S.L does not exist or no epoch exists for the
   * date
   */
  public synchronized ADSLEpoch getEpoch(String a, String d, String s, GregorianCalendar g) {
    return getEpoch(a, d, s, g.getTimeInMillis());
  }

  /**
   * return the best epoch for an A.D.S. This is defined as the lowest location code
   * lexicographically
   *
   * @param a Agency
   * @param d Deployment
   * @param s Station
   * @param g Epochal date desired
   * @return The epoch matching or null of the A.D.S.L does not exist or no epoch exists for the
   * date
   */
  public synchronized ADSLEpoch getEpoch(String a, String d, String s, long g) {
    ArrayList<ADSLEpoch> ads = epochs.get(makeADSKey(a, d, s));
    if (ads == null) {
      return null;
    }
    int besti = -1;
    String bestLoc = "ZZ";
    for (int i = 0; i < ads.size(); i++) {
      if (ads.get(i).getAgencyEffective().getTime() <= g
              && ads.get(i).getAgencyEnddate().getTime() >= g
              && ads.get(i).getDeploymentEffective().getTime() <= g
              && ads.get(i).getDeploymentEnddate().getTime() >= g
              && ads.get(i).getStationEffective().getTime() <= g
              && ads.get(i).getStationEnddate().getTime() >= g
              && ads.get(i).getLocationEffective().getTime() <= g
              && ads.get(i).getLocationEnddate().getTime() >= g) {
        if (ads.get(i).getLocation().compareTo(bestLoc) <= 0) {
          besti = i;
          bestLoc = ads.get(i).getLocation();
        }
      }
    }
    if (besti < 0) {
      return null;
    }
    return ads.get(besti);
  }

  /**
   * return a list of all epochs matching station between begin and end time
   *
   * @param adsRegExp A ADS regular expression
   * @param begin A begining date
   * @param end An ending time
   * @return The list of A.D.S.L epochs matching the regexpression and times
   */
  public synchronized ArrayList<ADSLEpoch> getADSLEpochs(String adsRegExp,
          GregorianCalendar begin, GregorianCalendar end) {
    ArrayList<ADSLEpoch> stats = this.getADSEpochs(adsRegExp);
    // Remove alls not overlapping the date range
    int ndel = 0;
    for (int i = 0; i < stats.size(); i++) {
      if (stats.get(i).getLocationEffective().getTime() > end.getTimeInMillis()
              || stats.get(i).getLocationEnddate().getTime() < begin.getTimeInMillis()) {
        ndel++;
        stats.set(i, null);
      }
    }
    ArrayList<ADSLEpoch> ret = new ArrayList<ADSLEpoch>(stats.size() - ndel);
    for (int i = 0; i < stats.size(); i++) {
      if (stats.get(i) != null) {
        ret.add(stats.get(i));
      }
    }
    return ret;
  }

  /**
   * return a epoch given an A.D.S.L and date
   *
   * @param a Agency
   * @param d Deployment
   * @param s Station
   * @param l Location
   * @param g Date for the epoch
   * @return The epoch or null if no match is found for A.D.S.L or the epochal date
   */
  public synchronized ADSLEpoch getEpoch(String a, String d, String s, String l, GregorianCalendar g) {
    return getEpoch(a, d, s, l, g.getTimeInMillis());
  }

  /**
   * return a epoch given an A.D.S.L and date
   *
   * @param a Agency
   * @param d Deployment
   * @param s Station
   * @param l Location
   * @param g Date for the epoch
   * @return The epoch or null if no match is found for A.D.S.L or the epochal date
   */
  public synchronized ADSLEpoch getEpoch(String a, String d, String s, String l, long g) {
    ArrayList<ADSLEpoch> ads = epochs.get(makeADSKey(a, d, s));
    if (ads == null) {
      return null;
    }
    for (int i = 0; i < ads.size(); i++) {
      if (ads.get(i).getAgencyEffective().getTime() <= g
              && ads.get(i).getAgencyEnddate().getTime() >= g
              && ads.get(i).getDeploymentEffective().getTime() <= g
              && ads.get(i).getDeploymentEnddate().getTime() >= g
              && ads.get(i).getStationEffective().getTime() <= g
              && ads.get(i).getStationEnddate().getTime() >= g
              && ads.get(i).getLocationEffective().getTime() <= g
              && ads.get(i).getLocationEnddate().getTime() >= g) {
        if (ads.get(i).getLocation().equals(l)) {
          return ads.get(i);
        }
      }
    }
    return null;
  }

  private class ShutdownIREpochs extends Thread {

    ADSLEpochs thr;

    public ShutdownIREpochs(ADSLEpochs t) {
      thr = t;
    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */
    @Override
    public void run() {
      terminate = true;
      thr.interrupt();
      prta("IRE: Shutdown started");
      int nloop = 0;
      prta("IRE:Shutdown Done.");
    }
  }
}
