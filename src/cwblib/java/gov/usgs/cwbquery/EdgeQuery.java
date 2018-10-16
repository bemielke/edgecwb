/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgeoutput.DeleteBlockInfo;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.seed.MiniSeedPool;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.List;
import java.util.Collections;
import java.util.Date;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * This class is a descendant of EdgeQuery but it works differently. When building a matches of
 * IndexFileReplicators on its indices matches for a given julian day range, it uses the
 * QueryFilesThread class to find the files for each julian day. It then opens those files using the
 * IndexFileReplicator method and adds those now open files for days of interest to the indices
 * (this is done in the "setParamters() method. The actually query for directories or data is then
 * done through other methods.
 *
 * Data Query : the EdgeQueryThread2 which owns this object sets up the file matches with
 * setParameters() and then uses getMatches() to get a channel matches. If the user uses the -delaz
 * switches, then the EdgeQueryThread2 calls the setDelaz() method before asking for data so that
 * only data within the delaz requests is returned. It then calls the query() method on all of the
 * matches3 to get the data blocks.
 *
 * Directories : The EdgeQueryThread2 calls this object setParameters() to build the file matches.
 * It then calls either queryDirector() to get a matches of file names, or getChannels() to get the
 * channel summary (-lsc).
 *
 * DeleteData : this type of query returns the DeleteBlockInfo array for all of the blocks that
 * match the query. It will return the information if the block is to be marked deleted or has
 * already been deleted but lies in the interval;
 *
 * See getMatches() for a description of how channel masks, excludes file and delaz limits work.
 */
public final class EdgeQuery implements MiniSeedOutputHandler {

  private final List indices = Collections.synchronizedList(new ArrayList(200)); // a matches of IndexFileReplicators for the given date range
  private static final int BLOCK_SIZE = 512;
  private static final MiniSeedPool msp = new MiniSeedPool();
  private boolean dbg;
  private static boolean quiet;
  private static final ArrayList<String> excludes = new ArrayList<>(50);             // the matches of seednames to be excluded
  private static long excludeLastUpdate;
  //private static DecimalFormat df3;
  private static long msQueryChannels;
  private static long msQueryMatches;
  private static long msQuery;
  private static long msSetParameters;
  private String tag;
  private boolean allowDeleted;           // If true, deleted blocks will be returned
  private boolean gapsonly;               // if true, only send 1st 64 bytes of each MiniSeed
  private final boolean readonly;               // set true if IndexFileReplicators are to be readonly
  // set false for servers running on a EdgeMom w/Replicator as
  // The replicator also ahs IFRs open, but for read/write.
  private int lastNDups;
  private int lastNblocks;
  private String delazargs, delazbegin;
  private final ArrayList<String> selectedChannels = new ArrayList<>(100);
  private Socket s;
  private final byte[] seedBuf = new byte[4096];
  private final byte[] bigbuf = new byte[64 * BLOCK_SIZE];
  private final EdgeThread par;
  private final StringBuilder sbdir = new StringBuilder(100);
  private final StringBuilder sbqc = new StringBuilder(1000);
  private final StringBuilder sbrange = new StringBuilder(100);
  private final StringBuilder sbset = new StringBuilder(100);
  //private long maskNotPublic;

  public static MiniSeedPool getMiniSeedPool() {
    return msp;
  }

  ;
  public static String getStatus() {
    return " setParm=" + msSetParameters + " match=" + msQueryMatches
            + " query=" + msQuery + " lsc=" + msQueryChannels + " qFiles=" + QueryFilesThread.getMSQueryFiles();
  }

  public int getLastNDups() {
    return lastNDups;
  }

  public void resetLastNDups() {
    lastNDups = 0;
  }

  public List getIndices() {
    return indices;
  }

  public int getLastNblks() {
    return lastNblocks;
  }

  public void resetLastNblks() {
    lastNblocks = 0;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
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
   * QueryThread calls this to create a delaz ring on donut of channels that are permitted to be
   * returned. The matches built here is used by the query() methods to include only data which is
   * on this matches.
   *
   * @param ss The delazc arguments for the metadata server ([mindeg:]maxdeg:lat:long)
   * @param beg The begin time for the -delazc (the day the matches is desired for)
   */

  /**
   * QueryThread calls this to create a delaz ring on donut of channels that are permitted to be
   * returned.The matches built here is used by the query() methods to include only data which is on
   * this matches.
   *
   * @param ss The delazc arguments for the metadata server ([mindeg:]maxdeg:lat:long)
   * @param beg The begin time for the -delazc (the day the matches is desired for)
   * @param seedname The seedname RE for a delazc command
   */
  public void setDelaz(String ss, String beg, String seedname) {
    delazargs = ss;
    String line = "-c r -delazc " + delazargs.trim() + " -b " + beg.trim() + " -s " + seedname + "\n";
    if (!quiet) {
      prta(tag + " delazc command line is " + line + "|");
    }

    if (selectedChannels.size() > 0) {
      selectedChannels.clear();
    }
    try {
      if (s == null || s.isClosed() || !s.isConnected()) {
        prta("Create socket to MDS on " + EdgeQueryServer.mdsServer + "/" + EdgeQueryServer.mdsPort);

        s = new Socket(EdgeQueryServer.mdsServer, EdgeQueryServer.mdsPort);
      }
      s.getOutputStream().write(line.getBytes());
      BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
      try {
        while ((line = in.readLine()) != null) {
          if (line.contains("Channel")) {
            continue;
          }
          if (line.contains("* <EOR>")) {
            break;
          }
          //prt(line);
          selectedChannels.add(line);
        }
        if (!quiet) {
          prta(tag + " " + selectedChannels.size() + " delazc channels inside of delaz=" + delazargs);
        }
        s.close();
        s = null;
      } catch (IOException e) {
        System.out.println("Stasrv: Error reading response" + e.getMessage());
        if (s != null) {
          try {
            s.close();
          } catch (IOException e2) {
          }
        }
        s = null;
      }

    } catch (IOException e) {
      System.out.println("Stasrv: IOException to " + EdgeQueryServer.mdsServer + "/" + EdgeQueryServer.mdsPort + " " + e.getMessage());
      e.printStackTrace(par.getPrintStream());
      if (s != null) {
        try {
          s.close();
        } catch (IOException e2) {
        }
      }
      s = null;
    }
  }

  /**
   * convert a julian day to "yy_DOY"
   *
   * @param julian the julian date to transform
   * @return String of form "yy_doy"
   */
  public static String yrday(int julian) {
    int[] ymd = SeedUtil.fromJulian(julian);
    int doy = SeedUtil.doy_from_ymd(ymd);
    return "" + Util.df3(ymd[0] % 100).substring(1, 3) + "_" + Util.df3(doy);
  }

  /* read int the excludes file. 
   * 
   */
  private static void readExcludes(String exclude) {
    if (exclude != null && !exclude.equals("") && System.currentTimeMillis() - excludeLastUpdate > 120000) {
      synchronized (excludes) {
        try {
          if (!excludes.isEmpty()) {
            excludes.clear();
          }
          try (BufferedReader in = new BufferedReader(new FileReader(IndexFile.getPath(0) + exclude))) {
            String line;
            while ((line = in.readLine()) != null) {
              // strip out comment line and any text in the comment.
              if (line.indexOf("#") == 0) {
                continue;
              }
              if (line.indexOf("#") > 0) {
                line = line.substring(0, line.indexOf("#") - 1);
              }
              line = line.trim();
              if (line.length() == 0) {
                continue;
              }
              if (excludeLastUpdate == 0 && !quiet) {
                Util.prt("Exclude :|" + line + "|");
              }
              excludes.add(line);
            }
            excludeLastUpdate = System.currentTimeMillis();
          }
        } catch (IOException e) {
          Util.prta(" Could not open or read lines from "
                  + IndexFile.getPath(0) + exclude + " nlines=" + excludes.size() + e.getMessage());
        }
      }
    }
  }

  /**
   * create a query object which can be reused
   *
   * @param ro Set readonly flag, must be set false if this is created in a thread on a Replicator
   * node as that also is creating IFR, but of read/write style.
   * @param parent for logging
   */
  public EdgeQuery(boolean ro, EdgeThread parent) {
    tag = "EQ:";
    readonly = ro;
    par = parent;
    quiet = EdgeQueryServer.quiet;
  }
  /**
   * This routine originated when the EdgeQuery class was made reusable over the EdgeQuery class. In
   * the original class the matches of files was built when an EdgeQuery object was created. Now,
   * the indices to files is built when this function is called against the reusable EdgeQuery class
   * Its primary function is to open all files for all julian days between beg and end inclusive. It
   * also set other query parameters like whether this is a "gaps only" request or "lsc" type.
   *
   * @param beg Date of beginning of query
   * @param end Date at end of query
   * @param d Debug flag
   * @param exclude Filename on PATH[0] to find excludes
   * @param gaps If true, this is a gap only scan, and the headers only will be returned
   * @param tg The logging tag for this query
   * @param lsc If true, run an LSC command - if so, the data stored in the indices is really the
   * names of the files and the files are not opened.
   */
  ArrayList<QueryFile> filesTmp = new ArrayList<>(10);

  /**
   * This routine originated when the EdgeQuery class was made reusable over the EdgeQuery class.In
   * the original class the matches of files was built when an EdgeQuery object was created. Now,
   * the indices to files is built when this function is called against the reusable EdgeQuery class
   * Its primary function is to open all files for all julian days between beg and end inclusive. It
   * also set other query parameters like whether this is a "gaps only" request or "lsc" type.
   *
   * @param beg Date of beginning of query
   * @param end Date at end of query
   * @param d Debug flag
   * @param exclude Filename on PATH[0] to find excludes
   * @param gaps If true, this is a gap only scan, and the headers only will be returned
   * @param tg The logging tag for this query
   * @param lsc If true, run an LSC command - if so, the data stored in the indices is really the
   * names of the files and the files are not opened.
   * @param del If true, deletes are allowed.
   */
  public void setParameters(Date beg, Date end, boolean d, String exclude, boolean gaps, String tg, boolean lsc, boolean del) {
    long start = System.currentTimeMillis();
    if (sbset.length() > 0) {
      sbset.delete(0, sbset.length());
    }
    gapsonly = gaps;
    allowDeleted = del;
    dbg = d;
    tag = "EQ:" + tg;
    readExcludes(exclude);
    if (dbg) {
      prta(tag + " # excludes=" + excludes.size() + " " + IndexFile.getPath(0) + exclude);
    }
    //dbg=true;
    IndexFileReplicator.init();               // This insures the edge.prop has been read
    int npaths = IndexFileReplicator.getNPaths();
    int begjulian = SeedUtil.toJulian(beg);
    int endjulian = SeedUtil.toJulian(end);
    if (dbg) {
      prt(tag + " npaths=" + npaths + " beg=" + beg + "/" + begjulian + " end=" + end + "/" + endjulian);
    }

    int nchk = 0;                     // number of files looked at
    int nopen = 0;                    // number needing to be opened
    int nfound = 0;                   // Number found already opened.

    synchronized (indices) {
      indices.clear();
      for (int julian = begjulian; julian <= endjulian; julian++) {
        if (QueryFilesThread.getThread().getJulianFiles(julian, filesTmp) == null) {
          continue;
        }
        for (QueryFile file : filesTmp) {
          if (lsc) {
            if (julian >= begjulian && julian <= endjulian) {
              int c = file.getCanonicalPath().lastIndexOf("/");
              if (c > 0) {
                indices.add(file.getCanonicalPath().substring(0, c + 1) + ":" + file.getCanonicalPath().substring(c + 1).replaceAll(".idx", ""));
              } else {
                indices.add(":" + file.getCanonicalPath()); // This should not happen
              }
            }
          } else {
            // If it is already open in the IndexFileReplicator, use it, else open it!
            IndexFileReplicator idx = IndexFileReplicator.getIndexFileReplicator(julian, file.getNode());
            if (idx == null) {
              try {
                nopen++;
                //idx = new IndexFileReplicator(file.getCanonicalPath(), file.getNode(), 0, false, readonly);
                idx = new IndexFileReplicator(julian, file.getNode(), 0, false, readonly);
                if (dbg) {
                  prta(tag + "   is NEW " + idx.toString());
                }
                indices.add(idx);
                idx.updateLastUsed();
                sbset.append(idx.getFilename()).append(" NEW ");
              } catch (EdgeFileDuplicateCreationException e) {
                prta(tag + " * EdgeFileDuplicateCreations should not happen! e=" + e + " julian=" + julian 
                        + " node=" + file.getNode() + " f=" + file);
                //e.printStackTrace(par.getPrintStream());
                idx = IndexFileReplicator.getIndexFileReplicator(julian, file.getNode());
                if (idx != null) {
                  indices.add(idx);
                  idx.updateLastUsed();
                  sbset.append(idx.getFilename()).append(" NEW");
                  prta(tag + "* Duplicate creation handled in setParameters() " + idx);
                } else {
                  prta(tag + " **** Duplicate creation NOT handled in setParameters()");
                }
              } catch (FileNotFoundException e) {
                prt(tag + " *** FileNotFound for file=" + file.getCanonicalPath() + "Too old to open? e=" + (e != null ? e.getMessage() : "null"));
                if (e != null) {
                  if (e.toString().contains("Too many open file")) {
                    SendEvent.pageSMEEvent("TooManyOpen", "In QueryServer on " + Util.getNode(), "EdgeQuery");
                    Util.exit(1);
                  }
                }
              } catch (EdgeFileReadOnlyException e) {
                prt(tag + "ReadFileOnlyException=" + file.getCanonicalPath());
              } catch (IOException e) {
                prt(tag + "EdgeQuery open file IOException=" + file.getCanonicalPath() + " " + e);
              }
            } else {
              indices.add(idx);     // add found to indices
              idx.updateLastUsed();
              sbset.append(idx.getFilename()).append(" OLD ");
              if (dbg) {
                prta("   is OLD " + idx.toString());
              }
              nfound++;
            }
          } //else on is this a lsc request
        } // for each file on that julian day
      } // for each julian day
    } // Synchronize on indices
    if (dbg) {
      prta(tag + " " + indices.size() + " files in date range. opened=" + nopen + " found=" + nfound 
              + " check=" + nchk + "\n" + sbset.toString());
    }
    if (dbg) {
      for (int i = 0; i < indices.size(); i++) {
        if (!lsc) {
          IndexFileReplicator ifr = (IndexFileReplicator) indices.get(i);
          prta(tag + " EQSP:    " + i + " is " + ifr.toString());
        }
      }
    }
    msSetParameters += (System.currentTimeMillis() - start);
  }

  /**
   * return a string with a matches of the files for the date range of the files from -b and -d
   *
   * @return A string with the directory
   */
  public String queryDirectory() {
    ArrayList list = new ArrayList(1000);
    if (sbdir.length() > 0) {
      sbdir.delete(0, sbdir.length());
    }
    IndexFileReplicator.init();               // This insures the edge.prop has been read
    int npaths = IndexFileReplicator.getNPaths();
    if (dbg) {
      prt(tag + " npaths=" + npaths);
    }
    // for each path, get all of the files
    for (int i = 0; i < npaths; i++) {
      String path = IndexFileReplicator.getPath(i);
      File dir = new File(path.substring(0, path.length() - 1));
      if (dbg) {
        prta(tag + "path=" + path + " dir=" + dir.toString() + " isDir=" + dir.isDirectory());
      }
      String[] files = dir.list();
      String node = "";
      for (String file : files) {
        if (file.indexOf(".idx") > 0) {
          list.add(file + " " + path);
        }
      }
    }
    Object[] all = list.toArray();
    Arrays.sort(all);
    for (int i = 0; i < all.length; i++) {
      sbdir.append(all[i]).append("\n");
      if (i > 0 && all[i].toString().equals(all[i - 1].toString())) {
        sbdir.append("   **** duplicate file!  This should not happen!\n");
      }
    }
    return sbdir.toString();
  }

  /**
   * create a matches of the channels available for the range of julian dates given when this
   * EdgeQuery was created (from -b and -d)
   *
   * @param showIllegals If true, illegal seed names will be returned if found
   * @param timeout A timeout thread to reset as thing progress
   * @return A channel matches /
   * @throws IOException
   */
  public String queryChannels(boolean showIllegals, TimeoutThread timeout) throws IOException {
    long st = System.currentTimeMillis();
    if (sbqc.length() > 0) {
      sbqc.delete(0, sbqc.length());
    }
    TreeMap<String, DaysList> chans = new TreeMap<>();
    boolean exclude;

    long lastIndicesUpdate = 0;
    int nexclude = 0;
    int ncomp = 0;
    if (!quiet) {
      prt(tag + " EQC: There are " + indices.size() + " files to analyze. exclude=" + excludes.size()
              + " delaz chans=" + (selectedChannels == null ? "null" : selectedChannels.size()));
    }
    Collections.sort(indices);
    for (int ifile = 0; ifile < indices.size(); ifile++) { // for each file
      timeout.resetTimeout();
      String[] parts = ((String) indices.get(ifile)).split(":");
      String path = parts[0];
      String filename = parts[1];
      int year, doy;
      String node;
      try {
        year = Integer.parseInt(filename.substring(0, 4));
        doy = Integer.parseInt(filename.substring(5, 8));
        node = (filename.substring(9) + "    ").substring(0, 4);
      } catch (NumberFormatException e) {
        continue;     // Probably not a data file
      }

      int julian = SeedUtil.toJulian(year, doy);
      // If it is already open in the IndexFileReplicator, use it, else open it!
      boolean found = true;
      IndexFileReplicator idx = IndexFileReplicator.getIndexFileReplicator(julian, node);
      //prt("File open for indices="+indices.get(ifile)+" node="+node+" julian="+julian+" idx="+idx);
      if (idx == null) {
        try {
          idx = new IndexFileReplicator(path + filename, node, 0, false, readonly);
          found = false;
        } catch (EdgeFileDuplicateCreationException e) {
          prt("Edge file duplicate creations cannot happen!");
          e.printStackTrace(par.getPrintStream());
        } catch (FileNotFoundException e) {
          prt("** FileNotFound for file=" + path + filename + node + " e=" + e);
          //prt(QueryFilesThread.getThread().dumpFiles());

          if (e != null) {
            if (e.toString().contains("oo many open file")) {
              SendEvent.pageSMEEvent("TooManyOpen", "In QueryServer on " + Util.getNode(), this);
              Util.exit(1);
            }
          }
          if (!quiet) {
            prt("Try to delete file =" + QueryFilesThread.getThread().delete(julian, path + filename + node + ".idx"));
          }
          continue;   // probably a file that has been deleted
        } catch (EdgeFileReadOnlyException e) {
          prt("ReadFileOnlyException=" + path + filename);
        } catch (IOException e) {
          prt("EdgeQuery open file IOException=" + path + filename + " " + e);
        }
      }
      if (idx == null) {
        prt("Count not find the referenced index file=" + filename + " julian=" + julian + " node=" + node + " This should be impossible!");
        continue;   // skip this file
      }
      //prt("p="+path+" f="+filename+" found="+found+" "+ifile+"/"+indices.size());

      MasterBlock[] mb;
      try {
        mb = idx.getMasterBlocks();
      } catch (IOException e) {
        prta(tag + " *** IOError getting master blocks ifr=" + idx.toString() + " e=" + e);
        continue;
      }
      if (dbg) {
        prta(tag + " EBC: " + ifile + " is " + idx + " #mb=" + mb.length);
      }
      julian = idx.getJulian();   // julian for this file
      for (int i = 0; i < mb.length; i++) {        // for each master block in the file
        if (mb[i] != null) {
          synchronized (mb[i]) {
            for (int j = 0; j < MasterBlock.MAX_CHANNELS; j++) { // for each entry in the MB
              if (mb[i].getSeedName(j).substring(0, 2).equals("ZZ")) {
                continue;
              }
              if (mb[i].getSeedName(j).indexOf("ILLEGAL") >= 0) {
                continue;
              }
              if (!showIllegals && !Util.isValidSeedName(mb[i].getSeedName(j))) {
                continue;
              }
              if (Util.stringBuilderEqual(mb[i].getSeedName(j), "            ")) {
                break;
              }
              if (!EdgeQueryServer.allowrestricted) {  // if this server is running in restricted mode, check on flags
                Channel c = EdgeChannelServer.getChannelNoTraceback(mb[i].getSeedName(j));
                exclude = false;
                if (c != null) {
                  if (EdgeChannelServer.isChannelNotPublic(c)) {
                    if (dbg) {
                      prt(tag + " EBC: Query for restricted channel omitted=" + mb[i].getSeedName(j));
                    }
                    nexclude++;
                    exclude = true;
                  }
                }
                // Do not include any excludes
                synchronized (excludes) {
                  for (String exclude1 : excludes) {
                    ncomp++;
                    if (mb[i].getSeedName(j).indexOf(exclude1) == 0) {
                      exclude = true;
                      nexclude++;
                      if (dbg) {
                        prt(tag + " EBC: Query for excluded channel omitted=" + mb[i].getSeedName(j) + " to " + exclude1);
                      }
                      break;
                    }
                  }
                }
              } else {
                exclude = false;
              }

              // If delaz is on, show it
              String delaz = "";
              if (!selectedChannels.isEmpty()) {
                boolean gotit = false;
                for (int ie = 0; ie < selectedChannels.size(); ie++) {
                  if (Util.stringBuilderEqual(mb[i].getSeedName(j), selectedChannels.get(ie).substring(0, 12))) {
                    gotit = true;
                    delaz = selectedChannels.get(i).substring(14, 32);
                    if (dbg) {
                      prt(tag + " EBC: lsc exclude to delaz " + selectedChannels.get(ie).trim());
                    }
                    break;
                  }
                }
                if (!gotit) {
                  exclude = true;
                }
              }

              // The DaysList contains a matches of days for this seedname, see if it is in there
              // and add this day to it either way
              if (!exclude) {
                EdgeQuery.DaysList dl = (EdgeQuery.DaysList) chans.get(Util.toAllPrintable(mb[i].getSeedName(j)).toString());
                if (dl == null) {
                  chans.put(Util.toAllPrintable(mb[i].getSeedName(j)).toString(), // create new seedname/dayslist entry
                          new EdgeQuery.DaysList(Util.toAllPrintable(mb[i].getSeedName(j)).toString(), delaz, 100, julian));
                } else {
                  dl.add(julian);               // add this day to an existing DaysList
                }
              }
            }
          }// synchronize on mb[i]
        }
      }
      //prt("Done with "+ifile);
      if (!found) {
        idx.close();         // If we opened this file just for a directory, close it immediately
      }
    }

    // Now iterate through the matches of seednames/dayslist and print out a line for each
    Iterator itr = chans.values().iterator();
    sbqc.append("There are ").append(chans.size()).append(" channels\n");
    int nstat = 0;
    String laststat = "";
    while (itr.hasNext()) {
      EdgeQuery.DaysList day = (EdgeQuery.DaysList) itr.next();
      sbqc.append(day.toString()).append("\n");
      if (!day.getSeedName().substring(0, 7).equals(laststat)) {
        nstat++;
        laststat = day.getSeedName().substring(0, 7);
      }
    }
    sbqc.append("\nThere are ").append(chans.size()).append(" channels ncomp=").append(ncomp).
            append(" # stations=").append(nstat).append(" today=").
            append(yrday(SeedUtil.toJulian(new GregorianCalendar()))).append("\n");
    if (!quiet) {
      prta(tag + "There are " + chans.size() + " channels. excluded=" + nexclude + " return=" + sbqc.length());
    }
    msQueryChannels += (System.currentTimeMillis() - st);
    return sbqc.toString();
  }

  //** inner class to create matches of days (keeps a matches of days as put in a creating add add()*/
  class DaysList {

    String seedname;
    int[] days;
    int nused;
    String delazString;

    public DaysList(String name, String delaz, int size, int jul) {
      seedname = name;
      days = new int[size];
      delazString = delaz;
      days[nused++] = jul;
    }

    @Override
    public String toString() {
      return seedname + " " + delazString + getRanges();
    }

    public String getSeedName() {
      return seedname;
    }

    public void add(int julian) {
      for (int i = 0; i < nused; i++) {
        if (julian == days[i]) {
          return;
        }
      }
      days[nused] = julian;
      nused++;
      // are we about to make this too big for array - expand it
      if (nused >= days.length) {
        int[] tmp = new int[days.length];
        System.arraycopy(days, 0, tmp, 0, days.length);
        days = new int[days.length + 50];
        System.arraycopy(tmp, 0, days, 0, tmp.length);
      }
    }

    /**
     * format up a line with the ranges of days in the matches
     */
    public String getRanges() {
      Arrays.sort(days, 0, nused);
      int begin = days[0];
      if (sbrange.length() > 0) {
        sbrange.delete(0, sbrange.length());
      }
      sbrange.append("#days=").append((nused + "    ").substring(0, 4)).append(" ");

      for (int i = 1; i < nused; i++) {
        if (begin == -1) {
          begin = days[i];
        } else if (days[i] != days[i - 1] + 1) {
          if (begin == days[i - 1]) {
            sbrange.append(yrday(begin)).append(" ");
          } else {
            sbrange.append(yrday(begin)).append("-").append(yrday(days[i - 1])).append(" ");
          }
          begin = days[i];
        }
      }
      if (begin == days[nused - 1]) {
        sbrange.append(yrday(begin));
      } else {
        sbrange.append(yrday(begin)).append("-").append(yrday(days[nused - 1]));
      }
      return sbrange.toString();
    }
  }

  /**
   * return a matches of seedname strings with match the seedmask, If excludes are set they are
   * excluded from the matches. List is returned in sorted order and without any duplicates.
   *
   * @param seedmask A regexp name which will be used to match against the full matches of available
   * @param start The starting time of the query as a Date
   * @param duration The time of the query in seconds
   * @param matches The matching seednames
   * @return Array of Strings with channel matches3.
   * @throws IOException If one is encountered
   */
  public int queryMatches(String seedmask, Date start, double duration, ArrayList<String> matches) throws IOException {
    Date end = new Date(start.getTime() + ((long) (duration * 1000)));
    return queryMatches(seedmask, start, end, matches);

  }

  /**
   * return a matches of seedname strings with match the seedmask. If excludes are set they are
   * excluded from the matches. List is returned in sorted order and without any duplicates.
   *
   * @param seedmask A regexp name which will be used to match against the full matches of available
   * @param start The starting time of the query as a Date
   * @param end The end time of the query as a Date
   * @param matches Return the list of channels here
   * @return An array of string with the channel matches3
   * @throws IOException If one is encountered
   */
  public int queryMatches(String seedmask, Date start, Date end, ArrayList<String> matches) throws IOException {
    long st = System.currentTimeMillis();
    if (dbg) {
      prta(tag + " start queryMatches() on " + seedmask + " days=" + SeedUtil.toJulian(start) + " to " + SeedUtil.toJulian(end));
    }

    int ncomp = 0;
    int nexclude = 0;
    int day = 0;
    Pattern p = Pattern.compile(seedmask);
    //if(dbg) prta(tag+" seedmask="+seedmask+" matcher="+p);
    //for (int day = SeedUtil.toJulian(start); day <= SeedUtil.toJulian(end); day++) {
    synchronized (indices) {
      Iterator itr = indices.iterator();
      boolean exclude;

      // Look at each file in indices (which was built based on the begin date and end time)
      while (itr.hasNext()) {
        IndexFileReplicator indexFile = (IndexFileReplicator) itr.next();
        boolean match = false;
        if (dbg) {
          prt(tag + " Check file=" + indexFile.getFilename() + " jul=" + indexFile.getJulian()
                  + " day=" + day + " " + Util.ascdatetime2(start.getTime()) + " to " + Util.ascdatetime2(start.getTime()));
        }
        //if(day == indexFile.getJulian()) {
        if (indexFile.isClosing()) {
          prt(tag + " *** Found file closed Exception before query complete.  Skip...=" + indexFile);
          continue;
        }
        MasterBlock[] mb = indexFile.getMasterBlocks();
        for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {  // for each MB
          if (mb[i] != null) {
            synchronized (mb[i]) {
              for (int j = 0; j < MasterBlock.MAX_CHANNELS; j++) { // for each entry in MB
                //if(dbg) prt("mask="+seedmask+" to "+mb[i].getSeedNameString(j)+"|");

                // if it matches3 the name, check it for exclusion on the matches
                //if(dbg) prt(tag+" "+i+"/"+j+" "+seedmask+"="+mb[i].getSeedName(j)+" match="+p.matcher(mb[i].getSeedName(j)).matches());
                if (mb[i].getSeedName(j).length() >= 10) {
                  try {
                    if (p.matcher(mb[i].getSeedName(j)).matches()) {
                      exclude = false;
                      if (!EdgeQueryServer.allowrestricted) {
                        Channel c = EdgeChannelServer.getChannelNoTraceback(mb[i].getSeedName(j));
                        if (c != null) {
                          if (EdgeChannelServer.isChannelNotPublic(c)) {

                            if (!quiet) {
                              prt(tag + "EBC: ** Query for restricted channel rejected=" + mb[i].getSeedName(j));
                            }
                            exclude = true;
                          }
                        }
                        synchronized (excludes) {
                          for (String exclude1 : excludes) {
                            // look for excluded channels
                            ncomp++;
                            if (mb[i].getSeedName(j).indexOf(exclude1) == 0) {
                              nexclude++;
                              exclude = true;
                              if (!quiet) {
                                prt(tag + "Query for excluded channel rejected=" + mb[i].getSeedName(j) + " to " + exclude1);
                              }
                              break;
                            }
                          }
                        }
                      }

                      // If a delaz ring or donut is in effect, only include channels in the the delaz
                      if (selectedChannels != null && !selectedChannels.isEmpty()) {
                        boolean found = false;
                        for (String selectedChannel : selectedChannels) {
                          if (Util.stringBuilderEqual(mb[i].getSeedName(j), selectedChannel.substring(0, 12))) {
                            found = true;
                            break;
                          }
                        }
                        if (!found) {
                          exclude = true;    // exclude this one as its not on the delazc matches
                        }
                      }

                      // If it is not excluded now, include it in the returned matches
                      if (!exclude) {
                        matches.add(mb[i].getSeedName(j).toString());     // it matches3 and is not excluded - add it
                        if (dbg) {
                          prt(tag + "  match! " + mb[i].getSeedName(j) + " mask=" + seedmask);
                        }
                        match = true;
                      }
                    } else if (mb[i].getSeedName(j).length() == 0) {
                      break;
                    }
                  } catch (StringIndexOutOfBoundsException | BufferUnderflowException | BufferOverflowException e) {
                    prta(tag + " *** String index out of range seedname=" + mb[i].getSeedName(j) + "|"
                            + seedmask + " " + p.toString() + " i=" + i + " j=" + j + " " + indexFile.getFilename());
                    try {
                      prta(tag + " *** matchers=" + p.matcher(mb[i].getSeedName(j)).matches());
                    } catch (StringIndexOutOfBoundsException | BufferUnderflowException | BufferOverflowException e2) {
                      prta(tag + "** match failed again! e2=" + e2);
                    }
                    e.printStackTrace(par.getPrintStream());
                  }
                }   // Is the length at least 10
              }
            }// synchronized on mb[i]
          } else {
            if (dbg) {
              prt(tag + "    mblock is null at " + i);
            }
            break;
          }
        }       // end of for each master block
        //}         // endi if this matches3 a day
        if (dbg && !match) {
          prt(tag + " Remove file=" + indexFile + " match=" + match);
        }
        if (!match) {
          itr.remove();    // This file has no matches3
        }
      }           // end of while more in iterator
    }           // end for each file available
    //}             // end of for each day in matches

    // get the full matches (may have duplicates), sort it and elimate dups
    if (dbg) {
      prta(tag + " Match exclude compares=" + ncomp + " exclude matches=" + nexclude 
              + " #files=" + indices.size() + " list=" + matches.size());
    }
    Collections.sort(matches);
    int i = 0;
    while (i < matches.size() - 1) {// Eliminate any duplications
      if (matches.get(i).equals(matches.get(i + 1))) {
        matches.remove(i + 1);
      } else {
        i++;
      }
    }
    //String [] ret = new String[matches.size()];
    //for(int i=0; i<matches.size(); i++) ret[i]= (String) matches.get(i);
    msQueryMatches += System.currentTimeMillis() - st;
    return matches.size();
  }

  /**
   * Query the edge files for all the records for a certain SEED name over a time period.
   *
   * The algorithm works like this: For every day in the date range, open the corresponding
   * IndexFile(s). Get a matches of all the IndexBlocks for {@code
   * seedName}. Get a matches of extents for each IndexBlock. For every extent that overlaps the
   * date range, get a bitmap of data records. Finally, add every data record that overlaps the date
   * range to the result matches.
   *
   * @param seedname the SEED name
   * @param start the beginning of the time period
   * @param duration The length to return in seconds.
   * @param out The output stream on which to output the data
   * @param nice If true, slow things down a bit
   * @param deleteBlock if true, delete this block
   * @throws IOException If one is thrown
   * @throws IllegalSeednameException if one is thrown
   */
  public void query(String seedname, Date start, double duration, OutputStream out, boolean nice, boolean deleteBlock)
          throws IOException, IllegalSeednameException {
    Date end = new Date(start.getTime() + ((long) (duration * 1000 + 0.5)));
    query(seedname, start, end, out, nice, deleteBlock);
  }

  private void doIndicesUpdate(List indices) {
    Iterator itr = indices.iterator();
    while (itr.hasNext()) {
      ((IndexFileReplicator) itr.next()).updateLastUsed();
    }
  }
  /**
   * Query the edge files for all the records for a certain SEED name over a time period.
   *
   * The algorithm works like this: For every day in the date range, open the corresponding
   * IndexFile. Get a matches of all the IndexBlocks for {@code
   * seedName}. Get a matches of extents for each IndexBlock. For every extent that overlaps the
   * date range, get a bitmap of data records. Finally, add every data record that overlaps the date
   * range to the result matches.
   *
   * @param seedName the SEED name
   * @param start the beginning of the time period
   * @param end the end of the time period
   * @param outmethod This can either be a OutputStream to write blocks to or an ArrayList of
   * MiniSeed to from the miniSeedPool
   * @param nice If true, slow down long transfers
   * @param deleteBlock If true, delete the blocks
   * @throws IOException
   * @throws IllegalSeednameException
   */
  //MiniSeed ms;
  private final StringBuilder seednameSB = new StringBuilder(12);
  private final StringBuilder seednameTempSB = new StringBuilder(12);

  public void query(String seedName, Date start, Date end, Object outmethod, boolean nice, boolean deleteBlock)
          throws IOException, IllegalSeednameException {
    IndexFileReplicator indexFile;
    IndexBlockQuery indexBlock;
    OutputStream out = null;
    ArrayList<MiniSeed> result = null;
    long st = System.currentTimeMillis();
    MiniSeed ms;
    //Date earliest, latest;
    //Date msStart, msEnd;
    long earliest, latest, msStart, msEnd;
    int blockAddress;
    int numExtents, i, j;
    int day;
    int[] extents;
    int nblks = 0;
    boolean opened;
    if (outmethod instanceof OutputStream) {
      out = (OutputStream) outmethod;
    } else if (outmethod instanceof ArrayList) {
      result = (ArrayList<MiniSeed>) outmethod;
    } else {
      prt("Unknown output object time in query " + outmethod);
      return;
    }
    int julstart = SeedUtil.toJulian(start);
    int julend = SeedUtil.toJulian(end);
    long duration = end.getTime() - start.getTime();
    long maxTime = 0;
    if ((julend - julstart) > 365 || julend < julstart) {
      prta(tag + " EQ: ***** Query from julian day " + julstart + "-" + julend + " Is absurd.  Reject this query.");
      return;
    }
    Util.clear(seednameSB).append(seedName);
    Util.rightPad(seednameSB, 12);
    //ArrayList result;
    //result = new ArrayList();
    if (dbg) {
      prta(tag + " EQ: Start query on " + seedName + " in " + indices.size() + " files.");
    }
    long lastIndicesUpdate = 0;
    doIndicesUpdate(indices);
    // for each day of query
    for (day = SeedUtil.toJulian(start); day <= SeedUtil.toJulian(end); day++) {
      opened = false;
      synchronized (indices) {
        Iterator itr = indices.iterator();
        // for each file in indices, see if its the right day
        while (itr.hasNext()) {
          if (System.currentTimeMillis() - lastIndicesUpdate > 20000) {
            doIndicesUpdate(indices);
            lastIndicesUpdate = System.currentTimeMillis();
          }
          indexFile = (IndexFileReplicator) itr.next();
          if (dbg) {
            prta("EQ:chk file=" + indexFile.toString() + " for day=" + day);
          }

          // if this file is for the current day index
          if (day == indexFile.getJulian()) {
            MasterBlock[] mb;
            try {
              mb = indexFile.getMasterBlocks();  // read in the master blocks
            } catch (IOException e) {
              prta(" EQ: **** did not get master blocks for " + indexFile.getFilename() + "skip file.  e=" + e);
              continue;
            }
            indexFile.updateLastUsed();

            /* Try the next file if this one doesn't have any records for
               seedName. */
            // See if this seedname is in the master block, keep scanning until its found.
            int ok = -1;
            for (int ii = 0; ii < IndexFile.MAX_MASTER_BLOCKS; ii++) {
              if (mb[ii] == null) {
                break;      // out of master blocks
              }
              ok = mb[ii].getFirstIndexBlock(seednameSB);// this will also write out the masterblock
              if (ok != -1) {
                break;
              }
            }
            blockAddress = ok;

            if (blockAddress == -1) {          // Seedname was not in the file, move on
              if (dbg) {
                prta(tag + "    EQ:seed=" + seedName + " is not in file " + indexFile.getFilename());
              }
              continue;
            }
            if (dbg) {
              prta(tag + "    EQ: For " + seedName + " process " + indexFile.getFilename() + " " + indexFile.getJulian());
            }
            // We have fouind the seedname in the master blocks, get the first index and scan for
            // indices which are in the time range.
            try {
              // Look for signs that this indexFile is closed or corrupted.
              if (indexFile.getRawDisk() == null || indexFile.getDataRawDisk() == null) {
                Util.prta(tag + "***  idx=" + indexFile + " " + indexFile.getRawDisk() + " or " + indexFile.getDataRawDisk() + " is null.  skip");
                continue;
              }
              if (indexFile.isClosing()) {
                Util.prta(tag + "*** idx=" + indexFile + " is closing.   skip");
              }
              indexBlock = new IndexBlockQuery(indexFile, seedName, blockAddress);// get 1st index block
              do {
                int indexBlockNumber = indexBlock.getIndexBlockNumber();
                numExtents = indexBlock.getNumExtents();
                //extents = indexBlock.getExtents();
                for (i = 0; i < numExtents; i++) {
                  earliest = makeDate(indexBlock.getJulian(), indexBlock.getEarliestTime(i));
                  latest = makeDate(indexBlock.getJulian(), indexBlock.getLatestTime(i) + 1); // add one because end was truncated down

                  /* We assume that the earliest and latest dates are correct; i.e.,
                     that there are not records that fall outside the range. The last extent is undated so always check it */
                  //if ( (latest.compareTo(start) >=0 && earliest.compareTo(end) <= 0) || // if this is inside the time window
                  if ((latest >= start.getTime() && earliest <= end.getTime())
                          || // if this is inside the time window
                          (i == numExtents - 1 && indexBlock.getNextIndex() <= 0)) {         // If its the last extent, always check it
                    //if(dbg) prt("EQ:Should find some "+latest+" end="+earliest);
                    extents = indexBlock.getExtents();
                    indexFile.getDataRawDisk().readBlock(bigbuf, extents[i], 64 * BLOCK_SIZE);

                    // look at the 64 possible data blocks in this extent adjusting for block size
                    for (j = 0; j < 64; j++) {
                      System.arraycopy(bigbuf, j * BLOCK_SIZE, seedBuf, 0, BLOCK_SIZE);
                      if (seedBuf[0] == 0 && seedBuf[1] == 0) {
                        continue;
                      }
                      //seedBuf = indexFile.getDataRawDisk().readBlock(extents[i] + j, BLOCK_SIZE);
                      try {
                        //if(ms == null) ms = new MiniSeed(seedBuf); // DEBUG: This might trip a SeedNameException
                        //else ms.load(seedBuf);                     // DEBUG:
                        if (MiniSeed.crackJulian(seedBuf) != day) {
                          prt(tag + " EQ: ****Data on wrong day=" + MiniSeed.crackJulian(seedBuf) + " not " + day);
                          j += MiniSeed.crackBlockSize(seedBuf) / BLOCK_SIZE - 1;// adjust for blocksizes >512
                          continue;   // reject its not the right day (should never happen!)
                        }
                        MiniSeed.crackSeedname(seedBuf, seednameTempSB);
                        if (!Util.stringBuilderEqual(seednameTempSB, seedName)) {
                          prt(tag + " EQ: **** data from another seedname found=" + seedName + "/" + seedName.length()
                                  + " found=" + seednameTempSB + "/" + seednameTempSB.length() + " " + indexFile + ":" 
                                  + extents[i] + ":" + j + " " + seednameSB + "|");
                          /*prt(tag+" EQ: **** data from another seedname="+Util.toAllPrintable(seedName)+" tmpsb="+Util.toAllPrintable(seednameTempSB)+" "+
                              (char)seedBuf[18]+(char)seedBuf[19]+
                              (char)seedBuf[8]+(char)seedBuf[9]+(char)seedBuf[10]+(char)seedBuf[11]+(char)seedBuf[12]+
                              (char)seedBuf[15]+(char)seedBuf[16]+(char)seedBuf[17]+
                              (char)seedBuf[13]+(char)seedBuf[14]
                              );*/

                          if (j == 63) {
                            new RuntimeException("data from another seedname " + seedName + " fnd=" + MiniSeed.crackSeedname(seedBuf)
                                    + " " + indexFile + ":" + extents[i] + ":" + j).printStackTrace(par.getPrintStream());
                          }
                          continue;
                        }

                        // If the blocksize is not BLOCK_SIZE(512), make the ms with more blocks
                        int msblockSize = MiniSeed.crackBlockSize(seedBuf);
                        if (msblockSize <= 0) {
                          msblockSize = 512;
                        }
                        // i is the extent index, j is the block index, 
                        if (deleteBlock && out != null) {
                          deleteBlock(out, indexFile, indexBlock, seedBuf, msblockSize, extents[i], indexBlockNumber, i, j,
                                  indexBlock.getEarliestTime(i), indexBlock.getLatestTime(i), start.getTime(), end.getTime());
                        }
                        if (msblockSize > BLOCK_SIZE) {
                          // Is all of the needed data in this 64 block extent, if yes copy to big buf
                          if (msblockSize / BLOCK_SIZE <= 64 - j) {
                            System.arraycopy(bigbuf, j * BLOCK_SIZE, seedBuf, 0, msblockSize);
                            j = j + msblockSize / BLOCK_SIZE - 1;  // update j to next spot
                          } else {

                            // more all of big buf that is in this extent to seed buf
                            if (dbg) {
                              prt(tag + "    EQ: *** need more data from another extent j=" + j + " blksiz=" + msblockSize);
                            }
                            System.arraycopy(bigbuf, j * BLOCK_SIZE, seedBuf, 0, (64 - j) * BLOCK_SIZE);
                            if (i < IndexBlock.MAX_EXTENTS - 1) {    // Is the next extent known to us?
                              if (!quiet) {
                                prt(tag + "     EQ: big buf is in another extent=" + extents[i + 1] + " j=" + j + " blksiz=" + msblockSize);
                              }
                              // read next extent and put rest needed to fill buffer into seedBuf
                              if (extents[i + 1] < 0) {
                                continue;
                              }
                              indexFile.getDataRawDisk().readBlock(bigbuf, extents[i + 1], 64 * BLOCK_SIZE);
                              i = i + 1;            // We are now in the i+1 extent of this index block
                            } else {    // next extent is in another index block, figure out which one!
                              if (!quiet) {
                                prt(tag + "     EQ: big buf is in another extent and index block! nextIndex="
                                        + indexBlock.getNextIndex() + " j=" + j + " blksiz=" + msblockSize);
                              }
                              //IndexBlockQuery tmp = new IndexBlockQuery(indexFile, seedName, 
                              //    indexBlock.getNextIndex());

                              // Get the next index block, since we are messing with a loop variable
                              // reset the i=0 and update the loop end condition variable, repopulate extents
                              if (!indexBlock.nextIndexBlock()) {
                                // There are no more index blocks- wee need to terminate
                                i = numExtents;   // force exit from i loop
                                break;      // break from the j loop
                              }
                              numExtents = indexBlock.getNumExtents();
                              extents = indexBlock.getExtents();
                              if (!quiet) {
                                prt(tag + "     EQ: next extent from next index =" + extents[0]);
                              }
                              indexFile.getDataRawDisk().readBlock(bigbuf, extents[0], 64 * BLOCK_SIZE);
                              i = 0;     // We are now in extent 0 of the next index block

                              //indexBlock = tmp; 
                            }
                            // we have modified i, we need to read the earliest and latest
                            //earliest = makeDate(indexBlock.getJulian(), indexBlock.getEarliestTime(i));
                            //latest = makeDate(indexBlock.getJulian(), indexBlock.getLatestTime(i));
                            // put the next extents data into the right place
                            System.arraycopy(bigbuf, 0, seedBuf, (64 - j) * BLOCK_SIZE, msblockSize - (64 - j) * BLOCK_SIZE);
                            j = (msblockSize - (64 - j) * BLOCK_SIZE) / BLOCK_SIZE - 1;// Skip over the blocks used in this extent
                          }
                          //if(ms == null) ms = new MiniSeed(seedBuf);
                          //else ms.load(seedBuf);
                          //prt("msbig="+ms);
                        } // if(msblocksize != BLOCK_SIZE) i.e. the block is not 512 bytes long
                        //if(dbg) 
                        //  prt("EQ:"+ms.getSeedNameString()+" "+ms.getTimeString()+
                        //    " ns="+ms.getNsamp()+" rt="+ms.getRate()+" iblk="+indexBlock.toString()+" ext="+extents[i]+" j="+j);

                        //msStart = ms.getGregorianCalendar().getTime();
                        msStart = makeStartFromSeed(seedBuf);
                        double rate = MiniSeed.crackRate(seedBuf);
                        if (rate == 0.) {
                          rate = 101.;
                        }
                        msEnd = msStart + (long) (MiniSeed.crackNsamp(seedBuf) / rate * 1000);
                        //  prt("i="+i+" j="+j+" ms="+ms.toString());

                        //if (msEnd.after(start) && msStart.before(end)) {
                        //prt(extents[i]+" "+j+" ms="+ms);
                        if (msEnd > start.getTime() && msStart < end.getTime()) {
                          //  prt("Out:"+ms.getSeedNameString()+" "+ms.getTimeString()+
                          //            " ns="+ms.getNsamp()+" rt="+ms.getRate());
                          if (seedBuf[7] == '~') {
                            prt("Deleted block at i=" + i + " j=" + j + " extent=" + extents[i]);
                            if (!allowDeleted) {
                              continue;
                            }
                          }
                          nblks++;
                          if (nice && nblks % 400 == 0) {
                            Util.sleep(20);  // Limit to 20000 blocks per second
                          }
                          if (out != null && !deleteBlock) {
                            out.write(seedBuf, 0, (gapsonly ? 64 : msblockSize));
                          }
                          // If this is a miniSEED request 

                          // Protect against massively duplicated data if request seems long and storing the data in result
                          if (result != null && !deleteBlock) {
                            boolean isDuplicate = false;
                            ms = msp.get(seedBuf, 0, msblockSize);  // create the block of MiniSeedPool 
                            int limit = (int) (duration / 1000. * rate / 100.);   // expected nsamples divided by a worst case 100 samples per block
                            if (result.size() > limit) {
                              if (ms != null) {
                                if (ms.getTimeInMillis() <= maxTime) {    // No need to check duplicates if this is newest seen
                                  if (!gapsonly) {
                                    for (int ii = result.size() - 1; ii >= Math.max(result.size() - 2 * limit, 0); ii--) {
                                      if (ms.isDuplicate(result.get(ii))) {
                                        isDuplicate = true;
                                        break;
                                      }
                                    }
                                  }
                                  if (!isDuplicate) {
                                    result.add(ms);
                                  } else {
                                    lastNDups++;
                                    msp.free(ms);
                                  }
                                } else {
                                  maxTime = ms.getTimeInMillis();
                                  result.add(ms); // If its not D or better, its been zapped!
                                }
                              }
                            } // Add the block since its not a duplicate
                            else if (ms != null) {
                              if (ms.getTimeInMillis() > maxTime) {
                                maxTime = ms.getTimeInMillis();
                              }
                              result.add(ms);
                            }
                          }
                        }
                      } catch (IllegalSeednameException expected) {/*prta("illegal Seedname query ignored j="
                          +j+" ext="+extents[i]+" nextext="+extents[i+1]);*/
                      }  // probably not yet written
                    }   // loop on j=0 ->64
                  }     // loop on i=0->numExtents
                }
              } while (indexBlock.nextIndexBlock());
              if (dbg) {
                prta(tag + "      EQ: " + nblks + " blks total");
              }
            } catch (EOFException e) {
              prta(tag + "EOF trying to get " + seedName + " from " + indexFile + " blk=" + blockAddress + " e=" + e);
              e.printStackTrace(par.getPrintStream());
              e.printStackTrace();
            } catch (IOException e) {
              prta(tag + " IOErr in query() "+seedName+" "+start+" "+duration+" e=" + e);
              Util.SocketIOErrorPrint(e, tag + " in query()");
              e.printStackTrace(par.getPrintStream());
              throw e;
            }
          } //else if(dbg) prt("   EQ:  file  is not right julian"); // end if this indexFile has the right julian day
        }   // End of while(itr.hasNext()
      }   // end synchronize on indices
    }     // end of for each day in the request
    //result.trimToSize();
    //Collections.sort(result);
    //prta(tag+"   "+nblks+" blks for "+seedName+" nice="+nice);
    lastNblocks = nblks;
    //return result;
    msQuery += System.currentTimeMillis() - st;
  }
  DeleteBlockInfo deleted;
  private MiniSeed msdel;
  private byte[] frames;
  private final GregorianCalendar sss = new GregorianCalendar();

  /**
   *
   * @param out OutputStream to send deleted block summary for this block
   * @param indexFile The indexFileReplicator for this file, this is used to delete the block
   * @param indexBlock The IndexBlockQuery opened right now, used for seedname
   * @param seedBuf The data currently in this seed block, so it can be written back with delete
   * indicator
   * @param msblockSize The size of the miniSeed block, ones larger than 512 can be deleted but not
   * reused
   * @param extentValue The actual value of the extent represented by extentIndex
   * @param indexBlockNumber The index block number in the index file
   * @param extentIndex The index into the extents array in this index block of the one being
   * deleted
   * @param iblk The block offset in the 64 block buffer represented by the extent
   * @param earliest The earliest time, so the user might use it to find a good matching block
   * @param latest The latest time for the same
   * @param start The long epoch time of the first time to delete,
   * @param end The long epoch time of the last sample to delete.
   * @throws IOException If the block cannot be written, or the socket to the caller is lost most
   * likely
   */

  private void deleteBlock(OutputStream out, IndexFileReplicator indexFile, IndexBlockQuery indexBlock, byte[] seedBuf,
          int msblockSize, int extentValue, int indexBlockNumber,
          int extentIndex, int iblk, int earliest, int latest, long start, long end) throws IOException {
    if (seedBuf[7] == '~') {
      prt("Redelete of block " + indexBlockNumber + "/" + extentIndex + "/" + iblk + " blk=" + extentValue + iblk);
    }
    try {
      if (msdel == null) {
        msdel = new MiniSeed(seedBuf);
        frames = new byte[512];
      } else {
        msdel.load(seedBuf);
      }
    } catch (IllegalSeednameException e) {

    }
    prta(tag + " start=" + Util.ascdatetime2(start, null) + " to "
            + Util.ascdatetime2(end, null) + " " + msdel.toStringBuilder(null));
    // check to make sure block is in the delete window
    if (msdel.getTimeInMillis() >= end || msdel.getNextExpectedTimeInMillis() <= start) {
      return;
    }

    // Is the entire delete interval inside of this one block!
    if (start >= msdel.getTimeInMillis() && end < msdel.getNextExpectedTimeInMillis()) {
      prta(tag + " Attempt to delete from " + Util.ascdatetime2(start, null) + " to "
              + Util.ascdatetime2(end, null) + " failed - its inside of one block!" + msdel.toStringBuilder(null));
      SendEvent.edgeSMEEvent("DelLess1Blk", "Attempt to delete from " + Util.ascdatetime2(start, null) + " to "
              + Util.ascdatetime2(end, null) + " failed - its inside of one block!", this);
      throw new IOException("Attempt to delete is within a single block - do nothing! " + msdel.getSeedNameSB() + " "
              + Util.ascdatetime2(start, null) + " " + Util.ascdatetime2(end, null));
    }

    // Does this block overlap the beginning
    if (msdel.getTimeInMillis() < start && msdel.getNextExpectedTimeInMillis() > start) {
      int[] data = null;
      MiniSeed ms = msdel;
      prta(tag + " shorten block at beginning  start=" + Util.ascdatetime2(start, null) + " to "
              + Util.ascdatetime2(end, null) + " " + msdel.toStringBuilder(null));
      if (ms.getBlockSize() - ms.getDataOffset() > frames.length) {
        SendEvent.debugEvent("BufOverTrimEQ", "Buffer overrun " + Util.getNode() + "/" + Util.getAccount() + " Buf Overrun", this);
      }
      System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, Math.min(frames.length, ms.getBlockSize() - ms.getDataOffset()));
      try {
        if (msdel.getEncoding() == 11) {
          data = Steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes());
        } else if (msdel.getEncoding() == 10) {
          data = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes());
        } else {
          prt("*** bad delete . Unknown encoding in block " + msdel);
        }
        int nsamp = (int) (((start - msdel.getTimeInMillis()) * ms.getRate()) / 1000.);
        sss.setTimeInMillis(msdel.getTimeInMillisTruncated());    // start time of ms buffer
        RawToMiniSeed rtms = new RawToMiniSeed(ms.getSeedNameSB(), msdel.getRate(), 7,
                sss.get(Calendar.YEAR), sss.get(Calendar.DAY_OF_YEAR),
                sss.get(Calendar.HOUR_OF_DAY) * 3600 + sss.get(Calendar.MINUTE) * 60 + sss.get(Calendar.SECOND),
                msdel.getUseconds(),
                1, null);
        rtms.setOutputHandler(this);        // This registers our putbuf
        rtms.process(data, nsamp,
                sss.get(Calendar.YEAR), sss.get(Calendar.DAY_OF_YEAR),
                sss.get(Calendar.HOUR_OF_DAY) * 3600 + sss.get(Calendar.MINUTE) * 60 + sss.get(Calendar.SECOND), // seconds
                msdel.getUseconds(), // uSec
                ms.getActivityFlags(), ms.getIOClockFlags(), ms.getDataQualityFlags(),
                ms.getTimingQuality(), 0);  // usecs
        rtms.forceOut();                  // This forces call to our putbuf
        // Putbuf put the new data into msdel, so now write it back out
        indexFile.getDataRawDisk().writeBlock(extentValue + iblk, msdel.getBuf(), 0, 512);
        return;
      } catch (SteimException e) {
        prt("Delete: bad steim decompression on block");
      }
    }
    // Does this block overlap the end
    if (msdel.getTimeInMillis() < end && msdel.getNextExpectedTimeInMillis() > end) {
      int[] data = null;
      MiniSeed ms = msdel;
      prta(tag + " shorten block at end " + " start=" + Util.ascdatetime2(start, null) + " to "
              + Util.ascdatetime2(end, null) + " " + msdel.toStringBuilder(null));
      if (ms.getBlockSize() - ms.getDataOffset() > frames.length) {
        SendEvent.debugEvent("BufOverTrimEQ", "Buffer overrun " + Util.getNode() + "/" + Util.getAccount() + " Buf Overrun", this);
      }
      System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, Math.min(frames.length, ms.getBlockSize() - ms.getDataOffset()));
      try {
        if (msdel.getEncoding() == 11) {
          data = Steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes());
        } else if (msdel.getEncoding() == 10) {
          data = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes());
        } else {
          prt("Delete : unknown encoding");
        }
        int offset = (int) ((end - ms.getTimeInMillis()) * msdel.getRate() / 1000. + 0.5);
        int newnsamp = ms.getNsamp() - offset;
        if (newnsamp > 0) {
          GregorianCalendar stmp = new GregorianCalendar();
          stmp.setTimeInMillis(ms.getTimeInMillis());
          if (dbg) {
            prt(" Overlapping offset=" + offset + " ns=" + ms.getNsamp() + " new ns=" + newnsamp
                    + " ms=" + ms);
          }
          try {
            System.arraycopy(data, offset, data, 0, newnsamp);
          } catch (ArrayIndexOutOfBoundsException e) {
            prt("OOB error offset=" + offset + " data.length=" + (data == null ? "null" : data.length) + " newnsamp=" + newnsamp);
          }
          stmp.setTimeInMillis(ms.getTimeInMillisTruncated());    // start time of ms buffer
          stmp.add(Calendar.MILLISECOND, (int) (offset / msdel.getRate() * 1000. + 0.5));// add offset to first sample

          RawToMiniSeed rtms = new RawToMiniSeed(ms.getSeedNameSB(), msdel.getRate(), 7,
                  stmp.get(Calendar.YEAR), stmp.get(Calendar.DAY_OF_YEAR),
                  stmp.get(Calendar.HOUR_OF_DAY) * 3600 + stmp.get(Calendar.MINUTE) * 60 + stmp.get(Calendar.SECOND), // seconds
                  stmp.get(Calendar.MILLISECOND) * 1000 + 
                  ms.getUseconds() % 1000, // uSec
                  1, null);
          rtms.setOutputHandler(this);        // This registers our putbuf
          if (dbg) {
            prt("new buffer computed output time=" + Util.ascdatetime(stmp));
          }

          rtms.process(data, newnsamp,
                  stmp.get(Calendar.YEAR), stmp.get(Calendar.DAY_OF_YEAR),
                  stmp.get(Calendar.HOUR_OF_DAY) * 3600 + stmp.get(Calendar.MINUTE) * 60 + stmp.get(Calendar.SECOND),
                  stmp.get(Calendar.MILLISECOND) * 1000 + ms.getUseconds() % 1000,
                  ms.getActivityFlags(), ms.getIOClockFlags(), ms.getDataQualityFlags(),
                  ms.getTimingQuality(), 0);  // usecs
          rtms.forceOut();                  // This forces call to our putbuf
          // putbuf() has msdel with the revised data, write it out
          indexFile.getDataRawDisk().writeBlock(extentValue + iblk, msdel.getBuf(), 0, 512);
          return;     // its not really a deleted block
        }
      } catch (SteimException e) {
        prt("Steim error - how can this happen!");
      }
    }

    prta(tag + " Make whole block deleted start=" + Util.ascdatetime2(start, null) + " to "
            + Util.ascdatetime2(end, null) + " " + msdel.toStringBuilder(null));
    seedBuf[7] = '~';
    try {
      indexFile.getDataRawDisk().writeBlock(extentValue + iblk, seedBuf, 0, 512);
    } catch (IOException e) {
      prt("IO error trying to delete a block - how does this happen e=" + e + " idx=" + indexFile);

      throw e;
    }
    try {
      if (msblockSize == 512) {     // If its a big block we just mark it deleted and go on, we cannot reuse it.
        if (deleted == null) {
          deleted = new DeleteBlockInfo(indexBlock.getSeedName(), indexFile.getInstance(),
                  indexFile.getJulian(), indexBlockNumber, extentIndex, iblk, (short) earliest, (short) latest);
        } else {
          deleted.reload(indexBlock.getSeedName(), indexFile.getInstance(),
                  indexFile.getJulian(), indexBlockNumber, extentIndex, iblk, (short) earliest, (short) latest);
        }
        prt("Deleted " + deleted);
        //byte [] tmp = deleted.getBytes();
        //for(int i=0; i<28; i++) prt(i+"="+tmp[i]);
        out.write(deleted.getBytes());
      }
    } catch (IOException e) {
      prt("IO error trying to write deleted block back to user");
    }

  }

  @Override
  public void putbuf(byte[] buf, int len) {
    try {
      msdel.load(buf, 0, len);
    } catch (IllegalSeednameException e) {
      prt("illegal seedname trying to fix bytes in EdgeQuery2 delete process !" + e);
    }
  }

  private synchronized static long makeStartFromSeed(byte[] seedBuf) throws IllegalSeednameException {
    int julian = SeedUtil.toJulian(MiniSeed.crackYear(seedBuf), MiniSeed.crackDOY(seedBuf));
    int[] ymd = SeedUtil.fromJulian(julian);
    cal.clear();
    cal.set(ymd[0], ymd[1] - 1, ymd[2]);
    cal.setTimeInMillis(cal.getTimeInMillis() / 86400000L * 86400000L);
    int[] time = MiniSeed.crackTime(seedBuf);
    return cal.getTimeInMillis() + time[0] * 3600000 + time[1] * 60000 + time[2] * 1000 + time[3] / 10;
  }
  /**
   * Make a {@code Date} object from a Julian day and a number of threes of seconds since the start
   * of the day.
   *
   * @param day the Julian day
   * @param sec the number of threes of seconds after the start of the day
   */
  static Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+0000"));

  private synchronized static long makeDate(int day, int threes) {
    //Calendar cal;
    int[] ymd;

    ymd = SeedUtil.fromJulian(day);
    //cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+0000"));
    cal.clear();
    cal.set(ymd[0], ymd[1] - 1, ymd[2]);
    cal.setTimeInMillis(cal.getTimeInMillis() / 86400000L * 86400000L);
    if (threes >= 32700) {
      return cal.getTimeInMillis();       // Its an unupdated earliest, use midnight
    }
    if (threes <= -32700) {
      cal.add(Calendar.SECOND, 86400);// its an unupdated latest use tommorrow
    } else {
      cal.add(Calendar.SECOND, threes * 3);
    }

    return cal.getTimeInMillis();
  }

  /**
   * set debug state
   *
   * @param t the debug state to set
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * close up any files in use and deallocate any object so they can be garbage collected.
   */
  @Override
  public void close() {
    indices.clear();
    if (selectedChannels != null) {
      selectedChannels.clear();
    }
  }

  public static void main(String[] argv) {
    String a = "USKSU1 BH1  ";
    if (a.matches("USKSU1 BH[N1]*")) {
      System.out.println("Matches");
    } else {
      System.out.println("Does not match");
    }
  }
}
