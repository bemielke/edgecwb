/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.util.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This writes out WFDISC where the time series remains as miniSEED blocks. This works with Antelope
 * as a method but is not a viable format for GeoTools.
 *
 * @author davidketchum
 */
public final class WFDiscMSOutputer extends Outputer {

  private static final String dbString = "#\nschema css3.0\ndblocks nfs\ndbidserver \n";
  private static final DecimalFormat df5 = new DecimalFormat("0.000000");
  //private static final DecimalFormat df3 = new DecimalFormat("000");
  //private static final DecimalFormat df2 = new DecimalFormat("00");
  //private static final DecimalFormat ef6 = new DecimalFormat("0.00000E00");
  private boolean dbg;
  private StringBuilder wfdisc = new StringBuilder(10000);    // Put the wfdisc stuff in here
  private static SacPZ stasrv;
  private boolean firstCall = true;
  //private byte [] buf2 = new byte[50000];
  private MSOutputer msoutput = new MSOutputer(false);
  private GregorianCalendar starttime = new GregorianCalendar();
  private GregorianCalendar endtime = new GregorianCalendar();
  private String name;
  private boolean concat;
  private long offset;
  private long offsettotal;

  /**
   * Creates a new instance of WFDiscOutputer
   *
   * @param name The name to put on the .wfdisc part (if concat true same name is used on .wfms
   * part)
   * @param concat If true, run this in concatenate MiniSeed mode, that is only one miniseed file
   * out name.wfms
   */
  public WFDiscMSOutputer(String name, boolean concat) {
    this.name = name;
    this.concat = concat;
    try {
      if (concat) {
        msoutput.setConcat(true, name);
      }
    } catch (IOException e) {
      Util.prt("**** Cannot open concatenated miniseed file e=" + e);
    }
  }

  @Override
  public void makeFile(String lastComp, String filename, String filemask, ArrayList<MiniSeed> blks,
          java.util.Date beg, double duration, String[] args) throws IOException {

    // Process the args for things that affect us
    boolean nogaps = false;       // if true, do not generate a file if it has any gaps!
    int fill = 2147000000;
    boolean sacpz = false;
    boolean quiet = false;
    boolean sactrim = false;      // return full length padded with no data value
    String pzunit = "nm";
    String begin = "";
    String stahost = Util.getProperty("metadataserver");
    int staport = 2052;
    boolean doFap = false;
    boolean doMeta = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-fill")) {
        fill = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-nogaps")) {
        fill = 2147000000;
        nogaps = true;
      }
      if (args[i].equals("-q")) {
        quiet = true;
      }
      if (args[i].equals("-b")) {
        begin = args[i + 1];
      }
      if (args[i].equals("-fap")) {
        doFap = true;
      }
      if (args[i].equals("-meta")) {
        doMeta = true;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      }
      if (args[i].equals("-sacpz")) {
        pzunit = args[i + 1];
        if (!args[i + 1].equalsIgnoreCase("nm") && !args[i + 1].equalsIgnoreCase("um")) {
          Util.prt("   ****** -sacpz units must be either um or nm switch values is " + args[i + 1]);
          System.exit(0);
        }
      }
    }
    if (stahost == null || stahost.equals("")) {
      stahost = "137.227.224.97";
    }
    if ((doFap || doMeta) && stasrv == null) {
      stasrv = new SacPZ(stahost, pzunit);
    }
    // Use the span to populate a sac file
    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(beg.getTime());
    if (concat) {
      offset = msoutput.getLength();
      offsettotal = offset;
    }
    msoutput.makeFile(lastComp, filename, filemask, blks, beg, duration, args);

    MiniSeed ms = null;
    for (int i = 0; i < blks.size(); i++) {
      if (blks.get(i).getNsamp() > 0 && blks.get(i).getRate() > 0.0001) {
        ms = blks.get(i);
        break;
      }
    }
    String path = ".";
    filename = msoutput.getFilename();
    String file = filename;
    if (filename.lastIndexOf("/") >= 0) {
      path = filename.substring(0, filename.lastIndexOf("/"));
      file = filename.substring(filename.lastIndexOf("/") + 1);
    }

    double period = 1.;
    double mag = 1.;
    if (doFap || doMeta) {
      String s = stasrv.getSACResponse(lastComp, begin.replaceAll("@", "-"));
      /*String s = "* A0-SEED      4.8539E07\n"+"CONSTANT              3.0538E+07\n"+
      "ZEROS   3\n"+
      "         0.0000E+00   0.0000E+00\n"+
      "         0.0000E+00   0.0000E+00\n"+
      "         0.0000E+00   0.0000E+00\n"+
      "POLES   5\n"+
      "        -3.7024E-02   3.7024E-02\n"+
      "        -3.7024E-02  -3.7024E-02\n"+
      "        -2.5133E+02   0.0000E+00\n"+
      "        -1.1863E+02   4.2306E+02\n"+
      "        -1.1863E+02  -4.2306E+02\n"+
      "* <EOE>\n"+
      "* <EOR>\n";*/

      if (doMeta) {
        RawDisk pz = new RawDisk(msoutput.getFilename() + ".pz", "rw");
        pz.position(0);
        pz.write(s.getBytes());
        pz.setLength(s.length());
      }
      if (doFap) {

        if (s.indexOf("no channels") > 0) {
          period = -1.;
          mag = .000001;
        } else {
          PNZ pnz = new PNZ(s);
          Complex resp = pnz.getResponse(period);
          mag = resp.getReal() * resp.getReal() + resp.getImag() * resp.getImag();
          mag = Math.sqrt(mag);
          if (doFap) {
            StringBuilder fap = new StringBuilder(10000);
            for (int i = 0; i < 41; i++) {
              double f = Math.pow(10., -1. + i * 0.05);
              resp = pnz.getResponse(f);
              fap.append(Util.leftPad(Util.ef5(f), 13)).append("  ").
                      append(Util.leftPad(Util.ef5(Math.sqrt(resp.getReal() * resp.getReal() + resp.getImag() * resp.getImag()) / mag), 13)).
                      append(" ").append(Util.leftPad(Util.ef5(Math.atan2(resp.getImag(), resp.getReal()) * 180. / Math.PI), 13)).
                      append("\n");
            }
            String pafile = lastComp.substring(2, 7).toLowerCase().replaceAll("_", " ").trim() + "." + lastComp.substring(7, 10).toLowerCase().trim() + ".fap";
            try {
              try (RawDisk pf = new RawDisk(pafile, "rw")) {
                pf.seek(0L);
                pf.write(fap.toString().getBytes());
                pf.setLength(fap.length());
              }
            } catch (IOException e) {
              Util.prt("IOExcept writing paf e=" + e);
            }
          }
        }
      }
    }

    double rate = ms.getRate();
    int nmax = (int) (rate * duration + 1.5);
    byte[] buf = new byte[nmax * 4];

    long startOffset = 0;
    starttime.setTimeInMillis(ms.getTimeInMillis());
    endtime.setTimeInMillis(starttime.getTimeInMillis());
    int nsamp = 0;
    if (!concat) {
      offset = 0;
      offsettotal = 0;
    }
    for (int i = 0; i <= blks.size(); i++) {
      if (i < blks.size()) {
        if (blks.get(i) == null) {
          continue;
        }
        offsettotal += blks.get(i).getBlockSize();
        if (blks.get(i).getNsamp() <= 0 || blks.get(i).getRate() <= 0.) {
          continue;
        }
      }
      if (i == blks.size()
              || Math.abs(blks.get(Math.min(i, blks.size() - 1)).getTimeInMillis() - endtime.getTimeInMillis()) > 1000 / ms.getRate()) {  // or end
        if (nsamp > 0) {      // There is data
          GregorianCalendar now = new GregorianCalendar();
          if (dbg) {
            Util.prt("start=" + Util.ascdate(starttime) + " " + Util.asctime2(starttime)
                    + " end=" + Util.ascdate(endtime) + " " + Util.asctime2(endtime)
                    + " diff=" + (endtime.getTimeInMillis() - starttime.getTimeInMillis()) / 1000. + " " + (nsamp - 1) / rate
                    + " ns=" + nsamp + " i=" + i + " of " + blks.size());
          }
          wfdisc.append(Util.rightPad(lastComp.substring(2, 7).trim(), 7)).
                  append(Util.rightPad(lastComp.substring(7, 10) + "_" + lastComp.substring(10, 12).trim(), 8)).
                  append(" ").append(Util.leftPad(df5.format(starttime.getTimeInMillis() / 1000.), 17)).
                  append("       -1 " + "      -1  ").append(starttime.get(Calendar.YEAR)).
                  append(Util.df3(starttime.get(Calendar.DAY_OF_YEAR))).append(" ").
                  append(Util.leftPad(df5.format(starttime.getTimeInMillis() / 1000. + (nsamp - 1) / rate), 17)).
                  append(" ").append(Util.leftPad("" + nsamp, 8)).append(" ").
                  append(Util.leftPad(df5.format(rate), 11)).append(" ").
                  append(Util.leftPad(df5.format(mag), 16)).append(" ").
                  append(Util.leftPad(df5.format(period), 16)).
                  append(" " + "-      " + "- " + "sd " + "- ").append(Util.rightPad(path, 65)).
                  append(Util.rightPad(file.trim(), 33)).append(Util.leftPad("" + offset, 10)).
                  append("       -1 ").append(now.get(Calendar.YEAR)).append(Util.df2(now.get(Calendar.MONTH) + 1)).
                  append(Util.df2(now.get(Calendar.DAY_OF_MONTH))).append(" ").
                  append(Util.df2(now.get(Calendar.HOUR_OF_DAY))).append(":").
                  append(Util.df2(now.get(Calendar.MINUTE))).append(":").
                  append(Util.df2(now.get(Calendar.SECOND))).append("\n");
          if (i == blks.size()) {
            if (dbg) {
              Util.prt("last block break");
            }
            break;
          }
          offset = offsettotal - blks.get(i).getBlockSize();       // save offset in binary file
          starttime.setTimeInMillis(blks.get(i).getTimeInMillis());
          endtime.setTimeInMillis(blks.get(i).getNextExpectedTimeInMillis());
          nsamp = blks.get(i).getNsamp();
        }
      } else {
        nsamp += blks.get(i).getNsamp();
        endtime.setTimeInMillis(blks.get(i).getNextExpectedTimeInMillis());
      }
    }

    try {
      try (RawDisk wf = new RawDisk(name + ".wfdisc", "rw")) {
        wf.seek(0L);
        wf.write(wfdisc.toString().getBytes());
        wf.setLength(wfdisc.length());
      }
    } catch (FileNotFoundException e) {
      Util.IOErrorPrint(e, "File not found opening " + filename);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Writing file=" + filename);
    }
    // Now march through the data creating .w and .wfdisc records

  }
}
