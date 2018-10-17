package org.trinet.util.old;

import java.util.Calendar;

import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
//import org.trinet.jasi.DataSource;
//import org.trinet.jasi.TestDataSource;

/**
 * <p>Handle conversion of epoch times to and from bases that include leap seconds. </p>
 *
 * In the NCEDC vernacular on which this is based "true" means with leap-seconds included
 * and "nominal" means without leap-seconds.
 *
 * Which you use doesn't matter so long as all epoch times use the same system or base.
 * However, if you mix them you will get discrepencies of up to 23 seconds.<p>
 *
 * <pre>
 *        00:59|01:00
 *     --+--+--|--+--+--+--+--  "nominal" time (no leap second)
 *     57 58 59  0  1  2  3  4
 *
 *           00:60|01:00
 *     --+--+--+##|--+--+--+--  "true" time (leap second)
 *     57 58 59 60  0  1  2  3
 *
 * </pre>
 *
 * True to nominal: subtract leap seconds<br>
 * Nominal to true: add leap seconds<br>
 * See: http://www.leapsecond.com/java/gpsclock.htm
 <tt>
            Dump of the Leap_Seconds Table

 S_NOMINAL  E_NOMINAL     S_TRUE     E_TRUE   LS_COUNT
---------- ---------- ---------- ---------- ----------
-6.214E+10   78796799 -6.214E+10   78796799          0
  78796800   94694399   78796801   94694400          1
  94694400  126230399   94694402  126230401          2
 126230400  157766399  126230403  157766402          3
 157766400  189302399  157766404  189302403          4
 189302400  220924799  189302405  220924804          5
 220924800  252460799  220924806  252460805          6
 252460800  283996799  252460807  283996806          7
 283996800  315532799  283996808  315532807          8
 315532800  362793599  315532809  362793608          9
 362793600  394329599  362793610  394329609         10
 394329600  425865599  394329611  425865610         11
 425865600  489023999  425865612  489024011         12
 489024000  567993599  489024013  567993612         13
 567993600  631151999  567993614  631152013         14
 631152000  662687999  631152015  662688014         15
 662688000  709948799  662688016  709948815         16
 709948800  741484799  709948817  741484816         17
 741484800  773020799  741484818  773020817         18
 773020800  820454399  773020819  820454418         19
 820454400  867715199  820454420  867715219         20
 867715200  915148799  867715221  915148820         21
 915148800 1136073599  915148822 1136073621         22
1136073600 1230767999 1136073623 1230768022         23
1230768000 1341100799 1230768024 1341100823         24
1341100800 32503680000 1341100825 32503680024       25
</tt>
*/
public class LeapSeconds {

/* Starting "nominal" epoch second of time span with a given leap second contained */
  protected static long nominalSecEnd[] = {
          78796799,
          94694399,
         126230399,
         157766399,
         189302399,
         220924799,
         252460799,
         283996799,
         315532799,
         362793599,
         394329599,
         425865599,
         489023999,
         567993599,
         631151999,
         662687999,
         709948799,
         741484799,
         773020799,
         820454399,
         867715199,
         915148799,
        1136073599,
        1230767999,
        1341100799, 
        Long.MAX_VALUE 
  };

  protected static long trueSecEnd[] = {
          78796799,
          94694400,
         126230401,
         157766402,
         189302403,
         220924804,
         252460805,
         283996806,
         315532807,
         362793608,
         394329609,
         425865610,
         489024011,
         567993612,
         631152013,
         662688014,
         709948815,
         741484816,
         773020817,
         820454418,
         867715219,
         915148820,
        1136073621,
        1230768022,
        1341100823,
        Long.MAX_VALUE
  };

  protected static long nominalSecStart[] = {
     Long.MIN_VALUE,
     78796800,
     94694400,
     126230400,
     157766400,
     189302400,
     220924800,
     252460800,
     283996800,
     315532800,
     362793600,
     394329600,
     425865600,
     489024000,
     567993600,
     631152000,
     662688000,
     709948800,
     741484800,
     773020800,
     820454400,
     867715200,
     915148800,
     1136073600,
     1230768000,
     1341100800
  };

/* Starting "true" epoch second of time span with a given leap second contained */
  protected static long trueSecStart[] = {
    Long.MIN_VALUE,
    78796801,
    94694402,
    126230403,
    157766404,
    189302405,
    220924806,
    252460807,
    283996808,
    315532809,
    362793610,
    394329611,
    425865612,
    489024013,
    567993614,
    631152015,
    662688016,
    709948817,
    741484818,
    773020819,
    820454420,
    867715221,
    915148822,
   1136073623,
   1230768024,
   1341100825 
  };

/* Ending epoch second of time span with a given leap second contained */
 // Yah, I know. It's just the index of the time span but double leapseconds are POSSIBLE.
    protected static int leapSeconds[] = {
      0,
      1,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      9,
      10,
      11,
      12,
      13,
      14,
      15,
      16,
      17,
      18,
      19,
      20,
      21,
      22,
      23,
      24,
      25
    };

  /* Uncomment below to get db init of data arrays
  static {
      // initializes data arrays from database if available
      loadArraysFromDataSource();
      //for (int idx = 0; idx< leapSeconds.length; idx++) {
      //    System.out.println(idx + " " + leapSeconds[idx] + " " + trueSecStart[idx]);
      //}
  }
  */

 /* public static boolean loadArraysFromDataSource() {
    boolean status = false;
    if (! DataSource.isNull() && ! DataSource.isClosed()) {
      System.out.println("Loading leap second array from datasource LEAP_SECONDS table.");
      Statement sm = null;
      ResultSet rs = null;
      try {
        sm = DataSource.getConnection().createStatement();
        rs = sm.executeQuery( "SELECT S_NOMINAL, E_NOMINAL, S_TRUE, E_TRUE, LS_COUNT FROM LEAP_SECONDS" );
        ArrayList snom =  new ArrayList(32);
        ArrayList enom =  new ArrayList(32);
        ArrayList stru =  new ArrayList(32);
        ArrayList etru =  new ArrayList(32);
        ArrayList lsec =  new ArrayList(32);
        if (rs != null) {
          int count = 0;
          while ( rs.next() ) {
            snom.add(new Long(rs.getLong(1)));
            enom.add(new Long(rs.getLong(2)));
            stru.add(new Long(rs.getLong(3)));
            etru.add(new Long(rs.getLong(4)));
            lsec.add(new Integer(rs.getInt(5)));
            count++;
          }
          nominalSecStart = new long [count];
          nominalSecEnd = new long [count];
          trueSecStart = new long [count];
          trueSecEnd = new long [count];
          leapSeconds = new int [count];

          for (int idx = 0; idx < count; idx++) {
            nominalSecStart[idx] = ((Long) snom.get(idx)).longValue();
            nominalSecEnd[idx] = ((Long) enom.get(idx)).longValue();
            trueSecStart[idx] = ((Long) stru.get(idx)).longValue();
            trueSecEnd[idx] = ((Long) etru.get(idx)).longValue();
            leapSeconds[idx] = ((Integer) lsec.get(idx)).intValue();
          }
          status = true;
        }
      }
      catch (SQLException ex) {
        System.err.println(ex);
        ex.printStackTrace();
      }
      finally {
          try {
            if (rs != null) rs.close();
            if (sm != null) sm.close();
          }
          catch (SQLException ex) {}
      }
    }
    return status;
  }*/
  //

  public static double nominalToTrue(double nominalEpochTime) {
    return nominalEpochTime + getLeapSecsAtNominal(nominalEpochTime);
  }
  public static double trueToNominal(double trueEpochTime) {
    return trueEpochTime - getLeapSecsAtTrue(trueEpochTime);
  }

  /** Given an epoch time in the nominal system (no leap seconds) return
   * the number of leap seconds to add to get "true". */
  public static int getLeapSecsAtNominal(double nominalEpochTime) {
    // look thru the list backwards because current times are more common and
    // this way you need only  compare to the nominalSecStart value (not the end).
    // Note we don't examine the earliest time window but just fall through to 0 leapseconds.
    for (int i = nominalSecStart.length-1; i >= 0; i--) {
      if ( nominalEpochTime >= nominalSecStart[i]) {
        return leapSeconds[i];
      }
    }
    return -1;
  }

  /** Given an epoch time in the true system return
   * the number of leap seconds to subtract to get "nominal". I.e. this is
   * the number of leap seconds "included" in this "true" time. */
  public static int getLeapSecsAtTrue(double trueEpochTime) {
    // look thru the list backwards because current times are more common and
    // this way you need only compare to the nominalSecStart value (not the end).
    // Note we don't examine the earliest time window but just fall through to 0 leapseconds.
    int leap = -1;
    double tt = Math.floor(trueEpochTime);
    for (int i = trueSecStart.length-1; i >= 0; i--) {
      if (tt > trueSecEnd[i]) break;
      if ( tt >= trueSecStart[i] && tt <= trueSecEnd[i]) {
        leap = leapSeconds[i];
        break;
      }
    }
    if (leap >= 0) return leap; // true is not a leap second

    // Test for one leap second revert nominal to :59
    tt--;;
    leap = -1;
    for (int i = trueSecStart.length-1; i >= 0; i--) {
        if (tt > trueSecEnd[i]) break;
        if ( tt >= trueSecStart[i] && tt <= trueSecEnd[i] ) {
            leap = leapSeconds[i] + 1;
            break;
        }
    }
    if (leap >= 0)  return leap;

    // Test for two leap seconds revert nominal to :59
    tt--;
    leap = -1;
    for (int i = trueSecStart.length-1; i >= 0; i--) {
        if (tt > trueSecEnd[i]) break;
        if ( tt >= trueSecStart[i] && tt <= trueSecEnd[i] ) {
            leap = leapSeconds[i] + 2;
            break;
        }
    }
    return leap;

  }

  /** Given a true epoch time (leap seconds added) return
   * the number of leap seconds occurring just at that time. 
   * Returns 0, unless the input value is a leap second, then 
   * return value is 1 for first leap second and 2 only if its
   * a second leap second in a 2-second leap.
   */
  public static int secondsLeptAt(double trueEpochTime) {
    // look thru the list backwards because current times are more common and
    // this way you need only compare to the nominalSecStart value (not the end).
    // Note we don't examine the earliest time window but just fall through to 0 leapseconds.
    int leap = -1;
    double tt = Math.floor(trueEpochTime);
    for (int i = trueSecStart.length-1; i >= 0; i--) {
      if (tt > trueSecEnd[i]) break;
      if ( tt >= trueSecStart[i] && tt <= trueSecEnd[i]) {
        leap = 0;
        break;
      }
    }
    if (leap >= 0) return leap; // true is not a leap second

    // Test for one leap second revert nominal to :59
    tt--;;
    leap = -1;
    for (int i = trueSecStart.length-1; i >= 0; i--) {
        if (tt > trueSecEnd[i]) break;
        if ( tt >= trueSecStart[i] && tt <= trueSecEnd[i] ) {
            leap = 1;
            break;
        }
    }
    if (leap >= 0)  return leap;

    // Test for two leap seconds revert nominal to :59
    tt--;
    leap = -1;
    for (int i = trueSecStart.length-1; i >= 0; i--) {
        if (tt > trueSecEnd[i]) break;
        if ( tt >= trueSecStart[i] && tt <= trueSecEnd[i] ) {
            leap = 2;
            break;
        }
    }
    return leap;

  }

  public static final String trueToString(double trueEpochTime) {
      return trueToString(trueEpochTime, EpochTime.DEFAULT_FORMAT);
  }

  public static final String trueToString(double trueEpochTime, String pattern) {
    //return trueToString(trueEpochTime, pattern, "GMT");
    return trueToString(trueEpochTime, pattern, "UTC");
  }

  public static final String trueToString(double trueEpochTime, String pattern, String zone) {
        StringBuffer tStr = null;
        double tt = Math.floor(trueEpochTime);
        int lsecs = -1;
        for (int ii = trueSecEnd.length-1; ii >= 0; ii--) {
            if (trueSecEnd[ii] < tt) break;
            if (trueSecEnd[ii] >= tt && trueSecStart[ii] <= tt) {
                lsecs = leapSeconds[ii];
                break;
            }
        }

        if (lsecs >= 0) { // input not a leap second
           tStr = new StringBuffer(EpochTime.epochToString(trueEpochTime - lsecs, pattern, zone));
        }
        else { // input has one or more leap seconds
          int idx = pattern.indexOf("ss");  // >= 0 when pattern has seconds
          lsecs = -1;
          tt--;
          for (int ii =trueSecEnd.length-1; ii >= 0; ii--) {
            if (trueSecEnd[ii] < tt) break;
            if (trueSecEnd[ii] >= tt && trueSecStart[ii] <= tt) {
                lsecs = leapSeconds[ii];  // closest leap
                break;
            }
          }
          if (lsecs >= 0) { // input with just one leap second
            tStr = new StringBuffer(EpochTime.epochToString(trueEpochTime - lsecs - 1, pattern, zone));
            if (idx >= 0 && tStr.substring(idx, idx+2).equals("59")) tStr.replace(idx, idx+2, "60"); // only when pattern has seconds
          }
          else { // input with two leap seconds
            lsecs = -1;
            tt--;
            for (int ii =trueSecEnd.length-1; ii >= 0; ii--) {
              if (trueSecEnd[ii] < tt) break;
              if (trueSecEnd[ii] >= tt && trueSecStart[ii] <= tt) {
                lsecs = leapSeconds[ii];  // closest leap
                break;
              }
            }
            tStr = new StringBuffer(EpochTime.epochToString(trueEpochTime - lsecs - 2, pattern, zone));
            if (idx >= 0 && tStr.substring(idx, idx+2).equals("59")) tStr.replace(idx, idx+2, "61"); // only when pattern has seconds
          } 
        }
        return tStr.toString(); 
  }

  public static String trueToPST(double dateTime, String pattern ) {
    return trueToString(dateTime, pattern, "PST");
  }

  public static String trueToPST(double dateTime) {
    return trueToString(dateTime, EpochTime.DEFAULT_FORMAT, "PST");
  }

  public static String trueToDefaultZone(double dateTime) {
    return trueToDefaultZone(dateTime, EpochTime.DEFAULT_FORMAT);
  }

  public static String trueToDefaultZone(double dateTime, String pattern) {
    return trueToString(dateTime, pattern, Calendar.getInstance().getTimeZone().getID());
  }

  public static final double stringToTrue(String dateStr) {
      return stringToTrue(dateStr, EpochTime.DEFAULT_FORMAT);
  }
  public static final double stringToTrue(String dateStr, String pattern) {
      StringBuffer tStr = new StringBuffer(dateStr);
      int idx = pattern.indexOf("ss");
      int leap = 0;
      if (idx >= 0 ) { // only when input has possible leap seconds in format
          if (tStr.substring(idx, idx+2).equals("60")) {
              tStr.replace(idx, idx+2, "59"); // also done by stringToEpoch call below
              leap = 1; // input with one leap second
          }
          else if (tStr.substring(idx, idx+2).equals("61")) {
              tStr.replace(idx, idx+2, "59"); // also done by stringToEpoch call below
              leap = 2; // input with two leap seconds
          }
      }
      return (nominalToTrue(EpochTime.stringToEpoch(tStr.toString(), pattern)) + leap);
  }

  public static java.util.Date trueToDate(double trueEpochTime) {
    return EpochTime.epochToDate(trueToNominal(trueEpochTime));
  }

  public static double dateToTrue(java.util.Date date) {
    return nominalToTrue(EpochTime.dateToEpoch(date));
  }

  /* 
  public static void main(String[] args) {

    //org.trinet.jasi.TestDataSource.create("makalu");
    //LeapSeconds.loadArraysFromDataSource(); // do this after creating DataSource

    // 915148820 true  and 915148799 nominal at Dec 31, 1998 23:59:59 
    long start = 915148818;
    double tt = 0;
    double tt2 = 0;
    String dStr = null;
    for (long i = start; i < start + 6; i++) {
      dStr = LeapSeconds.trueToString((double)i);
      System.out.print(i + " =true: " + dStr);
      System.out.print(" :true= " + LeapSeconds.stringToTrue(dStr));
      tt2 = EpochTime.stringToEpoch(dStr);
      tt = LeapSeconds.trueToNominal((double)i);
      System.out.println(" stringToEpoch tt2= " + tt2 + " trueToNominal tt= " + tt);
      System.out.println("tt2 - tt = " + (tt2-tt));
      System.out.println("epoch2Str(trueToNominal) =       " + EpochTime.epochToString(tt));
      System.out.println("epoch2Str(str2epoch(true2str)) = " + EpochTime.epochToString(tt2) + "\n");
      
    }
    //
    // Test nominal (epoch) to leap seconds and back again.
    //
    Format df = new Format("%12.3f");
    double nominal = 1072102160.123;
    double leap = LeapSeconds.nominalToTrue(nominal);
    System.out.println("\nmominalToTrue(double) test should have 22.0 leap seconds:");
    System.out.println("nominalToTrue("+df.form(nominal)+") "+df.form(leap));
    System.out.println("trueToNominal("+df.form(leap)+") "+df.form(LeapSeconds.trueToNominal(leap)));
    nominal = 10.123;
    leap = LeapSeconds.nominalToTrue(nominal);
    System.out.println("\nRelative time seconds nominal=10.123 should be leap= 10.123");
    System.out.println("nominalToTrue("+df.form(nominal)+") "+df.form(leap));

      int yr   = 1997;
      int jday = 181;
      int hr   = 23;
      int mn   = 59;
      int sec  = 60;
      int frac = 1507;

      // DateTime class keeps precision to nanoseconds (0.000001),
      // native Java time only good to milliseconds (0.001)
      frac = Math.round((float)frac/10.f);
      StringBuffer sb = new StringBuffer(32);
      Format d4 = new Format("%04d");
      Format d3 = new Format("%03d");
      Format d2 = new Format("%02d");
      sb.append(d4.form(yr)).append(" ");
      sb.append(d3.form(jday)).append(" ");
      sb.append(d2.form(hr)).append(":");
      sb.append(d2.form(mn)).append(":");
      sb.append(d2.form(sec)).append(".");
      sb.append(d3.form(frac));
      //Concatenate.format(sb,(long)yr,4,4).append(" ");
      //Concatenate.format(sb,(long)jday,3,3).append(" ");
      //Concatenate.format(sb,(long)hr,2,2).append(":");
      //Concatenate.format(sb,(long)mn,2,2).append(":");
      //Concatenate.format(sb,(long)sec,2,2).append(".");
      //Concatenate.format(sb,(long)frac,3,3);
      tt = LeapSeconds.stringToTrue(sb.toString(), "yyyy DDD HH:mm:ss.SSS");
      System.out.println("stringToTrue: " + sb.toString() + " = " + tt);
      System.out.println("stringToTrue: " + "1997 181 23:59:60.151 = " + tt);
      System.out.println("trueToString: " + LeapSeconds.trueToString(tt));

      System.out.println("trueLeapString: " + LeapSeconds.trueToString(867715220.151));
      System.out.println("trueLeapString: " + LeapSeconds.trueToString(867715220.151, "yyyy DDD HH:mm:ss.SSS"));

      System.out.println("trueLeapString 2009/01/01: " + LeapSeconds.trueToString(1230768022., "yyyy DDD HH:mm:ss.SSS"));
      System.out.println("trueLeapString 2009/01/01: " + LeapSeconds.trueToString(1230768023., "yyyy DDD HH:mm:ss.SSS"));
      System.out.println("trueLeapString 2009/01/01: " + LeapSeconds.trueToString(1230768024., "yyyy DDD HH:mm:ss.SSS"));
      System.out.println("trueLeapString 2009/01/01: " + LeapSeconds.stringToTrue("2009 01 00:00:00.000", "yyyy DDD HH:mm:ss.SSS"));
  }
  */

}
