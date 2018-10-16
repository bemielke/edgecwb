/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.FileOutputStream;

public final class TimedPrintStream extends PrintStream {

  long lastms;
  String filename;

  public TimedPrintStream(String s, boolean t) throws FileNotFoundException {
    super(new FileOutputStream(s, true), t);
    filename = s;
    lastms = System.currentTimeMillis();

  }

  @Override
  public void print(String s) {
    lastms = System.currentTimeMillis();
    super.print(s);
  }

  @Override
  public void println(String s) {
    lastms = System.currentTimeMillis();
    super.println(s);
  }

  public long getLastMS() {
    return lastms;
  }

  public String getFilename() {
    return filename;
  }

  @Override
  public String toString() {
    return filename + (System.currentTimeMillis() - lastms);
  }
}
