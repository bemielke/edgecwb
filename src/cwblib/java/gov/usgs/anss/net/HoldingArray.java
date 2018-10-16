/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

 /*
 * HoldingArray.java
 *
 * Created on May 3, 2005, 4:22 PM
 */
package gov.usgs.anss.net;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.GregorianCalendar;

/**
 * This class holds a lot of the holdings so they can be updated while in MySQL backed objects. The
 * array of holdings in managed and kept in 1st degree order by the seedname//type. Within the same
 * seedname//type there may be many records but without any particular ordering for time. The class
 * has several maintenance routines that purge out Holdings that are not being updated, and one that
 * attempts to consolidate holdings which may have grown to touch or overlap. These routines should
 * be called fairly sparingly to keep things cleaned up.
 *
 * @author davidketchum
 */
public final class HoldingArray {

  static int debugValue;
  private Holding[] h;
  private final int myValue;
  private int nh;         // Number of used eliments in h
  private String tag;
  public final Holding hempty;
  private int state;
  private static long lastPage;
  private final boolean dbg = false;
  private boolean deferUpdate;

  public int getState() {
    return state;
  }

  /**
   * If this is set true, the holdings will not be updated unless it is done explicity
   *
   * @param t True, defer all updates
   */
  public void setDeferUpdate(boolean t) {
    deferUpdate = t;
  }

  ;

  public Holding getHolding(int i) {
    return h[i];
  }

  public Holding[] getHoldings() {
    return h;
  }

  @Override
  public String toString() {
    return "HA: nh=" + nh + " state=" + state;
  }

  public String toStringDetail() {
    StringBuilder sb = new StringBuilder(1000);
    for (int i = 0; i < nh; i++) {
      sb.append(h[i].toString()).append("\n");
    }
    return sb.toString();
  }

  /**
   * given a time in GregorianCalendar.getTimeInMillis() form and duration in millis return true if
   * this is entirely within one of the hold holdings. Use this call only if this Holding array
   * contains multiple seedname.
   *
   * @param seedname The seedname to test for
   * @param time A millisecond time per GregorianCalendar
   * @param ms The duration being tested in Millis
   * @return True if the time and duration are entirely within a holding
   */
  public boolean containsFully(String seedname, long time, int ms) {
    for (int i = 0; i < nh; i++) {
      if (h[i].getSeedName().equals(seedname)) {
        if (h[i].completelyContains(time, ms)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * given a time in GregorianCalendar.getTimeInMillis() form and duration in millis return true if
   * this is entirely within one of the hold holdings. Use this call only if this Holding array only
   * contains the correct seedname.
   *
   * @param time A millisecond time per GregorianCalendar
   * @param ms The duration being tested in Millis
   * @return True if the time and duration are entirely within a holding
   */
  public boolean containsFully(long time, int ms) {
    for (int i = 0; i < nh; i++) {
      if (h[i].completelyContains(time, ms)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a new instance of HoldingArray
   */
  public HoldingArray() {
    state = 1;
    h = new Holding[1000];
    myValue = debugValue++;
    nh = 0;
    tag = "" + myValue;
    hempty = new Holding("ZZZZZZZZZZZZ", "ZZ", 10000L, 0);
    hempty.setDeferUpdate(deferUpdate);

    for (int i = 0; i < 1000; i++) {
      h[i] = hempty;
    }
    state = 0;
  }

  public synchronized void clearNoWrite() {
    for (int i = 0; i < nh; i++) {
      h[i] = hempty;
    }
    nh = 0;
  }

  public synchronized void setTag(String t) {
    tag = t;
  }

  /**
   * given a holding ID, add it to the end of this Holding array
   *
   * @param ID the holdingID
   */
  public synchronized void addEnd(int ID) {
    state = 2;
    try {
      Holding a = new Holding(ID);
      a.setDeferUpdate(deferUpdate);
      addEnd(a);
    } catch (InstantiationException e) {
      Util.prt(" *** bad new Holding(ID) e=" + e);
    }
    state = 0;
  }

  /**
   * Given a positioned result set, add the represented Holding to the end
   *
   * @param rs The positioned ResultSet
   */
  public synchronized void addEnd(ResultSet rs) {
    state = 3;
    Holding a = new Holding(rs);
    a.setDeferUpdate(deferUpdate);
    addEnd(a);
    state = 0;
  }

  /**
   * given a Holding, add it to the end of this HoldingArray
   *
   * @param a The Holding to add
   */
  public synchronized void addEnd(Holding a) {
    // If we did not expand an existing one, create a new one
    state = 4;
    try {
      nh = nh + 1;
      if (nh > h.length) {
        Util.prta("HA:[" + tag + "] Expand msgs by 1000 len=" + h.length + " nmsgs=" + nh);
        Holding[] tmp = new Holding[h.length + 1000];
        System.arraycopy(h, 0, tmp, 0, h.length);
        for (int i = 0; i < 1000; i++) {
          tmp[h.length + i] = hempty;
        }
        h = tmp;
      }

      int insertAt = nh - 1;
      //Util.prt("HA:["+tag+"] add holding="+insertAt+" "+a.toString());
      if (insertAt + 1 < h.length) {
        System.arraycopy(h, insertAt, h, insertAt + 1, h.length - insertAt - 1);
      }
      h[insertAt] = a;
      if (dbg) {
        Util.prta("HA:[" + tag + "] Insert record at " + insertAt + " ps=" + h[insertAt].toString2());
      }
      h[insertAt].setDeferUpdate(true);
    } catch (RuntimeException e) {
      Util.prt("RuntimeException in HoldingArray.addEnd() =" + e.getMessage());
      e.printStackTrace();

    }
    state = 0;
  }
  private Holding testHolding;

  public synchronized void addOn(String name, String ty, long time, long ms) {
    state = 5;
    int ins = -320000000;
    if (name == null) {
      return;
    }
    if (ty == null) {
      return;
    }
    state = 51;
    if (!Util.isValidSeedName(name)) {
      Util.prt("HA: holdings array exception back seedname=" + name);
      state = 6;

      return;   // cannot porcess invalid seed names.
    }
    boolean dbgorg = dbg;
    if (dbgorg == false) {
      if (name.substring(0, 10).equals("USDUG  BHZ")) {
        dbgorg = true;
      }
    }
    try {
      //if(name.substring(2,5).compareTo("AAAAA") == -1) return;
      //if(name.substring(0,7).compareTo("ZZZZZZ") == 1) return;
      state = 52;
      if (h == null) {
        return;         // This holdingArray has been closed!
      }
      state = 53;
      try {
        if (testHolding == null) {
          testHolding = new Holding(name, ty, time, -1);
          testHolding.setDeferUpdate(deferUpdate);
        }    // create and empty one, no write to DB
        testHolding.reload(name, ty, time, ms);
      } catch (RuntimeException e) {
        if (e.getMessage() != null) {
          if (e.getMessage().contains("rejected bad")) {
            Util.prt("Holding rejected " + name + " type=" + ty + " time=" + time);
            return;
          }
        }
      }
      state = 54;
      if (nh == 0) {
        ins = -1;
      } else {
        ins = Arrays.binarySearch(h, testHolding);
      }
      state = 55;
      if (dbgorg) {
        Util.prta("HA:[" + tag + "]Addon ins=" + ins + " seed=" + name + " " + ty + " time=" + time + " " + ms);
      }
      if (ins >= 0) {
        boolean done = false;
        while (!done) {
          state = 56;
          if (ins <= 0) {
            break;
          }
          if (dbgorg) {
            Util.prta("HA:[" + tag + "]search back for first ins=" + ins + " h[ins-1]=" + h[ins - 1].toString());
          }
          // If the prior one is still the same type, count done
          if (h[ins - 1].getSeedName().equals(name) && h[ins - 1].getType().equals(ty)) {
            ins--;
          } else {
            done = true;
          }

        }
        state = 57;
        done = false;
        boolean update = false;
        while (!done) {
          update = h[ins].addOn(time, ms);
          if (dbgorg) {
            Util.prta("HA:[" + tag + "]Try to add to ins=" + ins + " h=" + h[ins] + " upd=" + update);
          }
          if (update) {
            break;
          }
          ins++;
          if (ins >= nh) {
            break;
          }
          if (!h[ins].getSeedName().equals(name) || !h[ins].getType().equals(ty)) {
            ins--;
            done = true;
          }
        }
        state = 58;
        // If update is false, then this is the start of a new holding, create it
        if (!update) {
          //a = new Holding(name,ty,time,ms);
          if (dbgorg) {
            Util.prta("HA:[" + tag + "] no update do insert ins=" + ins);
          }
          ins = -(ins + 1);           // fake up so its like the binary search return
        } else if (dbgorg) {
          Util.prta("HA:[" + tag + "] Update done at ins=" + ins + " h[ins]=" + h[ins].toString());
        }
      }

      // If we did not expand an existing one, create a new one
      if (ins < 0) {
        state = 59;
        if (nh + 1 > h.length) {
          Util.prta("HA:[" + tag + "] Expand msgs by 1000 len=" + h.length + " nmsgs=" + nh);
          Holding[] tmp = new Holding[h.length + 1000];
          System.arraycopy(h, 0, tmp, 0, h.length);
          for (int i = 0; i < 1000; i++) {
            tmp[h.length + i] = hempty;
          }
          h = tmp;
        }
        state = 60;
        int insertAt = -(ins + 1);
        if (insertAt + 1 < h.length) {
          System.arraycopy(h, insertAt, h, insertAt + 1, h.length - insertAt - 1);
        }
        try {
          h[insertAt] = new Holding(name, ty, time, ms);
          h[insertAt].setDeferUpdate(deferUpdate);
        } catch (RuntimeException e) {
          if (e.getMessage() != null) {
            if (e.getMessage().contains("rejected bad")) {
              Util.prt("Holding rejected " + name + " type=" + ty + " time=" + time);
              return;
            }
          }
        }
        nh = nh + 1;
        if (dbgorg) {
          Util.prta("HA:[" + tag + "] Insert record at " + insertAt + " ps=" + h[insertAt].toString2());
        }
        h[insertAt].setDeferUpdate(true);
      }
    } catch (RuntimeException e) {
      Util.prt(tag + " RuntimeException in HoldingArray.addOn() ins=" + ins + " nh=" + nh + " nm=" + name + " ty=" + ty + " msg=" + e.getMessage());
      if (nh > 0) {
        if (ins - 1 >= 0 && h[ins - 1] != null) {
          Util.prt("ins-1=" + h[ins - 1]);
        }
        if (ins >= 0 && h[ins] != null) {
          Util.prt("ins=" + h[ins]);
        }
      }
      if (System.currentTimeMillis() - lastPage > 7200000) {
        SendEvent.pageSMEEvent("HoldArrRuntime", "Runtime Exception in HoldingArray.addOn() " + tag, this);

        lastPage = System.currentTimeMillis();
      }
      e.printStackTrace();
    }
    state = 0;
  }

  public void list() {
    int nmod = 0;
    for (int i = 0; i < nh; i++) {
      Util.prt(i + " " + h[i].toString2());
      if (h[i].isModified()) {
        nmod++;
      }
    }
    Util.prt(nmod + " of " + nh + " records need writing.");

  }

  public int getSize() {
    return nh;
  }

  /**
   * timeoutWrite causes any holdings which have aged at least ms millis to be update in the
   * database. Often the deferWrite flags are set for records so that updates do not occur on each
   * change and this routine would then cause the changed records to update periodically
   *
   * @param ms The number of ms that the record must have aged to be updated
   * @param max Number of records to update as a maximum
   * @return Number of records updated.
   */
  public synchronized int timeoutWrite(int ms, int max) {
    state = 8;
    int recs = 0;
    for (int i = 0; i < nh; i++) {
      if (h[i].doUpdate(ms)) {
        recs++;
        if (recs >= max) {
          return recs;
        }
      }
    }
    state = 0;
    return recs;
  }

  private void drop(int i) {
    if (i < h.length - 1) {
      h[i].close();
      System.arraycopy(h, i + 1, h, i, h.length - i - 1);
    }
    h[h.length - 1] = hempty;
    nh--;
  }
  /**
   * keepWriting causes any holdings which have aged at least sec seconds to be update in the
   * database. Often the deferWrite flags are set for records so that updates do not occur on each
   * change and this routine would then cause the changed records to update periodically. A maximum
   * of max records will be update on each call. The starting point proceeds through the array so
   * all records will eventually be written.
   *
   * @param sec The number of seconds that the record must have aged to be updated
   * @param max Number of records to update as a maximum
   */
  int keepStart = 0;

  public synchronized int keepWriting(int sec, int max) {
    if (h == null) {
      Util.prta("HA:[" + tag + "] keepwriting h is null? ");
      return 0;
    }
    state = 10;
    int recs = 0;
    if (keepStart >= nh) {
      keepStart = 0;    // insure the keepStart is in current range
    }
    int i = keepStart + 1;
    if (nh == 0) {
      return 0;
    }
    if (i >= nh) {
      i = 0;
    }
    int loops = 0;
    while (recs < max && i != keepStart) {
      if (h[i] != null) {
        if (h[i].getAgeWritten() >= sec && h[i].isModified()) {
          boolean updated = h[i].doUpdate();
          recs++;
          if (recs >= max) {
            break;
          }
          if (!updated && h[i].getOutOfScope()) {
            Util.prta("** Found out-of-scope holding - drop it " + h[i].toString2());
            drop(i);
            i--;
            if (i < 0) {
              i = 0;
            }
          }
        }
      } else {
        Util.prta("HA:[" + tag + "] Exception : keepwriting h[i] is null! nh=" + nh + " i=" + i + " tag=" + tag);
      }
      i++;
      loops++;
      if (i >= nh) {
        i = 0;
      }
      if (loops > nh) {
        Util.prta("HA:[" + tag + "] Exception: keepwriting is stuck in a loop nh=" + nh
                + " keepStart=" + keepStart + " i=" + i + " recs=" + recs + " max=" + max + " tag=" + tag);
        break;
      }
    }
    keepStart = i;

    // This insure no one is missed!
    if (h[i] == null) {
      Util.prta("HA:[" + tag + "]  ***** keepwriting2 h[i] is null! nh=" + nh + " i=" + i + " tag=" + tag);
    } else if (h[i].getAgeWritten() >= sec && h[i].isModified()) {
      h[i].doUpdate();
    }
    state = 0;
    return recs;
  }

  /**
   * Try to consolidate overlapping Intervals into a single one
   *
   * @param max max number of combines to allow (used to limit processing
   * @return Number of combines done
   */
  public synchronized int consolidate(int max) {
    state = 11;
    return consolidate(max, false);
  }

  /**
   * Try to consolidate overlapping intervales into a singe one.
   *
   * @param max maximum number of combines to allow (used to limit processing time)
   * @param ordered If the data are ordered, then set this to eliminate unnecessary compares.
   * @return Number combined
   */
  public synchronized int consolidate(int max, boolean ordered) {
    if (h == null) {
      return 0;
    }
    state = 12;
    String last = "";
    int start = 0;
    int end = 0;
    int ncombined = 0;
    int ncompares = 0;
    boolean update;
    long total = System.currentTimeMillis();
    long tcompares = 0;
    long taddon = 0;
    long tmp;
    long started = System.currentTimeMillis();
    int lastStart = -1;
    boolean dbg2 = false;
    try {
      while (start < nh) {
        if (start == lastStart) {
          Util.prta("HA:[" + tag + "] consolidate not making progress! start=" + start);
        }
        lastStart = start;
        // print progress if this is taking a bit
        if (System.currentTimeMillis() - started > 30000) {
          Util.prta("HA:[" + tag + "] cons prog start=" + start + " end=" + end + " nh=" + nh
                  + " seed=" + h[start].getSeedName() + " ncomps=" + ncompares + " tcomp=" + tcompares
                  + " tadd=" + taddon + " ncomb=" + ncombined + " max=" + max);
          started = System.currentTimeMillis();
        }
        if (h[start].getSeedName().contains("USDUG   BHZ")) {
          dbg2 = true;
        }
        // Find next record which is not same, go to nth because the if will force 
        // the last group to be processed at the if
        for (end = start + 1; end <= nh; end++) {
          if (!h[start].getSeedName().equals(h[Math.min(end, nh - 1)].getSeedName())
                  || !h[start].getType().equalsIgnoreCase(h[Math.min(end, nh - 1)].getType())
                  || end == nh) {
            end--;
            if (start != end) {         // are more than one holding for this channel/type present

              tmp = System.currentTimeMillis();
              for (int i = start; i < end; i++) {
                for (int j = i + 1; j <= end; j++) {
                  if (i == j) {
                    continue;
                  }
                  //String s1=h[i].toString(); 
                  //String s2=h[j].toString();
                  ncompares++;
                  tmp = System.currentTimeMillis();
                  update = h[i].addOn(h[j].getStart(), h[j].getLengthInMillis());
                  taddon += (System.currentTimeMillis() - tmp);

                  // They overlap, remove the jth one, including in the database
                  if (update) {
                    if (dbg2) {
                      Util.prt("Combine i=" + (i + "   ").substring(0, 4) + " j=" + (j + "   ").substring(0, 4));
                      Util.prt("Combine i=" + (i + "   ").substring(0, 4) + " j=" + (j + "   ").substring(0, 4) + " " + h[i] + " overlaps");
                      Util.prta("          " + h[j]);
                    }
                    h[i].doUpdate();      // Update the consolidated one
                    h[j].delete();        // Remove from database
                    h[j].close();
                    if (j < h.length - 1) {
                      System.arraycopy(h, j + 1, h, j, h.length - j - 1);
                    }

                    // adjust indexes beyond the one just deleted
                    end--;
                    h[h.length - 1] = hempty;   // Empty one in the last one
                    nh--;
                    ncombined++;
                    if (ncombined >= max) {
                      Util.prta("Consolidate early nh=" + nh + " ncompares=" + ncompares + " tcomp=" + tcompares
                              + " taddon=" + taddon + " total=" + (System.currentTimeMillis() - total) + " max=" + max);
                      state = 0;
                      return ncombined;
                    }
                    //list();
                  }
                  if (ordered && j < nh) {
                    if (h[j].getStart() > (h[i].getEnd() + h[i].getTolerance())) {
                      break;  // all further comparison futile
                    }
                  }
                  if (update) {
                    j--;         // check the same index (but new record)
                  }
                }     // end for loop on j
              }       // end for loop on i
              tcompares += (System.currentTimeMillis() - tmp);
            }
            break;
          }
        }
        start = end + 1;

      }
    } catch (RuntimeException e) {
      Util.prt("Runtime Exception in HoldingArray.consolidate()) start=" + start + " end=" + end + " nh=" + nh + " " + e);
      Util.prt("RuntimeException = " + e.getMessage());
      e.printStackTrace();
    }
    if (System.currentTimeMillis() - total > 2000) {
      Util.prta("Consolidate nh=" + nh + " ncompares=" + ncompares + " tcomp=" + tcompares
              + " taddon=" + taddon + " total=" + (System.currentTimeMillis() - total) + " ncomb=" + ncombined);
    }
    state = 0;
    return ncombined;
  }

  /**
   * Look through the array of data by channel and if a day has more than the given number of
   * elements, trim down to only the longest ones and move all that do not qualify to the
   * holdingspurge table. No holding longer than 120 seconds can be purged so often the target
   * number of longest holdings is extended. This has never been put into production but was tested
   * in Dec 2017.
   *
   * @param nPerDay The target number of holdings to have left on the data
   * @param stmt A SQL statement to use to move holdings and delete the moved ones
   * @param table The table which is to be purged and moved.
   * @return Number of records moved and purge from the table
   * @throws SQLException If one occurs.
   */
  public int moveHackedUpToPurge(int nPerDay, Statement stmt, String table)
          throws SQLException {
    StringBuilder replaceList = new StringBuilder();
    if (compare == null) {
      sort = new Holding[1000];
      compare = new HoldingsLengthComparator();
    }
    int i = 0;
    int ndelete = 0;
    int nsqldelete = 0;
    int ndel = 0;
    long thisDay = h[0].start / 86400000;
    while (i < nh) {
      int start = i;
      int j = 0;
      while (thisDay == h[i].start / 86400000 && i < nh) {    // each record on the same day
        if (j >= sort.length) {
          Holding[] tmp = new Holding[sort.length * 2];
          System.arraycopy(sort, 0, tmp, 0, j);
          sort = tmp;
        }
        sort[j++] = h[i];
        i++;
      }
      int nsort = j;
      if (j > nPerDay * 5) {
        Arrays.sort(sort, 0, nsort, compare);
        long longDelete = 0;
        long shortKept = Long.MAX_VALUE;

        for (j = 0; j < nsort; j++) {
          if (j < nsort - nPerDay) {
            if (sort[j].getLengthInMillis() <= 120000) {
              if (sort[j].getLengthInMillis() > longDelete) {
                longDelete = sort[j].getLengthInMillis();
              }
              if (dbg) {
                Util.prt("Delete j=" + j + " " + sort[j].getLengthInMillis() + " " + sort[j]);
              }
              ndelete++;
              ndel++;
              replaceList.append(sort[j].getID()).append(",");
            } else {      // Its longer than two minutes, keep it anyway
              if (sort[j].getLengthInMillis() < shortKept) {
                shortKept = sort[j].getLengthInMillis();
              }
            }
          } else {
            if (sort[j].getLengthInMillis() < shortKept) {
              shortKept = sort[j].getLengthInMillis();
            }
            if (dbg) {
              Util.prt("Save  j=" + j + " " + sort[j].getLengthInMillis() + " " + sort[j]);
            }

          }
        }
        Util.prt(sort[0].getSeedName() + " " + Util.ascdate(thisDay * 86400000) + " #del=" + ndel + " Longest delete=" + longDelete
                + " short Kept =" + shortKept + " longkept=" + sort[nsort - 1].getLengthInMillis()
                + " nrec=" + nsort + " " + Util.df21(ndel * 100. / nsort) + "%");
      }
      if (replaceList.length() > 0) {
        try {
          replaceList.deleteCharAt(replaceList.length() - 1);
          int nrep = stmt.executeUpdate("REPLACE INTO holdingspurge SELECT * FROM " + table + " WHERE ID IN (" + replaceList.toString() + ")");
          nsqldelete += stmt.executeUpdate("DELETE FROM " + table + " WHERE ID IN (" + replaceList.toString() + ")");
        } catch (SQLException e) {
          throw e;
        }
        if (nsqldelete != ndelete) {
          Util.prta("delets out of sync.");
        }
      }// drop the ending comma
      Util.clear(replaceList);
      // Its a new day or the end has been reached, decide what to do with these
      thisDay = h[i].getStart() / 86400000;
      ndel = 0;
    }
    Util.prt(" Total deletes=" + ndelete + " sqndeletes=" + nsqldelete);
    return ndelete;
  }

  private HoldingsLengthComparator compare;   // Only used in moveHackedUpToPurge
  private Holding[] sort;                    // Only used in moveHackedUpToPurge

  /**
   * Purge the list of any that are older than given number of seconds
   *
   * @param age Then age at which we will purge out a record in seconds
   * @return Number of purged records.
   */
  public synchronized int purgeOld(int age) {
    if (h == null) {
      return 0;     // This is closed
    }
    state = 13;
    int i = nh - 1;
    int npurge = 0;
    if (age != 0) {
      Util.prta("HA:[" + tag + "]Start purge at i=" + i + " age=" + age);
    }
    while (i >= 0) {
      if (h[i].getAge() >= age) {
        if (age == -1 && i % 500 == 0) {
          Util.prta("HA:[" + tag + "] Purge on age " + h[i].getAge() + ">=" + age + " i=" + i + " nh=" + nh + " len=" + h.length + " " + h[i].toString());
        }
        h[i].doUpdate(0);
        npurge++;

        if (i < h.length - 1) {
          h[i].close();
          System.arraycopy(h, i + 1, h, i, h.length - i - 1);
        }
        h[h.length - 1] = hempty;
        nh--;
      } else {
        if (age <= 0) {
          Util.prt("HA:[" + tag + "] ***** age 0 left this record=" + h[i].toString2());
        }
      }
      i--;
    }
    if (age != 0) {
      Util.prta("HA:[" + tag + "] End of npurge=" + npurge);
    }
    state = 0;
    return npurge;
  }

  public boolean isClosed() {
    return (h == null);
  }

  public synchronized void close() {
    state = 0;
    //Util.prta("HA:["+tag+"] release all memory hsize="+h.length+" nh="+nh);
    for (int i = 0; i < h.length; i++) {
      h[i] = hempty;  // release all of the array
    }
    state = 0;
    nh = 0;
  }

  public static void cleanup() {

    Util.prt(" HoldingArray Cleanup called");
  }

  private class HoldingsLengthComparator implements Comparator {

    /**
     * Creates a new instance of LatencyComparator
     */
    public HoldingsLengthComparator() {
    }

    /**
     * implement Comparator
     *
     * @param o1 First object to compare
     * @param o2 2nd object to compare
     * @return 1 if o1 is older than o2, 0 otherwise
     */
    @Override
    public int compare(Object o1, Object o2) {
      long l1 = ((Holding) o1).getLengthInMillis();
      long l2 = ((Holding) o2).getLengthInMillis();
      return (l1 > l2) ? 1 : (l1 == l2) ? 0 : -1;
    }

    /**
     * This has no meaning for equality, always return false
     *
     * @param o1 the object to compare to
     * @return always false
     */
    @Override
    public boolean equals(Object o1) {
      if (o1 instanceof Holding) {
        return false;
      }
      return false;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      return hash;
    }
  }

  /**
   * test main routine
   *
   * @param args Command line args
   */
  public static void main(String[] args) {
    DBConnectionThread jcjbl;
    Util.init("edge.prop");
    Connection C;
    User user = new User("dkt");
    try {
      jcjbl = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", true, false, "testHolding", Util.getOutput());
      HoldingArray ha = new HoldingArray();
      Statement stmt = jcjbl.getConnection().createStatement(
              ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      int i = stmt.executeUpdate("DELETE FROM holdings WHERE ID>0");

      GregorianCalendar g = new GregorianCalendar(2005, 1, 1, 10, 0, 0);
      long base = g.getTimeInMillis();
      ha.addOn("USAAA  BHZ00", "AA", base + 15000, 10000);
      ha.addOn("USAAA  BHZ00", "AA", base + 25000, 10000);
      ha.addOn("USAAA  BHZ00", "AA", base + 40000, 10000);
      ha.addOn("USAAA  BHZ00", "AA", base + 35000, 5000);
      ha.addOn("USBBB  BHZ00", "AA", base + 30000, 10000);
      ha.addOn("USBBB  BHZ00", "AA", base + 40000, 10000);
      ha.addOn("USBBB  BHZ00", "AA", base + 60000, 10000);
      ha.addOn("USBBB  BHZ00", "AA", base + 50000, 10000);
      ha.list();
      ha.timeoutWrite(10000, 5);
      long was = new GregorianCalendar().getTimeInMillis();
      while ((new GregorianCalendar().getTimeInMillis() - was) < 10020) {
        Util.prta("Waiting");
      }
      Util.prt("try a consolidation");
      ha.consolidate(5);
      ha.list();
      ha.timeoutWrite(10000, 5);
      ha.purgeOld(8);

      //user=new User(C,"dkt","karen");
    } catch (InstantiationException e) {
      Util.prt("Could not instatiation DB connection");
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, " Main SQL unhandled=");
      System.err.println("SQLException  on  getting test Holdings");
    }
  }
}
