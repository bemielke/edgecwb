/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.utility;

import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.IllegalSeednameException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.TreeMap;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class can convert SAC files to MiniSEED files. It has a special options for SAC files
 * created by Guralp which only contains the serial number in the station portion of the header. It
 * has options for overriding the parts of the NSCL and for whether separate or a single miniseed
 * file should be created.
 * <br>
 * <PRE>
 * FloatsToMS : [-nooutput][-dbg][-fn NN][-fc CCC][-fl LL][-fs SSSSS][-fr RATE][-fseed NSCL][-merge][-guralp mapfile] [SACFILES]
 * -nooutput           No output miniseed will be written
 * -dbg                Add output including dump of SAC header
 * -fn NN              Override network to always be NN
 * -fl LL              Override the location code to always be LL
 * -fc CCC             Override the channel code to always be CCC
 * -fs SSSSS           Override the station code to always be SSSSS
 * -fr nnnn.nn         Override the rate to be this rate
 * -fseed NNSSSSSCCCLL Force the location code to be as given.  Use hyphens to represent spaces
 * -merge              Take all input files and merge them into one output file of miniSEED instead of one-to-one
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class FloatsToMS {

  private static final DecimalFormat df2 = new DecimalFormat("00");
  private static final DecimalFormat df3 = new DecimalFormat("000");
  private static final TreeMap<String, String> guralpMap = new TreeMap<String, String>();

  public static void main(String[] args) {
    Util.init("msread.prop");
    if (args.length == 0) {
      Util.prt("USAGE : java -jar FloatsToMS [-nooutput] [Files");
      System.exit(1);
    }
    boolean nooutput = false;
    boolean dbg = false;
    String overNet = null;
    String overChan = null;
    String overLoc = null;
    String overStat = null;
    String overSeedname = null;
    double overRate = -1;
    MakeRawToMiniseed maker = new MakeRawToMiniseed();
    int istart = 0;
    boolean merge = true;
    double fscale = 1000;
    GregorianCalendar start = new GregorianCalendar();
    if (args.length <= 1) {
      Util.prt("FloatToMS : [-nooutput][-dbg][-fn NN][-fc CCC][-fl LL][-fs SSSSS][-fr RATE][-fseed NSCL] [SACFILES]");
      Util.prt("-nooutput           No output miniseed will be written");
      Util.prt("-dbg                Add output including dump of SAC header");
      Util.prt("-fn NN              Override network to always be NN");
      Util.prt("-fl LL              Override the location code to always be LL");
      Util.prt("-fc CCC             Override the channel code to always be CCC");
      Util.prt("-fs SSSSS           Override the station code to always be SSSSS");
      Util.prt("-fr nnnn.nn         Override the rate to be this rate");
      Util.prt("-fseed NNSSSSSCCCLL Force the location code to be as given.  Use hyphens to represent spaces");
      Util.prt("-merge              Take all input files and merge them into one output file of miniSEED instead of one-to-one");
      System.exit(0);
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nooutput")) {
        nooutput = true;
        istart = i + 1;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
        istart = i + 1;
      } else if (args[i].equals("-fn")) {
        overNet = args[i + 1];
        istart = i + 2;
        i++;
      } else if (args[i].equals("-fc")) {
        overChan = args[i + 1];
        istart = i + 2;
        i++;
      } else if (args[i].equals("-fl")) {
        overLoc = args[i + 1];
        istart = i + 2;
        i++;
      } else if (args[i].equals("-fs")) {
        overStat = args[i + 1];
        istart = i + 2;
        i++;
      } else if (args[i].equals("-fr")) {
        overRate = Double.parseDouble(args[i + 1]);
        istart = i + 2;
        i++;
      } else if (args[i].equals("-fscale")) {
        fscale = Double.parseDouble(args[i + 1]);
        istart = i + 2;
        i++;
      } else if (args[i].equals("-fseed")) {
        overSeedname = (args[i + 1] + "        ").substring(0, 12);
        istart = i + 2;
        i++;
      } else if (args[i].equals("-merge")) {
        merge = true;
        istart = i + 1;
      } else if (args[i].equals("-float2ms")) ; else if (args[i].contains("-")) {
        Util.prt("Unknown option i=" + i + " arg=" + args[i]);
      }

    }

    byte[] b = new byte[4096];
    ByteBuffer bb = ByteBuffer.wrap(b);
    int iblk = 0;
    MiniSeed ms = null;
    for (int i = istart; i < args.length; i++) {
      try {
        RawDisk rw = new RawDisk(args[i], "rw");
        while (iblk * 512 < rw.length()) {
          rw.readBlock(b, iblk, 512);
          int len = MiniSeed.crackBlockSize(b);
          if (len > 512) {
            rw.readBlock(b, iblk, len);
          }
          iblk += len / 512;
          if (ms == null) {
            ms = new MiniSeed(b, 0, len);
          } else {
            ms.load(b, 0, len);
          }
          if (dbg) {
            Util.prt("ms=" + ms);
          }
          String seedname = ms.getSeedNameString();
          if (overNet != null) {
            seedname = (overLoc + "  ").substring(0, 2) + seedname.substring(2);
          }
          if (overChan != null) {
            seedname = seedname.substring(0, 7) + (overChan + "   ").substring(0, 3) + seedname.substring(10);
          }
          if (overLoc != null) {
            seedname = seedname.substring(0, 10) + overLoc;
          }
          if (overStat != null) {
            seedname = seedname.substring(0, 2) + (overStat + "     ").substring(0, 5) + seedname.substring(7);
          }
          if (overSeedname != null) {
            seedname = overSeedname.replaceAll("-", " ");
          }

          double rate = overRate;
          if (overRate < 0) {
            rate = ms.getRate();
          }
          start.setTimeInMillis(ms.getTimeInMillis());
          int year = start.get(Calendar.YEAR);
          int doy = start.get(Calendar.DAY_OF_YEAR);
          int hr = start.get(Calendar.HOUR_OF_DAY);
          int min = start.get(Calendar.MINUTE);
          int sec = start.get(Calendar.SECOND);
          int millis = start.get(Calendar.MILLISECOND);
          sec = hr * 3600 + min * 60 + sec;
          int usec = millis * 1000;
          int nsamp = ms.getNsamp();
          int[] data = new int[nsamp];
          int encoding = ms.getEncoding();
          if (encoding >= 4 && encoding <= 5) {
            int[] samples = new int[ms.getNsamp()];
            bb.position(ms.getDataOffset());
            for (int ii = 0; ii < ms.getNsamp(); ii++) {
              if (encoding == 5) {
                data[ii] = (int) (bb.getDouble() * fscale + 0.5);
              } else if (encoding == 4) {
                data[ii] = (int) (bb.getFloat() * fscale + 0.5);
              }
            }
          } else {
            Util.prt(" **** Block does not have floats!! ms=" + ms);
            System.exit(1);
          }

          if (dbg) {
            Util.prt(seedname + " ns=" + nsamp + " " + year + "," + doy + "," + sec + "nsec=" + usec);
          }
          maker.loadTSIncrement(seedname, nsamp,
                  data, year, doy, sec, usec, rate, 0, 0, 0, 0);

        }
      } catch (IOException e) {
        Util.prt("IOErr=" + e);
        e.printStackTrace();
      } catch (IllegalSeednameException e) {
        Util.prt("Illegal seedname=" + e);
      }

    }
    ArrayList<MiniSeed> rets = maker.getBlocks();
    Collections.sort(rets);
    String lastchan = "";
    iblk = 0;
    try {
      RawDisk msout = null;
      for (int i = 0; i < rets.size(); i++) {
        if (!rets.get(i).getSeedNameString().equals(lastchan) || i == rets.size() - 1) {
          if (i == rets.size() - 1) {
            msout.writeBlock(iblk++, rets.get(i).getBuf(), 0, 512);
            continue;
          }
          if (msout != null) {
            msout.close();
          }
          GregorianCalendar g = rets.get(i).getGregorianCalendar();
          String filename = rets.get(i).getSeedNameString().replaceAll(" ", "_") + "_" + g.get(Calendar.YEAR) + "_"
                  + df3.format(g.get(Calendar.DAY_OF_YEAR)) + "_"
                  + df2.format(g.get(Calendar.HOUR_OF_DAY)) + df2.format(g.get(Calendar.MINUTE))
                  + df2.format(g.get(Calendar.SECOND)) + ".ms";
          msout = new RawDisk(filename, "rw");
          msout.setLength(0L);      // always a new file if opened during run.
          lastchan = rets.get(i).getSeedNameString();
          iblk = 0;
        }
        if (dbg) {
          Util.prt(msout.getFilename() + " " + rets.get(i).toString());
        }
        msout.writeBlock(iblk++, rets.get(i).getBuf(), 0, 512);
      }
      msout.close();
    } catch (IOException e) {
      Util.prt("IOErr=" + e);
      e.printStackTrace();
    }
    System.exit(0);
  }
}
