/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * I was looking at if I cached all of the file names for julian days whether a performance increase
 * in query could be obtained. While I found that it does not take long to look through all of the
 * directories in the old manner (less than 0.1 sec) if the server was doing a lot of queries it is
 * possible this would be a significant amount of time to look through the directories on each
 * query. So in the new QueryServer2 this is used to make it so the files are not taken from the
 * directories when the query server is building a file list with data for certain julian days.
 * <p>
 * This thread reads all of the directories every 5 minutes, unless its a new day where it reads
 * them every 15 seconds to pick up new file creations.
 * <p>
 * The QueryServer uses this to look for files containing data for the julian day range of a
 * request, it then ensures the files are open using the normal IndexFileReplicator methods.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class QueryFilesThread extends Thread {

  private final TreeMap<Integer, ArrayList<QueryFile>> files = new TreeMap<>();
  private final ArrayList<Integer> julians = new ArrayList<>(10000);
  private static QueryFilesThread thisThread;
  private static long msQueryFiles;
  private final int updateMS;
  private boolean terminate;
  private boolean dbg = false;
  private int nfiles;
  private long elapse;
  private long lastLoad;
  private final ArrayList<QueryFile> deleteList = new ArrayList<>(10);
  private final EdgeThread par;
  private final StringBuilder sbdump = new StringBuilder(1000);

  /**
   * Static access to this thread (there better be only one of these running!)
   *
   * @return This thread
   */
  public static QueryFilesThread getThread() {
    return thisThread;
  }

  public static long getMSQueryFiles() {
    return msQueryFiles;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public void terminate() {
    terminate = true;
    interrupt();
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

  public boolean delete(int julian, String fullPath) {
    if (getJulianFiles(julian, deleteList) == null) {
      return false;
    }
    for (int i = 0; i < deleteList.size(); i++) {
      if (deleteList.get(i).getCanonicalPath().equals(fullPath)) {
        prta("QFT: * delete file " + fullPath);
        deleteList.remove(i);
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "QFT: #files=" + nfiles + " last elapse=" + elapse;
  }

  /**
   * return an array list of files for a julian date. QueryServer calls this to get the list of
   * candidate files to open for a given julian day.
   *
   * @param julian The julian date to get the list of files for.
   * @param userFiles If null, a new ArrayList is return, if not null this array list is cleared and
   * the files for the day added and returned.
   * @return the ArrayList of QueryFiles or null if there are none on this day
   */
  public synchronized ArrayList<QueryFile> getJulianFiles(int julian, ArrayList<QueryFile> userFiles) {
    Integer julianInt = getJulianInt(julian);
    if (dbg) {
      int[] ymd = SeedUtil.fromJulian(julian);
      int doy = SeedUtil.doy_from_ymd(ymd);
      ArrayList<QueryFile> f = files.get(julianInt);
      if (f == null) {
        prt("getJulianFiles for jul=" + julian + " yr=" + ymd[0] + "," + doy + " returns NULL files " + " julianint=" + julianInt);
      } else {
        prt("getJulianFiles for jul=" + julian + " yr=" + ymd[0] + "," + doy + " returns " + f.size() + " files.");
        for (int i = 0; i < f.size(); i++) {
          prt(i + " " + f.get(i));
        }
      }
    }
    ArrayList<QueryFile> tmp = files.get(julianInt);
    if (tmp == null) {
      // just to be sure rerun the check if its beem more than one second
      if(System.currentTimeMillis() - lastLoad > 1000) {
        int[] ymd = SeedUtil.fromJulian(julian);
        int doy = SeedUtil.doy_from_ymd(ymd);
        prta("*getJulianFiles return null for jul="+julian+" yr="+ymd[0]+","+doy+
                " check the directories again"+(System.currentTimeMillis() - lastLoad));
        doQueryFilesCheck();
        tmp = files.get(julianInt);
        if(tmp == null) {
          return null;
        }
      }        // cause the run thread to execute again
      
      return null;
    }
    synchronized (tmp) {
      if (userFiles == null) {
        userFiles = new ArrayList<QueryFile>(tmp.size());
      } else {
        userFiles.clear();       // User supplied the files
      }
      for (QueryFile file : tmp) {
        userFiles.add(file);
      }
    }
    return userFiles;
  }

  /**
   * Return an integer of this julian date
   *
   * @param julian
   * @return
   */
  public Integer getJulianInt(int julian) {
    Integer julianInt = null;
    synchronized (julians) {
      for (Integer julian1 : julians) {
        if (julian1 == null) {
          continue;
        }
        if (julian1 == julian) {
          julianInt = julian1;
          break;
        }
      }
      if (julianInt == null) {
        julianInt = julian;
        julians.add(julianInt);
      }
    }
    return julianInt;
  }

  public QueryFilesThread(EdgeThread par) {
    this.par = par;
    this.updateMS = 300000;
    setDaemon(true);
    start();

  }

  @Override
  public void run() {
    IndexFileReplicator.init();               // This insures the edge.prop has been read
    thisThread = this;
    long wait;
    long remainder;
    long now;
    prt("QFT: npaths=" + IndexFileReplicator.getNPaths());
    for (int i = 0; i < IndexFileReplicator.getNPaths(); i++) {
      prt("QFT: " + i + " path=" + IndexFileReplicator.getPath(i));
    }
    while (!terminate) {
      try {
        int npaths = IndexFileReplicator.getNPaths();
        if (dbg) {
          prt("QFT: npaths=" + npaths);
        }

        nfiles = 0;
        long top = System.currentTimeMillis();
        doQueryFilesCheck();

        now = System.currentTimeMillis();
        elapse = now - top;
        prta("QFT: elapse=" + elapse + " #files=" + nfiles + " updMS=" + updateMS + " " + EdgeQueryServer.getString());
        msQueryFiles += elapse;
        wait = updateMS;        // assume to wait the full time
        remainder = (now + updateMS) % 86400000L;     // milliseconds of day e
        if (remainder < 2 * updateMS) {
          if (now % 86400000L > 80000000) {
            wait = 86400000L - (now % 86400000L) + 10000; // Wait until 10 seconds after the midnight
          } else if (remainder > updateMS + 120000) {
            wait = updateMS;   // If we are after the first two minutes of the day
          } else {
            wait = 15000;        // do it every 15 seconds until two minutes of the day have passed
          }
        }   // If the end spans midnight an before one full cycle, do it quicker
        if (wait != updateMS) {
          prta("QFT: short wait for " + wait + " must be near beginning of new day");
        }

        // WE wait the normal interval unless its the beginning of a new day, then it is less
        try {
          sleep(wait);
        } // if near the end of the day, wait for the end of the day+10000
        catch (InterruptedException expected) {
        }

      } catch (RuntimeException e) {
        prta("QFT: Runtime in QFT run(). continue e=" + e);
        e.printStackTrace(par.getPrintStream());
      }
    }   // while!terminate
    prta("QFT: exiting");
  }
  public synchronized void doQueryFilesCheck() {
    int npaths = IndexFileReplicator.getNPaths();
        // for each path, get all of the files
    int year;
    int doy;
    int julian;
    ArrayList<QueryFile> f;
    for (int i = 0; i < npaths; i++) {
      String path = IndexFileReplicator.getPath(i);
      File dir = new File(path.substring(0, path.length() - 1));
      if (dbg) {
        prta("QFT: path=" + path + " dir=" + dir.toString() + " isDir=" + dir.isDirectory() + " dbg=" + dbg);
      }
      File[] file = dir.listFiles();
      String node;
      if (file == null) {
        if (dbg) {
          prta("No files on path!!!");
        }
        continue;
      }       // no files in directory
      // for each file, if it is an .idx file, see if its in the date range and open if needed
      for (File file1 : file) {
        if (file1.getName().indexOf(".idx") > 0) {
          nfiles++;
          if (dbg) {
            prt(file1.toString() + " " + path);
          }
          String filename = file1.getName();
          if (!Character.isDigit(filename.charAt(0))) {
            continue; // not in the right form
          }
          int period = filename.indexOf(".");
          filename = filename.substring(0, period);
          if (filename.length() < 8) {
            prta("**** filename is too short =" + filename + " path=" + path + " dir=" + dir.toString());
            continue;
          }
          try {
            year = Integer.parseInt(filename.substring(0, 4));
            doy = Integer.parseInt(filename.substring(5, 8));
            node = (filename.substring(9) + "    ").substring(0, 4);
          } catch (NumberFormatException e) {
            continue;     // Probably not a data file
          }
          julian = SeedUtil.toJulian(year, doy);
          Integer julianInt = getJulianInt(julian);
          f = files.get(julianInt);

          if (f == null) {
            f = new ArrayList<>(10);
            synchronized (files) {
              files.put(julianInt, f);
            }
          }
          boolean ok = false;
          try {
            for (QueryFile f1 : f) {
              if (file1.getCanonicalPath().equals(f1.getCanonicalPath())) {
                ok = true;
                break;
              }
            }
            if (!ok) {
              if (dbg && f.size() > 0) {
                prt("Add " + file1.getAbsolutePath() + " to " + julian + "/" + julianInt + " " + f.get(0).toString() + "sz=" + f.size());
              }
              synchronized (f) {
                f.add(new QueryFile(file1, julian));
              }
            }
          } catch (IOException e) {
            prt("QFT: Cannot get a canonical path for file");
          }
        }
      }
    } // end of if on all paths
    lastLoad = System.currentTimeMillis();
  }
  public StringBuilder dumpFiles() {
    if (sbdump.length() > 0) {
      sbdump.delete(0, sbdump.length());
    }
    Iterator<ArrayList<QueryFile>> itr = files.values().iterator();
    Object[] keys = files.keySet().toArray();
    int j = 0;
    while (itr.hasNext()) {
      ArrayList<QueryFile> qf = itr.next();
      sbdump.append(((Integer) keys[j++]).intValue()).append(" ");
      for (QueryFile qf1 : qf) {
        sbdump.append(qf1.toString()).append(" ");
      }
      sbdump.append("\n");
    }
    return sbdump;
  }

  public static void main(String[] args) {
    QueryFilesThread qft = new QueryFilesThread(null);
    boolean dbg = false;
    for (String arg : args) {
      if (arg.equals("-dbg")) {
        dbg = true;
        qft.setDebug(true);
      }
    }
    for (;;) {
      Util.sleep(30000);
      Util.prta((dbg ? qft.dumpFiles().toString() + "\n" : "") + qft.toString());
    }
  }
}
