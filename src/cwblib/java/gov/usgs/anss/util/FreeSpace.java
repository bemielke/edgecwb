/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.IOException;
import java.io.File;

/**
 *
 * @author davidketchum
 */
public final class FreeSpace {

  /**
   * Creates a new instance of FreeSpace normally in megabytes free
   */
  public FreeSpace() {
  }

  public static int getFree(String file) {
    //Util.prta(Util.getOS()+" is the OS");
    if (Util.getOS().contains("Mac") || Util.getOS().contains("SunOS") || Util.getOS().contains("Linux")) {
      try {
        // Since version 1.6 freespace is available from file
        if (System.getProperty("java.version").compareTo("1.5~") > 0) {
          File f = new File(file);
          Util.prt("Freespace using library JRE=" + System.getProperty("java.version") + " " + (System.getProperty("java.version").compareTo("1.5") > 0));
          return (int) (f.getFreeSpace() / 1024 / 1024);
        }
        gov.usgs.anss.util.Subprocess sp;
        if (Util.getOS().contains("Linux")) {
          sp = new gov.usgs.anss.util.Subprocess(Util.homedir + "bin" + Util.fs + "freespace " + file);
          Util.prta("Do freespace linux on file=" + file);
        } else {
          sp = new gov.usgs.anss.util.Subprocess(Util.fs + "bin" + Util.fs + "freespace " + file);
        }
        sp.waitFor();
        String line = sp.getOutput().trim();
        Util.prt("Returned freespace=" + line + " err=" + sp.getErrorOutput());
        //Util.prta("FreeSpace.getFree(Mac) returned="+line);
        if (!sp.getErrorOutput().equals("")) {
          Util.prt("Err=" + sp.getErrorOutput());
        }
        return Integer.parseInt(line);
      } catch (IOException e) {
        Util.prt("Freespace getFree() IOException " + e.getMessage());
      } catch (InterruptedException e) {
        Util.prt("FreeSpace.getFree() Mac interrupted");
      }
    } else if (Util.getOS().contains("Windows")) {
      if (System.getProperty("java.version").compareTo("1.5") > 0) {
        File f = new File(file);
        return (int) (f.getFreeSpace() / 1024 / 1024);
      }
      Util.prt("***** freespace is not implemented on Windows at less the JRE 1.6");
    }
    return 0;
  }

  public static void main(String[] args) {
    Util.init();
    Util.prt("FreeSpace.getFree()=" + FreeSpace.getFree("/Users/data"));
  }
}
