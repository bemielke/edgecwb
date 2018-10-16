/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;

/*
 * Subprocess.java 
 *
 * Created on December 1, 2004, 12:21 PM
 */
/**
 * This class really wraps the Process class providing simple err and output processing. It executes
 * a command using the Runtime.getRuntime().exec() function as a subprocess which could still be
 * running after construction. It facilitates getting the returned stderr and stdout as strings and
 * allows for a waitFor(). It mainly is used to hide the details of getting the err and output from
 * the users and the details of process control. It prevents OS dependent lock ups if stdout and
 * stderr have lots of output and need to be relieved. This Class extends thread and the run()
 * method is emptying the stderr and stdout every 0.1 seconds and exiting when it discovers the
 * subprocess has exited. The waitFor() not only waits for the subprocess to exit, but insures the
 * run() has exited insuring the stdout and stdin buffers are complete before returning.
 *
 * @author davidketchum
 */
public final class Subprocess extends Thread {

  private final InputStreamReader err;       // the error output
  //InputStreamReader out;        // the stdout
  private final InputStreamReader out;        // the stdout
  private final StringBuffer errText = new StringBuffer(200);       // Build the error output here
  private final StringBuffer outText = new StringBuffer(1000);       // Build the stdout here
  private final Process proc;               // The subprocess object
  private final String cmd;
  private final char[] c;
  private static boolean dbg;
  private int val;
  private boolean terminate;
  private boolean running;
  private boolean blockInput;

  @Override
  public String toString() {
    return "SP: " + cmd;
  }

  public OutputStream getStdin() {
    return proc.getOutputStream();
  }

  public static void setDebug(boolean t) {
    dbg = t;
  }

  public void terminate() {
    terminate = true;
  }

  public boolean getTerminate() {
    return terminate;
  }

  /**
   * Creates a new instance of Subprocess
   *
   * @param cin A command string to process
   * @throws IOException If reading stderr or stdout gives an error
   */
  public Subprocess(String cin) throws IOException {
    cmd = cin;
    terminate = false;
    blockInput = false;
    proc = Runtime.getRuntime().exec(cmd);
    err = /*new BufferedReader(*/ new InputStreamReader(proc.getErrorStream());
    out = /*new BufferedReader(*/ new InputStreamReader(proc.getInputStream());
    c = new char[100];
    setDaemon(true);
    running = true;
    start();

  }

  /**
   * This is implemented as a Thread so that it will keep reading any stderr or stdout input into
   * the StringBuffers so that they cannot block the operating system from running the subprocess.
   * Read any input each 1/10th second and exits when the subprocess has exited.
   */
  @Override
  public void run() {

    val = -99;
    long loop = 0;
    while (val == -99) {
      if (terminate) {
        break;
      }
      getInput();
      /* it is possible but unlikely that input comes in after being emptied above
       *but then finding that the subprocess has exited.
       */
      if (loop++ % 2 == 0) {
        val = exitValue();
      }
      //Util.prta("Run out="+outText.toString()+"\nRun err="+errText.toString());
      try {
        sleep(100L);
      } catch (InterruptedException e2) {
      }
    }
    getInput();
    if (val == -99) {
      proc.destroy();  // If we left by terminate, insure its down and not orphaned.
    }
    terminate = false;
    running = false;
  }

  private void getInput() {
    int l;
    if (blockInput) {
      return;    // user is reading it himself
    }
    try {
      l = 1;
      while (l > 0) {
        if (out.ready()) {
          l = out.read(c);    // if output is ready, get it, if not go on
        } else {
          l = 0;
        }
        if (l > 0) {
          synchronized (outText) {
            outText.append(c, 0, l);
          } // append up to 100 chars to outText
        }
        if (dbg) {
          Util.prta("getinput l=" + l);
        }
      }
      l = 1;                                // Read in all stderr input
      while (l > 0) {
        if (err.ready()) {
          l = err.read(c);    // read upto 100 chars
        } else {
          l = 0;
        }
        if (l > 0) {
          synchronized (errText) {
            errText.append(c, 0, l);
          }    // append it
        }
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IOerror on subprocess:" + cmd);
    }

  }

  /**
   * return the link to the stdout from this process
   *
   * @return the InputStream which gets the output
   */
  public InputStream getOutputStream() {
    blockInput = true;
    return proc.getInputStream();
  }

  /**
   * Wait for this subprocess to exit. This suspends the current thread until the subprocess is done
   * so be careful that the subprocess does not dead lock or run continuously.
   *
   * @return Exit value of process, zero is success, -99 means it is still running.
   * @throws InterruptedException Means wait did not complete but was interrupted
   */
  public int waitFor() throws InterruptedException {
    return waitFor(Long.MAX_VALUE);
  }

  /**
   * Wait for this subprocess to exit. This suspends the current thread until the subprocess is done
   * so be careful that the subprocess does not dead lock or run continuously.
   *
   * @param timeoutMS the maximum number of millis to wait before aborting. Return -99
   * @return Exit value of process, zero is success.
   * @throws InterruptedException Means wait did not complete but was interrupted
   */
  public int waitFor(long timeoutMS) throws InterruptedException {
    if (dbg) {
      Util.prt("Subprocess : waiting " + cmd);
    }
    long start = System.currentTimeMillis();
    while (running) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
      if (System.currentTimeMillis() - start > timeoutMS) {
        Util.prta("Subprocess: ** timed out=" + timeoutMS + " cmd=" + cmd);
        terminate = true;
        break;
      }
    }
    //int i = proc.waitFor();
    getInput();     // insure the buffers are complete
    return val;
  }

  /**
   * Wait for this subprocess to exit. This suspends the current thread until the subprocess is done
   * so be careful that the subprocess does not dead lock or run continuously.
   *
   * @param timeoutMS the maximum number of millis to wait before aborting. Return -99
   * @param log The EdgeThread to us for loggin, it must not be null.
   * @return Exit value of process, zero is success.
   * @throws InterruptedException Means wait did not complete but was interrupted
   */
  public int waitFor(long timeoutMS, EdgeThread log) throws InterruptedException {
    if (dbg) {
      log.prt("Subprocess : waiting " + cmd);
    }
    long start = System.currentTimeMillis();
    while (running) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
      if (System.currentTimeMillis() - start > timeoutMS) {
        log.prta("Subprocess: ** timed out=" + timeoutMS + " cmd=" + cmd);
        terminate = true;
        break;
      }
    }
    //int i = proc.waitFor();
    getInput();     // insure the buffers are complete
    return val;
  }

  /**
   * Returns the exit status of the subprocess. If the subprocess is still running this should
   * return -99 to indicate it is still running and so can be used to test for the process still
   * running without blocking the thread's execution
   *
   * @return The exit status of the subprocess or -99 if the subprocess is still running
   */
  public int exitValue() {
    try {
      return proc.exitValue();
    } catch (IllegalThreadStateException e) {
      return -99;
    }
  }

  /**
   * return the output of the subprocess as a string. If the subprocess is still running, it may
   * return some portion of the stdout to this point, but will not block.
   *
   * @return String with the contents of stdout
   */
  public String getOutput() {
    return getOutput(false);
  }

  /**
   * return the output of the subprocess as a string. If the subprocess is still running, it may
   * return some portion of the stdout to this point, but will not block. if flag is true, clear the
   * output buffer
   *
   * @param clear If true, clear the output buffer after getting the string
   * @return String with the contents of stdout
   */
  public String getOutput(boolean clear) {
    String s;
    synchronized (outText) {
      s = outText.toString();
      if (clear) {
        Util.clear(outText);
      }
    }
    return s;
  }

  /**
   * return error output to date of the subprocess. If the subprocess is still running, it may
   * return some portion of the stderr to this point, but will not block.
   *
   * @return String with contents of stderr
   *
   */
  public String getErrorOutput() {//throws IOException {
    return getErrorOutput(false);
  }

  /**
   * return error output to date of the subprocess. If the subprocess is still running, it may
   * return some portion of the stderr to this point, but will not block.
   *
   * @param clear If true, clear the text field after returning it
   * @return String with contents of stderr
   *
   */
  public String getErrorOutput(boolean clear) {//throws IOException {
    String s;
    synchronized (errText) {
      s = errText.toString();
      if (clear) {
        Util.clear(errText);
      }
    }
    return s;
  }

  /**
   * unit test routine
   *
   * @param args command line args
   */
  public static void main(String[] args) {
    Util.init();
    try {
      //Process p = Runtime.getRuntime().exec("ifconfig -a");
      Util.prta("Issue command");
      //Subprocess p = new Subprocess("ping -c 1 69.19.65.251");
      Subprocess p = new Subprocess("netstat -nrv");

      Util.prta("return from command.");

      Util.prta("Out immediate=" + p.getOutput());
      Util.prta("Err immediate=" + p.getErrorOutput());
      Util.prta("immediate return=" + p.exitValue());
      p.waitFor();
      Util.prta("Waitfor done");
      Util.prta("Out=" + p.getOutput());
      Util.prta("Err=" + p.getErrorOutput());
      Util.prt("error return=" + p.exitValue());

    } catch (IOException e) {
      Util.IOErrorPrint(e, "reading file :");
    } catch (InterruptedException e) {
      Util.prt("Interrupted waiting for command");
    }

  }
}
