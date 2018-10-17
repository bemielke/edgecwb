/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.edge;
/** This class holds a HoldingSummary record.
 * 
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
  public class HoldingSummary {
    String seedname;
    int length;
    public HoldingSummary(String seed, int ms){
      seedname=seed;
      length=ms;
    }
    public void add(int ms) {length += ms;}
    public String getSeedname(){return seedname;}
    public int getLength() {return length;}
    @Override
    public String toString() {return "Hsum: "+seedname+" "+length+" ms";}
  }
