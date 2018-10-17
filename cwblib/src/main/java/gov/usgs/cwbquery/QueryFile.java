/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.util.SeedUtil;
import java.io.File;
import java.io.IOException;

/**
 * This class contains data about a file. It is used by the QueryFilesThread to track all of the
 * files on all of the paths for quick access within the QueryServer.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class QueryFile {

  private final String fullPath;
  private final int julian;
  private final String node;

  @Override
  public String toString() {
    return julian + " " + fullPath + " nd=" + node;
  }

  public int getJulian() {
    return julian;
  }

  public String getNode() {
    return node;
  }

  public String getCanonicalPath() {
    return fullPath;
  }

  public QueryFile(File f, int jul) throws IOException {
    fullPath = f.getCanonicalPath();
    if (f.getName().length() > 13) {
      node = f.getName().replaceAll(".idx", "").substring(9);
    } else {
      node = "";
    }
    String name = f.getName();
    int year = Integer.parseInt(name.substring(0, 4));
    int doy = Integer.parseInt(name.substring(5, 8));
    julian = SeedUtil.toJulian(year, doy);
  }
}
