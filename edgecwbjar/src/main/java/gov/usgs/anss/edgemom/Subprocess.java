/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
import java.io.*;

/*
 * Subprocess.java 
 *
 * Created on December 1, 2004, 12:21 PM
 */

/**
 * This class wraps up the Process class by providing simple err and output
 * processing. It executes a command using the Runtime.getRuntime().exec()
 * function as a subprocess which could still be running after constuction. It
 * facilitates getting the returned stderr and stdout as strings and allows for
 * a waitFor(). It mainly is used to hide the details of getting the err and
 * output from the users and the details of process control. It prevents OS
 * dependent lock ups if stdout and stderr have lots of output and need to be
 * relieved. This Class extends thread and the run() method empties the stderr
 * and stdout every 0.1 seconds and exits when it discovers the subprocess has
 * exited. The waitFor() not only waits for the subprocess to exit, but insures
 * the run() has exited, while insuring that the stdout and stdin buffers are
 * complete before returning.
 * <p>
 * This class does not have EdgeThread arguments. The arguments are passed to
 * the subprocess.
 *
 * @author davidketchum
 */
public final class Subprocess extends EdgeThread {

  private InputStreamReader err;       // the error output
  private InputStreamReader stdout;        // the stdout
  private final StringBuilder errText = new StringBuilder(200);
  private final StringBuilder outText = new StringBuilder(1000);
  private Process proc;               // The subprocess object
  private String cmd;
  private String[] cmdline;
  private String cmdkill;
  private final char[] c;
  private static boolean dbg;
  private boolean monitor;
  private int pid;      // If the program puts out a "pid=" on the first line, then we can get its pid

  /**
   * return the pid if it is known, else zero
   *
   * @return the PID if known if not zero.
   */
  public int getPID() {
    return pid;
  }

  public static void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates a new instance of Subprocess.
   *
   * @param cin A command string to process
   * @param tg Logging tag
   * @throws IOException if reading stderr or stdout gives an error
   */
  public Subprocess(String cin, String tg) throws IOException {
    super(cin, tg);
    //initThread(cin,tg);
    // Anything between [] is the "kill" command for this line
    cmd = cin;
    int b = cin.indexOf("[");
    int e = cin.indexOf("]");
    if (b >= 0 && e >= 0) {
      cmdkill = cin.substring(b + 1, e);
      cmd = cin.substring(e + 1);
      prta("Killcmd=" + cmdkill);
    }

    // Start using bash , -c means use next command as start of line
    terminate = false;
    tag += "SP:";
    pid = 0;
    prta(tag + "SP: line=" + cmd);
    int pos = cmd.indexOf(">");
    if (pos >= 0) {
      cmd = cmd.substring(0, pos);
    }
    monitor = false;
    if (tag.contains("!") || cin.contains("anssq330")) {
      monitor = true;
    }

    // Break this up into the command elements in quotes
    proc = Runtime.getRuntime().exec(Util.parseCommand(cmd));
    err = new InputStreamReader(proc.getErrorStream());
    stdout = new InputStreamReader(proc.getInputStream());
    c = new char[100];
    prta(tag + "Subprocess started : " + cmd);
    start();

  }

  @Override
  public void terminate() {
    prta(tag + " terminate called");
    terminate = true;
  }

  /**
   * This is implemented as a Thread so that it will keep reading any stderr or
   * stdout input into the StringBuffers so that they cannot block the operating
   * system from running the subprocess. Read any input each 1/10th second and
   * exits when the subprocess has exited.
   */
  @Override
  public void run() {

    int val = -99;
    long loop = 0;
    running = true;
    String lastErr = "";
    String lastOut = "";

    try {
      while (val == -99) {

        getInput();
        if (monitor) {
          if (!lastErr.equals(errText.toString())) {
            prta(tag + " err out now=" + errText.toString().replaceAll("\n", "|"));
            lastErr = errText.toString();

          }
          if (!lastOut.equals(outText.toString())) {
            prta(tag + "std out now=" + outText.toString().replaceAll("\n", "|"));
            lastOut = outText.toString();
            if (lastOut.substring(0, Math.min(4, lastOut.length())).equalsIgnoreCase("pid=") && pid == 0) {
              String[] parts = lastOut.split("[ =\n]");
              try {
                pid = Integer.parseInt(parts[1]);
                prta(tag + " PID=" + pid);
              } catch (RuntimeException e) {
                prta(tag + " could not decode PID ");
              }
            }
          }
        }
        /* It is possible but unlikely that input comes in after being emptied above
         *but then finding out that the subprocess has exited.
         */
        if (loop++ % 2 == 0) {
          val = exitValue();
        }
        if (Util.isShuttingDown()) {
          terminate = true;
        }
        if (terminate) {
          break;
        }
        //prta("Run out="+outText.toString()+"\nRun err="+errText.toString());
        try {
          sleep(monitor ? 2000L : 100L);
        } catch (InterruptedException e2) {
        }
      }
    } catch (RuntimeException e) {
      prta(tag + " Runtime exception caught ");
      e.printStackTrace(getPrintStream());
    }
    getInput();
    prta(tag + " Exiting run() loop.  val=" + val + " wait for child to exit or 20 seconds");

    if (cmdkill != null) {
      try {
        proc = Runtime.getRuntime().exec(Util.parseCommand(cmdkill));
        err = new InputStreamReader(proc.getErrorStream());
        stdout = new InputStreamReader(proc.getInputStream());
        sleep(1000L);
        getInput();
        prta(tag + " kill cmd=" + cmdkill + " exit=" + proc.exitValue() + " output=" + getOutput() + " err=" + getErrorOutput());
      } catch (IOException e) {
        prta(tag + " kill cmd i/o cmd=" + cmdkill);
      } catch (InterruptedException e) {
      }
    }
    for (int i = 0; i < 100; i++) {
      getInput();
      if ((val = exitValue()) != -99) {
        break;
      }
      prta(tag + " wait val=" + val);
      try {
        sleep(200);
      } catch (InterruptedException e) {
      }
    }
    prta(tag + " exit run loop val=" + val + " terminate=" + terminate);
    prta(tag + " Final out=" + outText.toString().replaceAll("\n", "|") + "\n          " + tag + " Run err=" + errText.toString().replaceAll("\n", "|"));
    proc.destroy();           // kill the subprocess
    if (terminate) {
      prta(tag + " terminated:" + toString());
    } else {
      prta(tag + " : run() exit: val=" + val + " cmd=" + cmd);
    }
    try {
      sleep(1000);
    } catch (InterruptedException e) {
    }  // sleep so output is available a for a little longer
    running = false;
    terminate = false;
  }

  public boolean isRunning2() {
    return running;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append(tag).append(":").append(cmd);
  }

  private void getInput() {
    int l;
    try {
      l = 1;
      while (l > 0) {
        if (stdout.ready()) {
          l = stdout.read(c);
          if (l <= 0) {
            prta(tag + " ready on stdout, but no data l=" + l);
          }
          if (l < 0) {
            prta(tag + " EOF stdout detected.");
            break;
          }
        } // if output is ready, get it, if not go on
        else {
          l = 0;
        }
        if (l > 0) {
          synchronized (outText) {
            outText.append(c, 0, l);
          } // Append up to 100 chars to outText
        }
        if (dbg) {
          prta(tag + "getinput l=" + l);
        }
      }
      l = 1;                                // Read in all stderr input
      while (l > 0) {
        if (err.ready()) {
          l = err.read(c);
          if (l <= 0) {
            prta(tag + " ready on stderr, but no data l=" + l);
          }
          if (l < 0) {
            prta(tag + " EOF stderr detected.");
            break;
          }
        } // read upto 100 chars
        else {
          l = 0;
        }
        if (l > 0) {
          synchronized (errText) {
            errText.append(c, 0, l);
          }    // Append it
        }
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IOerror on subprocess:" + cmd);
    }

  }

  /**
   * Wait for this subprocess to exit. This suspends the current thread until
   * the subprocess is done. Be careful that the subprocess does not dead lock
   * or run continuously.
   *
   * @return Exit value of process, zero is success.
   * @throws InterruptedException: This Means that wait did not complete but was
   * interrupted.
   */
  public int waitFor() throws InterruptedException {
    int i;
    if (dbg) {
      prt(tag + " waiting " + cmd);
    }
    i = proc.waitFor();
    getInput();     // insure the buffers are complete
    return i;
  }

  /**
   * Returns the exit status of the subprocess. If the subprocess is still
   * running, this should return -99 to indicate it is still running. This can
   * be used to test for the process still running without blocking the thread's
   * execution.
   *
   * @return The exit status of the subprocess or -99 if the subprocess is still
   * running.
   */
  public int exitValue() {
    try {
      return proc.exitValue();
    } catch (IllegalThreadStateException e) {
      return -99;
    }
  }

  /**
   * Return the output of the subprocess as a string. If the subprocess is still
   * running, it may return some portion of the stdout to this point, but will
   * not block.
   *
   * @return String with the contents of stdout.
   */
  public StringBuilder getOutput() {
    return outText;
  }

  /**
   * Return the output of the subprocess as a string. If the subprocess is still
   * running, it may return some portion of the stdout to this point, but will
   * not block. If flag is true, clear the output buffer.
   *
   * @param clear If true, clear the output buffer after getting the string.
   * @return String with the contents of stdout.
   */
  public StringBuilder getOutput(boolean clear) {
    StringBuilder s = new StringBuilder(outText.length());
    synchronized (outText) {
      s.append(outText);
      if (clear) {
        outText.delete(0, outText.length());
      }
    }
    return s;
  }

  /**
   * Return error output to date of the subprocess. If the subprocess is still
   * running, it may return some portion of the stderr to this point, but will
   * not block.
   *
   * @return String with contents of stderr.
   *
   */
  public StringBuilder getErrorOutput() {//throws IOException {
    return errText;
  }

  /**
   * Return error output to date of the subprocess. If the subprocess is still
   * running, it may return some portion of the stderr to this point, but will
   * not block.
   *
   * @param clear Clear the text after returning it.
   * @return String with contents of stderr.
   *
   */
  public StringBuilder getErrorOutput(boolean clear) {//throws IOException {
    StringBuilder s = new StringBuilder(errText.length());
    synchronized (errText) {
      s.append(errText);
      if (clear) {
        errText.delete(0, errText.length());
      }
    }
    return s;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    if (outText.length() > 0 && errText.length() == 0) {
      return getOutput(true);
    } else if (outText.length() > 0 && errText.length() > 0) {
      return getOutput(true).append("\n" + "stderr=").append(getErrorOutput(true));
    } else if (outText.length() == 0 && errText.length() > 0) {
      return getErrorOutput(true);
    } else {
      return errText;
    }
  }

  /**
   * Unit test routine.
   *
   * @param args Command line args.
   */
  public static void main(String[] args) {
    Util.init();
    try {
      //Process p = Runtime.getRuntime().exec("ifconfig -a");
      Util.prta("Issue command");
      Subprocess p = new Subprocess("ping -c 1 137.227.224.97", "tag");

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
