/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.anss.net;

/*
 * Wget.java
 *
 * Created on October 24, 2005, 3:15 PM
 *
 * Pick up this basic form off the internet.  Implements a basic WGET command
 * for use mainly with testing balers.  Needs to be expanded to :
 *
 *1) user threads on the read back
 *2) Allow selection of output to a) file, b) StringBuffer, c) List on console
 *3) Q330/Baler specific helper functions for the common commands
 *
 */
 /*
 * This is a simply wget. 
 * You can test your URL
 *
 * $Log: Wget.java,v $
 * Revision 1.5  2009/11/27 18:02:01  ketchum
 * Fix to use socketRead() rather than socketReadFully().
 *
 * Revision 1.4  2009/11/25 17:30:40  ketchum
 * Fix change in EOF condition between straing read and socketRead()
 *
 * Revision 1.3  2009/11/18 22:25:19  ketchum
 * Change to use Util.socketRead()
 *
 * Revision 1.2  2009/09/01 19:30:58  ketchum
 * Move to this project.
 *
 * Revision 1.1  2009/08/26 17:58:31  ketchum
 * Initial Version.
 *
 * Revision 1.1  2009/05/13 18:03:02  ketchum
 * Initial version.
 *
 * Revision 1.2  2006/04/10 21:08:04  ketchum
 * Misc tinkering save!
 *
 * Revision 1.1  2005/10/25 18:40:09  ketchum
 * Basic test functionality
 *
 */
import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.net.MalformedURLException;
//import java.net.URLEncoder;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import gov.usgs.anss.util.Util;

/**
 * @version $Revision: 1.5 $
 */
public final class Wget {

  private String commandName;
  private int count;
  private boolean dbg;
  private URLConnection url;
  private URL urlorg;
  private int responseCode;
  private byte[] out = new byte[1000000];
  private int nbyte;

  public int getLength() {
    return nbyte;
  }

  public byte[] getBuf() {
    return out;
  }

  public Wget(String command) throws IOException, MalformedURLException {

    commandName = command;
    try {
      urlorg = new URL(command);
      url = urlorg.openConnection();
      //printHeader(url);
      if (url instanceof HttpURLConnection || url instanceof HttpsURLConnection) {
        //if(((HttpURLConnection) url).getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM) {
        //  Util.prt("Looks like this is a https site  retry it command="+command);
        //url = (new URL(command.replace("http:", "https:"))).openConnection();
        //Util.prt("response="+((HttpsURLConnection) url).getResponseCode());   // This did not work
        //}
        readHttpURL((HttpURLConnection) url);
      } else {
        readURL(url.getInputStream());
      }

    } catch (MalformedURLException e) {
      System.out.println("WGET: Malformed URL = command");
      throw e;
    } catch (java.io.IOException e) {
      //e.printStackTrace();
      System.out.println("WGET: IOException thrown" + e);
      throw e;
    }
  }

  private void readURL(InputStream in) throws IOException {

    try {
      while (true) {
        int nb = Util.socketRead(in, out, nbyte, out.length - nbyte);
        if (nb <= 0) {
          break;
        }
        nbyte += nb;
        if (nbyte >= out.length) {
          byte[] temp = new byte[out.length * 2];
          System.arraycopy(out, 0, temp, 0, nbyte);
          out = temp;
        }
      }
    } catch (EOFException e) {
      if (dbg) {
        Util.prta(commandName
                + ": Read " + nbyte
                + " bytes from " + url.getURL());
      }
    } catch (IOException e) {
      Util.prta(e + ": " + e.getMessage());
      if (dbg) {
        Util.prt(commandName
                + ": Read " + count
                + " bytes from " + url.getURL());
      }
    }
    //Util.exit(0);
  }

  private void readHttpURL(HttpURLConnection url)
          throws IOException {

    long before, after;

    //url.setAllowUserInteraction (true);
    if (dbg) {
      Util.prt(commandName + ": Contacting the URL ...");
    }
    url.connect();
    if (dbg) {
      Util.prt(commandName + ": Connect. Waiting for reply ...");
    }
    before = System.currentTimeMillis();
    InputStream in = url.getInputStream();
    after = System.currentTimeMillis();
    if (dbg) {
      Util.prt(commandName + ": The reply takes "
              + ((int) (after - before) / 1000) + " seconds");
    }

    before = System.currentTimeMillis();
    responseCode = url.getResponseCode();

    try {
      if (url.getResponseCode() != HttpURLConnection.HTTP_OK) {
        Util.prt(commandName + ": " + url.getResponseMessage());
      } else {
        //printHeader(url);
        readURL(url.getInputStream());
      }
    } catch (EOFException e) {
      after = System.currentTimeMillis();
      int milliSeconds = (int) (after - before);
      if (dbg) {
        Util.prt(commandName
                + ": Read " + count
                + " bytes from " + url.getURL());
      }
      if (dbg) {
        Util.prt(commandName + ": HTTP/1.0 " + url.getResponseCode()
                + " " + url.getResponseMessage());
      }
      url.getInputStream().close();
      url.disconnect();

      Util.prt(commandName + ": It takes " + (milliSeconds / 1000)
              + " seconds" + " (at " + round(count / (float) milliSeconds)
              + " K/sec).");
      if (url.usingProxy()) {
        if (dbg) {
          Util.prt(commandName + ": This URL uses a proxy");
        }
      }
    } catch (IOException e) {
      Util.prt(e + ": " + e.getMessage());
      if (dbg) {
        Util.prt(commandName
                + ": I/O Error : Read " + count
                + " bytes from " + url.getURL());
      }
      Util.prt(commandName + ": I/O Error " + url.getResponseMessage());
    }
    url.getInputStream().close();
    url.disconnect();
    //Util.exit(0);
  }

  /**
   * the HTTP response code. They are enumberated in HttpURLConnection.HTTP_OK, etc.
   *
   * @return The HTTP response code
   */
  public int getResponseCode() {
    return responseCode;
  }

  public float round(float f) {
    return Math.round(f * 100) / (float) 100;
  }

  private void printHeader(URLConnection url) {
    Util.prt(": Content-Length   : "
            + url.getContentLength());
    Util.prt(": Content-Type     : "
            + url.getContentType());
    if (url.getContentEncoding() != null) {
      Util.prt(": Content-Encoding : "
              + url.getContentEncoding());
    }
  }

  // -v http://69.19.65.27:5354/RETRIEVE.HTM?SEED=BHZ&MAX=&BEG=&END=&STN=&FILE=&REQ=List+Data+Avail.&DONE=YES
  public static void main(String args[]) throws IOException {
    boolean out = true;
    boolean verb = false;
    Wget w2 = new Wget("http://earthquake.usgs.gov/fdsnws/event/1/query?format=csv&starttime=2017-02-03T00:00:00&endtime=2017-02-04T00:00:00");
    Util.prt(new String(w2.getBuf(), 0, w2.getLength()) + " len=" + w2.getLength());

    String seed = "USISCO BHZ";
    String ip = args[args.length - 1];
    try {
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-v")) {
          verb = true;
        }
        if (args[i].equals("-np")) {
          out = false;
        }
        if (args[i].equals("-c")) {
          seed = args[i + 1];
        }
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("USAGE: Wget [-v] [-np] <url>");
      System.out.println("\t-v  : verbose all action.");
      System.out.println("\t-np : don't print the data from the URL.");
      System.out.println("\t-c  channel (BH?)");

      System.exit(1);
    }

    String command;
    /*command = "http://" + args[args.length - 1] + ":5354/RETRIEVE.HTM?SEED="
            + URLEncoder.encode(seed, "UTF-8") + "&MAX=&BEG="
            + URLEncoder.encode("2008/1/1 10:30", "UTF-8")
            + "&END=" + URLEncoder.encode("2008/1/1 10:45", "UTF-8")
            + "&STN=&REQ=Download+Data&FILE=tmp.ms&DONE=YES";*/
    command = "http://www.iris.edu/earthscope/usarray/_US-TA-OpStationList.txt";

    Util.prt(command);
    Util.prt("http://67.47.200.99:5354/RETRIEVE.HTM?SEED=USISCO+BHZ&MAX=&BEG=2009%2F1%2F1+10%3A00&END=2009%2F1%2F1+11%3A00&STN=&REQ=Download+Data&FILE=tmp.ms&DONE=YES");
    Wget w = new Wget(command);
    Util.prt("getLength=" + w.getLength());
    Util.prt(new String(w.getBuf(), 0, w.getLength()));

    //URLConnection url = (new URL(args[0])).openConnection();
  }
//http://67.47.200.99:5354/RETRIEVE.HTM?SEED=USISCO+BHZ&MAX=&BEG=2008%2F1%2F1+10%3A00&END=2008%2F1%2F1+11%3A00&STN=&REQ=Download+Data&FILE=&DONE=YES
}
