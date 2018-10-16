/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.utility;

import edu.sc.seis.TauP.SacTimeSeries;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.MathContext;
import java.util.Collections;

/**
 * This class is for HVO data which was extracted into SAC files in 12 hour time sections. This is
 * problematic because the sample rate on the digitizers is odd and not constant. This routine
 * attempts to catch these cases, and use the succeeding file first samples and the overlapping data
 * in this file, to get an accurate time for a sample late in the day, and then compute the average
 * data rate for the 12 hour period. If the data cannot be matched up (usually because the overlap
 * is missing on the next day), then the average rate for all of the days in the run is applied. The
 * other correction of the DST digitizer is we know these TraceBufs are exactly 200 samples. When
 * there is a gap, the Earthworm code made a decision about which "slot" to put the next sample and
 * it often guessed wrong (generally creating a 201 sample gap). When this happens the data for the
 * remainder of the file needs to be moved down one sample.
 * <br>
 * This routine is invoked from CWBQuery via the -hvoscan arguments. it is not normally execute
 * directly.
 * <br>
 * Notes: To get a accurate time on DST channels having data from the next day is needed so the last
 * file run is always at the average rate. It is better to always run a large group of days (10 -30)
 * and to make the next run, to include the last day from the prior one. This can be accomplished
 * with the regular expression for matching days used to select the stations to run.
 * <br>
 * The TimeQuality will be set : 90 - if this is a reftek or non-DST file and it passed the
 * algorithm (not operational now) 80 - if this is a Reftek or other non-DST file 75 - this is a DST
 * and the algorithm worked and the rate was calculated from the day 30+navg - This is a DST file
 * where the algorithm did not work, rate is the average rate for the run, navg is number averaged
 * 10 - This is a DST and we had no viable average so the rate is sac.npts/sac.e
 *
 * <PRE>
 * switch argument  description
 * -hvoscan         Must be present to select this code to do the conversion
 * -sac2ms          Optionally might be present to indicate sac2ms conversion is intended.
 * -d     path      The path to where the directories with times are located
 * -c     match     Normally the station and channel which matches files for one channel (eg 'BYL.HHE')
 * -re    Regexp    This regular expression is used to match directory names with dates (201002.*|20100131.*)
 * -nooutput        This is a test run, do not write miniSEED files
 * -fn    NN        Force the network code to NN
 * -fc    CCC       FOrce the channel code to CCC
 * -fs    SSSSS     Force the station code to SSSSS
 * -fl    LL        Force the location code to LL
 * -fr    rate      Set the digitizing rate to rate
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class HVOScan {

  static DecimalFormat di2 = new DecimalFormat("00");
  static DecimalFormat di3 = new DecimalFormat("000");
  static DecimalFormat df6 = new DecimalFormat("0.000000");
  static DecimalFormat df3 = new DecimalFormat("0.000");
  static String overNet = null;
  static String overChan = null;
  static String overLoc = null;
  static String overStat = null;
  static String overSeedname = null;
  static double overRate = -1;
  static boolean nooutput;
  static boolean dbg;

  public static void main(String[] args) {
    String directory = ".";
    String channelMatch = null;
    String directoryRE = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-hvoscan":
          ;
          break;
        //else if(directory == null) directory=args[i];
        case "-c":
          channelMatch = args[i + 1];
          i++;
          break;
        case "-re":
          directoryRE = args[i + 1];
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-sac2ms":
          ;
          break;
        case "-nooutput":
          nooutput = true;
          break;
        case "-fn":
          overNet = args[i + 1];
          i++;
          break;
        case "-fc":
          overChan = args[i + 1];
          i++;
          break;
        case "-fl":
          overLoc = args[i + 1];
          i++;
          break;
        case "-fs":
          overStat = args[i + 1];
          i++;
          break;
        case "-fr":
          overRate = Double.parseDouble(args[i + 1]);
          i++;
          break;
        case "-d":
          directory = args[i + 1];
          i++;
          break;
      }
    }
    double avgRate = 0;       // contains sums of rates computed by the algorithm
    double navg = 0;          // Number of rates summed in avgRate
    int lastMissed = 0;
    // These contain the summary of gaps (string end ending offsets, etc
    int[] missStart = new int[10000];
    int[] missEnd = new int[10000];
    int[] lastMissStart = new int[10000];
    int[] lastMissEnd = new int[10000];
    StringBuilder s = new StringBuilder(200);
    // these are one per SAC file containing the SAC, their time, filenames, and processing status
    ArrayList<SacTimeSeries> sacs = new ArrayList<>(200);
    ArrayList<String> filenames = new ArrayList<>(200);
    ArrayList<String> done = new ArrayList<>(200);
    ArrayList<GregorianCalendar> gcs = new ArrayList<>(200);

    File dir = new File(directory);       // This is the directory that contains directories like 20100103_1200000
    if (dir.isDirectory()) {
      File[] files = dir.listFiles();    // This is a list of directory filenames like 20100103_12000000
      for (File file : files) {
        // For each date directory
        //for(int j=0; j<files.length; j++) Util.prt("Dir bef="+files[j].getAbsolutePath());
        Arrays.sort(files);
        //for(int j=0; j<files.length; j++) Util.prt("Dir aft="+files[j].getAbsolutePath());
        boolean dirmatch = true;
        if (directoryRE != null) {
          dirmatch = file.getName().matches(directoryRE); // does it match
        }
        if (file.isDirectory() && dirmatch) {
          // This should be a directory with the time 20100103_000000 with many files in it
          File[] files2 = file.listFiles(); // List of files from the date directory with filenames like BYL.HHE.HV.--
          for (File files21 : files2) {
            if (files21.isDirectory()) {
              // we are not expectind directories here, just SAC files
              Util.prt("Expecting file got directory " + files21.getAbsolutePath());
            } else if (!files21.getAbsolutePath().contains(channelMatch)) {
              // not the right channel
            } else {
              // Its the right channel
              // Create a SAC file and add it to the list of SAC files
              SacTimeSeries sac = new SacTimeSeries();
              try {
                sac.read(files21);
              } catch (IOException e) {
                Util.prt("error reading file e=" + e + " file=" + files21);
              }
              sacs.add(sac);
              filenames.add(files21.getAbsolutePath());
              // Make a starting data GregorianCalendar from the bits and pieces in the SAC header
              GregorianCalendar gc = new GregorianCalendar();
              int[] ymd = SeedUtil.ymd_from_doy(sac.nzyear, sac.nzjday);
              gc.set(sac.nzyear, ymd[1] - 1, ymd[2], sac.nzhour, sac.nzmin, sac.nzsec);
              gc.setTimeInMillis(gc.getTimeInMillis() / 1000L * 1000L + sac.nzmsec);
              gcs.add(gc);
              double sacrate = roundToSig(1. / sac.delta, 8);
              double rate1 = roundToSig(sac.npts / sac.e, 8);
              int noval = 0;
              for (int ii = 0; ii < sac.npts; ii++) {
                if (sac.y[ii] == -12345) {
                  noval++;
                }
              }
              Util.prt("gc=" + Util.ascdatetime2(gc) + " " + sac.nzyear + "/" + ymd[1] + "/" + ymd[2] + " " + sac.nzjday + " "
                      + sac.nzhour + ":" + sac.nzmin + ":" + sac.nzsec + "." + sac.nzmsec
                      + " npts=" + sac.npts + " #gap=" + noval + " e=" + sac.e + " sacrate=" + sacrate + "/" + rate1 + " " + files21.getAbsolutePath());
            }
          }
        }
      }

      // All of the SAC files have been read, loop through them.
      Collections.sort(sacs);
      for (int j = 0; j < sacs.size(); j++) {
        Util.prt(j + " " + sacs.get(j).toString());
      }
      Util.prt("StartDate,EndDate,elapse,nptm,noval,sac.e,sac.npts,prefRate,compRate,sacRate,npt/e,avgRate,lastMissed,filename");
      for (int isac = 0; isac < sacs.size(); isac++) {
        SacTimeSeries sac = sacs.get(isac);
        SacTimeSeries sacold = null;
        if (isac > 0) {
          sacold = sacs.get(isac - 1);
        }
        int nmiss = 0;
        // Compute the rate per SAC, if its > 100.1 then it must be a DST
        double sacrate = roundToSig(1. / sac.delta, 8);
        // If this file is a Reftek or other time locked file, just convert it.
        if (sacrate < 100.05) {
          makeMS(sac, sacrate, filenames.get(isac), 80);    // This must be a reftek!
          continue;
        }
        double rate1 = roundToSig(sac.npts / sac.e, 10);
        double ratedelta = roundToSig(1. / sac.delta, 10);
        for (int ii = 0; ii < 10000; ii++) {
          missStart[ii] = 0;
          missEnd[ii] = 0;
        }
        int missoff = 0;    // number of missing sections
        GregorianCalendar gc = gcs.get(isac);// if the SAC file does not begin at 23:59:58 or 11:59:58
        // Look for gaps to fix in last one
        for (int k = 0; k < sac.npts; k++) {
          if (sac.y[k] == -12345) {
            if (missStart[missoff] == 0) {
              missStart[missoff] = k;
              missEnd[missoff] = k;
            } else if (missEnd[missoff] == k - 1) {
              missEnd[missoff] = k;
            } else {
              missoff++;
            }
            nmiss++;/*Util.prt("missing k="+k);*/
          } else if (missEnd[missoff] != 0) {
            // If its just some data, its not really a gap
            if (missEnd[missoff] - missStart[missoff] < 10) {
              missEnd[missoff] = 0;
              missStart[missoff] = 0;
            } else {
              for (int kk = missStart[missoff]; kk <= missEnd[missoff]; kk++) {
                sac.y[kk] = 2130000000;
              }
              missoff++;
            }
          }
        }
        if (missoff > 0) // If this is a DST, fix up any gaps which are too small
        {
          fixGapsDST(sac, missStart, missEnd, missoff);
        }
        double rate;
        if (isac > 0) {    // cannot run algorith on first file
          GregorianCalendar gc2 = gcs.get(isac - 1);    // Time of last start point
          double ratecomp;
          double elapsed;
          int j;
          int i = -1;
          // For this algorith to work, this file must start just before the 12 hour marks
          if ((gc.get(Calendar.HOUR_OF_DAY) == 11 || gc.get(Calendar.HOUR_OF_DAY) == 23)
                  && gc.get(Calendar.MINUTE) == 59
                  && (gc.get(Calendar.SECOND) == 58 || gc.get(Calendar.SECOND) == 59)) {

            // Since it overlaps the beginning of the next one, we have some samples to check to get the right number of samples per time
            double[] oldy = sacs.get(isac - 1).y;
            boolean found = false;
            for (j = 0; j < 400; j++) {
              for (i = oldy.length - 12800; i < oldy.length - 8; i++) {
                if (Math.abs(sac.y[j] - oldy[i]) < 0.1
                        && Math.abs(sac.y[j + 1] - oldy[i + 1]) < 0.1
                        && Math.abs(sac.y[j + 2] - oldy[i + 2]) < 0.1
                        && Math.abs(sac.y[j + 3] - oldy[i + 3]) < 0.1
                        && Math.abs(sac.y[j + 4] - oldy[i + 4]) < 0.1
                        && Math.abs(sac.y[j + 5] - oldy[i + 5]) < 0.1
                        && Math.abs(sac.y[j + 6] - oldy[i + 6]) < 0.1
                        && Math.abs(sac.y[j + 7] - oldy[i + 7]) < 0.1
                        && sac.y[j] != 2130000000
                        && sac.y[j + 1] != 2130000000
                        && sac.y[j + 4] != 2130000000) {
                  found = true;
                  break;
                }
              }
              if (found) {
                break;
              }
            }
            // So i is offset in new array that matches j in the old array
            if (found) {
              done.add(isac - 1, "Done");         // mark algorithm success
              long matchtime = gc.getTimeInMillis() + (long) (j * sac.delta * 1000 + 0.5); // time of match near beginning of this buffer
              elapsed = (matchtime - gc2.getTimeInMillis()) / 1000.;      // elapse time since beginning of last to match
              ratecomp = roundToSig(i / elapsed, 10);                      // sample rate computed
              if (dbg) {
                for (int k = 0; k < 12; k++) {
                  Util.prt("j=" + (j + k) + " i=" + (i + k) + " " + sac.y[j + k] + " " + oldy[i + k]);
                }
                Util.prt(Util.ascdatetime2(gc2) + " i=" + i + " j=" + j + " elapse=" + elapsed + " rate comp=" + ratecomp + " rate 1/delta=" + ratedelta
                        + " rate (npt/e)=" + rate1 + " npt=" + sacold.npts + " e=" + sacold.e);
              }
              int clkqual;
              rate = ratecomp;      // Assume this will be the rate
              clkqual = 75;           // normal quality for DSP with algorithm
              if (ratecomp < 100.1 && Math.abs(ratecomp - 100.) < 0.001) {
                clkqual = 90;// if not a DST and algorithm 
              }
              if (s.length() > 0) {
                s.delete(0, s.length());     // create a log line suitable for a spread sheet
              }
              int noval = 0;
              for (int ii = 0; ii < sacs.get(isac - 1).npts; ii++) {
                if (oldy[ii] == 2130000000) {
                  noval++;
                }
              }
              s.append(Util.ascdatetime2(gc2)).append(",").append(Util.ascdatetime2(gc)).append(",").
                      append(df3.format(elapsed)).append(",").
                      append(i).append(",").append(noval).append(",").
                      append(df3.format(sacs.get(isac - 1).e)).append(",").
                      append(df3.format(sacs.get(isac - 1).npts)).append(",").
                      append(rate).append(","). // preferred rate
                      append(ratecomp).append(","). // computed rate if matchfound
                      append(sacrate).append(","). // Rate of this sac section
                      append(roundToSig(sacold.npts / sacold.e, 10)).append(","). // npts/e rate old section
                      append(navg > 0 ? roundToSig(avgRate / navg, 10) : "NaN").append(",").
                      append(lastMissed).append(",").
                      append(clkqual).append(",").
                      append(filenames.get(isac - 1));

              for (int ii = 0; ii < lastMissed; ii++) {
                s.append(",").append(lastMissEnd[ii] - lastMissStart[ii] + 1);
              }
              Util.prt(s);
              // accumulate the average
              avgRate += rate;
              navg++;
              makeMS(sacold, rate, filenames.get(isac - 1), clkqual);    // compress the last sac file with computed rate
            } else {
              done.add(isac - 1, null);      // could not line upd data, mark algorithm failed
            }
          } else {
            done.add(isac - 1, null);        // This file does not overlap last, mark algorithm failed
          }
        }

        lastMissed = missoff;
        for (int ii = 0; ii < missoff; ii++) {
          lastMissStart[ii] = missStart[ii];
          lastMissEnd[ii] = missEnd[ii];
        }
      }   // end of loop on all sac files

      // For all the ones with no matches, use the average rate as our best guess
      if (done.size() > 0) {
        done.add(sacs.size() - 1, null);  // add the last one if this is a DST
      }
      for (int i = 0; i < done.size(); i++) {
        if (done.get(i) == null) {
          double rate;
          int clkqual;
          if (navg > 0) {
            rate = roundToSig(avgRate / navg, 10);
            clkqual = 30 + (int) navg;
          } else {
            rate = roundToSig(sacs.get(i).npts / sacs.get(i).e, 10);
            clkqual = 10;
          }
          if (s.length() > 0) {
            s.delete(0, s.length());
          }
          s.append(Util.ascdatetime2(gcs.get(i))).append(",").append(i == done.size() - 1 ? "None" : Util.ascdatetime2(gcs.get(i + 1))).append(",").
                  append("0,").
                  append(rate).append(","). // preferred rate
                  append("-1.0000,"). // computed rate if matchfound
                  append(roundToSig(1. / sacs.get(i).delta, 10)).append(","). // Rate of this sac section
                  append(roundToSig(sacs.get(i).npts / sacs.get(i).e, 10)).append(","). // npts/e rate old section
                  append(navg > 0 ? roundToSig(avgRate / navg, 10) : "NaN").append(",").
                  append("-1,").
                  append(clkqual).append(",").
                  append(filenames.get(i));
          Util.prt(s);
          makeMS(sacs.get(i), rate, filenames.get(i), clkqual);
        }
      }
    } else {
      Util.prt(directory + " is not a path to a directory containing directories of SAC files!");
    }
    Util.prt("Done file avg rate=" + (avgRate / navg));
    System.exit(0);
  }

  public static double roundToSig(double num, int sigfigs) {
    return new BigDecimal(num).round(new MathContext(sigfigs, RoundingMode.HALF_EVEN)).doubleValue();
  }

  static private void fixGapsDST(SacTimeSeries sac, int[] missStart, int[] missEnd, int nmiss) {
    if (dbg) {
      Util.prt("Adjust start npts=" + sac.npts + " nmiss=" + nmiss);
    }
    if (dbg) {
      for (int i = 0; i < nmiss; i++) {
        Util.prt("S[" + missStart[i] + "-" + missEnd[i] + "]");
      }
    }
    for (int ii = nmiss - 1; ii >= 0; ii--) {
      if ((missEnd[ii] - missStart[ii] + 1) % 200 != 0 && missEnd[ii] - missStart[ii] > 180) {
        int shift = (missEnd[ii] - missStart[ii] + 1) % 200;
        if (dbg) {
          Util.prt("npts=" + sac.npts + " start=" + missStart[ii] + " end=" + missEnd[ii] + " shift=" + shift);
        }
        if (shift >= 180) {
          // We need to put an extra point in the gap and move the array down, add one point
          shift = 200 - shift;
          if (dbg) {
            Util.prt("*** Gap shift up npts=" + sac.npts + " shift=" + shift + " gap[" + missStart[ii] + "-" + missEnd[ii] + "] move "
                    + (missEnd[ii] + 1) + " to " + (missEnd[ii] + shift + 1));
          }
          double[] tmp = new double[sac.npts + shift];
          System.arraycopy(sac.y, 0, tmp, 0, missEnd[ii] + 1);
          System.arraycopy(sac.y, missEnd[ii] + 1, tmp, missEnd[ii] + shift + 1, sac.npts - missEnd[ii] - 2);
          for (int i = 0; i < shift; i++) {
            tmp[missEnd[ii] + i + 1] = 2147000000;
          }
          sac.y = tmp;
          sac.npts += shift;  // Adjust number of points
          for (int i = ii; i < nmiss; i++) {
            if (i != ii) {
              missStart[i] += shift;
            }
            missEnd[i] += shift;
          }
        } else if (shift == 1) {    // we need to move it back one slot
          if (dbg) {
            Util.prt("*** Gap shift down npts=" + sac.npts + " shift=" + shift + " gap[" + missStart[ii] + "-" + missEnd[ii] + "] move "
                    + (missEnd[ii] + 1) + " to " + (missEnd[ii] - shift + 1) + " nmove=" + (sac.npts - missEnd[ii] - 1));
          }

          System.arraycopy(sac.y, missEnd[ii] + 1, sac.y, missEnd[ii] - shift + 1, sac.npts - missEnd[ii] - 1);
          sac.npts -= shift;
          for (int i = 0; i < shift; i++) {
            sac.y[sac.npts + i] = 2147000000;
          }
          for (int i = ii; i < nmiss; i++) {
            if (i != ii) {
              missStart[i] -= shift;
            }
            missEnd[i] -= shift;
          }
        }

      }
    }
    if (dbg) {
      for (int i = 0; i < nmiss; i++) {
        Util.prt("D[" + missStart[i] + "-" + missEnd[i] + "]");
      }
    }
    if (dbg) {
      Util.prt("Adjustments done npts=" + sac.npts);
    }
  }

  /**
   * This converts a time series from SAC format to MiniSEED using a precisely computed rate so that
   * time is carefully spread over the long interval represented by the time series. This was
   * written for HVOs waveman2disk files which were 12 hour long sac files with a variable rate. The
   * successive 12 hour intervals were used to compute the average rate, ant the time series in hear
   * are converted to time should come out correct.
   *
   * @param sacts The Sac timeseries
   * @param rate The Rate to use for converting this time series
   * @param inFile The input file name, used to create the output filename
   * @param clkqual 0-100 clock quality to put in blockette 1001
   */
  static void makeMS(SacTimeSeries sacts, double rate, String inFile, int clkqual) {
    String seedname;
    if (overNet != null) {
      sacts.knetwk = overNet;
    }
    if (overChan != null) {
      sacts.kcmpnm = overChan;
    }
    if (overLoc != null) {
      sacts.khole = overChan;
    }
    if (overStat != null) {
      sacts.kstnm = overChan;
    }
    if (overSeedname != null) {
      seedname = overSeedname.replaceAll("-", " ");
    } else {
      seedname = (sacts.knetwk + "  ").substring(0, 2) + (sacts.kstnm + "     ").substring(0, 5)
              + (sacts.kcmpnm + "   ").substring(0, 3) + (sacts.khole.trim().equals("-12345") ? "  " : (sacts.khole + "  ").substring(0, 2));
    }
    // Create the start time in a more usuable format
    GregorianCalendar gc = new GregorianCalendar();
    //int is = inFile.lastIndexOf("/");
    //is = inFile.substring(0,is).lastIndexOf("/");
    //if(is <0) is=0;
    //String [] date = inFile.substring(is+1,is+16).split("_");
    //int thisYear = Integer.parseInt(date[0].substring(0,4));
    //int mo = Integer.parseInt(date[0].substring(4,6));
    //int dy = Integer.parseInt(date[0].substring(6,8));
    //int thisDOY = SeedUtil.doy_from_ymd(thisYear, mo, dy);
    //int thisEnds;
    //if(date[1].startsWith("00")) thisEnds =12*3600000;
    //else thisEnds =24*3600000;

    int[] ymd = SeedUtil.ymd_from_doy(sacts.nzyear, sacts.nzjday);
    gc.set(sacts.nzyear, ymd[1] - 1, ymd[2], sacts.nzhour, sacts.nzmin, sacts.nzsec);
    gc.setTimeInMillis(gc.getTimeInMillis() / 1000L * 1000L + sacts.nzmsec);
    long starttime = gc.getTimeInMillis();
    int[] data = new int[1000];
    MakeRawToMiniseed maker = new MakeRawToMiniseed();
    if (dbg) {
      RawToMiniSeed.setDebug(true);
    }
    seedname = seedname.replaceAll("-", " ");
    if (dbg) {
      RawToMiniSeed.setDebugChannel(seedname);
    }
    // We do not want to process out of the 12 hour period, so calulate the first index and last to use
    long start = ((long) ((2 * starttime + (sacts.y.length) / rate * 1000) / 2.)) / 43200000L * 43200000L;// time at beginning of period
    if (dbg) {
      Util.prt("Start=" + Util.ascdatetime2(start));
    }
    int i = (int) ((start - starttime + 1000. / rate - 0.1) / 1000. * rate);  // first sample on the new day
    int maxi = (int) ((43200000L + start - starttime) / 1000. * rate);
    if (dbg) {
      Util.prt("i=" + i + " maxi=" + maxi + " y.len=" + sacts.y.length + " " + Util.ascdatetime2(starttime + (long) (Math.round(i / rate * 1000))) + " "
              + Util.ascdatetime2(starttime + (long) (Math.round(maxi / rate * 1000.))) + " rt=" + rate);
    }

    if (i < 0) {
      i = 0;
      Util.prt("**** Odd start time! is after interval " + Util.ascdatetime2(starttime));
    } else if (dbg) {
      for (int j = Math.max(0, i - 5); j < i + 20; j++) {
        Util.prt(Util.ascdatetime2(starttime + (long) (j / rate * 1000)) + " j=" + j + " " + sacts.y[j]);
      }
      for (int j = maxi; j < sacts.y.length; j++) {
        Util.prt(Util.ascdatetime2(starttime + (long) (j / rate * 1000)) + " j=" + j + " " + sacts.y[j]);
      }
    }
    int k = 0;
    int starti = i;
    boolean inGap = false;
    boolean doit = false;
    int ncomp = 0;
    int nskip = 0;
    while (i <= Math.min(maxi, sacts.y.length - 1)) {
      if (sacts.y[i] >= 2130000000) {
        if (!inGap) {  // Start of gap, force data out
          doit = true;
          inGap = true;
        } else {
          i++;
          nskip++;
          continue;     // point to next and more on
        }
      } else {          // Not a gap value
        if (inGap) {   // If we are in a gap, start things up again
          inGap = false;
          k = 0;
          doit = false;
        }
      }
      if (k == 0) {
        starti = i; // If this is the beginning of a data section, save the offset for time calculation
      }
      if (!doit) {
        data[k++] = (int) (sacts.y[i] + 0.4999);// convert the data value
        i++;
      }
      if (doit || k >= 1000 || i >= Math.min(maxi + 1, sacts.y.length)) {
        gc.setTimeInMillis(starttime + Math.round(starti / rate * 1000.)); // time of this offset
        long usec = starttime * 1000 + Math.round(starti / rate * 1000000.);
        int year = gc.get(Calendar.YEAR);
        int doy = gc.get(Calendar.DAY_OF_YEAR);
        int sec = (int) (usec % 86400000000L / 1000000);  // milliseconds into this day
        usec = usec % 1000000;                      // microseconds left over

        if (k > 0) {
          ncomp += k;
          maker.loadTSIncrement(seedname, k, data, year, doy, sec, (int) usec, rate, 0, 0, 0, clkqual);
          if (inGap) {
            maker.flush();
          }
        }
        k = 0;
        doit = false;
      }
    }
    if (!nooutput) {
      try {
        maker.flush();
        String filename = seedname.replaceAll(" ", "_") + "_" + sacts.nzyear + "_" + di3.format(sacts.nzjday) + "_"
                + di2.format(sacts.nzhour) + di2.format(sacts.nzmin) + di2.format(sacts.nzsec) + ".ms";
        Util.prt("#blks=" + maker.getBlocks().size() + " rate=" + rate + " ns=" + sacts.npts + " " + sacts.y.length + " #comp=" + ncomp + " #skip=" + nskip);
        int ns = 0;
        for (i = 0; i < maker.getBlocks().size(); i++) {
          ns += maker.getBlocks().get(i).getNsamp();
        }
        Util.prt("ns=" + ns);
        maker.writeToDisk(filename);
        maker.close();
      } catch (IOException e) {
        Util.prt("IO error writing file " + seedname + ".ms");
        e.printStackTrace();
      }
    }
  }
}
