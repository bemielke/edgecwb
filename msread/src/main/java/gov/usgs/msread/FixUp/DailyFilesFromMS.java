/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.msread.FixUp;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.EOFException;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * The DailyFilesFromMS reads a miniSEED files (normally a EDGE/CWBF and creates daily miniSEED
 * files with all duplicates eliminated - this was written to clean up a problem at Caltech where a
 * data loop blew up some data files.   It was based on the DailyFileWrite class used to write out 
 * day/chan files at ASL
 * <p>
 * These channel days are used at ASL and others for local copy
 * of the data. The key for ASL is like "/tr1/telemetry_days/%n_%s/%y/%y_%j/%l_%c.512.seed". Note:
 * You can use underscores in the filemask - the underscores filling fields will be removed and only
 * the ones in the mask will remain with all of the fields trimmed.
 * <p>
 * <PRE>
 *switch   arg       Description
 * -dbg               Turn debug output on
 * -f      file  Use this ring file as input
 * -mutex  filepath  Use the presence of this file as a mutex for operation, if the file does not exist or disappears - stop processing.
 * -mask   filenmask   The ringfile name to use.  This is used to build the file name  various
 * %N the whole seedname NNSSSSSCCCLL");
 * %n the two letter SEED network          %s the 5 character SEED station code");
 * %c the 3 character SEED channel         %l the two character location");
 * %y Year as 4 digits                     %Y 2 character Year");
 * %j Day of year (1-366)                  %J Julian day (since 1572)");
 * %M 2 digit month                        %D 2 digit day of month");
 * %h 2 digit hour                         %m 2 digit minute");
 * %S 2 digit second                       %z zap all underscores from name");
 * %a Convert full file name to lower case");
 *                   options are available %y=YYYY %j=
 * </PRE>
 *
 * @author davidketchum
 */
public final class DailyFilesFromMS extends EdgeThread {

  private final static TreeMap<ByteBuffer, DailyFile> files = new TreeMap();
  private final static TreeMap<String, ArrayList<Long>> chantimes = new TreeMap<>();
  private String filemask;
  private String ringfile;
  private MutexFile mutex;
  private RawDisk in;
  private final ShutdownDailyFileWriter shutdown;
  private int nblocks;
  private boolean dbg;
  private byte[] key2 = new byte[12];  // used by keyToString()

  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    try {
      in.close();
    } catch (IOException expected) {
    }
    interrupt();
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append("DFFMS: nblks=").append(nblocks).append(" #files=").append(files.size()).
            append(" msp=").append(DailyFile.getMiniSeedPool().toString());
  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb.append("DFFMS: nblks=").append(nblocks).append(" #files=").append(files.size()).
            append(" msp=").append(DailyFile.getMiniSeedPool().toString());
  }

  /**
   * return console output - this is fully integrated so it never returns anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to the host/port
   * source of data. This one gets its arguments from a command line
   *
   * @param argline The argument line to parse for running arguments
   * @param tg the logging tag
   */
  public DailyFilesFromMS(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    boolean nocsend = false;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-mask")) {
        filemask = args[i + 1];
        i++;
      } else if (args[i].equals("-f")) {
        ringfile = args[i + 1];
        i++;
      } else if (args[i].equals("-instance") || args[i].equals("-i")) {
        i++;
      } else if (args[i].equals("-mutex")) {
        try {
          mutex = new MutexFile(args[i + 1]);
        } catch (IOException e) {
          prta("MutexFile exception e=" + e);
          Util.exit(0);
        }
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("DFFMS: unknown switch=" + args[i] + " ln=" + argline);
      }

    }
    prt("DFFMS: created args=" + argline + " tag=" + tag + " mask=" + filemask + " ring=" + ringfile);
    shutdown = new ShutdownDailyFileWriter();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any information on that socket,
   * keeps a list of unique StatusInfo, and updates that list when new data for the unique key comes
   * in.
   */
  @Override
  public void run() {
    running = true;
    byte[] buf = new byte[4096];
    MiniSeed ms = null;
    int nzero = 0;
    boolean opened = false;
    byte[] keybuf = new byte[16];
    ByteBuffer key = ByteBuffer.wrap(keybuf);
    long lastStatus = System.currentTimeMillis();
    int nlookback = 200000;
    ArrayList<byte[]> lookback = new ArrayList<>(nlookback);
    int ilook = 0;
    long ndup = 0;
    byte[] seedtmp = new byte[12];
    StringBuilder chan = new StringBuilder(12);
    while (!opened) {
      try {
        in = new RawDisk(ringfile, "r");
        opened = true;
      } catch (FileNotFoundException e) {
        prt("DFFMS: ring file was not found- cannot start! file=" + ringfile);
        try {
          sleep(10000);
        } catch (InterruptedException expected) {
        }
      } 
    }
    int iblk = 0;
    if (dbg) {
      iblk = 20000000;
    }
    try {     // this try catches any RuntimeExceptions
      prta("Starting ring reads at seq=" + iblk);
      while (!terminate) {
        if (terminate) {
          break;
        }
        try {
          in.readBlock(buf, iblk, 512);
        } catch (EOFException e) {
          in.close();
          break;      // End of file reached.
        }
        if (terminate) {
          break;
        }
        try {
          if (buf[0] == 0 && buf[6] == 0 && buf[8] == 0 && buf[15] == 0) {
            nzero++;
            if (nzero % 1000 == 0) {
              prta("DFFMS: Got zero block " + nzero + " block=" + iblk);
            }
          } else {
            key.position(0);
            key.put(buf, 8, 16);
            key.position(0);        // The compare operation does so from the position, so put it at start
            DailyFile file = files.get(key);
            // If the file for this channel does not exist, create it
            if (file == null) {
              if (ms == null) {
                ms = new MiniSeed(buf);
              } else {
                ms.load(buf);
              }
              String f = EdgeQueryClient.makeFilename(filemask.replaceAll("_", "&"), ms.getSeedNameString(), ms, false);
              f = f.replaceAll("&", "_");
              //f = f.replaceAll("/_", "/");   // if no location code they do not like to start filenames with /_
              file = new DailyFile(f, this);
              prta("DFFMS: Create iblk=" + iblk + " datafile " + file.toString());
              byte[] tmpkey = new byte[16];
              ByteBuffer tmpbb = ByteBuffer.wrap(tmpkey);
              tmpbb.position(0);
              tmpbb.put(buf, 8, 16);
              if (dbg) {
                prta("Key=" + keyToString(key) + " " + keyToString(tmpbb) + " ms=" + ms);
              }
              tmpbb.position(0);          // compare operation compares from position, so put it at start
              files.put(tmpbb, file);
            }

            // Some feed back as its reading.
            if (nblocks % 1000 == 2) {
              prta(MiniSeed.crackSeedname(buf) + " " + MiniSeed.crackRate(buf) + " " + nblocks);
            }

            // Get the time from the channel and see if it is on the list of times already written, if so, skip i
            long time = MiniSeed.crackTimeInMillis(buf);
            MiniSeed.crackSeedname(buf, chan);
            ArrayList<Long> times = chantimes.get(chan.toString());   // List of times of prior blocks for channel
            if (times == null) {
              times = new ArrayList<Long>(20);                // create this channels initial list of times
              chantimes.put(chan.toString(), times);          // save it to channel index tree
            }
            boolean found = false;
            for (long tt : times) {      // Loop through prior times and see if it matches, set found if so
              if (time == tt) {
                found = true;
                break;
              }
            }
            if (!found) {
              times.add(time);   // Not prior time, add this time to the list
            }
            if (dbg) {
              if(ms != null) ms.load(buf);
              prta(iblk + " " + ms);
            }

            // if the time was found, then this is a dup, add it to the list
            if (found) {
              ndup++;
              if (dbg || ndup % 10000 == 0) {
                if (ms != null) {
                  ms.load(buf);
                  prta("DUP** : #dup=" + ndup + " ms=" + ms);
                }
              }
            } else {        // Block not found, write it to the day file
              try {
                file.add(buf, 0, 512);
              } catch (IOException e) {
                prta("DFFMS: *** IOException e=" + e);
                SendEvent.edgeSMEEvent("DWFIOErr", "DFW io erro=" + e, this);
                terminate();
                break;   // Do not continue if IO errors are occuring.
              } catch (RuntimeException e) {
                prta("DFFMS: *** RuntimeError in writeMiniSeedMidnight - ignore ms=" + ms);
                SendEvent.edgeSMEEvent("DWFRuntimErr", "DFW runtime erro=" + e, this);
                e.printStackTrace(getPrintStream());
              }
            }
          }
          // increment to the next block to read 
          iblk++;
          nblocks++;
          if (nblocks % 200 == 0) {
            if (nblocks % 10000 == 0) {
              if(ms != null) ms.load(buf);
              prta(nblocks + " iblk=" + iblk + " " + ms);
            }
            if (System.currentTimeMillis() - lastStatus > 600000) {
              if (MemoryChecker.checkMemory()) {
                Util.exit(101);
              }
              prta(getStatusString());
              lastStatus = System.currentTimeMillis();
            }
          }
        } catch (IllegalSeednameException e) {
          prta("DFFMS: Got illegal seedname=" + e);
          iblk++;
        }
      }
      // The main loop has exited so the thread needs to exit
    } catch (IOException e) {
      prta("DFFMS: Got IO error trying to read an Ring or writting to file for DailyFileWriter iblk=" + iblk + " e=" + e);
      SendEvent.edgeSMEEvent("DWFIOErr", "DFW io error=" + e, this);
      e.printStackTrace(this.getPrintStream());
      terminate();
    } catch (RuntimeException e) {
      prta("DFFMS: *** RuntimeException in " + this.getClass().getName() + " e=" + e);
      SendEvent.edgeSMEEvent("DWFIORunErr", "DFW runtime error=" + e, this);

      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.doOutOfMemory(tag, this);
          throw e;
        }
      }
      terminate();
    }
    prta("DFFMS: exit main loop - close all files");
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    for (DailyFile f : files.values()) {
      f.close();                          // This also sorts and removes duplicates 
    }
    prta("DFFMS: is exiting..");
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }
  /**
   * Make a string from the 16 character ByteBuffer (seedname+year+doy)
   *
   */
  DecimalFormat df3 = new DecimalFormat("000");

  private String keyToString(ByteBuffer key) {
    int pos = key.position();
    key.position(12);
    short year = key.getShort();
    short doy = key.getShort();
    key.position(0);
    key.get(key2);
    key.position(pos);
    return new String(key2, 0, 12) + year + df3.format(doy);
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit
   */
  class ShutdownDailyFileWriter extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     */
    public ShutdownDailyFileWriter() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(" DFFMS: DailyFileWriter Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
        if (loop > 30) {
          prta(" DFFMS: Showdown() loop expired.  ******* Force an exit!");
          break;
        }
      }
      prta(" DFFMS: Shutdown() of DailyFileWriter is complete.");
    }
  }

  private final class MutexFile extends Thread {

    String file;

    public MutexFile(String filename) throws FileNotFoundException {
      file = filename;
      setDaemon(true);
      File f = new File(file);
      if (!f.exists()) {
        throw new FileNotFoundException("MutexFile : " + file + " *** does not exist!  ");
      }
      start();
    }

    @Override
    public void run() {
      prta("MutexFile: started file=" + file);
      while (!terminate) {
        File f = new File(file);
        if (!f.exists()) {
          prta("MutexFile: " + file + " *** has gone away - force terminate");
          terminate();
        }
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
      prta("MutexFile: is exiting");
    }
  }

  public static void main(String[] args) {
    EdgeProperties.init();
    String argline = "";
    String logfile = "dfwfms";
    Util.setModeGMT();
    Util.setProcess("DailyFilesFromMS");
    String f = "/data2/2017_347_5#1a.ms";
    if (args.length == 0) {
      argline = "-dbg -mask /data/%N_%y_%j.ms -log dave -f /data2/2017_347_5#1a.ms";
    }
    f = f.replaceAll("/_", "/");
    Util.prt("f=" + f);

    for (int i = 0; i < args.length; i++) {
      argline += args[i] + " ";
      if (args[i].equals("-log")) {
        logfile = args[i + 1];
      }
      if (args[i].equals("-mask")) {
        args[i + 1] = args[i + 1].replaceAll("=", "%").replaceAll("@", "%");
      }
      //Util.prt(i+"="+args[i]);
    }
    setMainLogname(logfile);
    staticprta("Starting DailyFileWriter argline=" + argline);
    //Util.prt("argline="+argline);
    try {
      DailyFilesFromMS dfw = new DailyFilesFromMS(argline.trim() + " >>" + logfile, "DFFMS");
      long iday = System.currentTimeMillis() / 86400000L;
      for (;;) {
        Util.sleep(30000);
        if (!dfw.isRunning()) {
          staticprta("Exiting - thread has died");
          System.exit(1);
        }
        if (System.currentTimeMillis() / 86400000L != iday) {
          iday = System.currentTimeMillis() / 86400000L;
          staticprta(Util.ascdatetime(System.currentTimeMillis()));
        }
        //dfw.prta(dfw.getStatusString());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
