/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.msread.FixUp;
import java.io.*;
/** Kragness created a channel list of MT channels to use.  He wanted the UseForMwb
 * flag set for each channel on this list.  This code does just that.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class SetMwbList {
  public static void main(String [] args) {
    try {
      BufferedReader in = new BufferedReader(new FileReader("mt_channel_list.txt"));
      in.readLine();  // skip a line
      in.readLine();  // skip a line
      String line;
      StringBuilder sb = new StringBuilder(10000);
      sb.append("UPDATE channel SET hydraflags=hydraflags | 8 WHERE channel in (");
      while( (line = in.readLine()) != null) {
        String channel = line.substring(8,10)+line.substring(0,5)+line.substring(28,31)+line.substring(18,20);
        channel = channel.replaceAll("-"," ").trim();
        sb.append("'").append(channel).append("',");
        
      }
      sb.delete(sb.length()-1, sb.length());
      sb.append(");");
      RandomAccessFile out = new RandomAccessFile("mt_channel_list.sql", "rw");
      out.seek(0);
      out.write(sb.toString().getBytes());
      out.setLength(sb.length());
      out.close();
    }
    catch(IOException e) {}
  }
}
