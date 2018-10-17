/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.utility;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
/** This classs changes a MySQL dump so that it can be loaded into Postgres.  This was part of the
 * Postgres conversion that never happened, we sent to MariaDB instead.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class MySQLDump2PG {
  public static void main(String [] args) {
    String line;
    for (String arg : args) {
      try {
        PrintStream out;
        try (BufferedReader in = new BufferedReader(new FileReader(arg))) {
          out = new PrintStream(arg + "pg");
          while ( (line=in.readLine()) != null) {
            if(line.length() > 8) {
              if(line.substring(0,11).equals("LOCK TABLES")) line = "--- "+line;
              if(line.substring(0,13).equals("UNLOCK TABLES")) line = "--- "+line;
              line = line.replaceAll("0000-00-00","1970-01-03");
              line = line.replaceAll("`","");
              line = line.replaceAll("\\\\'","\\\\047");
              
            }
            out.println(line);
          }
        }
        out.close();
      }catch(IOException e) {
        Util.prt("IOErrof="+e);
        e.printStackTrace();
      }
    }
  }
}
