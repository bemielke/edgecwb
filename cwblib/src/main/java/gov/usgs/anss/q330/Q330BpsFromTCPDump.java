/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.q330;
import java.io.*;
import java.util.*;

/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class Q330BpsFromTCPDump {
  private final TreeMap<String, Stat> q330s = new TreeMap<>();
  public Q330BpsFromTCPDump() {
    
  }
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(100000);
    Iterator<Stat> itr = q330s.values().iterator();
    while(itr.hasNext()) {
      StringBuilder append = sb.append(itr.next().toString()).append("\n");
    }
    return sb.toString();
  }
  public void analyze(String inboundFile, String outboundFile) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(inboundFile));
      String line;
      String lastHour="";
      long timeOffset=0;
      while( (line = in.readLine()) != null) {
        String [] parts = line.split("\\s");
        if(parts.length != 8) {
          System.out.println("Skip bad line="+line);
          continue;
        }           
        String [] tparts = parts[0].split(":");
        if(tparts.length != 3) {
          System.out.println("Skip bad line="+line);
          continue;
        }        
        if(lastHour.equals("23") && tparts[0].equals("00")) 
          timeOffset += 86400;
        long time = (long) (Integer.parseInt(tparts[0])*3600.+Integer.parseInt(tparts[1])*60.+Double.parseDouble(tparts[2])+timeOffset+0.5);
        long length = Long.parseLong(parts[7]);
        lastHour = tparts[0];
        String station = parts[2].substring(0,parts[2].indexOf("-"));
        Stat s = q330s.get(station);
        if(s == null) {
          s = new Stat(station);
          q330s.put(station,s);
        }
        s.addIn(time, length);
      }
      in.close();
      in = new BufferedReader(new FileReader(outboundFile));
      timeOffset = 0;
      lastHour="";
      while( (line = in.readLine()) != null) {
        String [] parts = line.split("\\s");
        if(parts.length != 8) {
          System.out.println("Skip bad line2="+line);
          continue;
        }        
        String [] tparts = parts[0].split(":");
        if(tparts.length != 3) {
          System.out.println("Skip bad line2="+line);
          continue;
        }
        if(lastHour.equals("23") && tparts[0].equals("00")) 
          timeOffset += 86400;
        long time = (long) (Integer.parseInt(tparts[0])*3600.+Integer.parseInt(tparts[1])*60.+Double.parseDouble(tparts[2])+timeOffset+0.5);
        long length = Long.parseLong(parts[7]);
        lastHour=tparts[0];
        String station = parts[4].substring(0,parts[4].indexOf("-"));
        Stat s = q330s.get(station);
        if(s == null) {
          System.out.println("Found outbound but no inbound!!! "+station);
          s = new Stat(station);
          q330s.put(station,s);
        }
        s.addOut(time, length);
      }
      
    }
    catch(IOException e) {
      System.err.println("IOError processing ="+e);
      e.printStackTrace();
    }
  }
  class Stat {
    long startTime;
    long endTime;
    long nbytes;
    long noutbytes;
    int npackets;
    int npacketsout;
    String station;
    @Override
    public String toString() {
      return station+"\t"+(endTime-startTime)+"\t"+nbytes+"\t"+noutbytes+"\t"+npackets+"\t"+npacketsout;
    }
    public Stat(String name) {
      station=name;
    }
    public void addIn(long time, long nbytes) {
      this.nbytes += nbytes;
      npackets++;
      if(time > endTime) endTime=time;
      if(startTime == 0) startTime=time;
    }
    public void addOut(long time, long nbytes) {
      if(time >= startTime && time <= endTime) {
        noutbytes += nbytes;
        npacketsout++;
      }
    }
  }
  public static void main(String [] args) {
    Q330BpsFromTCPDump q330 = new Q330BpsFromTCPDump();
    q330.analyze("TEMP/q330_bps.tmp","TEMP/q330_bps_out.tmp");
    System.out.println("Station\tSeconds\tInBytes\tOutBytes\t#PacketIn\t#PacketOut");
    System.out.println(q330.toString());
  }
}
