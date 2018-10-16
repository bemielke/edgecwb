/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.Date;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This does a query using the usual query methods, but does not use the TCP/IP interface to get the
 * data. Hence, it must be used by a thread of the QueryMom process that is running a QueryServer
 * thread. This is used by the RAM based QuerySpanCollection and MiniSeedCollection and its related
 * classes to get data from the disk storage. It might be useful to other tasks needing to do
 * queries and are part of the QueryMom process.
 * <p>
 * This saves some overhead where we really just want to get raw blocks back. It uses a MiniSeedPool
 * in the EdgeQuery class to pool the returned blocks so the user has to be conscious of the need to
 * free the blocks when done with them via the freeBlocks() method.
 * <p>
 * It has additional routines to sort and eliminate duplicate blocks, and to free the blocks in the
 * MiniSeedPool.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 * @created December 6, 2013
 */
public class EdgeQueryInternal {

  private final EdgeQuery eq;
  private int nquery, totblks;
  private boolean nice;
  private final boolean nonice;
  private boolean dbg, quiet = true, eqdbg;
  //private boolean terminate;
  private int state;
  private final String tag;
  private Date beg = new Date();
  private Date end = new Date();
  private final EdgeThread par;
  private int lastNDups;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final ArrayList<String> matches = new ArrayList<>(100);

  public void setDebug(boolean t) {
    dbg = t;
  }

  public void setQueryDebug(boolean t) {
    eqdbg = t;
  }

  public int getLastNDups() {
    return lastNDups;
  }

  // Print helpers
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

  @Override
  public String toString() {
    return "#query=" + nquery + " #blks=" + totblks + " state=" + state;
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append("#query=").append(nquery).append(" #blks=").append(totblks).append(" state=").append(state);
    return sb;
  }

  /**
   * Create a EdgeQueryInternal to access the stored data from a CWB from within the QueryMom
   * environment. Certain overall parameters are needed
   *
   * @param tg Logging tag
   * @param readonly If the data files are to be open readonly (normally true)
   * @param nonice Override any nice setting to make this try to run as fast as possible.
   * @param parent The logging parent EdgeThread - if null, use normal prt() logging.
   * @throws InstantiationException
   */
  public EdgeQueryInternal(String tg, boolean readonly, boolean nonice, EdgeThread parent)
          throws InstantiationException {
    // Allow the EdgeQueryServer to start and abort if it does not!
    for (int i = 0; i < 100; i++) {
      if (EdgeQueryServer.getServer() != null) {
        break;
      }
      if (i == 99) {
        throw new InstantiationException("Trying to create s EdgeQueryInternal in a process without a EdgeQueryServer!");
      }
      Util.sleep(100);
    }
    par = parent;
    eq = new EdgeQuery(readonly, par);
    tag = tg;
    this.nonice = nonice;
  }

  /**
   * This frees all of the blocks on a return ArrayList from a call to query. The ArrayList will be
   * empty on return as all blocks are now free.
   *
   * @param blks TReturned from a call to query() to be freed
   */
  public void freeBlocks(ArrayList<MiniSeed> blks) {
    if (blks == null) {
      return;
    }
    MiniSeedPool msp = EdgeQuery.getMiniSeedPool();
    for (int i = blks.size() - 1; i >= 0; i--) {
      msp.free(blks.get(i));
      blks.remove(i);
    }
  }

  /**
   * This is the main routine for a EdgeQueryInternal - it returns miniseed blocks from a query but
   * without using the TCP/IP server interface. The return blocks come from the
   * EdgeQuery.MiniSeedPool and the user is responsible for freeing them. The normal way is to call
   * freeBlocks().
   * <p>
   * The blocks returned are unsorted and not checked for any problems. If the user needs this done,
   * call cleanBlocks().
   *
   * @param seedname The seedname or regular expression to get
   * @param begin The beginning time as a long
   * @param duration Duration in decimal seconds
   * @param blks To return the blocks from the EdgeQuery.MiniSeedPool - user must free these blocks
   * when done!
   * @throws gov.usgs.anss.edge.IllegalSeednameException
   * @throws java.io.IOException
   */
  public void query(String seedname, long begin, double duration, ArrayList<MiniSeed> blks)
          throws IllegalSeednameException, IOException {
    beg.setTime(begin);
    end.setTime(begin + (long) (duration * 1000. + 0.001));
    long now, st, msMatch, msSet, msQuery, elapse;
    state = 12;
    nquery++;
    try {
      if (nonice) {
        nice = false;
      }
      elapse = System.currentTimeMillis();
      if (elapse - begin > 31557600000L && !nonice) {
        nice = true;
      }
      st = elapse;
      // This insures the files are open for the date range and are in the EdgeQueries indices list
      eq.setParameters(beg, end, eqdbg | dbg, (EdgeQueryServer.allowrestricted ? null : "exclude.txt"), false, tag, false, false);
      now = System.currentTimeMillis();
      msSet = now - st;

      if (nice) {
        Util.sleep(100);
      }
      // If this is not a regular expression, then this is unneeded
      matches.clear();
      seedname = seedname.replaceAll("-", " ");
      if (seedname.length() == 12 && !seedname.contains(".") && !seedname.contains("[")
              && !seedname.contains("^") && !seedname.contains("$")) {
        matches.add(seedname);
      } else {
        int nret = eq.queryMatches(seedname, beg, duration, matches);
      }
      if (matches.size() > 0) {
        state = 13;
        if (dbg) {
          prta(Util.clear(tmpsb).append(tag).append(" result: #matches = ").
                  append(matches.size()).append(" seedmask=").append(seedname).
                  append(" beg=").append(beg).append(" match[0]=").append(matches.get(0)).
                  append(" nice=").append(nice).append(" #files=").append(eq.getIndices().size()));
          if (!quiet) {
            Util.clear(tmpsb);
            tmpsb.append(tag);
            for (int i = 0; i < matches.size(); i++) {
              tmpsb.append(matches.get(i)).append(" ");
              if (i % 8 == 7) {
                tmpsb.append("\n").append(tag);
              }
            }
            prt(tmpsb.toString());
          }
        }
      } else if (dbg) {
        prta(Util.clear(tmpsb).append(tag).append(seedname).append(" result: #matches = 0"));
      }
      now = System.currentTimeMillis();
      msMatch = now - st;

      state = 14;
      st = now;
      eq.resetLastNblks();
      eq.resetLastNDups();
      for (String match : matches) {
        if (match.trim().equals("")) {
          continue;
        }
        if (dbg) {
          prta(Util.clear(tmpsb).append(tag).append(match).append(" beg=").append(beg).append(" dur=").append(duration));
        }
        state = 15;
        eq.query(match, beg, end, blks, nice, false);
        state = 16;
        totblks += eq.getLastNblks();
        if (nice) {
          Util.sleep(100);
        }
      }
      lastNDups = eq.getLastNDups();
      if (lastNDups > 10000) {
        SendEvent.edgeSMEEvent("CWBWSDups", seedname + " "
                + Util.ascdatetime(beg.getTime()) + " " + ((end.getTime() - beg.getTime()) / 1000) + "s #dups=" + lastNDups, this);
      }
      now = System.currentTimeMillis();
      msQuery = now - st;
      state = 17;
      if (!quiet) {
        prta(Util.clear(tmpsb).append(tag).append(seedname).append("I ").append(beg).append(" ").append(duration).
                append(" query done. close up... nblks=").append(eq.getLastNblks()).
                append("/").append(blks.size()).append(" #match=").append(matches.size()).
                append(" elapsed=").append((System.currentTimeMillis() - elapse) / 1000.).
                append(" s nice=").append(nice).append(" set=").append(msSet).
                append(" match=").append(msMatch).append(" query=").append(msQuery));
      }
    } catch (IllegalSeednameException e) {
      prt(Util.clear(tmpsb).append(tag).append(" Seedname in query is illegal=").append(seedname));
      e.printStackTrace();
      throw e;
    } catch (IOException e) {
      Util.SocketIOErrorPrint(e, tag + "EQI: during query processing");
      e.printStackTrace();
      throw e;
    } catch (RuntimeException e) {
      prt(Util.clear(tmpsb).append(tag).append("RuntimeException in query.  e=").append(e.toString()));
      e.printStackTrace(par.getPrintStream());
      throw e;
    }
  }

  /**
   * This routines will sort the ArrayList of MiniSEED returned by query(), and eliminate any
   * duplicates (including freeing them from the MiniSeedPool.
   *
   * @param blks The returned ArrayList from a query()
   * @return The number of duplicates eliminated.
   */
  public int cleanupBlocks(ArrayList<MiniSeed> blks) {
    int ndups = 0;
    if (blks.size() > 0) {
      MiniSeedPool miniSeedPool = EdgeQuery.getMiniSeedPool();
      Collections.sort(blks);
      for (int i = blks.size() - 1; i > 0; i--) {
        if (blks.get(i).isDuplicate(blks.get(i - 1))) {
          ndups++;
          miniSeedPool.free(blks.get(i));
          blks.remove(i);
        }
      }
    }
    return ndups;
  }
}
