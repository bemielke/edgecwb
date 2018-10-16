/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.Complex;
import gov.usgs.anss.util.PNZ;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This outputer creates WFDISC files from the CWBQuery list of miniseed blocks.
 *
 * @author davidketchum
 */
public final class WFDiscOutputer extends Outputer {

  boolean dbg;
  private static SacPZ stasrv;
  private boolean firstCall = true;
  private byte[] buf2 = new byte[50000];

  /**
   * Creates a new instance of WFDiscOutputer
   */
  public WFDiscOutputer() {
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
    DecimalFormat df5 = new DecimalFormat("0.000000");
    DecimalFormat df3 = new DecimalFormat("000");
    DecimalFormat df2 = new DecimalFormat("00");
    DecimalFormat ef6 = new DecimalFormat("0.00000E00");
    String begin = "";
    String stahost = Util.getProperty("metadataserver");
    int staport = 2052;
    boolean doFap = false;
    boolean oneFile = false;
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
      if (args[i].equals("-dbg")) {
        dbg = true;
      }
      if (args[i].equals("-t")) {
        if (args[i + 1].equalsIgnoreCase("wf1")) {
          oneFile = true;
        }
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
      stahost = "137.227.230.1";
    }
    if (stasrv == null && doFap) {
      stasrv = new SacPZ(stahost, pzunit);
    }
    // Use the span to populate a sac file
    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(beg.getTime());

    // build the zero filled area (either with exact limits or with all blocks)
    ZeroFilledSpan span = new ZeroFilledSpan(blks, start, duration, fill);
    if (dbg) {
      Util.prt("ZeroSpan=" + span.toString());
    }
    if (oneFile) {
      filename = EdgeQueryClient.makeFilename(filemask, lastComp, Util.stringToDate2(begin.replaceAll("@", " ")), false);
      Util.prt("one file name=" + filename);
    } else {
      filename = filename.substring(2);
      filename = filename.replaceAll("[__]", "_");
      filename = filename.toLowerCase();
      filename = filename.replaceAll("_", ".");
      while (filename.endsWith(".")) {
        filename = filename.substring(0, filename.length() - 1);
      }
    }
    Util.chkFilePath(filename.trim() + ".w");

    PNZ pnz;
    String s;
    double period = 1.;
    double mag = 1;
    if (doFap) {
      s = stasrv.getSACResponse(lastComp, begin.replaceAll("@", "-"));
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
      if (s.indexOf("no channels") > 0) {
        period = -1.;
        mag = .000001;
      } else {
        pnz = new PNZ(s);
        Complex resp = pnz.getResponse(period);
        mag = resp.getReal() * resp.getReal() + resp.getImag() * resp.getImag();
        mag = Math.sqrt(mag);
        if (doFap) {
          StringBuilder fap = new StringBuilder(10000);
          for (int i = 0; i < 41; i++) {
            double f = Math.pow(10., -1. + i * 0.05);
            resp = pnz.getResponse(f);
            fap.append(Util.leftPad(ef6.format(f), 13)).append("  ").
                    append(Util.leftPad(ef6.format(Math.sqrt(resp.getReal() * resp.getReal() + resp.getImag() * resp.getImag()) / mag), 13)).
                    append(" ").append(Util.leftPad(ef6.format(Math.atan2(resp.getImag(), resp.getReal()) * 180. / Math.PI), 13)).
                    append("\n");

          }
          String pafile = lastComp.substring(2, 7).toLowerCase().replaceAll("_", " ").trim() + "." 
                  + lastComp.substring(7, 10).toLowerCase().trim() + ".fap";
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

    double rate = span.getRate();
    int nmax = (int) (rate * duration + 1.5);
    byte[] buf = new byte[nmax * 4];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    StringBuilder wfdisc = new StringBuilder(10000);    // Put the wfdisc stuff in here
    bb.position(0);
    GregorianCalendar startTime = null;
    boolean inGap = false;
    long offset = 0;
    long startOffset = 0;
    if (oneFile) {
      try {
        RawDisk rw = new RawDisk(filename.trim() + ".w", "rw");
        if (firstCall) {
          rw.setLength(0L);
        }
        offset = rw.length();
        startOffset = offset;
        rw.close();
        rw = new RawDisk(filename.trim() + ".wfdisc", "rw");
        if (firstCall) {
          rw.setLength(0L);
        } else {
          //Util.prt(filename.trim()+".wfdisc len="+rw.length());
          if (rw.length() > buf2.length * 0.9) {
            buf2 = new byte[(int) (rw.length() * 2)];// make buf2 bigger for really big queries
          }
          rw.read(buf2, 0, (int) rw.length());
          wfdisc.append(new String(buf2, 0, (int) rw.length()));
        }
        firstCall = false;

      } catch (FileNotFoundException e) {
        Util.prt("First file");
      }
    }
    int nsamp = 0;
    for (int i = 0; i < span.getNsamp(); i++) {
      if (bb.position() == 0) {        // first sample in file, save time
        startTime = span.getGregorianCalendarAt(i);
      }
      int samp = span.getData(i);
      if (samp != fill) {
        if (inGap) {
          startTime = span.getGregorianCalendarAt(i);
          nsamp = 0;
        }
        bb.putInt(samp);
        nsamp++;
        inGap = false;
      }
      if (samp == fill || i == span.getNsamp() - 1) {              // this is either a gap or a start of a gap
        if (inGap && i < span.getNsamp() - 1 || nsamp == 0) {
          continue;
        }
        inGap = true;
        if (bb.position() != 0) {      // There is data
          int ns = bb.position() / 4;   // number of samples
          GregorianCalendar now = new GregorianCalendar();
          long endtime = startTime.getTimeInMillis() + ((long) ((nsamp - 1) / rate * 1000.));
          if (dbg) {
            Util.prt("start=" + Util.ascdate(startTime) + " " + Util.asctime2(startTime)
                    + " end=" + Util.ascdate(endtime) + " " + Util.asctime2(endtime)
                    + " diff=" + (endtime - startTime.getTimeInMillis()) / 1000. + " " + (nsamp - 1) / rate + " ns=" + nsamp);
          }
          wfdisc.append(Util.rightPad(lastComp.substring(2, 7).trim(), 7)).
                  append(Util.rightPad(lastComp.substring(7, 10) + lastComp.substring(10, 12).trim(), 8)).
                  append(" ").append(Util.leftPad(df5.format(startTime.getTimeInMillis() / 1000.), 17)).
                  append("       -1 " + "      -1  ").append(startTime.get(Calendar.YEAR)).
                  append(df3.format(startTime.get(Calendar.DAY_OF_YEAR))).append(" ").
                  append(Util.leftPad(df5.format(startTime.getTimeInMillis() / 1000.
                          + (nsamp - 1) / span.getRate()), 17)).
                  append(" ").append(Util.leftPad("" + nsamp, 8)).append(" ").
                  append(Util.leftPad(df5.format(span.getRate()), 11)).append(" ").
                  append(Util.leftPad(df5.format(mag), 16)).append(" ").append(Util.leftPad(df5.format(period), 16)).
                  append(" " + "-      " + "- " + "s4 " + "- ").append(Util.rightPad(".", 65)).
                  append(Util.rightPad(filename.trim() + ".w", 33)).append(Util.leftPad("" + offset, 10)).
                  append("       -1 ").append(now.get(Calendar.YEAR)).append(df2.format(now.get(Calendar.MONTH) + 1)).
                  append(df2.format(now.get(Calendar.DAY_OF_MONTH))).append(" ").
                  append(df2.format(now.get(Calendar.HOUR_OF_DAY))).append(":").
                  append(df2.format(now.get(Calendar.MINUTE))).append(":").
                  append(df2.format(now.get(Calendar.SECOND))).append("\n");
          offset = bb.position();       // save offset in binary file
          nsamp = 0;
        }
      }
    }
    try {
      RawDisk wf = new RawDisk(filename.trim() + ".w", "rw");
      Util.prt("add to waveform file=" + filename + " offset=" + startOffset + " len=" + bb.position());
      wf.seek(startOffset);
      wf.write(buf, 0, bb.position());
      wf.close();
      wf = new RawDisk(filename.trim() + ".wfdisc", "rw");
      wf.seek(0L);
      wf.write(wfdisc.toString().getBytes());
      wf.setLength(wfdisc.length());
      wf.close();
    } catch (FileNotFoundException e) {
      Util.IOErrorPrint(e, "File not found opening " + filename);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Writing file=" + filename);
    }
    // Now march through the data creating .w and .wfdisc records

  }
}
