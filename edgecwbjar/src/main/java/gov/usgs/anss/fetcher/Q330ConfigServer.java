/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;
import gov.usgs.anss.util.Util;
import java.io.PrintStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
/** config/list/station - list the stations
 *  overview/get/station/NN-SSSS - just serial
 * config/get/station/NN-SSSS - contains ethernet
 * config/get/tagid/TAGID
 * 
 * 
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class Q330ConfigServer {
  String host;
  int port;
  String user;
  String pass;
  String directory;
  TreeMap<String, Q330ConfigServerXML> xml = new TreeMap<>();
  PrintStream par;
  byte [] buf = new byte[50000];
  public ArrayList<String> stations = new ArrayList(100);
  public ArrayList<String> getStations() {return stations;}
  public Q330ConfigServerXML getXMLForStation(String station) {return xml.get(station);}
  public Q330ConfigServer(String host, int port, String user, String pass, PrintStream par) {
    if(host != null) 
      if(host.trim().length() > 0) this.host=host;
    if(port > 0) this.port = port;
    else this.port = 8133;
    this.user=user;
    this.pass=pass;
    this.par = par;
    getData(par);
  }
  private final StringBuilder status = new StringBuilder(100);
  /** This causes a new curl command to get all of the station names and then one 
   * curl command for each station to get is XML.  Each station XML is parsed into
   * a Q330ConfigServerXML object.
   * 
   * @param prt A place to log stuff
   */
  public final void getData(PrintStream prt) {
    String curlline;
    if(user != null) curlline = "http://"+user+":"+pass+"@"+host+":"+port+"/config/list/station";
    else curlline = "http://"+host+":"+port+"/config/list/station";
    try {
      Util.curlit(curlline, "q330cs.tmp", 599, par);
      directory = getStringFromFile("q330cs.tmp");
      String [] stats = directory.split(",");
      stations.addAll(Arrays.asList(stats));
      for(int i=stations.size()-1; i>=0; i--) {
        if(stations.get(i).charAt(2) != '-' && stations.get(i).length() > 8) stations.remove(i);  // Anything that is not a station
      }
      Collections.sort(stations);
      Util.clear(status).append(Util.asctime2()).append(" ");
      for(String station: stations) {
        if(user != null) curlline="http://"+user+":"+pass+"@"+host+":"+port+"/config/get/station/"+station;
        else curlline="http://"+host+":"+port+"/config/get/station/"+station;
        Util.curlit(curlline, "q330.tmp", 250, par);
        String xml2 = getStringFromFile("q330.tmp");
        Q330ConfigServerXML obj = xml.get(station);
        if(obj != null) obj.reload(xml2);
        else xml.put(station, new Q330ConfigServerXML(xml2, prt));
        status.append(Util.rightPad(station,9));
        if(status.length()>75) {
          if(prt != null) prt.println(status);
          Util.clear(status).append(Util.asctime2()).append(" ");
        }
      }
    }
    catch(IOException e) {
      e.printStackTrace(par);       
     }
  }
public final String getStringFromFile(String filename) throws IOException {
    File f = new File(filename);
    if(f.exists()) {
      if(f.length() > buf.length) {
        buf = new byte[(int)f.length()*2];
      }
      try (RandomAccessFile raw = new RandomAccessFile(filename, "r")) {
        raw.seek(0);
        raw.read(buf, 0, (int) f.length());
      }
      return new String(buf, 0, (int) f.length());
    }
    else return null;
  }


  public static void main(String [] args) {
    //String host="magma3.gps.caltech.edu";
    String host = Util.getProperty("ConfigServerHost");
    int port=8133;
    String user = Util.getProperty("ConfigServerUser");
    String pass = Util.getProperty("ConfigUserPass");
    if(args.length ==0) {
      host = "acqdb.cr.usgs.gov";
      user="timer";
      pass="timer123";
    }
    Q330ConfigServer qcs = new Q330ConfigServer(host, port, user, pass, System.out);
    ArrayList<String> stations = qcs.getStations();
    qcs.getData(null);
    for(String station: stations) System.out.println(qcs.getXMLForStation(station));
    
  }
}
