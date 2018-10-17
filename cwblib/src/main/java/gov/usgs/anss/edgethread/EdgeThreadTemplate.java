/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.Util;

/**
 * This is a template for an EdgeThread (one that can be run by EdgeMom. It includes a timeout on
 * the run() and a registered shutdown handler.
 *
 * At a minimum EdgeThreadTemplate should be replaced to the new class And $$$ replaces with a short
 * hand for the class. (ETT??).
 *
 * @author davidketchum
 */
public final class EdgeThreadTemplate extends EdgeThread {

  private final ShutdownEdgeThreadTemplate shutdown;
  private boolean dbg;

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
    return statussb;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb;
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
   * @param argline The command line to parse for this EdgeThread
   * @param tg The tag to use in logging for this thread.
   */
  public EdgeThreadTemplate(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    for (String arg : args) {
      //prt(i+" arg="+args[i]);
      if (arg.equals("-dbg")) {
        dbg = true;
      } else if (arg.substring(0, 1).equals(">")) {
        break;
      } else {
        prt("EdgeThreadTemplate: unknown switch=" + arg + " ln=" + argline);
      }
    }
    prt("Rep: created args=" + argline + " tag=" + tag);
    shutdown = new ShutdownEdgeThreadTemplate();
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
    EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 30000, 60000);
    try {     // this try catches any RuntimeExceptions
      while (!terminate) {
        if (terminate) {
          break;
        }
        eto.resetTimeout();           // reset the timeout
      }       // while(!terminate) do socket open
      // The main loop has exited so the thread needs to exit
    } catch (RuntimeException e) {
      prta("$$$: RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace(getPrintStream());
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + this.getClass().getName() + " on " + Util.getNode(),
                  this);
          throw e;
        }
      }
      terminate();
    }
    prta("$$$: ** EdgeThreadTemplate terminated TO.int=" + eto.hasSentInterrupt() + " TO.destroy="
            + eto.hasSentDestroy());
    eto.shutdown();           // shutdown the timeout thread
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception e) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit
   */
  class ShutdownEdgeThreadTemplate extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     */
    public ShutdownEdgeThreadTemplate() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("$$$: EdgeThreadTemplate Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
      }
      System.err.println("$$$: Shutdown() of EdgeThreadTemplate is complete.");
    }
  }

}
