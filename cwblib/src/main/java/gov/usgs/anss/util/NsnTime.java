/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.Calendar;

/**
 * Encapsulate an NSN time code in both communications buffer and decoded form
 *
 * @author ketchum
 */
public class NsnTime {

  byte[] tc;
  short doy;
  short year;
  int ms;
  byte flags;

  /**
   * Creates a new instance of NsnTime from a communications buffer raw bytes
   *
   * @param b the raw bytes (six of them)
   */
  public NsnTime(byte[] b) {
    tc = new byte[6];
    if (b.length >= tc.length) {
      System.arraycopy(b, 0, tc, 0, tc.length);
    }
    tcToVars();
  }

  /**
   * create an NsnTime from raw year, day, hour, minute,second, ms and leap data
   *
   * @param yr The year
   * @param id the Julian day
   * @param ih Hour
   * @param im minute
   * @param is second
   * @param m milliseconds
   * @param leap -1, 0, or +1 indicating what kind of leap day today is
   */
  public NsnTime(int yr, int id, int ih, int im, int is, int m, int leap) {
    year = (short) yr;
    doy = (short) id;
    ms = (ih * 3600000 + im * 60000 + is * 1000 + m);
    flags = (byte) leap;
    //Util.prt("nsnTime yr="+year+" doy="+doy+" ms="+ms);
  }

  /**
   * create an NsnTime from a GregorianCalendar
   *
   * @param g The GregorianCalendar with desired time
   */
  public NsnTime(GregorianCalendar g) {
    year = (short) g.get(Calendar.YEAR);
    doy = (short) g.get(Calendar.DAY_OF_YEAR);
    ms = (int) (g.getTimeInMillis() % 86400000L);
    flags = 0;
  }

  /**
   * tcToVars takes a 6 byte NSN time in raw form and converts it to the internal variables for this
   * class. It is called mainly by the constructors
   */
  private void tcToVars() {
    //Util.prt("to vars tc="+tc[0]+" "+tc[1]+" "+tc[2]+" "+tc[3]+" "+tc[4]+" "+tc[5]+" ");

    // Note : the ((int) byte & 0xff) is necessary to prevent sign extensions screwing up
    // the bits.
    year = (short) (((int) tc[0] & 0xff) / 2 + 1970);
    doy = (short) (((int) tc[1] & 0xff) + (((tc[0] & 1) == 1) ? 256 : 0));

    ms = (((int) tc[2] & 0xff) << 24) | (((int) tc[3] & 0xff) << 16)
            | (((int) tc[4] & 0xff) << 8) | ((int) tc[5] & 0xff);
    //Util.prt(" ms="+ms+" hex="+Util.toHex(ms));
    flags = (byte) (ms & 0xf);
    ms = ms / 16;
  }

  /**
   * getAsBuf returns the time code as a 6 byte raw byte array as it would be on the wire. the
   * conversions from wider forms to byte seems to transfer the bits from the lower order and throw
   * away the upper bits without paying any attentions to the sign extension or sign changes that
   * must occur. Hopefully this works on other platforms as well!
   *
   * @return The 6 bytes of a NsnTime ready for a communications buffer
   */
  public byte[] getAsBuf() {
    byte[] b = new byte[6];
    int m;
    b[0] = (byte) ((year - 1970) * 2);
    if (doy > 255) {
      b[0] |= 1;
      b[1] = (byte) (doy & 0xff);        // note the 9 bit is thrown away here
    } else {
      b[1] = (byte) doy;
    }
    m = ms * 16 | flags;
    b[2] = (byte) ((m >> 24) & 0xff);
    b[3] = (byte) ((m >> 16) & 0xff);
    b[4] = (byte) ((m >> 8) & 0xff);
    b[5] = (byte) (m & 0xff);
    return b;
  }

  /**
   * convert the time to a string of form yyyy ddd:hh:mm:ss.nnn
   *
   * @return String of form yyyy ddd:hh:mm:ss:nnnn
   */
  @Override
  public String toString() {
    DecimalFormat df = new DecimalFormat("00");
    int m = ms;
    StringBuilder s = new StringBuilder(20);
    s.append(year).append(" ").append(doy).append(":");
    s.append(df.format(m / 3600000)).append(":");
    m = m % 3600000;
    s.append(df.format(m / 60000)).append(":");
    m = m % 60000;
    s.append(df.format(m / 1000)).append(".");
    m = m % 1000;
    s.append(df.format(m));
    return s.toString();
  }

  /**
   * unit test main
   *
   * @param args Ignored
   */
  public static void main(String[] args) {
    NsnTime t = new NsnTime(2002, 211, 1, 13, 14, 999, 1);
    byte[] b = t.getAsBuf();
    for (int i = 0; i < 6; i++) {
      Util.prt("b[" + i + "] = " + b[i]);
    }
    Util.prt("toString=" + t);
    NsnTime t2 = new NsnTime(b);
    Util.prt("t2 b.len=" + b.length + " toString=" + t2.toString());
    System.exit(0);
  }

}
