/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.io.RandomAccessFile;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class implements normal fetcher related interface. It uses the
 * BalerWfdiscDirectory and Wfdisc classes to handle the action with the baler
 * 44. This routine contains the general routine for executing a curl and
 * checking the latency and doing the dynamic throttle in method curlit(). The
 * rest of the methods are those needed to implement extension of the Fetcher
 * class to a concrete one.
 *
 * <PRE>
 * The behavior of this class is set in the -baler44:balerPath:balerLimit:balerTimeout to the fetcher.
 * Switch
 * -baler44 [[[balerPath[:balerLimit[:balerTimeout]]]
 *
 * args for Baler44 switch
 * balerPath    A path to use for the working directory (def=Baler44)
 * balerLimit   The throttle bandwidth - def=fetchers throttle variable
 * balerTimeout The timeout to use on curl commands (def=3600 seconds)
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at stw-software.com>
 */
public final class Baler44Request extends Fetcher {

  static public boolean bdbg = false;
  private final int blockSize = 4096;
  private byte[] fullbuf;
  private Subprocess sp;
  private final String station;
  private String balerPath = "Baler44";
  private int balerLimit; // bps
  private int balerTimeout = 600;
  private long lastStats;           // track last time stats.html was fetched.
  private final long[] lastLen = new long[5];      // Used by curlit to track odd length files.
  private final StringBuilder curlitsb = new StringBuilder(400);
  private final StringBuilder curlitsb2 = new StringBuilder(1);
  private final ArrayList<String> statdirs = new ArrayList<String>(5); // List of top level directories for compressed data
  private final ArrayList<Wfdisc> wfdiscs = new ArrayList<>(20);    // Place to put matching wfdisc files
  private boolean compressedFormat;
  private final ArrayList<MiniSeed> mss = new ArrayList<>(1);
  private final ArrayList<BalerWfdiscDirectory> wfdirs = new ArrayList<>(3);

  public boolean isCompressedFormat() {
    return compressedFormat;
  }

  @Override
  public void closeCleanup() {
    prta(station + " closeCleanup - terminate mysql and any subprocesses");
    //if(mysqlanss != null) mysqlanss.terminate();
    if (sp != null) {
      sp.terminate();
    }
  }

  public Baler44Request(String[] args) throws SQLException {
    super(args);
    station = getSeedname().substring(2, 7).trim() + getSeedname().substring(10).trim();
    balerLimit = throttle;
    for (String arg : args) {
      if (arg.startsWith("-baler44")) {
        String[] parts = arg.split(":");
        if (parts.length > 1) {
          balerPath = parts[1];
        }
        if (parts.length > 2) {
          balerLimit = Integer.parseInt(parts[2]);
          throttle = balerLimit;
        }
        if (parts.length > 3) {
          balerTimeout = Integer.parseInt(parts[3]);
        }
      }
    }

    // Since this is a thread mysqanss might be in use by other threads.  Always
    // create
    tag = "Baler44" + getName().substring(getName().indexOf("-")) + "-" + getSeedname().substring(2, 7).trim() + ":";
    prt(tag + " start " + orgSeedname + " " + host + "/" + port);
    if (log == null) {
      log = Util.getOutput();    // If not started in full fetchers, there might not yet be a log stream.
    }
  }

  @Override
  public void startup() {
    try {
      compressedFormat
              = isCompressedBaler44(host + ":" + port, balerPath, balerTimeout);
      //compressedFormat=false;     //DEBUG: force old way
    } // Is this a compressed directory
    catch (IOException e) {
      Util.prta("IOException in isCompressedBaler44 **** this is not a compressed station? " + this.station + " " + host + ":" + port + " e=" + e);
      prta("IOException in isCompressedBaler44 **** this is not a compressed station? " + this.station + " " + host + ":" + port + " e=" + e);
      compressedFormat = false;
      //e.printStackTrace(getPrintStream());
    }
    checkNewDay();
  }

  /**
   * get a file from a Baler44 and put it in a disk file.
   *
   * @param url The URL of the file on the baler. This combined with the url
   * gives the curl target
   * @param blkLimit The number of 4096 blocks to read in any individual curl
   * @param dataFilename Name of the file to write to diskThe name of the file
   * on the baler. This combined with the url gives the curl target
   * @param timeout The timeout to use for the curl command
   * @return The length of the file received in bytes.
   * @throws IOException If there is a disk problem
   */
  public int getFileWithCurl(String url, int blkLimit, String dataFilename, int timeout) throws IOException {
    int offset = 0;
    int fileLength = 0;
    if (fullbuf == null) {
      fullbuf = new byte[2 * blkLimit * blockSize];
    }
    if (fullbuf.length < blkLimit * blockSize) {
      fullbuf = new byte[2 * blkLimit * blockSize];
    }
    RandomAccessFile out = new RandomAccessFile(dataFilename, "rw");  // Open the output file
    out.seek(0L);
    while (offset < 4096000) {
      int upperOffset = offset + blkLimit * blockSize - 1;
      String curlline = "curl -# -sS -o " + dataFilename + ".tmp"
              +//(limitRate > 0 ?" --limit-rate "+(limitRate/8):"")+
              (timeout > 0 ? " --max-time " + timeout + " " : "")
              + " -r " + offset + "-" + upperOffset
              + " http://" + url;
      prta("WFDISC:fetch a file offset=" + offset + " curl:" + curlline);
      fileLength += curlit(curlline, dataFilename + ".tmp", upperOffset - offset - 1);
      try (RandomAccessFile in = new RandomAccessFile(dataFilename + ".tmp", "r")) {
        in.seek(0L);

        in.read(fullbuf, 0, (int) in.length());
        out.write(fullbuf, 0, (int) in.length());
        File tmp = new File(dataFilename + ".tmp");
        if (tmp.exists()) {
          tmp.delete();
        }
        if (in.length() != upperOffset - offset + 1) {
          prta("Input file is not expected length of " + (upperOffset - offset + 1) + " got " + in.length() + " EOF");
          break;
        }
      }
      offset += blkLimit * blockSize;
    }
    return fileLength;
  }

  public Subprocess unzip(String file) throws IOException {
    Subprocess sp2 = null;
    try {
      sp2 = new Subprocess("gunzip -f " + file);
      sp2.waitFor(10000);
      if (sp2.getOutput().length() > 1) {
        prt(sp2.getOutput());
      }
      if (sp2.getErrorOutput().length() > 1) {
        prt(sp2.getErrorOutput());
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return sp2;
  }

  public final boolean isCompressedBaler44(String url, String path, int timeout) throws IOException {
    prta("isCompressedBaler44 starting " + url + "-list.active.wfdisc.gz");
    File f = new File(path + "/" + url + "-list.active.wfdisc.gz");
    if (f.exists()) {
      f.delete();
    }
    f = new File(path + "/" + url + "-list.active.wfdisc");
    if (f.exists()) {
      f.delete();
    }
    String line = "curl -# -sS -o " + path + "/" + url + "-list.active.wfdisc.gz " 
            + (timeout > 0 ? " --max-time " + timeout + " " : "")
            + "http://" + url + "/list.active.data.gz";
    curlit(line, path + "/" + url + "-list.active.wfdisc.gz", 200);
    f = new File(path + "/" + url + "-list.active.wfdisc.gz");
    if (f.exists()) {
      try {
        Subprocess sp2 = unzip(path + "/" + url + "-list.active.wfdisc.gz");
      } catch (IOException e) {
        prta("isCompressedBaler44 returning false - UNZIP failed - likely FileNotFound");
        throw e;
      }
      try (BufferedReader in = new BufferedReader(new FileReader(path + "/" + url + "-list.active.wfdisc"))) {
        while ((line = in.readLine()) != null) {
          prta("isCompressedBaler44 returning " + line.startsWith("data/") + " f=" + f.getAbsolutePath() + " " + f.length() + " b");
          return line.startsWith("data/");
        }
      }
    }
    prta("isCompressedBaler44 returning false");
    return false;
  }

  /**
   * Get a Stats.html from a unit, only due this once per day after initial one.
   * This contains the compressed directories of storage areas like
   * list.active.wfdisc or list.reserve.wfdisc.
   *
   * @param url URL to the Q330, form ip.adrs:port
   * @param file The full path name to where to put the file
   * @param timeout A timeout on the curl request
   * @param par A Baler44Request to use for loging
   * @throws IOException
   */
  public void getWFStatFile(String url, String file, int timeout, Baler44Request par)
          throws IOException {
    boolean curlit = true;
    if (lastStats == 0) {
      File p = new File(file);
      if (p.exists()) {
        lastStats = p.lastModified();
      }
      curlit = (System.currentTimeMillis() - lastStats > 86400000);
      par.prta("Stat file=" + file + " exists=" + p.exists() + " curlit needed=" + curlit);
    } else {// Only do curl every day or so 
      if (System.currentTimeMillis() - lastStats < 86400000 && !statdirs.isEmpty()) {
        par.prta("Stat file age is o.k.  reuse it f=" + file);
        return;
      }
    }
    statdirs.clear();
    String line = "curl -# -sS -o " + file
            + (timeout > 0 ? " --max-time " + timeout + " " : "") + " http://" + url + "/stats.html";
    lastStats = System.currentTimeMillis();
    if (curlit) {
      par.curlit(line, file, 2000);
    }
    par.prta("Get stats.html to " + file);
    //String path = file.substring(0, file.lastIndexOf("/")+1);
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      while ((line = in.readLine()) != null) {
        if (line.contains("<A HREF=") && line.contains("wfdisc")) {
          String name = line.substring(line.indexOf("HREF=") + 6, line.indexOf(">") - 1);// /WDIR/data/
          name = name.replaceAll("/WDIR/", "").replaceAll("/", "");
          statdirs.add(name);

        }
      }
    }
    par.prta("Stat file size=" + statdirs.size() + " directories");
    for (String s : statdirs) {
      par.prt(s);
    }
  }

  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) {
    if (compressedFormat) {
      return getDataCompressed(fetch);
    } else {
      return getDataCompressed(fetch);    // DEBUG: this should be merged to simply get data when proven to work
    }
  }

  public ArrayList<MiniSeed> getDataCompressed(FetchList fetch) {
    mss.clear();
    try {
      if (compressedFormat) {
        // Make up a filename to store the highest level directory "/WDIR" and get how many wfdisc directories are in it
        String f = balerPath + "/" + host + "-" + port + "-stats.html";

        // get or refresh the statdirs list of top level compressed directories.  This contains list of data files
        getWFStatFile(host + ":" + port, f, balerTimeout, this);  // get the top level directory
        //for(int i=0; i<statdirs.size(); i++) prta("getWFStat: directory found "+statdirs.get(i));
        prta(tag + " BalerWfdiscDirectory called " + host + "/" + port + " " + getSeedname()
                + " dur=" + fetch.getDuration() + " " + Util.ascdatetime2(fetch.getStart().getTime()) + " #dirs=" + statdirs.size());
        // create a BalerWfdiscDirectory object for each of the directories, these contain the name
        // of all of the wfdisc.gz files, but does not download any of them.  Put all of them in wfdirs
        for (int i = 0; i < statdirs.size(); i++) {
          String dir = statdirs.get(i);
          try {
            if (dir != null) {
              BalerWfdiscDirectory wfdir = new BalerWfdiscDirectory(host, port,
                      getSeedname().substring(0, 2) + "-" + getSeedname().substring(2, 7).trim(), dir,
                      fetch.getStart().getTime() + (long) (fetch.getDuration() * 1000.),
                      balerPath, throttle, balerTimeout, this);
              prta(tag + " BalerWfdiscDirector returned " + wfdir + " bdbg=" + bdbg + " for dir=" + dir);
              wfdirs.add(wfdir);
            }

          } catch (MalformedURLException e) {
            statdirs.set(i, null);
            prta(tag + " *** give up on directory=" + dir + " set to NULL e=" + e);
          }
        }
      } else {
        // Make up a filename to store the highest level directory "/WDIR" and get how many wfdisc directories are in it
        String f = balerPath + "/" + host + "-" + port + ".dir";
        ArrayList<String> dirs = new ArrayList<>(4);
        BalerWfdiscDirectory.getWFDirs(host + ":" + port, f, balerTimeout, this, dirs);  // get the top level directory

        prta(tag + " BalerWfdiscDirectory called " + host + "/" + port + " " + getSeedname()
                + " dur=" + fetch.getDuration() + " " + Util.ascdatetime2(fetch.getStart().getTime()));
        // create a BalerWfdiscDirecotry object for each of the directories, these contain the name
        // of all of the wfdisc.gz files, but does not download any of them.  Put all of them in wfdirs
        for (String dir : dirs) {
          BalerWfdiscDirectory wfdir = new BalerWfdiscDirectory(host, port,
                  getSeedname().substring(0, 2) + "-" + getSeedname().substring(2, 7).trim(), dir,
                  fetch.getStart().getTime() + (long) (fetch.getDuration() * 1000.),
                  balerPath, throttle, balerTimeout, this);
          prta(tag + " BalerWfdiscDirector returned " + wfdir + " bdbg=" + bdbg);
          wfdirs.add(wfdir);
        }
      }

      // Get a list of the wfdisc files that might contains some of the data from each of the Wfdisc files
      // a wfdisc that matches is still not fetched, only its data path is added.
      long start = fetch.getStart().getTime();
      long end = start + (long) (fetch.getDuration() * 1000);
      wfdiscs.clear();                            // clear the list of matching wfdisc files
      for (BalerWfdiscDirectory wfdir : wfdirs) {
        wfdir.getWfdiscAt(start, end, wfdiscs);   // This examines the directory and add to list of possible wfdisc files
        prta(tag + "Number of files is " + wfdir.getListSize() + " need to look at " + wfdiscs.size() + " total");
      }

      String chanLoc = (fetch.getSeedname().substring(7) + "     ").substring(0, 5);

      // For each wfdisc returned, get the miniseed by issuing a curl command, many might add to the mss structure
      int curlen =0;
      try {
        for (int i = 0; i < wfdiscs.size(); i++) {
          prt(i + " " + chanLoc + " " + wfdiscs.get(i).toString() + " matches " + Util.ascdatetime2(start) + "-" + Util.ascdatetime2(end));
          curlen += wfdiscs.get(i).getMiniSeed(balerPath, throttle, balerTimeout, chanLoc, start, end, mss, this);
        }
      } catch (IllegalSeednameException | IOException e) {
        if(log == null) {
          e.printStackTrace();
        }
        else {
          e.printStackTrace(log);
        }
      }

      // release the space in the BalerWfdiscDirectories, they are no longer needed
      for (BalerWfdiscDirectory wfd : wfdirs) {
        wfd.close();
      }
      wfdirs.clear();

      prt(tag + "Number of blocks returned =" + mss.size()+" curlen="+curlen);
      if (bdbg) {
        for (int i = 0; i < mss.size(); i++) {
          prt(i + " " + mss.get(i).toString());
        }
      }
      prt(tag + " return size mss=" + mss.size());
      return mss.isEmpty() ? null : mss;
    } catch (IOException e) {
      e.printStackTrace(log);
    }
    return null;
  }

  /**
   * retrieve the data in the fetch entry. Uses the various baler 44 classes to
   * get the data
   *
   * @param fetch A fetch list object containing the fetch details
   * @return The MiniSeed data resulting from the fetch
   */
  public ArrayList<MiniSeed> getDataUncompressed(FetchList fetch) {
    mss.clear();
    ArrayList<String> dirs = new ArrayList<>(3);

    try {
      // Make up a filename to store the highest level directory "/WDIR" and get how many wfdisc directories are in it
      String f = balerPath + "/" + host + "-" + port + ".dir";
      BalerWfdiscDirectory.getWFDirs(host + ":" + port, f, balerTimeout, this, dirs);  // get the top level directory

      prta(tag + " BalerWfdiscDirectory called " + host + "/" + port + " " + getSeedname() + " #dirs=" + dirs.size()
              + " dur=" + fetch.getDuration() + " " + Util.ascdatetime2(fetch.getStart().getTime()));
      // create a BalerWfdiscDirecotry object for each of the directories, these contain the name
      // of all of the wfdisc.gz files, but does not download any of them.  Put all of them in wfdirs
      for (String dir : dirs) {
        BalerWfdiscDirectory wfdir = new BalerWfdiscDirectory(host, port,
                getSeedname().substring(0, 2) + "-" + getSeedname().substring(2, 7).trim(), dir,
                fetch.getStart().getTime() + (long) (fetch.getDuration() * 1000.),
                balerPath, throttle, balerTimeout, this);
        prta(tag + " BalerWfdiscDirector returned " + wfdir + " bdbg=" + bdbg);
        wfdirs.add(wfdir);
      }

      // Get a list of the wfdisc files that might contains some of the data from each of the Wfdisc files
      // a wfdisc that matches is still not fetched, only its data path is added.
      //ArrayList<Wfdisc> wfdiscs = new ArrayList<>(20);    // Place to put matching wfdisc files
      wfdiscs.clear();
      long start = fetch.getStart().getTime();
      long end = start + (long) (fetch.getDuration() * 1000);
      for (BalerWfdiscDirectory wfdir : wfdirs) {
        wfdir.getWfdiscAt(start, end, wfdiscs);
        prta(tag + "Number of files is " + wfdir.getListSize() + " need to look at " + wfdiscs.size());
      }
      String chanLoc = (fetch.getSeedname().substring(7) + "     ").substring(0, 5);

      // For each wfdisc returned, get the miniseed by issuing a curl command, many might add to the mss structure
      try {
        for (int i = 0; i < wfdiscs.size(); i++) {
          prt(i + " " + chanLoc + " " + wfdiscs.get(i).toString() + " matches " + Util.ascdatetime2(start) + "-" + Util.ascdatetime2(end));
          wfdiscs.get(i).getMiniSeed(balerPath, throttle, balerTimeout, chanLoc, start, end, mss, this);
        }
      } catch (IllegalSeednameException | IOException e) {
        e.printStackTrace();
      }

      // release the space in the BalerWfdiscDirectories, they are no longer needed
      for (BalerWfdiscDirectory wfd : wfdirs) {
        wfd.close();
      }
      wfdirs.clear();

      prt(tag + "Number of blocks returned =" + mss.size());
      for (MiniSeed ms : mss) {
        Util.prt(ms.toString());
      }
      prt(tag + " return size mss=" + mss.size());
    } catch (IOException e) {
      e.printStackTrace(log);
    }
    if (mss.isEmpty()) {
      return null;
    }
    return mss;

  }

  /**
   * This methods creates a subprocess to exeucte a curl command and writes the
   * results in a file. The minimum length must be reached or the curl is
   * presumed defective. This curlit differs from the one in Util in that it
   * checks the latency of the station before starting and does the dynamic
   * throttle adjustment for the station after each curl.
   *
   * @param line Curl command line
   * @param file Filename to write with curl results, the curl command MUST make
   * this file
   * @param minlen Minimum length, if not reached retry the operation as
   * something went wrong
   * @return The length of the returned curl file.
   * @throws IOException
   * @throws MalformedURLException if the curl request returns a HTTP type error
   * page.
   */
  public long curlit(String line, String file, int minlen) throws IOException, MalformedURLException {
    long len = 0;
    int loopMinLength = 0;
    Arrays.fill(lastLen, Long.MIN_VALUE);
    //par.println("Curl file="+file+" line="+line);
    while (len < minlen) {
      if (bdbg) {
        prta("curlit waiting for latency");
      }
      waitForChangingLatency();
      lastThrottleCheck = System.currentTimeMillis();   // since we are waiting on latency, throttle evaluation starts now!
      if (bdbg) {
        prta("curlit is within latency");
      }
      Subprocess curl = new Subprocess(line);
      int loop = 0;
      while (curl.exitValue() == -99) {
        Util.sleep(100);
        if (loop++ % 200 == 0) {
          if (bdbg) {
            prta("curlit is waiting to exit");
          }
          if (curl.getOutput().length() > 1) {
            prta(curl.getOutput());
          }
          if (curl.getErrorOutput().length() > 1) {
            prta(curl.getErrorOutput());
          }
        }
      }
      if (bdbg) {
        prta("curlit process completed.");
      }
      if (curl.getErrorOutput().length() > 1) {
        prta("curlit line=" + line + "\ncurlit: err=" + curl.getErrorOutput());
      }
      if (curl.getOutput().length() > 1) {
        prta("curlit line=" + line + "\ncurlit: out=" + curl.getOutput());
      }
      if (curl.exitValue() != 0) {
        String err;
        switch (curl.exitValue()) {
          case 3:
            err = "URL Malformed";
            break;
          case 7:
            err = "Failed to connect";
            break;
          case 18:
            err = "Partial file";
            break;
          case 22:
            err = "HTTP page not retreived";
            break;
          case 23:
            err = "File write failed - permissions?";
            break;
          case 26:
            err = "Read error";
            break;
          case 28:
            err = "Operation timed out.  The specified timeout- period was reched according to conditions";
            break;
          case 37:
            err = "File read problem.  Permissions?";
            break;
          case 48:
            err = "Unknown TELNET option specified.";
            break;
          case 52:
            err = "Nothing in reply";
            break;
          case 56:
            err = "Failure in receiving data";
            break;
          default:
            err = "Unknown";
        }
        prta("curlit: ***** exit=" + curl.exitValue() + " " + err + " curl: " + line);
        if(line.contains("list.active.wfdisc.gz")) {
          Util.sleep(270000);     // make it 5 minutes betweens attempts as likely the station is down.
        }
        Util.sleep(30000);
        continue;
      }
      File f = new File(file);

      if (f.exists()) {    // Is the expected file there?
        len = f.length();
        lastLen[loopMinLength] = len;
        loopMinLength++;
        if (loopMinLength < 5) {
          if (len < minlen) { // Is it long enough
            if (lastLen[0] == lastLen[1] && lastLen[0] == lastLen[2]) {
              prta("curlit - file read looks to be at EOF len=" + len+" "+line);
              Util.readFileToSB(file, curlitsb);
              if (curlitsb.indexOf("Bad Request") >= 0 || curlitsb.indexOf("bad syntax") >= 0 || 
                      curlitsb.indexOf("Not Found") >= 0) {
                prta("curlit - BAD CURL command " + line+"\n" + curlitsb);
                int ititle = curlitsb.indexOf("<TITLE>");
                String addon = "NONE";
                if (ititle >= 0) {
                  addon = curlitsb.substring(ititle + 7, curlitsb.indexOf("</TITLE>"));
                }
                MalformedURLException e = new MalformedURLException("Request is bad " + addon + " line=" + line);
                if(curlitsb.indexOf("Not Found") < 0) {
                  if (log == null) {
                    e.printStackTrace();
                  } else {
                    e.printStackTrace(log);
                  }
                }
                throw e;

              }
              prta("curlit consistently gives under sized file.  Assume the file is right! len=" + len+" loop="+loopMinLength);
              break;
            }
            prta("curlit file is too short len=" + len + " try again");

            // See if this is a printable ASCII return - if so print it
            Util.readFileToSB(file, curlitsb);
            if (!line.endsWith(".gz")) {
              int nprint = 0;
              for (int i = 0; i < curlitsb.length(); i++) {
                Util.clear(curlitsb2).append(curlitsb.charAt(i));
                if (Character.isLetterOrDigit(curlitsb.charAt(i))
                        || curlitsb.charAt(i) == '"'
                        || Character.isSpaceChar(curlitsb.charAt(i))
                        || "<>-.#!?&=()[]{};',$@^&*-_+".contains(curlitsb2)) {
                  nprint++;
                }
              }
              if (nprint > curlitsb.length() * 9 / 10) {
                prta("Curlit - too short and ASCII len="+curlitsb2.length()+" buf=" + curlitsb2+"|");
                
              }
            }
            prta("curlit file too short ");
            f.delete();
          }
        } else {
          prta("curlit:  ** file size too small many times.  Size=" + len + " return anyway");
          break;
        }
      } else {
        prta("curlit: ** file does not exist after command ran.  What is wrong!  Try again!");
      }
    }
    //if(bdbg) prta("curlit do dynamic throttle len="+len);
    // Use waitForChangingLatency instead if(len > 512 && balerLimit < 100000) doDynamicThrottle((int) len);   // Do a dynamic throttle to pick up changes to throttle rate.
    if (bdbg) {
      prta("curlit returns len=" + len);
    }
    return len;
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {

    Util.setModeGMT();
    Util.init("edge.prop");
    boolean single = false;
    //if(args.length == 0) args = "-s CIRAG--BHZ-- -b 2016/06/18 12:00 -d 10800 -1 -t B4 -h 166.161.139.83 -p 5381 -baler44:/Users/ketchum/source/Baler44/temp:250000:300".split("\\s");
    if (args.length == 0) /*args = 
            ("-s CIUSC--BHZ-- -b 2017/01/15 12:00 -d 10800 -1 -t B4 -h 68.181.113.48 -p 5381 "+
            "-baler44:/Users/ketchum/source/Baler44/temp:250000:300 -latlevel 5").split("\\s");*/ {
      args
              = ("-s CIBEL--BHZ-- -b 2017/01/15 12:00 -d 600 -1 -t B4 -h 166.248.232.101 -p 6381 "
                      + "-baler44:/Users/ketchum/source/Baler44/temp:250000:300 -latlevel 5").split("\\s");
    }
    // set the single mode flag
    for (String arg : args) {
      if (arg.equals("-1")) {
        single = true;
      }
    }

    // Try to start up a new requestor
    try {
      EdgeChannelServer echn = new EdgeChannelServer("-empty >>echnft", "TAG");
      while (!EdgeChannelServer.isValid()) {
        Util.sleep(1000);
      }
      Baler44Request baler44 = new Baler44Request(args);
      /*for(int i=0; i<30; i++) {
        ChannelStatus cs = baler44.getChannelStatus();
        Util.prt("cs="+cs);
        
        Util.sleep(2000);
      }*/
      if (single) {
        ArrayList<MiniSeed> mss = baler44.getData(baler44.getFetchList().get(0));
        if (mss == null) {
          Util.prt("NO DATA was returned for fetch=" + baler44.getFetchList().get(0));
        } else if (mss.isEmpty()) {
          Util.prt("Empty data return - normally leave fetch open " + baler44.getFetchList().get(0));
        } else {
          try (RawDisk rw = new RawDisk("tmp.ms", "rw")) {
            for (int i = 0; i < mss.size(); i++) {
              rw.writeBlock(i * 8, mss.get(i).getBuf(), 0, 4096);
              Util.prt(mss.get(i).toString());
            }
            rw.setLength(mss.size() * 4096L);
          }
        }
        Util.exit(0);
      } else {      // Do fetch from table mode 
        baler44.startit();
        while (baler44.isAlive()) {
          Util.sleep(1000);
        }
      }
    } catch (IOException e) {
      Util.prt("IOError=" + e);
      e.printStackTrace();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Impossible in test mode");
    }
  }
}
