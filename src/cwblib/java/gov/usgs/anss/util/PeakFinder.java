/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.*;
import java.util.*;
import java.text.*;

/**
 * This class was written for H. Benz to find peaks in correlation outputs.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class PeakFinder {

  private static final DecimalFormat df3 = new DecimalFormat("0.000");

  /**
   * This is a static function to find "picks" from an array of correlations from the correlation
   * picker data. The routine looks for data points greater than a minimum value for a correlation,
   * and then looks inside of its window for the maximum correlation within that time frame. The
   * picks are returned as an array of indices into the data array and the number of such picks is
   * the return value. Since there is a history, it is possible that the index of a returned pick is
   * negative relative to the first data point in history.
   *
   * <p>
   * Since the history is being maintained separately this routine has a performance penalty in that
   * it has to create the full array (history + new data) so that it can be computed. This insures a
   * copy of every data point in data has to be done.
   *
   * @param data the correlation array with new correlation data
   * @param nsamp The number of data points in the array
   * @param width The width of the minimum window size (array elements before re-triggering is
   * possible)
   * @param minCorrelation The minimum correlation that might be considered as a real match
   * @param hist A history array at least maximum nsamp in data + width in size assumes next call
   * will contain correlation of data[nsamp+1]
   * @param picks The array of pick indices returned, must be big enough to contain all the picks
   * (data.length/width+1)
   * @param err A String builder with information about the error.
   * @return The number of elements in picks[]
   */
  public static int findPicks(double[] data, int nsamp, int width, double minCorrelation,
          double[] hist, int[] picks, StringBuilder err) {
    int npick = 0;
    if (hist.length < width + nsamp) {
      if (err.length() > 0) {
        err.delete(0, err.length());
      }
      err.append("*** the history array is too small - needs to be at least ").append(width + nsamp);
      return -1;
    }
    System.arraycopy(data, 0, hist, width, nsamp);
    int np = findPicks(hist, width, nsamp, width, minCorrelation, picks, err);  // Put new data into hist after width saved samples
    System.arraycopy(hist, nsamp, hist, 0, width);          // wave width samples to beginning of hist for next call
    for (int i = 0; i < np; i++) {
      picks[i] = picks[i] - width;    // correct the offsets to reflect the separate history
    }
    return np;
  }

  /**
   * This is a static function to find "picks" from an array of correlations from the correlation
   * picker data. The routine looks for data points greater than a minimum value for a correlation,
   * and then looks inside of its window for the maximum correlation within that time frame. The
   * picks are returned as an array of indices into the data array and the number of such picks is
   * the return value.
   * <p>
   * This is preferred method since it saves having to copy the data around and maintain a large
   * history buffer.
   * <p>
   * If just starting (no history), you an set offset=0 and add the new data. In this case be aware
   * that no pick can occur before width/2
   * <p>
   * typical call will be if there are width data points of old data from last call at beginning of
   * the data array and nsamp of new data starting at offset width have been added :
   * <p>
   * int npick = findPicks(data,width, nsamp,width,minCorrelation, picks, sb);
   *
   * @param data the correlation array with new data starting at offset+nwidth
   * @param offset the offset into the data array that contains the first new data point
   * @param nsamp The number of new data points in the array (first new point at offset)
   * @param width The width of the minimum window size (array elements before re-triggering is
   * possible)
   * @param minCorrelation The minimum correlation that might be considered as a real match
   * @param picks The array of pick indices returned, must be big enough to contain all the picks
   * (data.length/width+1)
   * @param err A String builder with information about the error.
   * @return The number of elements in picks[], if less than 0 then an error is being reported
   */
  public static int findPicks(double[] data, int offset, int nsamp, int width, 
          double minCorrelation, int[] picks, StringBuilder err) {
    int npick = 0;
    if (offset < width) {
      throw new IllegalArgumentException("Offset must be >= width offset=" + offset + " width=" + width);
    }
    for (int i = Math.max(0, offset - width); i < offset + nsamp; i++) {
      if (data[i] >= minCorrelation) {
        if (npick >= picks.length - 1) {
          if (err.length() > 0) {
            err.delete(0, err.length());
          }
          err.append("*** the picks array is too small - needs to be at least ").append(npick * 2);
          return -2;
        }
        picks[npick++] = i;
      }
    }
    // so we have a list of places above the minimum, now winnow these to the maxes within one window width
    int np = 0;                           // surviving pick counter
    int i = 0;                            // Offset in picks being considered
    int lastpeak = 0;
    while (i < npick) {
      double max = data[picks[i]];        // track the max across the window
      picks[np] = picks[i];                 // set the index of the current max
      int lastk = i;
      for (int k = Math.max(0, i - width / 2); k < Math.min(npick, i + width / 2); k++) {
        if (picks[k] - picks[np] < -width / 2) {
          continue; // too far away before
        }
        if (picks[k] - picks[np] > width / 2) {
          break;        // we are too far away
        }
        if (data[picks[k]] > max) {
          picks[np] = picks[k];
          max = data[picks[k]];
        }
        lastk = k;
      }
      i = lastk + 1;
      if (picks[np] < offset + nsamp - width / 2 && picks[np] >= offset - width / 2 && picks[np] - lastpeak >= width / 2) {
        lastpeak = picks[np];
        np++;
      }
    }
    return np;
  }

  /**
   * test routine - reads in some data Harley sent from station MOKD on day 348
   *
   * @param args
   */
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder(1000);
    try {
      BufferedReader in = new BufferedReader(new FileReader("MOKD_2013_248stack.dat"));
      String line = null;
      int totpick = 0;
      boolean dbg = false;
      int maxdata = 10240;
      long runtime = 0;
      int width = 100;
      double[] data = new double[maxdata + width];
      double[] hist = new double[maxdata + width];
      double minCorrellation = 0.4;
      int[] picks = new int[maxdata / width + 1];
      GregorianCalendar g = new GregorianCalendar();
      g.set(2013, 8, 5, 0, 0, 0);
      g.setTimeInMillis(g.getTimeInMillis() / 1000 * 1000);
      long base = g.getTimeInMillis();
      int off = 0;
      for (;;) {
        int i = 0;
        double max = 0;
        int maxi = 0;
        while (i < maxdata) {
          line = in.readLine();
          if (line == null) {
            break;
          }
          if (line.charAt(0) == '#') {
            continue;
          }
          if (Character.isAlphabetic(line.charAt(0))) {
            continue;
          }
          String[] parts = line.split("\\s");
          data[i] = Double.parseDouble(parts[1]);
          if (data[i] > max) {
            maxi = i;
            max = data[i];
          }
          i++;
        }
        if (line == null) {
          break;
        }
        long start = System.currentTimeMillis();
        long time = base + off * 10;
        if (max > minCorrellation || dbg) {
          System.err.println(Util.ascdate(time) + " " + Util.asctime2(time) + " off=" + off + (max > minCorrellation ? "*" : " ") + "max=" + max + "@" + maxi);
        }
        if (max > minCorrellation && dbg) {
          System.err.println("yay");
        }
        int npick = findPicks(data, maxdata, width, minCorrellation, hist, picks, sb);
        if (npick > 1) {
          System.err.println("more than one! " + npick);
        }
        if (npick < 0) {
          System.err.println("ERROR :" + sb.toString());
        }
        if (npick > 0) {
          totpick += npick;
        }
        for (i = 0; i < npick; i++) {
          time = base + (off + picks[i]) * 10;
          sb.append(Util.ascdate(time)).append(" ").append(Util.asctime2(time)).append(" ").
                  append(" ind=").append(picks[i]).append(" ").append(" max=").
                  append(picks[i] >= 0 ? df3.format(data[picks[i]]) : "LAST").append(" data=");
          if (dbg) {
            for (int j = Math.max(0, picks[i] - width / 2); j < Math.min(picks[i] + width / 2, maxdata - width); j++) {
              sb.append(j).append("=").append(df3.format(data[j])).append(" ");
            }
          }
          System.err.println(sb.toString());
          Util.clear(sb);
        }
        runtime += System.currentTimeMillis() - start;
        off += maxdata;
      }
      in.close();
      System.err.println("Total number of picks=" + totpick + " runtime=" + runtime + " ms");

      // No rerun this using the nonhistory one.
      in = new BufferedReader(new FileReader("MOKD_2013_248stack.dat"));
      off = 0;
      runtime = 0;
      for (;;) {
        int i = width;
        double max = 0;
        int maxi = 0;
        while (i < maxdata + width) {
          line = in.readLine();
          if (line == null) {
            break;
          }
          if (line.charAt(0) == '#') {
            continue;
          }
          if (Character.isAlphabetic(line.charAt(0))) {
            i = 0;
            continue;
          }
          String[] parts = line.split("\\s");
          data[i] = Double.parseDouble(parts[1]);
          if (data[i] > max) {
            maxi = i;
            max = data[i];
          }
          i++;
        }
        if (line == null) {
          break;
        }
        long start = System.currentTimeMillis();
        long time = base + off * 10;
        if (max > minCorrellation || dbg) {
          System.err.println(Util.ascdate(time) + " " + Util.asctime2(time) + " off=" + off + (max > minCorrellation ? "*" : " ") + "max=" + max + "@" + maxi);
        }
        if (max > minCorrellation && dbg) {
          System.err.println("yay");
        }
        int npick = findPicks(data, width, maxdata, width, minCorrellation, picks, sb);
        System.arraycopy(data, maxdata, data, 0, width);
        if (npick > 1) {
          System.err.println("more than one! " + npick);
        }
        if (npick < 0) {
          System.err.println("ERROR :" + sb.toString());
        }
        if (npick > 0) {
          totpick += npick;
        }
        for (i = 0; i < npick; i++) {
          time = base + (off + picks[i]) * 10;
          sb.append(Util.ascdate(time)).append(" ").append(Util.asctime2(time)).append(" ").
                  append(" ind=").append(picks[i]).append(" ").append(" max=").
                  append(picks[i] >= 0 ? df3.format(data[picks[i]]) : "LAST").append(" data=");
          if (dbg) {
            for (int j = Math.max(0, picks[i] - width / 2); j < Math.min(picks[i] + width / 2, maxdata - width); j++) {
              sb.append(j).append("=").append(df3.format(data[j])).append(" ");
            }
          }
          System.err.println(sb.toString());
          if (sb.length() > 0) {
            sb.delete(0, sb.length());
          }
        }
        runtime += System.currentTimeMillis() - start;
        off += maxdata;
      }
      in.close();
      System.err.println("Total number of picks=" + totpick + " runtime=" + runtime + " ms");
      System.exit(0);
    } catch (IOException e) {
      System.err.println("Err=" + e);
      e.printStackTrace();
    }
  }
}
