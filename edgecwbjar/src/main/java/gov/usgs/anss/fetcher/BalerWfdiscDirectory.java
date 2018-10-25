/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.GregorianCalendar;
import gov.usgs.anss.util.Subprocess;

/**
 * This class encapsulates a directory obtained from a baler 44. This normally
 * is of the files in the wfdisc area which are used to access data files in the
 * data and cont areas. This is curled in one piece and the HTML returned is
 * about 130 kB. This means a slow link Q330/Baler44 might be blocked off
 * significantly during this operation. It reads the HTML of the directory from
 * /WDIR/wfdisc directory which is just a list of files of the form
 * NN-SSSS_4-YYYYMMDDHHMMSS.wfdisc.gz. All files cause a Wfdisc object to be
 * created in the dirs variable. Other classes will use this list of wfdisc
 * files.
 *
 * <p>
 * This work was partially funded by Cal Tech under a contract in 2016.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class BalerWfdiscDirectory {

  ArrayList<Wfdisc> dirs = new ArrayList<>(200);
  GregorianCalendar g = new GregorianCalendar();
  private Baler44Request par;
  private long curlLength;
  private String url;
  private final int blockSize = 4096;
  private String directoryName;
  private final StringBuilder logsb = new StringBuilder(200);

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
      par.prt(s);
    }
  }

  public long getCurlLength() {
    return curlLength;
  }

  public ArrayList<Wfdisc> getDirectoryList() {
    return dirs;
  }

  public Wfdisc getWfdisc(int i) {
    return dirs.get(i);
  }

  public int getListSize() {
    return dirs.size();
  }

  private PrintStream getPrintStream() {
    if (par == null) {
      return Util.getOutput();
    }
    return par.getLog();
  }

  @Override
  public String toString() {
    return "WDIR: #dir=" + dirs.size() + " url=" + url;
  }

  /**
   * This checks to see if the station last wfdisc.dir is late enough to cover
   * this ending time, if it is, the cached one is parsed, if not then the URL
   * is read. This is used for Compressed balers
   *
   * @param host of the form of an IP address or DNS host
   * @param port The port to use
   * @param station The name of the station like USDUG
   * @param directoryName The name of the directory containing wfdisc files
   * (e.g. "wfdisc or wfdisk-20151210220550")
   * @param end The ending time that needs to be covered
   * @param path Path where baler related scratch and cache files are to be kept
   * @param limitRate Limit of the data rate in bits per second
   * @param timeout The timeout in seconds to abort at
   * @param parent A logging EdgeThread
   * @throws IOException if one occurs getting the data using curl or if reading
   * the resulting filename is bad
   */
  public BalerWfdiscDirectory(String host, int port, String station, String directoryName, long end, String path,
          int limitRate, int timeout, Baler44Request parent) throws IOException {
    par = parent;
    this.directoryName = directoryName;
    //int blkLimit = (int) (par.getLatencySignificant()/2. * par.throttle /8./blockSize); // number of blocks for significant at 2 times allowd rate
    //if(blkLimit <=0 ) blkLimit=1;
    this.url = host + ":" + port;
    if (Baler44Request.bdbg) {
      prta(Util.clear(logsb).append("BWFDIR: path=").append(path).append(" url=").append(url));
    }
    File p = new File(path);
    if (p.isDirectory()) {
      for (File f : p.listFiles()) {
        String fs = f.getName();
        if (fs.endsWith(directoryName + ".dir") && fs.startsWith(url + "-" + station) && f.length() > 500) {      // its a wfdisc.dir filename
          String time = fs.substring(fs.indexOf("_") + 1, fs.indexOf("." + directoryName + ".dir"));
          getEpochFromString(time, g);
          if (g.getTimeInMillis() > end + 24 * 3600000L || directoryName.contains("-")) {  // The time is o.k. read the filename
            if (parse(f.getCanonicalPath())) {
              prta(Util.clear(logsb).append("BWFDIR: found acceptable cache file ").append(fs).append(" end=").append(Util.ascdatetime(g)));
              return;
            } else {
              prta("BWFDIR: ** cached file did not parse - delete it and get it fresh");
              f.delete();
            }
          }
          if (System.currentTimeMillis() - g.getTimeInMillis() > 8 * 86400000L) {
            f.delete();   // filename is 8 days old   
          }
        }
      }
    } else {
      prta(Util.clear(logsb).append("BWFDIR: IOException on path=").append(path).append(" is not a directory!"));
      throw new IOException(Util.clear(logsb).append(" path ").append(path).append(" is not a directory!").toString());
    }

    // We did not find a suitable list of wfdisc directories, get a new one via the URL
    Util.clear(logsb).append(url).append("-").append(station.trim()).append("_").
            append(Util.ascdatetime(System.currentTimeMillis(), null).toString().
                    replaceAll("/", "").replaceAll(" ", "").replaceAll(":", "")).append(".").
            append(directoryName).append(".dir");
    String filename = logsb.toString();

    String line;
    if (directoryName.startsWith("list")) {
      Util.clear(logsb).append("curl -# -sS -o ").
              append(path).append("/").append(filename).append(timeout > 0 ? " --max-time " + timeout + " " : "").
              append(" http://").append(url).append("/").append(directoryName) //(limitRate > 0 ?" --limit-rate "+(limitRate/8/2):"")+ // /2 because this is a big one
              ;
    } else {
      Util.clear(logsb).append("curl -# -sS -o ").append(path).append("/").append(filename).
              append(timeout > 0 ? " --max-time " + timeout + " " : "").
              append(" http://").append(url).append("/WDIR/").append(directoryName).append("/") //(limitRate > 0 ?" --limit-rate "+(limitRate/8/2):"")+ // /2 because this is a big one
              ;
    }
    line = logsb.toString();
    try {
      prta(Util.clear(logsb).append("BFWDIR:  start directory fetch curls rl=").append(line));
      boolean done = false;
      while (!done) {
        curlLength = par.curlit(line, path + "/" + filename, 500);
        prta("DWFDIR: curl command complete.  Do parse");
        // If this is a uncompressed baler, the return is a long HTML document with the directory, insure it is completely read
        if (parse(path + "/" + filename)) {
          prta(Util.clear(logsb).append("BWFDIR: # of files in ").append(filename).
                  append(" is ").append(dirs.size()).append(" wait for changing latencies ...."));
          done = true;
        } else {
          prta("BWFDIR: **** parse failed. Try again");
          Util.sleep(30000);
        }
      }
      par.waitForChangingLatency();   // Make sure we get all caught up before moving on.
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
      throw e;
    }
    this.directoryName = directoryName;
  }

  /**
   * Close all resources with this object.
   */
  public void close() {     // release all resource from this object
    for (Wfdisc wf : dirs) {
      wf.close();
    }
    dirs.clear();
    par = null;
  }

  // Filename is like 
  private boolean parseCompress(String filename) {
    try {
      long avgLength;
      int nlengths = 0;
      try {
        Subprocess sp = new Subprocess("cp " + filename + " " + filename.replaceAll(".dir", ""));
        sp.waitFor(10000);
        par.unzip(filename.replaceAll(".dir", ""));
      } catch (InterruptedException e) {
        e.printStackTrace(getPrintStream());
      }

      try (BufferedReader in = new BufferedReader(new FileReader(filename.replaceAll(".gz.dir", "")))) {
        avgLength = 0;
        for (;;) {
          int len = Util.stringBuilderReadline(in, parsesb);
          if (len < 0) {
            break;
          }
          String name = "/WDIR/" + parsesb;
          //String name = line.substring(line.lastIndexOf(Util.fs)+1);
          String time = name.substring(name.indexOf("-", name.indexOf("_")) + 1, name.indexOf(".wfdisc"));// 20160615135726
          getEpochFromString(time, g);
          //Wfdisc wfd = new Wfdisc(url, name, directoryName.replaceAll("list.active.", "").replaceAll(".gz", ""), g.getTimeInMillis(),par);
          //Wfdisc wfd = new Wfdisc(url, name, directoryName.replaceAll("list.active.", "").replaceAll(".gz", ""), g.getTimeInMillis(),par);
          Wfdisc wfd = new Wfdisc(url, name, directoryName.replaceAll("list.active.", "").
                  replaceAll("list.reserve.", "").replaceAll(".gz", ""), g.getTimeInMillis(), par);
          //prta("Make size="+dirs.size()+" wfdisc="+wfd);
          dirs.add(wfd);
          if (dirs.size() >= 2) {
            dirs.get(dirs.size() - 2).setEnd(g.getTimeInMillis());
            avgLength += dirs.get(dirs.size() - 2).getEnd() - dirs.get(dirs.size() - 2).getStart();
            nlengths++;
          }

        }
      }
    } catch (IOException | RuntimeException e) {
      e.printStackTrace(getPrintStream());
      return false;
    }
    return true;
  }

  private boolean parse(String filename) {
    if (filename.contains("list.active") || filename.contains("list.reserve")) {
      return parseCompress(filename);
    } else {
      return parseUncompress(filename);
    }
  }
  private StringBuilder parsesb = new StringBuilder(100);

  private boolean parseUncompress(String filename) {
    try {
      long avgLength = 0;
      int nlengths = 0;
      File f = new File(filename);
      prta(Util.clear(logsb).append("parseUncompress filename=").append(filename).append(" exists=").append(f.exists()));
      boolean fileComplete;
      //String parsesb;
      try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
        //String parsesb;
        fileComplete = false;
        for (;;) {
          int len = Util.stringBuilderReadline(in, parsesb);
          if (parsesb.indexOf("</HTML>") >= 0 || parsesb.indexOf("</html?>") >= 0) {
            fileComplete = true;
          }
          if (len < 0) {
            break;
          }
          //( (line = in.readLine()) != null) {

          if (parsesb.indexOf("<A HREF=") >= 0 && parsesb.indexOf("wfdisc.gz") >= 0) {
            String name = parsesb.substring(parsesb.indexOf("HREF=") + 6, parsesb.indexOf(">") - 1);//WDIR/wfdisc/CI-RAG_4-20160515135726.wfdisc.gz
            String time = name.substring(name.indexOf("-", name.indexOf("_")) + 1, name.indexOf(".wfdisc"));// 20160615135726
            getEpochFromString(time, g);
            Wfdisc wfd = new Wfdisc(url, name, directoryName, g.getTimeInMillis(), par);
            //prta("Make size="+dirs.size()+" wfdisc="+wfd);
            dirs.add(wfd);
            if (dirs.size() >= 2) {
              dirs.get(dirs.size() - 2).setEnd(g.getTimeInMillis());
              avgLength += dirs.get(dirs.size() - 2).getEnd() - dirs.get(dirs.size() - 2).getStart();
              nlengths++;
            }
          }
        }
      }

      // is the file complete? (has a </html>)
      if (!fileComplete) {
        dirs.clear();     // no, try again
        return false;
      }
      if (nlengths > 0) {
        if (avgLength / nlengths < System.currentTimeMillis() - 900000 - dirs.get(dirs.size() - 1).getStart()) {
          dirs.get(dirs.size() - 1).setEnd(dirs.get(dirs.size() - 1).getStart() + (avgLength / nlengths));  // Set an estimated ending time
        } else {
          dirs.get(dirs.size() - 1).setEnd(System.currentTimeMillis() - 900000); // set it for 15 minutes ago
        }
      }
      return true;
    } catch (IOException | RuntimeException e) {
      e.printStackTrace(getPrintStream());
      return false;
    }
  }

  /**
   * given a YYYYMMDDhhmmss string, return a gregorian at that time.
   *
   * @param time The YYYYMMDDhhmmss string.
   * @param g User gregorian to return time in
   */
  public static void getEpochFromString(String time, GregorianCalendar g) {
    int year = Integer.parseInt(time.substring(0, 4));
    int month = Integer.parseInt(time.substring(4, 6));
    int day = Integer.parseInt(time.substring(6, 8));
    int hour = Integer.parseInt(time.substring(8, 10));
    int minute = Integer.parseInt(time.substring(10, 12));
    int second = Integer.parseInt(time.substring(12, 14));
    g.set(year, month - 1, day, hour, minute, second);
    g.setTimeInMillis(g.getTimeInMillis() / 1000 * 1000);
  }

  public void getWfdiscAt(long start, long end, ArrayList<Wfdisc> wfdisc) {
    long n = end - start;
    //prta("getWfdisc for "+Util.ascdatetime2(start)+"-"+Util.ascdatetime2(end)+" dirs.size="+dirs.size());
    for (int i = 0; i < dirs.size() - 1; i++) {  // if segment is not fully before after the files span, keep it
      //prt("i="+i+" "+dirs.get(i).toString()+" "+(dirs.get(i).getStart() - 4*3600000)+">"+end+" "+(dirs.get(i).getEnd() + 4*3600000)+"<"+start);
      if (!(dirs.get(i).getStart() - 4 * 3600000 > end || dirs.get(i).getEnd() + 4 * 3600000 < start)) {
        wfdisc.add(dirs.get(i));
      }
    }
  }

  public long curlit(String line, String file, int minlen, Baler44Request par) throws IOException {
    return par.curlit(line, file, minlen);
  }

  private static final StringBuilder wfdirsb = new StringBuilder(100);

  /**
   * Get the top level directory and return true if the compressed files
   * structures are available
   *
   * @param url
   * @param file
   * @param timeout
   * @param par
   * @param dirs
   * @throws IOException
   */
  public static void getWFDirs(String url, String file, int timeout, Baler44Request par, ArrayList<String> dirs)
          throws IOException {
    String line = "curl -# -sS -o " + file
            + (timeout > 0 ? " --max-time " + timeout + " " : "") + " http://" + url + "/WDIR/";
    par.prt("getWFDirs : " + line);
    boolean done = false;
    synchronized (wfdirsb) {
      while (!done) {
        par.curlit(line, file, 300);
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
          for (;;) {
            int len = Util.stringBuilderReadline(in, wfdirsb);
            if (len < 0) {
              if (!done) {
                par.prta("getWFDirs **** did not get complete file.  try again");
              }
              break;
            }
            if (wfdirsb.indexOf("</HTML>") >= 0) {
              done = true;
            }
            if (wfdirsb.indexOf("<A HREF=") >= 0 && wfdirsb.indexOf("wfdisc") >= 0) {
              String name = wfdirsb.substring(wfdirsb.indexOf("HREF=") + 6, wfdirsb.indexOf(">") - 1);// /WDIR/data/
              name = name.replaceAll("/WDIR/", "").replaceAll("/", "");
              dirs.add(name);
            }
          }
        }
      }
      if (dirs.isEmpty()) {
        par.prta(" ***** getWFDirs returned no directories - something is very wrong! file=" + file);
      }
      par.prta("getWFDirs #dirs=" + dirs.size());
    }
  }

}
