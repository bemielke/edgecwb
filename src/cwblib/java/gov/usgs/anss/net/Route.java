/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * Route.java
 *
 * Created on October 23, 2006, 12:26 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.net;
import java.util.StringTokenizer;
import gov.usgs.anss.util.*;


/** This class basically takes one line of output of a netstat -nr and stores
 * the routing destination and gateway from the line or builds up a route from
 * a given ip, gateway and mask.  It has methods to return same as a byte array or int with 
 * high order ip byte in the high order of the int, and can convert bit mask to 
 * nbits representation.
 */
public class Route {
  String tag;               // A user way of taging this route
  String destination;       // The VSAT address (or other routed destination
  String gateway;           // The gateway through which it is routed
  String mask;              // The mask of the route
  @Override
  public String toString(){return tag+" IP="+destination+"/"+getMaskNbits()+
      " gateway="+gateway;}
  public Route(String tg, String ip, String msk, String gate) {
    tag = tg;destination=clean(ip); mask=clean(msk); gateway=clean(gate); 
  }
  public static  String clean(String ip) {
    if(ip.equals("")) return "";
    int [] b = bytesFromIP(ip);
    if(b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0) return "";
    return b[0]+"."+b[1]+"."+b[2]+"."+b[3];
    
  }
  /** make a route from a line returned from netstat -nvr
   * @param tg A tag to store with the route.
   *@param line The line from the command
   */
  public Route(String tg,String line) {
    tag=tg;
    byte [] b=new byte[4];
    line = line.replaceAll("    ", " ");
    for(int i=0; i<10; i++) line=line.replaceAll("  "," ");
    String [] tokens = line.split("\\s");
    if(Util.getOS().equals("Linux")) {
      //Util.prt("Route len="+tokens.length+" 0="+tokens[0]+" 1="+tokens[1]+" 2="+tokens[2]+" 3="+tokens[3]);
      if(tokens.length > 3) {
        destination = tokens[0];
        gateway = tokens[1];
        mask = tokens[2];
      }
      
    }
    else {
      if(tokens.length > 3) {
        destination = tokens[0];
        mask = tokens[1];
        gateway = tokens[2];
      }
    }
  }

  public String getDestination() {return destination;}
  public String getGateway() {return gateway;}
  public String getMask() {return mask;}
  public String getTag() {return tag;}
  public int [] getDestinationBytes() {return bytesFromIP(destination);}
  public int [] getGatewayBytes() {return bytesFromIP(gateway);}
  public int [] getMaskBytes() {return bytesFromIP(mask);}
  public int getDestinationInt() {return intFromIP(destination);}
  public int getGatewayInt() {return intFromIP(gateway);}
  public int getMaskInt() {return intFromIP(mask);}
  /** return the number of bits in the mask ala the route command (e.g.255.255.255.248 return 29)
   *@return the number of bits of 1 in the mask, -1 if mask is invalid
   */
  public int getMaskNbits() {
    int nmaskbits;
    int m = 0x80000000;
    int msk = getMaskInt();
    for(nmaskbits =0; nmaskbits<32; nmaskbits++) {
      if( (m & msk) == 0) break; // first bit not set, break out
      msk = msk & ~m;       // turn bit off, looking or zero when done
      m = m >> 1;
    }    
    if(msk != 0) return -1;
    return nmaskbits;
  }
  /** check that these two routings are consistent.  If the IPs are in the same subnet
   * with either mask and the gateways are not the same or the masks are not the same,
   * then return false; 
   *@param r The route to check against for consistency
   *@return true if the routes are consistent
   */
  public boolean isCompatible(Route r) {
    if(destination.length() < 9) Util.prta("Under length string in is compatible dest="+destination);
    else {
      if(Util.isNEICHost(destination)) return true;
      if(destination.length() >= 10 && destination.substring(0,10).equals("10.177.0.0")) return true;  // VSATs on HN7700
      if(destination.length() >= 10 && destination.substring(0,10).equals("10.177.1.0")) return true;  // VSATs on HN7700
      if(destination.length() >= 11 && destination.substring(0,11).equals("10.177.24.0")) return true;  // VSATs on HN7700
      if(destination.length() >= 11 && destination.substring(0,11).equals("10.177.29.0")) return true;  // VSATs on HN7700
      if(destination.length() >= 11 && destination.substring(0,11).equals("10.177.28.0")) return true;  // VSATs on HN7700
      if(destination.length() >= 12 && destination.substring(0,12).equals("10.177.128.0")) return true;  // VSATs on HN7700
      if(destination.length() >= 12 && destination.substring(0,12).equals("10.177.142.0")) return true;  // VSATs on HN7700
      // A route line 10.177.0.0/16 to 24.158 advmss 1200 was created when something broke the max packet size.
      // So this route always conflicts with VSATs on override.  Do not allow it to be a problem.
      if(r.destination.equals("10.177.0.0") && r.getMaskNbits() == 16) return true;
      if(destination.equals("10.177.0.0") && getMaskNbits() == 16) return true;
      
    }
    
    if(getGatewayInt() == 0 || r.getGatewayInt() == 0) return true;  // default gateway
    if( (intFromIP(destination) & getMaskInt()) == (r.getDestinationInt() & getMaskInt()) ) {
      if(getMaskNbits() == -1 || r.getMaskNbits() == -1) return false;
      if(getGatewayInt() != r.getGatewayInt()) return false; // gateways to not match
      if(getMaskInt() != r.getMaskInt()) return false;        // masks not same
    }
    if( (intFromIP(destination) & r.getMaskInt()) == (r.getDestinationInt() & r.getMaskInt()) ) {
      if(getMaskNbits() == -1 || r.getMaskNbits() == -1) return false;
      if(getGatewayInt() != r.getGatewayInt()) return false; // gateways to not match
      if(getMaskInt() != r.getMaskInt()) return false;        // masks not same
    }
    return true;
  }
  /** is OK is true if the three components of the route are not null, and the
   * IP address is not all zeros, and the bit mask has all zeros below the first
   * zero bit found
   * @return Ture if the route is OK
   */
  public boolean isOK() {
    if(destination == null || gateway == null || mask == null) return false;
    int [] b = bytesFromIP(destination);
    if(b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0) return false;
    return getMaskNbits() != -1;
  }
  /** given an IP address of the form nnn.nnn.nnn.nnn return the component bytes
   * as an array of ints
   *@param ip The IP address as a string of dotted numbers
   *@return an array of 4 its representing the address 
   */
  public static int intFromIP(String ip) {
    int [] b = bytesFromIP(ip);
    return   b[0] << 24 | b[1] << 16 | b[2] << 8 | b[3];
  }
  /** get network address (the one at the subnet base) for this host using this netmask
   * @return The network address string from the destination
   */
  public String getNetworkAddress() {
    int ip = intFromIP(destination);
    ip = ip & getMaskInt();
    return stringFromInt(ip);
    
  }
  /** get a IP address from an int representing it
   * @param ip IP address as an int
   * @return The string representation of the IP
   *
   */
  public static String stringFromInt(int ip) {
    return ""+( (ip>>24) & 0xff)+"."+((ip >> 16) & 0xff)+"."+((ip >> 8) & 0xff)+"."+(ip & 0xff);
  }
   /** given an IP address of the form nnn.nnn.nnn.nnn return the component bytes
   * as an array of ints
   *@param ip The IP address as a string of dotted numbers
   *@return an array of 4 its representing the address 
   */
  public static int [] bytesFromIP(String ip) {
    int [] b = new int[4];
    if(ip.equals("")) return b;
    StringTokenizer tk = new StringTokenizer(ip,".");
    //Util.prt("bytes From IP count="+tk.countTokens());
    if(tk.countTokens() < 4) {
      new RuntimeException("BytesFromIP gives two few tokens="+tk.countTokens()+" ip="+ip).printStackTrace();
    }
    int i=0;
    while(tk.hasMoreTokens()) {
      try { 
        String  s = tk.nextToken();
        //Util.prt("i="+i+" s="+s);
        b[i]=Integer.parseInt(s);
        i++;
      }
      catch(NumberFormatException e) {
        //Util.prt("Bytes from IP i="+i+" could not convert");
        b[i]=0;
      }
      if(i == 4) break;
    }
    return b;
  }
  
}
