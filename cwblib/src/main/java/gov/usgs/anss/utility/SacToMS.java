/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.utility;

import edu.sc.seis.TauP.SacTimeSeries;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Collections;

/**
 * This class can convert SAC files to MiniSEED files. It has a special options for SAC files
 * created by Guralp which only contains the serial number in the station portion of the header. It
 * has options for overriding the parts of the NSCL and for whether separate or a single miniseed
 * file should be created.
 * <br>
 * <PRE>
 * SacToMS : [-nooutput][-dbg][-fn NN][-fc CCC][-fl LL][-fs SSSSS][-fr RATE][-fseed NSCL][-merge][-guralp mapfile] [SACFILES]
 * -nooutput           No output miniseed will be written
 * -dbg                Add output including dump of SAC header
 * -fn NN              Override network to always be NN
 * -fl LL              Override the location code to always be LL
 * -fc CCC             Override the channel code to always be CCC
 * -fs SSSSS           Override the station code to always be SSSSS
 * -fr nnnn.nn         Override the rate to be this rate
 * -guralp mapfile     Data came from Guralp program so decode sac kcmpnm according to file mapfile
 *                     Map file entries are : 'SSS1:SN1' one per line (StationName:GuralpSN
 *                     In Guralp mode the the -fn and -fc are required.
 * -fseed NNSSSSSCCCLL Force the location code to be as given.  Use hyphens to represent spaces
 * -merge              Take all input files and merge them into one output file of miniSEED instead of one-to-one
 * -hvoscan            Scan HVO data and report the times,  npts, offset, calculated rates, elapsed
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class SacToMS {

  private static final DecimalFormat df2 = new DecimalFormat("00");
  private static final DecimalFormat df3 = new DecimalFormat("000");
  private static final TreeMap<String, String> guralpMap = new TreeMap<>();

  public static void readGuralpFile(String filename) {
    String line;
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
        while ((line = in.readLine()) != null) {
          if (line.length() < 1) {
            continue;
          }
          if (line.charAt(0) == '#' || line.charAt(0) == '!') {
            continue;
          }

          String[] parts = line.split(":");
          if (parts.length != 2) {
            Util.prt("Bad file format for guralp file " + filename + " line=" + line);
            System.exit(1);
          }
          guralpMap.put(parts[1].trim().toLowerCase(), parts[0].trim().toUpperCase());
        }
      }
    } catch (IOException e) {
      Util.prt("could not read guralp file=" + filename + " e=" + e);
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    Util.init("msread.prop");
    if (args.length == 0) {
      Util.prt("USAGE : java -jar UUSSConvert [-nooutput] [UWFiles");
      System.exit(1);
    }
    boolean nooutput = false;
    boolean sac = false;
    boolean uw = false;
    boolean dbg = false;
    String overNet = null;
    String overChan = null;
    String overLoc = null;
    String overStat = null;
    String overSeedname = null;
    double overRate = -1;
    MakeRawToMiniseed maker = new MakeRawToMiniseed();
    int istart = 0;
    boolean merge = false;
    String guralpFile = null;
    if (args.length <= 1) {
      Util.prt("SacToMS : [-nooutput][-dbg][-fn NN][-fc CCC][-fl LL][-fs SSSSS][-fr RATE][-fseed NSCL][-merge][-guralp mapfile] [SACFILES]");
      Util.prt("-nooutput           No output miniseed will be written");
      Util.prt("-dbg                Add output including dump of SAC header");
      Util.prt("-fn NN              Override network to always be NN");
      Util.prt("-fl LL              Override the location code to always be LL");
      Util.prt("-fc CCC             Override the channel code to always be CCC");
      Util.prt("-fs SSSSS           Override the station code to always be SSSSS");
      Util.prt("-fr nnnn.nn         Override the rate to be this rate");
      Util.prt("-guralp mapfile     Data came from Guralp program so decode sac kcmpnm according to file mapfile");
      Util.prt("                    Map file entries are : 'SSS1:SN1' one per line (StationName:GuralpSN");
      Util.prt("                    In Guralp mode the the -fn and -fc are required.");
      Util.prt("-fseed NNSSSSSCCCLL Force the location code to be as given.  Use hyphens to represent spaces");
      Util.prt("-merge              Take all input files and merge them into one output file of miniSEED instead of one-to-one");
      Util.prt("-hvoscan            Scan HVO data and report the times,  npts, offset, calculated rates, elapsed");
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
      } else if (args[i].equals("-fseed")) {
        overSeedname = (args[i + 1] + "        ").substring(0, 12);
        istart = i + 2;
        i++;
      } else if (args[i].equals("-merge")) {
        merge = true;
        istart = i + 1;
      } else if (args[i].equals("-guralp")) {
        guralpFile = args[i + 1];
        istart = i + 2;
        i++;
      } else if (args[i].equals("-sac2ms")) {
        istart = i + 1;
      } else if (args[i].equals("-hvoscan")) {
        HVOScan.main(args);
        System.exit(0);
      } else if (args[i].startsWith("-")) {
        Util.prt("Unknown option i=" + i + " arg=" + args[i]);
      }

    }
    if (guralpFile != null) {
      readGuralpFile(guralpFile);
    }

    long mintime = Long.MAX_VALUE;
    long maxtime = Long.MIN_VALUE;
    for (int i = istart; i < args.length; i++) {
      SacTimeSeries sacts = new edu.sc.seis.TauP.SacTimeSeries();
      try {
        sacts.read(args[i]);
        String seedname = null;
        if (dbg) {
          sacts.printHeader();
          //continue;
        }

        if (guralpFile != null) {
          if (overNet == null || overChan == null) {
            Util.prt("Guralp map must select -fn and -fc on command line");
            System.exit(1);
          }
          String station = guralpMap.get(sacts.kcmpnm.substring(0, 4).toLowerCase());
          if (station == null) {
            Util.prt("Found file with serial =" + sacts.kcmpnm + " not in guralp map");
            System.exit(1);
          }

          seedname = (overNet + "  ").substring(0, 2) + (station + "     ").substring(0, 5)
                  + overChan.substring(0, 2) + sacts.kcmpnm.substring(4, 5) + (overLoc == null ? "  " : (overLoc + "  ").substring(0, 2));
          sacts.kstnm = station;
        }
        if (overNet != null) {
          sacts.knetwk = overNet;
        }
        if (overChan != null) {
          sacts.kcmpnm = overChan;
        }
        if (overLoc != null) {
          sacts.khole = overLoc;
        }
        if (overStat != null) {
          sacts.kstnm = overStat;
        }
        if (overSeedname != null) {
          seedname = overSeedname.replaceAll("-", " ");
        }
        sacts.khole = sacts.khole.replaceAll("-", " ");

        if (seedname == null) {
          seedname = (sacts.knetwk + "  ").substring(0, 2) + (sacts.kstnm + "     ").substring(0, 5)
                  + (sacts.kcmpnm + "   ").substring(0, 3) + (sacts.khole.trim().equals("-12345") ? "  " : (sacts.khole + "  ").substring(0, 2));
        }
        seedname = seedname.replaceAll("-", " ");
        double rate = overRate;
        if (overRate < 0) {
          rate = 1. / sacts.delta;
          if (rate >= 0.9) {
            rate = (int) (rate + 0.5);
          }
        }
        int year = sacts.nzyear;
        int doy = sacts.nzjday;
        int hr = sacts.nzhour;
        int min = sacts.nzmin;
        int sec = sacts.nzsec;
        int ms = sacts.nzmsec;
        sec = hr * 3600 + min * 60 + sec;
        int usec = ms * 1000;
        int nsamp = (int) sacts.y.length;
        int[] data = new int[nsamp];
        for (int j = 0; j < nsamp; j++) {
          data[j] = (int) (sacts.y[j] + 0.4999);
        }
        if (merge) {
          maker.loadTSIncrement(seedname, nsamp,
                  data, year, doy, sec, usec, rate, 0, 0, 0, 0);
        } else {
          ArrayList<MiniSeed> ret = maker.loadTS(seedname, nsamp,
                  data, year, doy, sec, usec, rate, 0, 0, 0, 0);

          if (dbg) {
            for (MiniSeed ret1 : ret) {
              Util.prt(ret1.toString());
            }
          }
          if (!nooutput) {
            try {
              maker.writeToDisk(args[i] + "_" + seedname.replaceAll(" ", "_") + ".ms");
              maker.close();
            } catch (IOException e) {
              Util.prt("IO error writing file " + args[i] + "_" + seedname + ".ms");
              e.printStackTrace();
            }
          }
        }
      } catch (IOException e) {
        Util.prt("IOErr=" + e);
        e.printStackTrace();
      }

    }
    if (merge) {
      ArrayList<MiniSeed> rets = maker.getBlocks();
      Collections.sort(rets);
      String lastchan = "";
      int iblk = 0;
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
            String filename = rets.get(i).getSeedNameString().replaceAll(" ", "_") + "_" + g.get(Calendar.YEAR) + "_" + df3.format(g.get(Calendar.DAY_OF_YEAR)) + "_"
                    + df2.format(g.get(Calendar.HOUR_OF_DAY)) + df2.format(g.get(Calendar.MINUTE)) + df2.format(g.get(Calendar.SECOND)) + ".ms";
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
    }
    System.exit(0);
  }
}
