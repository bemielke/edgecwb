/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import edu.sc.seis.TauP.SacTimeSeries;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.PNZ;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Write a SAC file from the CWBQuery list of miniSEED blocks with some options.
 * <PRE>
 * switch  args   Description
 * -fill   value  Change value from the SAC default of -12345
 * -sacpz         If present, write out the SAC style Poles and Zeros file for the instrument stages
 * -nogaps        If any gap is present, return nothing
 * -sactrim       If present, do not fill out and end-of-interval gap with nodata values, just shorten the nsamps
 * -nometa        If present, do not populate the SAC header with coordinates, etc from the MetaDataServer
 * -q             Quiet - less output
 * -derivelh      If present, change the input sample rate to 1 hz after lowpass and decimage
 * </pre>
 *
 * @author davidketchum
 */
public class SacOutputer extends Outputer {

  boolean dbg;
  private static SacPZ stasrv;
  private static String stasrvHost;
  private static int stasrvPort;
  private SacTimeSeries sac;
  private String filename;

  /**
   * Creates a new instance of SacOutputer
   */
  public SacOutputer() {
  }

  public SacTimeSeries getSacTimeSeries() {
    return sac;
  }

  public String getFilename() {
    return filename;
  }

  @Override
  public void makeFile(String lastComp, String file, String filemask, ArrayList<MiniSeed> blks,
          java.util.Date beg, double duration, String[] args) throws IOException {

    // Process the args for things that affect us
    filename = file;
    if (blks.isEmpty()) {
      return;    // no data to save
    }
    boolean nogaps = false;       // if true, do not generate a file if it has any gaps!
    int fill = -12345;
    boolean sacpz = false;
    boolean noStaSrv = false;
    boolean quiet = false;
    boolean sactrim = false;      // return full length padded with no data value
    String pzunit = "nm";
    boolean ascii = false;
    boolean deriveLH = false;
    String newComp = lastComp;
    for (MiniSeed blk : blks) {
      if (blk.getNsamp() > 0 && blk.getRate() > 0.001) {
        newComp = blk.getSeedNameString();
        break;
      }
    }

    String stahost = Util.getProperty("metadataserver");
    int staport = 2052;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-fill")) {
        fill = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-nogaps")) {
        fill = 2147000000;
        nogaps = true;
      }
      if (args[i].equals("-nometa")) {
        noStaSrv = true;
      }
      if (args[i].equals("-sactrim")) {
        sactrim = true;
      }
      if (args[i].equals("-ascii")) {
        ascii = true;
      }
      if (args[i].equals("-q")) {
        quiet = true;
      }
      if (args[i].equalsIgnoreCase("-derivelh")) {
        deriveLH = true;
      }
      if (args[i].equals("-sacpz")) {
        sacpz = true;
        pzunit = args[i + 1];
        if (stahost == null || stahost.equals("")) {
          stahost = "137.227.230.1";
        }
      }
    }
    if (stahost.equals("")) {
      noStaSrv = true;
    }
    if (!noStaSrv && (stasrv == null || !stahost.equals(stasrvHost) || stasrvPort != staport)) {
      stasrv = new SacPZ(stahost, pzunit);
      stasrvHost = stahost;
      stasrvPort = staport;
    }
    // Use the span to populate a sac file
    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(beg.getTime());

    // build the zero filled area (either with exact limits or with all blocks)
    if (deriveLH && (newComp.charAt(7) == 'C' || newComp.charAt(8) == 'I')) {
      start.setTimeInMillis(beg.getTime() + 27000);
    }
    ZeroFilledSpan span = new ZeroFilledSpan(blks, start, duration, fill);
    if (span.getRate() <= 0.00) {
      return;         // There is no real data to put in SAC
    }
    if (dbg) {
      Util.prt("ZeroSpan=" + span.toString());
    }
    int noval = span.getNMissingData();

    if (nogaps && span.hasGapsBeforeEnd()) {
      Util.prt("  ** " + lastComp + " has gaps - discarded # missing =" + noval);
      return;
    }
    if (filemask.equals("%N")) {
      filename += ".sac";
    }
    filename = filename.replaceAll("[__]", "_");

    //ZeroFilledSpan span = new ZeroFilledSpan(blks);
    sac = new SacTimeSeries();
    sac.npts = span.getNsamp();
    double[] coord = new double[3];
    double[] orient = new double[3];
    for (int i = 0; i < 3; i++) {
      orient[i] = SacTimeSeries.DOUBLE_UNDEF;
      coord[i] = SacTimeSeries.DOUBLE_UNDEF;
    }
    PNZ pnz = null;
    if (!noStaSrv) {
      //coord = stasrv.getCoord(lastComp.substring(0,2),
      //    lastComp.substring(2,7),lastComp.substring(10,12));
      // orientation [0] = azimuth clwse from N, [1]=dip down from horizontl, [2]=burial depth
      //orient = stasrv.getOrientation(lastComp.substring(0,2),
      //    lastComp.substring(2,7),lastComp.substring(10,12),lastComp.substring(7,10));
      String time = blks.get(0).getTimeString();
      time = time.substring(0, 4) + "," + time.substring(5, 8) + "-" + time.substring(9, 17);

      String s;
      if (sacpz) {
        s = stasrv.getSACResponse(lastComp, time, filename);  // write out the file too
      } else {
        s = stasrv.getSACResponse(lastComp, time);
      }
      int loop = 0;
      while (s.contains("MetaDataServer not up")) {
        if (loop++ % 15 == 1) {
          Util.prta("MetaDataServer is not up - waiting for connection");
        }
        try {
          Thread.sleep(2000);
        } catch (InterruptedException expected) {
        }
        s = stasrv.getSACResponse(lastComp, time, filename);
      }
      try {
        BufferedReader in = new BufferedReader(new StringReader(s));
        String line;
        while ((line = in.readLine()) != null) {
          if (line.indexOf("LAT-SEED") > 0) {
            coord[0] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("LONG-SEED") > 0) {
            coord[1] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("ELEV-SEED") > 0) {
            coord[2] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("AZIMUTH") > 0) {
            orient[0] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("DIP") > 0) {
            orient[1] = Double.parseDouble(line.substring(15));
          } else if (line.indexOf("DEPTH") > 0) {
            orient[2] = Double.parseDouble(line.substring(15));
          }
        }
        //Util.prt("coord="+coord[0]+" "+coord[1]+" "+coord[2]+" orient="+orient[0]+" "+orient[1]+" "+orient[2]);
      } catch (IOException e) {
        Util.prta("OUtput error writing sac response file " + lastComp + ".resp e=" + e.getMessage());
      }
    }

    // Set the byteOrder based on native architecture and sac statics
    sac.nvhdr = 6;                // Only format supported
    sac.b = 0.;           // beginning time offsed
    sac.e = (span.getNsamp() / span.getRate());
    sac.iftype = SacTimeSeries.ITIME;
    sac.leven = SacTimeSeries.TRUE;
    sac.delta = (1. / span.getRate());
    sac.depmin = span.getMin();
    sac.depmax = span.getMax();
    sac.nzyear = span.getStart().get(Calendar.YEAR);
    sac.nzjday = span.getStart().get(Calendar.DAY_OF_YEAR);
    sac.nzhour = span.getStart().get(Calendar.HOUR_OF_DAY);
    sac.nzmin = span.getStart().get(Calendar.MINUTE);
    sac.nzsec = span.getStart().get(Calendar.SECOND);
    sac.nzmsec = span.getStart().get(Calendar.MILLISECOND);
    sac.iztype = SacTimeSeries.IB;
    sac.knetwk = lastComp.substring(0, 2);
    sac.kstnm = lastComp.substring(2, 7);
    sac.kcmpnm = newComp.substring(7, 10);
    sac.khole = "  ";
    if (!lastComp.substring(10, 12).equals("  ")) {
      sac.khole = lastComp.substring(10, 12);
    }
    if (coord[0] != SacTimeSeries.DOUBLE_UNDEF) {
      sac.stla = coord[0];
    }
    if (coord[1] != SacTimeSeries.DOUBLE_UNDEF) {
      sac.stlo = coord[1];
    }
    if (coord[2] != SacTimeSeries.DOUBLE_UNDEF) {
      sac.stel = coord[2];
    }
    if (coord[0] == SacTimeSeries.DOUBLE_UNDEF && coord[1] == SacTimeSeries.DOUBLE_UNDEF && coord[2] == SacTimeSeries.DOUBLE_UNDEF) {
      if (!noStaSrv) {
        Util.prt("   **** " + lastComp + " did not get lat/long.  Is server down?");
      }
    }
    if (orient != null) {
      if (orient[2] != SacTimeSeries.DOUBLE_UNDEF) {
        sac.stdp = orient[2];
      }
      if (orient[0] != SacTimeSeries.DOUBLE_UNDEF) {
        sac.cmpaz = orient[0];
      }
      if (orient[1] != SacTimeSeries.DOUBLE_UNDEF) {
        sac.cmpinc = (orient[1] + 90.);   // seed is down from horiz, sac is down from vertical
      }
    } else {
      if (orient[0] == SacTimeSeries.DOUBLE_UNDEF && orient[1] == SacTimeSeries.DOUBLE_UNDEF) {
        if (!noStaSrv) {
          Util.prt("      ***** " + lastComp + " Did not get orientation.  Is server down?");
        }
      }
    }
    //Util.prt("Sac stla="+sac.stla+" stlo="+sac.stlo+" stel="+sac.stel+" cmpaz="+sac.cmpaz+" cmpinc="+sac.cmpinc+" stdp="+sac.stdp);
    sac.y = new double[span.getNsamp()];   // allocate space for data
    int nodata = 0;
    for (int i = 0; i < span.getNsamp(); i++) {
      sac.y[i] = span.getData(i);
      if (sac.y[i] == fill) {
        nodata++;
        //if(nodata <3) Util.prt(i+" nodata len="+span.getNsamp());
      }
    }
    if (nodata > 0 && !quiet) {
      Util.prt("#No data points = " + nodata + " fill=" + fill + " npts=" + sac.npts);
    }
    if (sactrim) {
      int trimmed = sac.trimNodataEnd(fill);
      if (trimmed > 0) {
        Util.prt(trimmed + " data points trimmed from end containing no data");
      }
    }
    try {
      if (ascii) {
        try (BufferedWriter asc = new BufferedWriter(new FileWriter(filename + ".txt"))) {
          String line = "" + span.getNsamp();
          asc.write(line);
          asc.newLine();
          line = span.getStart().get(Calendar.YEAR) + "," + span.getStart().get(Calendar.DAY_OF_YEAR) + " " + Util.asctime2(span.getStart());
          asc.write(line);
          asc.newLine();
          line = "" + span.getRate();
          asc.write(line);
          asc.newLine();
          line = "";
          for (int i = 0; i < span.getNsamp(); i++) {
            line += span.getData(i) + ",";
            if (i != 0 && (i % 10) == 0) {
              asc.write(line);
              asc.newLine();
              line = "";
            }
          }
          asc.newLine();
          asc.flush();
        }
      }

      sac.write(filename);
    } catch (FileNotFoundException e) {
      Util.IOErrorPrint(e, "File Not found writing SAC");
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IO exception writing SAC");
      throw e;
    }

  }
}
