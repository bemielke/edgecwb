/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.db.JDBConnection;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Connection;

/**
 * Update various flag fields in the channel table based on the metadata. MDS flags and flags in
 * particular.
 *
 * @author davidketchum
 */
public final class UpdateFromMetadata {

  public static int RESP_FLAG_DO_NOT_USE;
  public static int RESP_FLAG_BAD;
  public static int RESP_FLAG_A0_WARN;
  public static int RESP_FLAG_A0_BAD;
  public static int RESP_FLAG_SENSITIVITY_WARN;
  public static int RESP_FLAG_SENSITIVITY_BAD;
  public static int RESP_FLAG_ELEV_AT_DEPTH;
  public static int RESP_FLAG_ELEV_AT_SURFACE;
  public static int RESP_FLAG_DEPTH_IS_NEGATIVE;
  public static int RESP_FLAG_ELEVATION_INCONSISTENT;
  public static int RESP_FLAG_MIXED_POLES;       // True if mixed poles are found.
  public static long CHANNEL_FLAG_HAS_METADATA;
  private static final String mdshost = "cwbpub.cr.usgs.gov";

  /**
   * For every channel, transfer the metadata data server flags, indicate if there is current
   * metadata for the channel (an open epoch)
   *
   * The user must have set up JDBCConnections (or DBConnections) with names 'meta' and 'edge'
   */
  public static void updateChannelResponseFromMetadata() {
    try {
      Statement stmt = DBConnectionThread.getThread("meta").getNewStatement(false);
      Statement stmt2 = DBConnectionThread.getThread("edge").getNewStatement(false);
      Statement stmt3 = DBConnectionThread.getThread("edge").getNewStatement(true);

      // For every channel, check its meta data response and set the response flags and RESPONSE_IN_MDS flag
      ResultSet rs = stmt.executeQuery("SELECT ID,responseFlags FROM metadata.responseflags order by id");
      while (rs.next()) {
        if (rs.getString("responseflags").contains("Do Not Use")) {
          RESP_FLAG_DO_NOT_USE = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("- Bad")) {
          RESP_FLAG_BAD = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("a0 Bad")) {
          RESP_FLAG_A0_BAD = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("a0 Warn")) {
          RESP_FLAG_A0_WARN = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Sensitivity Warn")) {
          RESP_FLAG_SENSITIVITY_WARN = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Sensitivity Bad")) {
          RESP_FLAG_SENSITIVITY_BAD = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Mixed Poles")) {
          RESP_FLAG_MIXED_POLES = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Mixed Poles")) {
          RESP_FLAG_MIXED_POLES = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Elevation at Depth")) {
          RESP_FLAG_ELEV_AT_DEPTH = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Elevation at Surface")) {
          RESP_FLAG_ELEV_AT_SURFACE = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Depth is Negative")) {
          RESP_FLAG_DEPTH_IS_NEGATIVE = 1 << (rs.getInt("ID") - 1);
        } else if (rs.getString("responseflags").contains("Elev Inconsistent")) {
          RESP_FLAG_ELEVATION_INCONSISTENT = 1 << (rs.getInt("ID") - 1);
        } else {
          Util.prt("   *** Metadata could not response flag for variable " + rs.getString("responseflags"));
        }
      }
      rs.close();
      rs = stmt2.executeQuery("SELECT * FROM edge.flags where flags regexp 'Has Metadata'");
      if (rs.next()) {
        CHANNEL_FLAG_HAS_METADATA = 1L << (rs.getInt("ID") - 1);

      }
      rs.close();
      Util.prta("Start update channel with MDS HAS_METADATA=" + Util.toHex(CHANNEL_FLAG_HAS_METADATA));
      ResultSet rss = stmt3.executeQuery("SELECT * FROM edge.channel ORDER BY channel");
      Timestamp now = new Timestamp(1000);
      now.setTime(System.currentTimeMillis());
      while (rss.next()) {
        long flags = 0;
        long flagsOrg = rss.getLong("mdsflags");
        String channel = rss.getString("channel");
        channel = (channel + "        ").substring(0, 12);
        long channelFlags = rss.getLong("flags");
        long channelFlagsOrg = channelFlags;
        // Need to get the values of the response flags
        try (ResultSet rs2 = stmt.executeQuery("SELECT * FROM metadata.channelepoch WHERE channel='" + channel + "' ORDER BY endingdate desc")) {
          if (rs2.next()) {
            //String sac= stasrv.getSACResponse(channel, Util.ascdate(), "nm");
            //PNZ resp = stasrv.getCookedResponse(channel.substring(0,2), channel.substring(2,7), channel.substring(7,10), channel.substring(10,12));
            //Util.prt("Sac="+sac);
            //String sac = rs2.getString("cookedresponse");
            long respflags = rs2.getLong("flags");
            Timestamp end = rs2.getTimestamp("endingdate");
            //Util.prt(channel+" respflags="+Util.toHex(respflags)+" ending="+end+" now="+now+" compare="+end.compareTo(now));
            if (end.compareTo(now) <= 0) {
              //Util.prt("No response for "+channel+" "+end.toString().substring(0,10));
              channelFlags &= ~CHANNEL_FLAG_HAS_METADATA;

            } else {
              channelFlags |= CHANNEL_FLAG_HAS_METADATA;
              flags |= Channel.FLAG_RESPONSE_IN_MDS;
              if ((respflags & RESP_FLAG_DO_NOT_USE) != 0) {
                flags |= Channel.RESP_FLAG_DO_NOT_USE;
              }
              if ((respflags & RESP_FLAG_BAD) != 0) {
                flags |= Channel.RESP_FLAG_BAD;
              }
              if ((respflags & RESP_FLAG_A0_BAD) != 0) {
                flags |= Channel.RESP_FLAG_A0_BAD;
              }
              if ((respflags & RESP_FLAG_A0_WARN) != 0) {
                flags |= Channel.RESP_FLAG_A0_WARN;
              }
              if ((respflags & RESP_FLAG_MIXED_POLES) != 0) {
                flags |= Channel.RESP_FLAG_MIXED_POLES;
              }
              if ((respflags & RESP_FLAG_SENSITIVITY_BAD) != 0) {
                flags |= Channel.RESP_FLAG_SENSITIVITY_BAD;
              }
              if ((respflags & RESP_FLAG_SENSITIVITY_WARN) != 0) {
                flags |= Channel.RESP_FLAG_SENSITIVITY_WARN;
              }
              if ((respflags & RESP_FLAG_ELEVATION_INCONSISTENT) != 0) {
                flags |= Channel.RESP_FLAG_ELEVATION_INCONSISTENT;
              }
            }
          }
        }
        if (flags != flagsOrg || channelFlags != channelFlagsOrg) {
          rss.updateLong("mdsflags", flags);
          rss.updateLong("flags", channelFlags);
          rss.updateRow();
        }
        //Util.prt("resp="+resp);
      }
      rss.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Updating channel response flags from metadata");
      e.printStackTrace();
    }
  }

  /**
   * In SNWStation 1) set the latitude, longitude, elevation,description if bad or doall 2) If any
   * channel is expected, set the expected snw groups on, if not clear it 3) Set the disable monitor
   * flag based on disable, and disableExpires in snwstation
   *
   * @param doAll If true, all of the station are updated with geographic, descriptions
   * @param doCoord Update coordinates in snwstation from STASRV
   * @param dbg If true, more output
   */
  public static void setMetaFromMDS(boolean doAll, boolean doCoord, boolean dbg) {
    try {
      StaSrv stasrv = new StaSrv(mdshost, 2052);
      StaSrv stasrvnsn8 = new StaSrv(mdshost, 2052);
      Statement stmt2 = DBConnectionThread.getThread("edge").getNewStatement(false);
      Statement stmt3 = DBConnectionThread.getThread("edge").getNewStatement(true);

      Util.prta("Start snwstation update with metadata");

      // Figure out the disabled and expected masks
      ResultSet rss = stmt3.executeQuery("SELECT * FROM edge.snwgroup WHERE snwgroup ='_Expected' OR snwgroup='_Disable Monitor'");
      long expectedMask = 0;
      long disabledMask = 0;
      while (rss.next()) {
        if (rss.getString("snwgroup").equals("_Expected")) {
          expectedMask = 1L << (rss.getInt("ID") - 1);
        }
        if (rss.getString("snwgroup").equals("_Disable Monitor")) {
          disabledMask = 1L << (rss.getInt("ID") - 1);
        }
      }
      rss = stmt3.executeQuery("SELECT * from edge.snwstation order by network,snwstation");
      Timestamp now = new Timestamp(1000);
      now.setTime(System.currentTimeMillis());
      while (rss.next()) {
        boolean changed = false;
        String station = (rss.getString("network") + "  ").substring(0, 2).toUpperCase()
                + rss.getString("snwstation").trim().toUpperCase();
        if (station.substring(0, 2).equals("  ")) {
          Util.prt("Bad station name :" + station);
          continue;
        }
        if (doCoord && (doAll || rss.getDouble("latitude") == 0 || rss.getDouble("longitude") == 0
                || rss.getString("description").equals("")
                || rss.getString("description").contains("unknown station"))) {
          double[] coord;
          String siteDescription;
          coord = stasrv.getMetaCoord(station);
          siteDescription = stasrv.getSiteDescription(station);
          siteDescription = siteDescription.replaceAll("'", "^");
          if (coord[0] == 0. || coord[1] == 0. || siteDescription.indexOf("unknown station") > 0) {
            if (!station.substring(0, 2).trim().equals("")) {
              //coord = stasrvnsn8.getCoord(station.substring(0,2), station.substring(2).trim(), "  ");
              //names = stasrvnsn8.getComment(station.substring(0,2),station.substring(2).trim(), "  ");
              //names[0] = names[0].replaceAll("'","^");

              coord = stasrvnsn8.getMetaCoord(station.trim(), "all");
              siteDescription = stasrvnsn8.getSiteDescription(station.trim(), "all");
              siteDescription = siteDescription.replaceAll("'", "^");
              if (siteDescription.contains("unknown")) {
                if (dbg) {
                  Util.prt("*** station is not in MDS under network code=" + station);
                }
              }
            }
          }
          if (coord[0] == 0. || coord[1] == 0. || siteDescription.indexOf("unknown station") > 0) {
            //coord = stasrvnsn8.getCoord("IR", station.substring(2).trim(), "  ");
            //names = stasrvnsn8.getComment("IR",station.substring(2).trim(), "  ");
            coord = stasrvnsn8.getMetaCoord("IR" + station.substring(2).trim());
            siteDescription = stasrvnsn8.getSiteDescription("IR" + station.substring(2).trim());
            siteDescription = siteDescription.replaceAll("'", "^");
            if (siteDescription.contains("unknown")) {
              if (dbg) {
                Util.prt("*** station is unknown in MDS as an IR =" + station);
              }
            }
            if (coord[1] != 0) {
              Util.prt("IR for " + station + " was sucessful, but suspicions");
            }
          }
          if (siteDescription.contains("unknown")) {
            Util.prta("Set " + station + " to " + coord[0] + " " + coord[1] + " " + coord[2] + " " + siteDescription);
          }
          rss.updateString("description", siteDescription);
          rss.updateDouble("latitude", coord[0]);
          rss.updateDouble("longitude", coord[1]);
          rss.updateDouble("elevation", coord[2]);
          changed = true;
        }

        long groupmask = rss.getLong("groupmask");
        // Set the _Disabled Group mask
        if (rss.getInt("disable") != 0) {
          String disableDate = rss.getString("disableExpires");
          if (disableDate.length() == 10) {
            disableDate += " 00:00:00";
          }
          if (!disableDate.equals("")) {
            try {
              Timestamp disable = Timestamp.valueOf(disableDate);
              if (disable.compareTo(now) > 0 && (groupmask & disabledMask) != 0) {   // disable is still on
                changed = true;
                groupmask |= disabledMask;
                if (dbg) {
                  Util.prt("Turn on disable mask for " + station);
                }
              } else if ((groupmask & disabledMask) != 0) {
                changed = true;
                groupmask = ~disabledMask;
                if (dbg) {
                  Util.prt("Turn OFF disable mask for " + station);
                }
              }
            } catch (RuntimeException e) {
              Util.prt("Disable date looks illegal " + disableDate);
            }
          }
        } else if ((groupmask & disabledMask) != 0) {
          changed = true;
          groupmask = ~disabledMask;
          if (dbg) {
            Util.prt("Turn OFF disable mask for " + station);
          }
        }

        try ( // Set the _Expected group mask based on whether any channel is expected
                ResultSet rs2 = stmt2.executeQuery("SELECT expected FROM edge.channel WHERE channel regexp '^"
                        + (station + "     ").substring(0, 7) + "' AND expected!=0")) {
          if (rs2.next()) {
            if ((groupmask & expectedMask) == 0) {
              rss.updateLong("groupmask", (groupmask | expectedMask));
              changed = true;
              if (dbg) {
                Util.prt("Turn ON  expected SNWGroup for " + station);
              }
            }
          } else {
            if ((groupmask & expectedMask) != 0) {
              rss.updateLong("groupmask", (groupmask & ~expectedMask));
              changed = true;
              if (dbg) {
                Util.prt("Turn OFF expected SNWGroup for " + station);
              }
            }
          }
        }

        if (changed) {
          try {
            rss.updateRow();
          } catch (SQLException e) {
            Util.SQLErrorPrint(e, "updating snwstation " + rss.getString("network") + rss.getString("snwstation")
                    + " " + Util.toHex(groupmask));
            e.printStackTrace();
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "looking for differences in UpdateFromMetadata");
      e.printStackTrace();
    }
    Util.prta("Metadata updates done.");
  }

  /**
   * This main Updates the channel table in edge with rates, NSN triplets, and adds new channels.
   */
  public static void updateTriplet() {
    JDBConnection jcjbl;
    Connection C;
    Util.prt("****** UpdateTriplet does not make sense if VMS is gone!");
    try {

      // Get a new connection to the edge database
      C = JDBConnection.getConnection("edge");
      UC.setConnection(C);
      String seedname;
      String line;
      int net;
      int node;
      int chan;
      BufferedReader in = new BufferedReader(new FileReader("xsta_edge.dat"));
      DBObject obj;
      int nchanged = 0;
      int nnew = 0;
      while ((line = in.readLine()) != null) {
        if (line.substring(0, 3).equals("Sta")) {
          continue;
        }
        seedname = line.substring(10, 12) + line.substring(0, 5) + line.substring(6, 9) + line.substring(12, 14);
        obj = new DBObject(DBConnectionThread.getThread("edge"), "edge", "channel", "channel", seedname);
        //Util.prt("seedname="+seedname+" new="+obj.isNew());
        if (!obj.isNew()) {
          if (line.substring(15, 23).equals("        ")) {
            net = 0;
            node = 0;
            chan = 0;
          } else {
            net = Integer.parseInt(line.substring(15, 17), 16);
            node = Integer.parseInt(line.substring(18, 20), 16);
            chan = Integer.parseInt(line.substring(21, 23), 16);
          }
          if (obj.isNew() || net != obj.getInt("nsnnet") || node != obj.getInt("nsnnode")
                  || chan != obj.getInt("nsnchan")) {
            Util.prt(seedname + " " + " " + net + "-" + node + "-" + chan + " was "
                    + obj.getInt("nsnnet") + "-" + obj.getInt("nsnnode") + "-" + obj.getInt("nsnchan"));
            obj.setInt("nsnnet", net);
            obj.setInt("nsnnode", node);
            obj.setInt("nsnchan", chan);
            nchanged++;
            obj.updateRecord();
          }
        } else {
          Util.prt("New station? " + seedname + " From vms. Ignore");
          //obj.setString("channel",seedname);
          nnew++;
        }

        obj.close();
      }
      Util.prta("# new=" + nnew + " #changed=" + nchanged);

    } catch (SQLException e) {
      System.err.println("SQLException on getting Channel" + e.getMessage());
      Util.SQLErrorPrint(e, "SQLExcepion on gettning Channel");
    } //catch (JCJBLBadPassword E) {
    //  Util.prt("bad password");
    //}
    catch (FileNotFoundException e) {
      Util.prt("could not open xsta_edge.dat" + e.getMessage());
    } catch (IOException e) {
      Util.prt("IOException found " + e.getMessage());
    }

  }

  public static void main(String args[]) {
    //JDBConnection jcjbl;
    DBConnectionThread db, dbedge, dbanss;
    Util.init("edge.prop");
    Util.setProcess("UpdateFromMetadata");
    //Connection C;
//		User u = new User("test");
    String host = Util.getProperty("DBServer");
    DBConnectionThread.init(host);
    args = "-resp -mdschanged -xsta".split(" ");    // DEBUG:
    if (args.length == 0) {
      Util.prt("Usage :");
      Util.prt("   -mdschanged  Update Channel expected flag and geographic, & description for all with bad fields");
      Util.prt("   -mdsall      Update expected flag, geographic and description (lat, long, elev, description");
      Util.prt("   -resp        Update response flags from Metadata database for all channels");
      Util.prt("   -xsta        Update triplettes from xsta_edge.dat");
      Util.prt("   -host         MySQL host");
      Util.prt("   -mdshost     USe this host for the MDS rather than the USGS internal one.");
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-host")) {
        host = args[i + 1];
      }
    }
    //new User("dkt");
    try {
      // Get user from the Inventory database user file
      db = new DBConnectionThread(Util.getProperty("MetaDBServer"), "readonly", "metadata", false, false, "meta", Util.getOutput());
      //jcjbl = new JDBConnection(host,"metadata","ro","readonly", true,"meta");

      //jcjbl = new JDBConnection(host,"inv","ro","readonly", true);
      //C = JDBConnection.getConnection();
      //User u = new User(C,"dkt","karen");
      // Get a new connection to the EDGE database
      dbanss = new DBConnectionThread(host, "update", "anss", true, false, "anss", Util.getOutput());
      dbedge = new DBConnectionThread(host, "update", "edge", true, false, "edge", Util.getOutput());
      if (!db.waitForConnection()) {
        if (!db.waitForConnection()) {
          if (!db.waitForConnection()) {
            System.out.println("*** could not connect to metadata");
          }
        }
      }
      if (!dbedge.waitForConnection()) {
        if (!dbedge.waitForConnection()) {
          if (!dbedge.waitForConnection()) {
            System.out.println("*** could not connect to edge");
          }
        }
      }
      if (!dbanss.waitForConnection()) {
        if (!dbanss.waitForConnection()) {
          if (!dbanss.waitForConnection()) {
            System.out.println("*** could not connect to anss");
          }
        }
      }
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "-resp":
            updateChannelResponseFromMetadata();
            break;
          case "-mdsall":
            setMetaFromMDS(true, true, false);
            break;
          case "-mdschanged":
            setMetaFromMDS(false, true, false);
            break;
          case "-xsta":
            updateTriplet();
            break;
          case "-host":
            i++;
            break;
          default:
            Util.prt("Unknown switch=" + args[i]);
            break;
        }
      }

      // this check for differences in snwgroup settings versus what ANSS.jar predicts!
      // Why is this commented?
      //setSNWGroupsFromANSS("");
    } catch (InstantiationException e) {
      System.err.println("SQLException on getting SNWStation" + e.getMessage());
    }

  }
}
