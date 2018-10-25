/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.IllegalSeednameException;
import java.io.File;
import java.io.IOException;
import java.io.EOFException;
import java.util.zip.GZIPInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.net.MalformedURLException;

/**
 * Every file in the /WDIR/wfdisc can create an object of this type. A list of
 * such files on a baler is created in the BalerWfdiscDirectory object. This
 * class uses the filename to set the the beginning time of data in this files,
 * and stores the url and filename should this file need to be curled from the
 * Baler and parsed using getWfdisc(). The file is read/decompressed/parsed by
 * readWfdisc() which stores all of the wfdisc lines in wfdisc variable which is
 * an ArrayList of strings. These lines can then be scanned to find the exact
 * block/offset of any data in the file (time, offset, channel/location,etc on
 * each line).
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class Wfdisc {

  private static final byte[] buf = new byte[4096];
  private byte[] fullbuf;
  private final String filename;
  private final String directoryName;   // this is the name of the wfdisk directory (wfdisc or wfdisc-yyyymmddhhmmss)
  private final String url;
  private final long start;
  private Baler44Request par;
  private long end;
  private boolean dbg;
  private final ArrayList<StringBuilder> wfdisc = new ArrayList<>(200);
  private final ArrayList<MatchBlock> offsetList = new ArrayList<>(100);
  private final StringBuilder logsb = new StringBuilder(200);

  public void close() {
    offsetList.clear();
    wfdisc.clear();
    par = null;
    fullbuf = null;

  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public String toString() {
    return url + " " + filename + " " + Util.ascdatetime2(start, null) + "-" + Util.ascdatetime2(end, null);
  }

  public Wfdisc(String url, String name, String directoryName, long st, Baler44Request parent) {
    this.url = url;
    par = parent;
    filename = name;
    this.directoryName = directoryName;
    start = st;
  }

  public String getFilename() {
    return filename.substring(filename.indexOf("wfdisc/") + 7);
  }

  public String getFullPathName() {
    return directoryName + Util.fs + filename;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  public void setEnd(long e) {
    end = e;
  }

  private void readWfdisc(String file) throws FileNotFoundException, IOException {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"))) {
      wfdisc.clear();
      int len;
      for (;;) {
        StringBuilder l = new StringBuilder(100);
        len = Util.stringBuilderReadline(in, l);
        if (len < 0 && l.length() == 0) {
          break;
        }
        wfdisc.add(l);
        if (len < 0) {
          break;
        }
      }
    }
  }

  class MatchBlock {

    long start;
    String chanLoc;
    int offset;
    int durMS;

    public long getStart() {
      return start;
    }

    public String getChanLoc() {
      return chanLoc;
    }

    public int getOffset() {
      return offset;
    }

    @Override
    public String toString() {
      return chanLoc + " " + Util.ascdatetime2(start) + " dur=" + durMS + "ms off=" + offset;
    }

    public MatchBlock(int offset, String chanLoc, long start, long durMS) {
      this.offset = offset;
      this.chanLoc = chanLoc;
      this.start = start;
      this.durMS = (int) durMS;
    }
  }

  /**
   * This returns a offset list where within the data file the data from the
   * right channel and spanning the desired timespan exist. This will cause the
   * file to be read from cache or curled from the baler depending on whether
   * the file exists in the cache. It builds an list of MatchBlocks that have
   * the chanLoc from the time period given. These are then used to actually
   * fetch the data from the data file.
   *
   *
   * @param path The path to the cache area
   * @param limitRate Rate limit in bps
   * @param timeout TImeout in seconds
   * @param chanLoc The channel and location code part of the Seedname
   * @param start Start time of the query in millis
   * @param end End time of the query in millis
   * @param mss User supplied list for the miniSEED block matching this query.
   * @param par A logging printstream, must not be NULL
   * @return The number of bytes curled
   * @throws FileNotFoundException
   * @throws IOException
   * @throws gov.usgs.anss.edge.IllegalSeednameException
   */
  public int getMiniSeed(String path, int limitRate, int timeout, String chanLoc, long start,
          long end, ArrayList<MiniSeed> mss, Baler44Request par)
          throws FileNotFoundException, IOException, IllegalSeednameException {
    long curlLength = 0;
    getWfdisc(path, limitRate, timeout, par);
    long earliest = Long.MAX_VALUE;
    long latest = Long.MIN_VALUE;
    double rate = -1.;
    for (StringBuilder line : wfdisc) {    // for each line in the WFDISC file
      if (line.length() < 256) {
        continue;     // no need to read short lines
      }
      String chloc = line.substring(7, 10); // Decode channel and location code, location codes are not in the baler!
      if (chanLoc.substring(0, 3).equals(chloc)) {                             // Is it the right one
        long time = (long) (Double.parseDouble(line.substring(16, 33).trim()) * 1000. + 0.5); // Yes decode elements need to decide if its in the time span
        String jdate = line.substring(53, 60);
        long endtime = (long) (Double.parseDouble(line.substring(62, 79).trim()) * 1000. + 0.5);
        //int nsamp = Integer.parseInt(line.substring(81,88).trim());
        double r = Double.parseDouble(line.substring(88, 99).trim());
        if (r > rate) {
          rate = r;
        }
        int offset = Integer.parseInt(line.substring(247, 257).trim());
        long diff = (endtime - end);
        StringBuilder t = Util.ascdatetime2(endtime);
        if (!(start > endtime || end < time)) {
          if (dbg) {
            par.prta(Util.clear(logsb).append("WFDISK: getMiniSEED ").append(chanLoc).
                    append(" offset=").append(offset).append(" rt=").append(rate).
                    append(" matches ").append(Util.ascdatetime2(time)).append("-").append(Util.ascdatetime2(endtime)).
                    append(" to ").append(Util.ascdatetime2(start)).append("-").append(Util.ascdatetime2(end)));
          }
          offsetList.add(new MatchBlock(offset, chanLoc, time, (endtime - time)));
        }
      }
    }
    if (offsetList.isEmpty()) {
      par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ").append(filename).
              append(" does not contain any data for this channel ").append(chanLoc).
              append("| in time range ").append(Util.ascdatetime2(start)).append(" - ").append(Util.ascdatetime2(end)));
      return 0;
    }    // Nothing in this file
    par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ").append(filename).append(" has ").
            append(offsetList.size()).append(" offsets which match ").append(chanLoc).append("|"));

    // estimate the number of blocks needed based on the data rate assuming 4000 samples per 4096 byte block
    int nblksEstimated = (int) ((end - start) * rate / 1000. / 4000. + 0.5); // basically number of samples divided by 2000

    // Check the data file name and see if this file is in the cache already
    int blockSize = 4096;
    int blkLimit = (int) (2. * par.getLatencySignificant() * par.throttle / 8. / blockSize); // number of blocks for significant at 2 times allowd rate
    String dataFilename = path + "/" + filename.replaceFirst("wfdisc", "data").
            replaceAll(".wfdisc.gz", "").replaceAll("/WDIR/", "");
    File data = new File(dataFilename.replaceAll("/WDIR/", ""));
    par.prta(Util.clear(logsb).append("WFDISC: getMiniSeed() start ").append(dataFilename).
            append(" exists=").append(data.exists()).append(" dur=").append((end - start) / 1000.).
            append(" estBlocks=").append(nblksEstimated).append(" #blocks in file=").append(offsetList.size()).
            append(" rt=").append(rate).append(" exists=").append(data.exists()).
            append(" len=").append(data.length()).append(" blkLimit=").append(blkLimit).
            append(" nblkExt=").append(nblksEstimated).append(" offsetsize=").append(offsetList.size()));
    int lastThrottle = par.throttle;
    // The request is for at least an hour, the number of blocks in a full file at this rate is expected to be at
    // least 10, and the number of blocks in the offset list is at least 10, then get the whole file rather than
    // curl it out one block at a time
    if ((!data.exists() || data.length() < 4096000) && (end - start > 3600000)
            && // The data file is not cached, the request looks big
            nblksEstimated >= 10 && offsetList.size() >= 10 && blkLimit > 4) {    // The request is for at least a couple of hours, get the whole file
      // and the blkLimit (gulpSize) is big enough to get the whole file piecemeal
      try {      // Need to fetch the bigger file in increments that should not take more than 1 minute to fetch
        if (fullbuf == null) {
          fullbuf = new byte[2 * blkLimit * blockSize];
        }
        if (fullbuf.length < blkLimit * blockSize) {
          fullbuf = new byte[2 * blkLimit * blockSize];
        }

        int offset = 0;
        if (limitRate > 0) {
          timeout = Math.max(timeout, blkLimit * blockSize * 8 / limitRate * 10); // adjust to 10 times limit rate
        }
        String outfilename = getFullPathName().replaceFirst("wfdisc", "data").replaceAll(".wfdisc.gz", "");
        Util.chkFilePath(dataFilename);
        RandomAccessFile out = new RandomAccessFile(dataFilename, "rw");  // Open the output file
        out.seek(0L);
        par.prta(Util.clear(logsb).append("WFDISK: getMiniSEED() Start entire fetch for ").append(dataFilename).
                append(" limitRate=").append(limitRate).append(" maxtime=").append(timeout).
                append(" blkLimit=").append(blkLimit).append(" dur=").append((end - start) / 1000));
        while (offset < 4096000) {
          limitRate = par.throttle;
          int upperOffset =  Math.min(offset + blkLimit * blockSize - 1, 4096000 - 1);// limit to 4 mB files size
          Util.clear(logsb).append("curl -# -sS -o ").append(dataFilename).append(".tmp").
                  append(limitRate > 0 ? " --limit-rate " + (limitRate / 8) : "").
                  append(timeout > 0 ? " --max-time " + timeout + " " : "").
                  append(" -r ").append(offset).append("-").append(upperOffset).
                  append(" http://").append(url).append(filename.replaceFirst("wfdisc", "data").replaceAll(".wfdisc.gz", ""));
          String curlline = logsb.toString();
          par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED()     fetch offset=").
                  append(Util.leftPad("" + offset, 7)).append("-").append(Util.leftPad("" + upperOffset, 7)).
                  append(" thr=").append(limitRate).append(dbg ? " curl:" + curlline : ""));
          try {
            curlLength += par.curlit(curlline, dataFilename + ".tmp", (upperOffset - offset + 1));
          }
          catch(MalformedURLException e) {
            if(e.toString().contains("Not Found")) {
              par.prta(Util.clear(logsb).append("File not found on curlit skip=").append(curlline).append(" skip"));
            }
            break;
          }
          try (RandomAccessFile in = new RandomAccessFile(dataFilename + ".tmp", "r")) {
            in.seek(0L);
            if (in.length() != upperOffset - offset + 1) {
              par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ** Input file is not expected length of ").
                      append(upperOffset - offset).append(1).append(" got ").append(in.length()));
              //continue;     // do this fetch again
            }
            in.read(fullbuf, 0, (int) in.length());
            par.prt(Util.clear(logsb).append("WFDISC: getMiniSEED() ms received=").append(MiniSeed.toStringRawSB(fullbuf)));
            out.write(fullbuf, 0, (int) in.length());

          }
          File tmp = new File(dataFilename + ".tmp");
          if (tmp.exists()) {
            tmp.delete();
          }
          offset += blkLimit * blockSize;

          // If the throttle was adjusted, reduce size of fetches by one with a limit of 2
          if (lastThrottle > par.throttle) {
            blkLimit--;
            if (blkLimit < 2) {
              blkLimit = 2;
            }
            par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ** is throttling down new blkLimit=").
                    append(blkLimit).append(" lastThr=").append(lastThrottle).append(" newThr=").append(par.throttle));
          }
          lastThrottle = par.throttle;
        }
      } catch (IOException e) {
        par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ** Got IO exception trying to read a whole file e=").append(e));
        e.printStackTrace(par.getLog());
        throw e;
      }
    }   // If we need to get the whole file

    // The file might have been fetched above or we might already have the file, check and load from the file if present.
    data = new File(dataFilename);
    if (data.exists() && data.length() >= 4096000) {
      par.prta(Util.clear(logsb).append("WFDISC: getMiniSeed() read data from full sized file ").append(dataFilename).
              append(" #offsets=").append(offsetList.size()));
      try ( // is the whole file here?
              RandomAccessFile raw = new RandomAccessFile(dataFilename, "r")) {
        for (MatchBlock blk : offsetList) {   // For each offset in this file
          raw.seek(blk.getOffset());
          raw.read(buf, 0, 4096);
          MiniSeed ms = new MiniSeed(buf);
          if (ms.getTimeInMillis() < earliest) {
            earliest = ms.getTimeInMillis();
          }
          if (ms.getNextExpectedTimeInMillis() > latest) {
            latest = ms.getNextExpectedTimeInMillis();
          }
          if (chanLoc.equals(ms.getSeedNameSB().substring(7))) {
            mss.add(ms);
          } else {
            par.prta(Util.clear(logsb).append("** loccode problem?  chLoc=").append(chanLoc).
                    append("| ms.seedname=").append(ms.getSeedNameSB()).append("|").
                    append(ms.getSeedNameSB().substring(10)).append("|").append(chanLoc.length()).
                    append(" ").append(ms.getSeedNameSB().length()).append("|").
                    append(Util.toAllPrintable(chanLoc)).append("|").append(Util.toAllPrintable(ms.getSeedNameSB())).append("|"));
          }
        }
      } catch (IOException e) {
        par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ** Got IO exception trying to read a whole file e=").append(e));
        e.printStackTrace(par.getLog());
        throw e;
      }
      par.prta(Util.clear(logsb).append("WFDISC: getMiniSeed() read disk ").append(mss.size()).
              append(" blks found from ").append(Util.ascdatetime2(earliest)).append("-").
              append(Util.ascdatetime2(latest)).append(" ").append((latest - earliest) / 1000.));
      return (int) curlLength;
    }

    // make up the outfile and curl lines for all of the blocks found, and create the file of miniSEED blocks
    if (!offsetList.isEmpty()) {
      String outfile = path + "/" + url + "_tmp.ms";
      int iblk = 0;
      par.prt(Util.clear(logsb).append("Do 4k Curls like :curl -# -sS -o ").append(outfile).
              append(timeout > 0 ? " --max-time " + timeout + " " : "").append(" -r 0-4095 http://").
              append(url).append(filename.replaceFirst("wfdisc", "data").replaceAll(".wfdisc.gz", "")));
      for (MatchBlock blk : offsetList) {
        Util.clear(logsb).append("curl -# -sS -o ").append(outfile).append(timeout > 0 ? " --max-time " + timeout + " " : "").
                append(" -r ").append(blk.getOffset()).append("-").append(blk.getOffset() + 4095).
                append(" http://").append(url).append(filename.replaceFirst("wfdisc", "data").replaceAll(".wfdisc.gz", "")) //(limitRate > 0 ?" --limit-rate "+(limitRate/8):"")+
                ;
        String curlline = logsb.toString();
        par.prta(Util.clear(logsb).append("Data curl:").append(iblk).append(" of ").
                append(offsetList.size()).append(" ").append(blk).append(dbg ? " curl=" + curlline : ""));
        iblk++;
        try {
          curlLength += par.curlit(curlline, outfile, 4096);     // curl some data 
        }
        catch(MalformedURLException e) {
          if(e.toString().contains("Not Found")) {
            par.prta(Util.clear(logsb).append("File not found on Baler - skip all read ").append(curlline));
            break;
          }
        }
        try ( // Read back the blocks, create a MiniSeed object for each 4096 bytes, and put them in the mss the user gave us
                RandomAccessFile rw = new RandomAccessFile(outfile, "rw")) {
          if (rw.length() != 4096) {
            par.prta(Util.clear(logsb).append("WFDISC: getMiniSEED() ** Length of fetched curl data is less than expected=").
                    append(offsetList.size()).append(" got ").append(rw.length() / 4096).append(" big MiniSEED blocks"));
          }
          rw.seek(0);
          synchronized (buf) {
            rw.read(buf, 0, 4096);
            MiniSeed ms = new MiniSeed(buf);
            if (ms.getTimeInMillis() < earliest) {
              earliest = ms.getTimeInMillis();
            }
            if (ms.getNextExpectedTimeInMillis() > latest) {
              latest = ms.getNextExpectedTimeInMillis();
            }
            mss.add(ms);
          }
        }
      }
      par.prt(Util.clear(logsb).append("WFDISC:getMiniSeed() curled ").append(mss.size()).
              append(" blks found from ").append(Util.ascdatetime2(earliest)).append("-").
              append(Util.ascdatetime2(latest)).append(" ").append((latest - earliest) / 1000.));

    }
    return (int) curlLength;

  }

  /**
   * This looks for the desired file on the scratch/cache path and if it finds
   * it, reads it. If it cannot be found in the cache, then the file is curled
   * over and then read.
   *
   * @param path The path to the cache area
   * @param limitRate Rate limit in bps
   * @param timeout TImeout in seconds
   * @param par A logging print stream
   * @throws FileNotFoundException
   * @throws IOException
   */
  private void getWfdisc(String path, int limitRate, int timeout, Baler44Request par) throws FileNotFoundException, IOException {
    // Do we have this files
    Util.clear(logsb).append(path).append("/").append(filename.replace("/WDIR/" + directoryName + "/", url + "-"));
    String file = logsb.toString();
    if (wfdisc.isEmpty()) {
      File p = new File(path);
      String compareFile = filename.replaceAll("/WDIR/" + directoryName + "/", url + "-");
      if (p.isDirectory()) {
        for (File f : p.listFiles()) {
          String fs = f.getName();
          // The file is on disk, read it in
          if (fs.equals(compareFile) && f.length() > 500) {      // its a wfdisc.dir file
            try {
              readWfdisc(f.getCanonicalPath());                 // read it
            } catch (EOFException e) { // This means the GUNZIPing the file got an unexpected ending
              par.prta(Util.clear(logsb).append(
                      "WFDISK: ** got EOF exception trying to read WFDISC file.  Try getting it from the Q330 again ").append(file));
              break;
            }
            return;
          }
        }
      } else {
        throw new IOException(Util.clear(logsb).append(" path ").append(path).append(" is not a directory!").toString());
      }

      // The file is not on disk,  get local filename using curl from baler file.
      boolean success = false;
      int tryCount = 0;
      while (!success) {
        try {
          int len = par.getFileWithCurl(url + filename, 1, file, timeout);    // curl it
          readWfdisc(file);                                         // read it
          par.prta(Util.clear(logsb).append("WFDISC: # of bytes in ").append(file).append(" is ").append(len).
                  append(" bytes and ").append(wfdisc.size()).append(" lines"));
          success = true;
        } catch (EOFException e) {   // This means the GUNZIPing the file got an unexpected ending
          tryCount++;
          par.prta(Util.clear(logsb).append("WFDISK: ** EOF reading WFDISK file - retry getting it with curl ").
                  append(tryCount).append(" ").append(file));
        } catch (IOException e) {
          e.printStackTrace(par.getLog());
          throw e;
        }
      }
    }
  }
}
