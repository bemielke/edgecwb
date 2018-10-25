/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */




/*
 * Holding.java
 *
 * Created on January 25, 2008, 5:20 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.edge;
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
    public String toString() {return "Hsum: "+seedname+" "+length+" ms";}
  }
