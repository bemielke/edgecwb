/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Arrays;
import gov.usgs.alarm.SendEvent;

/**
 * Simple utility for testing program output. Intercepts System.out to print both to the console and
 * a buffer. From 'Thinking in Java, 3rd ed.' (c) Bruce Eckel 2002
 *
 * Adapted by D.Ketchum to simultaneously write the SESSION.OUT file and to put output on standard
 * out. Added ring buffer of output which can be checked for errors and then trigger dialog box to
 * send the ring buffer of output by mail to D. Ketchum
 *
 */
public class LoggingStream extends PrintStream {

  static boolean exceptionOccurred = false;
  static boolean noConsole;
  static boolean noInteractive;
  static PrintStream console = System.out;    // save original output (console)
  static PrintStream err = System.err;
  protected int numOfLines;
  private static final StringBuffer sb = new StringBuffer(20000);
  private final Integer mutex = Util.nextMutex();
  private PrintStream fout;

  // To store lines sent to System.out or err
  private final String className;

  public void setConsole() {
    fout = console;
  }

  public LoggingStream(String className) {

    super(System.out, true); // Autoflush
    //System.out.println("About to change SYstem.out");
    noConsole = false;
    noInteractive = false;
    this.className = className;

    openOutputFile(false);
  }

  public LoggingStream(String className, boolean append) {
    super(System.out, true); // Autoflush
    System.out.println(Util.ascdate() + " " + Util.asctime() + " About to change out/err to " + className + " with append=" + append);
    noConsole = false;
    noInteractive = false;
    this.className = className;
    openOutputFile(append);
  }

  /**
   * when set true, no output goes to console print stream and no dialog boxes are put up for
   * mailing when an exception is detected
   *
   * @param t If true, ture n off console print stream and disable exception based mail via dialog
   */
  public static void setNoConsole(boolean t) {
    noConsole = t;
  }

  /**
   * when set true, this make the "append" function which keeps the last 10000 characters available
   * will not do dialog boxes if SQLException or EventQueueExceptoinHander are found. It also
   * disables dialog boxes which would be checked if noConsole was false.
   *
   * @param t if true, set noInteractive mode
   */
  public static void setNoInteractive(boolean t) {
    noInteractive = t;
  }

  public void suppressFile() {
    fout = null;
  }

  // public PrintStream getConsole() { return console; }
  public synchronized void dispose() {
    System.setOut(console);
    System.setErr(err);
  }

  // This will write over an old Output.txt file:
  public final void openOutputFile(boolean append) {
    System.setOut(this);
    System.setErr(this);
    try {
      Util.chkFilePath(className);
      fout = new PrintStream(new FileOutputStream(
              new File(className), append));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  /**
   * Add this string to the buffer and check for appearance of magic strings which cause the
   * exceptionOccurred flag to be set and a timed dialog to be triggered off.
   */
  boolean ignoreSQLDialog;

  public void setIgnoreSQLDialog(boolean t) {
    ignoreSQLDialog = t;
  }

  /**
   * all users of this routine have to have synchronized(mutex) to be thread safe
   *
   * @param s
   */
  StringBuilder s = new StringBuilder(20);
  private void appendit(CharSequence seq) {
    Util.clear(s).append(seq);
    sb.append(s);
    ncharCounter += s.length();
    if (s.indexOf("EventQueueExceptionHandler") >= 0) {
      exceptionOccurred = true;
      TimedDialog tm = new TimedDialog(sb, "EventQueueException", 1000);
    } else if (s.indexOf("SeedLinkException") >= 0) {
      if (s.indexOf("failed to decode") >= 0 && s.indexOf("NumberFormatExc") >= 0) {
        return;    // These are not worth the e-mail
      }
      exceptionOccurred = true;
      TimedDialog tm = new TimedDialog(sb, "SeedLinkException ", 3000);
    } else if (s.indexOf("SQLException") >= 0) {
      int i = s.indexOf("e=");
      String extra;
      if (i > 0) {
        extra = s.substring(0, i - 2);
      } else {
        extra = "";
      }
      exceptionOccurred = true;
      if (!ignoreSQLDialog) {
        TimedDialog tm = new TimedDialog(sb, "SQLException " + extra, 3000);
      }
    } else if (s.indexOf("SeedLinkException") >= 0) {
      exceptionOccurred = true;
      int pos = s.indexOf("SeedLinkException");
      pos = s.indexOf("[", pos);
      String extra = "Unknown SeedlinkException";
      if (pos >= 0) {
        int end = s.indexOf("\n", pos);
        if (end >= 0 && end > pos) {
          extra = s.substring(pos, end);
        }
      }
      TimedDialog tm = new TimedDialog(sb, "SeedLinkException " + extra, 3000);
    } else if (s.indexOf("RuntimeException") >= 0) {
      exceptionOccurred = true;
      int i = s.indexOf("e=");
      String extra;
      if (i > 0) {
        extra = s.substring(0, i - 2);
      } else {
        extra = "";
      }
      TimedDialog tm = new TimedDialog(sb, "RuntimeException " + extra, 3000);
    } else if (s.indexOf("duplicate entry") > 0) {
      exceptionOccurred = true;
      int i = s.indexOf("entry Duplicate entry");
      String extra = "";
      if (i > 0) {
        extra = s.substring(i + 20);
      }
      TimedDialog tm = new TimedDialog(sb, "DuplicateException" + extra, 3000);
    } else if (s.indexOf("Exception") >= 0) {
      exceptionOccurred = true;
      int i = s.indexOf("e=");
      String extra;
      if (i > 0) {
        extra = s.substring(0, i - 2);
      } else {
        extra = "";
      }
      TimedDialog tm = new TimedDialog(sb, "Exception " + extra, 3000);
    }
    if (sb.length() > 10000) {
      Util.clear(sb);
    }
    if (fout != null && ncharCounter > 100000000) {      // writing to a file, is it time to reset its size
      ncharCounter = 0;
      fout.close();
      openOutputFile(false);
    }
  }
  long ncharCounter;

  public static void setExceptionOccurred(boolean b) {
    exceptionOccurred = true;
  }

  public static boolean getExceptionOccurred() {
    return exceptionOccurred;
  }

  public static StringBuffer getText() {
    return sb;
  }

  // Override all possible print/println methods to send
  // intercepted console output to both the console and
  // the Output.txt file:
  @Override
  public void print(boolean x) {
    synchronized (mutex) {
      appendit(String.valueOf(x));
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(boolean x) {
    synchronized (mutex) {
      appendit(String.valueOf(x) + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void print(char x) {
    synchronized (mutex) {
      appendit(String.valueOf(x));
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(char x) {
    synchronized (mutex) {
      appendit(String.valueOf(x) + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void print(int x) {
    synchronized (mutex) {
      appendit(String.valueOf(x));
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(int x) {
    synchronized (mutex) {
      appendit(String.valueOf(x) + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void print(long x) {
    synchronized (mutex) {
      appendit(String.valueOf(x));
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(long x) {
    synchronized (mutex) {
      appendit(String.valueOf(x) + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void print(float x) {
    synchronized (mutex) {
      appendit(String.valueOf(x));
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(float x) {
    synchronized (mutex) {
      appendit(String.valueOf(x) + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void print(double x) {
    synchronized (mutex) {
      appendit(String.valueOf(x));
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(double x) {
    synchronized (mutex) {
      appendit(String.valueOf(x) + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  public void print(CharSequence x) {
    synchronized (mutex) {
      int l = x.length();
      appendit(x);   // this might not be very efficient
      console.print(x);
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void print(char[] x) {
    synchronized (mutex) {
      appendit(new String(x));
      if (!noConsole) {
        console.print(Arrays.toString(x));
      }
      if (fout != null) {
        fout.print(Arrays.toString(x));
      }
    }
  }

  @Override
  public void println(char[] x) {
    numOfLines++;
    synchronized (mutex) {
      if (!noConsole) {
        console.println(Arrays.toString(x));
      }
      if (fout != null) {
        fout.println(Arrays.toString(x));
      }
    }
  }

  public void println(CharSequence x) {
    synchronized (mutex) {
      appendit(x);
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }
  @Override
  public void print(String x) {
    synchronized (mutex) {
      appendit(x);
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(String x) {
    synchronized (mutex) {
      appendit(x + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
        console.flush();
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void print(Object x) {
    synchronized (mutex) {
      appendit(x.toString());
      if (!noConsole) {
        console.print(x);
      }
      if (fout != null) {
        fout.print(x);
      }
    }
  }

  @Override
  public void println(Object x) {
    synchronized (mutex) {
      appendit(x.toString() + "\n");
      numOfLines++;
      if (!noConsole) {
        console.println(x);
      }
      if (fout != null) {
        fout.println(x);
      }
    }
  }

  @Override
  public void println() {
    synchronized (mutex) {
      appendit("\n");
      //if(false) if(!noConsole) console.print("println");
      numOfLines++;
      if (!noConsole) {
        console.println();
      }
      if (fout != null) {
        fout.println();
      }
    }
  }

  @Override
  public void write(byte[] buffer, int offset, int length) {
    synchronized (mutex) {

      for (int i = offset; i < offset + length; i++) {
        appendit(String.valueOf(buffer[i]));
      }
      if (!noConsole) {
        console.write(buffer, offset, length);
      }
      fout.write(buffer, offset, length);
    }
  }

  @Override
  public void write(int b) {
    synchronized (mutex) {
      if (!noConsole) {
        console.write(b);
      }
      fout.write(b);
    }
  }

  /**
   * This class is instantiated to cause a slight pause before a dialog box is displayed. If he user
   * answers yes, the ring buffer of output is sent to dave
   */
  static int numberHeadless = 0;
  static int numberExceptions = 0;
  static long lastException;
  static long lastEMail;
  static int skippedExceptions = 0;
  static int lastSkippedExceptions = 0;

  public final class TimedDialog extends Thread {

    int timeout;
    String title;
    private final StringBuffer sb;

    public TimedDialog(StringBuffer sbin, String titlein, int ms) {
      sb = sbin;
      if (noConsole || noInteractive) {
        return;
      }
      title = titlein;
      timeout = ms;
      //System.out.println("TimedDialogLaunch title="+titlein+" sb="+sbin+" noInteractive="+noInteractive+" noconsole="+noConsole);
      start();
    }

    @Override
    public void run() {
      try {
        sleep(timeout);
      } catch (InterruptedException e) {
      }
      if (true) {
        return;
      }
      synchronized (sb) {
        try {
          int i;
          int start = sb.indexOf(title);
          if (start > 0) {
            int count = 8;
            for (i = start; i < sb.length(); i++) {
              if (sb.charAt(i) == '\n') {
                count--;
              }
              if (count == 0) {
                start = i;
                break;
              }
            }
          } else {
            start = sb.length();
            int count;
            count = 20;
            for (i = start - 1; i > 0; i--) {
              if (sb.charAt(i) == '\n') {
                count--;
              }
              if (count == 0) {
                break;
              }
            }
          }

          // If this is a interactive application, show a dialog and let the user decide
          numberExceptions++;
          if (numberExceptions > 10) {
            if (System.currentTimeMillis() - lastException < 60000) {
              skippedExceptions++;
              Util.prt("TimedDialog: Skip Excpt =" + skippedExceptions + " of " + numberExceptions + " "
                      + Util.getNode() + " " + User.getUser() + " " + title + "\n"
                      + Util.getProcess() + " " + System.getProperty("user.home") + " " + System.getProperty("user.dir") + " " + Util.getNode()
                      + " " + Util.ascdate() + " " + Util.asctime() + "\n" + sb.toString());
              return;
            } else {
              title = "STORM " + title;
            }
          }
          if (System.currentTimeMillis() - lastException > 600000) {
            numberExceptions = 0; // rearm the storm delayer
          }
          lastException = System.currentTimeMillis();
          if (lastException - lastEMail > 600000) {
            lastEMail = lastException;
            Util.prta("TimedDialog: Email exception=" + title + " to " + Util.getProperty("emailTo"));
            SendEvent.edgeSMEEvent("TimeDialog", title + " reported #exp=" + numberExceptions + " skip=" + skippedExceptions, this);
            SimpleSMTPThread.email(Util.getProperty("emailTo"),
                    "_Exception:" + User.getUser() + " " + title, // subject
                    Util.ascdate() + " " + Util.asctime() + " " + User.getUser() + " " + Util.getIDText()
                    + (skippedExceptions != lastSkippedExceptions ? " skipped=" + (skippedExceptions - lastSkippedExceptions) : "")
                    + "\n" + sb.toString() + "\n");
            //new RuntimeException("Trace a TimedDialog exception").printStackTrace();
          }
          lastSkippedExceptions = skippedExceptions;
        } catch (RuntimeException e) {
          Util.prt("RuntimeError in timed dialog title=" + title + " e=" + e);
          //e.printStackTrace();

        }
      }
    }
  }
}
