/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.IOException;
import static java.lang.Thread.sleep;

/**
 * RRPGapFiller.java this class examines files in a given directory stub for
 * files of the form $TAG.gapN where TAG is a TAG on an RRPServerSocket
 * configuration line and N is the number of the gap file. If it finds such
 * files, it checks the RRPServerSocket for whether it is currently filling a
 * gap, and if not, sets up a new gap filling range. This file also periodically
 * checks all running gap fills and updates the gap file with new starting
 * sequences as things proceed.
 *
 * When the gap is filled, the gap file is deleted.
 *
 * Created on Feb 18, 2015
 *
 * @author davidketchum <ketchum at usgs.gov>
 */
public final class RRPServerGapFiller extends Thread {

  private String arglin;
  private String dirStub;     // the directory to monitor
  private String filename;    // The full file name we are monitoring.
  private final ShutdownRRPServerGapFiller shutdown;
  private EdgeThread par;
  private String tag;
  boolean dbg;
  boolean running, terminate;

  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  public String getStatusString() {
    return "RRPServerGapFiller : no status!";

  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  public String getMonitorString() {
    return "";
  }

  /**
   * return console output - this is fully integrated so it never returns
   * anything
   *
   * @return "" since this cannot get output outside of the par.prt() system
   */
  public String getConsoleOutput() {
    return "";
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline The argument line to cause the filling (an ISILink command
   * line)
   * @param tg The logging tag
   * @param parent The edgethread to use for logging
   */
  public RRPServerGapFiller(String argline, String tg, EdgeThread parent) {
//initThread(argline,tg);
    arglin = "";
    par = parent;
    String[] args = argline.split("\\s");
    dbg = false;
    tag = tg;
    for (int i = 0; i < args.length; i++) {
      //par.prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
        arglin += args[i] + " ";
      } else if (args[i].equals("-s")) {
        dirStub = args[i + 1];
        filename = args[i + 1];
        if (dirStub.lastIndexOf("/") >= 0) {
          dirStub = dirStub.substring(0, dirStub.lastIndexOf("/"));
          filename = filename.substring(filename.lastIndexOf("/") + 1);
        } else {
          dirStub = "";
        }
        i++;
      } // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        arglin += args[i] + " ";
      }
    }

    /*if(shutdown != null) {
      par.prt(tag+" RRPServerGap: ******** attempt to create another RRPServerGapFiller!");
      return;
    }*/
    par.prt(tag + "RRPServerGap: created args=" + argline + " dir=" + dirStub + " filename=" + filename + " arglin=" + arglin);
    shutdown = new ShutdownRRPServerGapFiller();
    Runtime.getRuntime().addShutdownHook(shutdown);
    gov.usgs.anss.util.Util.prta("new ThreadGap " + getName() + " " + getClass().getSimpleName() + " argline=" + argline);

    start();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    running = true;
    try {
      sleep(30000);
    } catch (InterruptedException expected) {
    }
    int loop = 0;
    //EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 30000,60000);
    try {     // this try catches any RuntimeExceptions
      RawDisk updFileWriter = null;
      try {
        updFileWriter = new RawDisk(dirStub + "/" + filename + ".caughtup", "rw");
      } catch (IOException e) {
        par.prta(tag + " RRPServerGap: Cannot open " + filename + ".caughtup");
        Util.IOErrorPrint(e, tag + "RRPServerGap: Cannot open " + filename + ".caughtup", par.getPrintStream());
      }
      File dir = new File(dirStub);
      ISILink isi = null;
      par.prta(tag + " RRPServerGap: path=" + dirStub + " dir=" + dir.toString() + " isDir=" + dir.isDirectory() + " dbg=" + dbg);
      while (!terminate) {
        try {
          sleep(10000);
        } catch (InterruptedException expected) {
        }
        if (terminate) {
          break;
        }
        // If there is an ISI running for this one, monitor it for completion
        if (isi != null) {
          if (!isi.isRunning()) {
            par.prta(tag + Util.ascdate() + " RRPServerGap: file completed on " + isi);
            isi = null;
          }
        } else {    // no isi is running, see if any gap files exist
          //dir = new File(dirStub);  // Use the old one over and over
          String[] files = dir.list();
          // for each file, if it is an .idx file, see if its in the date range and open if needed
          boolean done = false;
          for (String file : files) {
            if (file.contains(filename + ".gap")) {
              par.prta(Util.ascdate() + tag + " RRPServerGap: create Gap fill ISILink " + arglin + " -s " + dirStub + "/" + file);
              try {
                isi = new ISILink(arglin + "-rcvsiz 4096 -s " + dirStub + "/" + file + " >>" + file, file);
                par.prta(tag + "RRPServerGap: tag created=" + isi.getTag() + " gap size=" + isi.getGapSize());
              } catch (RuntimeException e) {
                par.prta(Util.ascdate() + tag + " RRPServerGap: could not start ISILink for gap file=" + file + " e=" + e);
                e.printStackTrace(par.getPrintStream());
                e.printStackTrace();
                SendEvent.debugEvent("RRPServerGapRunTim", tag + " Runtime=" + e + " " + file, this);
              }
              done = true;
              break;
            }
          }

          if (!done) {
            if (loop++ % 360 == 0 && updFileWriter != null) {
              try {
                updFileWriter.seek(0L);
                updFileWriter.write((Util.ascdate() + " " + Util.asctime() + "\n").getBytes());
                updFileWriter.setLength(23);
                //updFileWriter.close();
                par.prta(tag + "Write caughtup file");

              } catch (IOException e) {
                par.prta(tag + "RRPServerGap: Did not update " + filename + ".caughtup");
                Util.IOErrorPrint(e, tag + "RRPServerGap: Did not update " + filename + ".caughtup", par.getPrintStream());
              }
            }
          }
        }
      }       // while(!terminate) check on ISI gaps
      if (isi != null) {
        isi.terminate();
      }
      // The main loop has exited so the thread needs to exit
    } catch (RuntimeException e) {
      par.prta(tag + "RRPServerGap: RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
      if (par.getPrintStream() != null) {
        e.printStackTrace(par.getPrintStream());
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
    par.prta(tag + "RRPServerGap: ** RRPServerGapFiller terminated terminate=" + terminate);
    //eto.shutdown();           // shutdown the timeout thread
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit
   */
  class ShutdownRRPServerGapFiller extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run()
     * thread
     */
    public ShutdownRRPServerGapFiller() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(tag + "RRPServerGap: RRPServerGapFiller Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
      }
      System.err.println(tag + "RRPServerGap: Shutdown() of RRPServerGapFiller is complete.");
    }
  }
}
