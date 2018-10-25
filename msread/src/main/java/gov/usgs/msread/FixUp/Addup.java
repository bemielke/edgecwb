/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.msread.FixUp;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author davidketchum
 */
import java.io.*;

public class Addup {
  public static void main(String [] args) {
    String line;
    int size;
    long totalsize=0;
    args=new String[2];
    args[0]="data4.ms";
    args[1]="data4.ms2";
    try {
      for(int i=0; i<args.length; i++) {
        totalsize=0;
        BufferedReader in = new BufferedReader(new FileReader(args[i]));
        while ( (line = in.readLine())!= null) {
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          String [] parts = line.split(" ");
          //System.out.println(parts[4]+" line="+line);
          totalsize += Long.parseLong(parts[4]);
        }
        System.out.println(args[i]+" "+totalsize/1024/1024);
      }
    }
    catch(IOException e) {
      System.out.println("e="+e);
      e.printStackTrace();
    }
  }
}
