/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;
import java.io.PrintStream;

/** This class pull the DP4 and Q330 sections of the ConfigServer XML apart and
 * allows access through getters.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */

public class Q330ConfigServerXML {
  private String xmlorg;
  private  int dp4WebPort;
  private  String dp4StationID;
  private  String dp4Type;
  private  int dp4BasePort;
  private  String dp4IPAdr;
  private  String q330HexSerial;
  private  String q330TagID;
  private  int q330WebPortDef;
  private  int q330BasePortDef;
  private  String q330IPAdrDef;
  private  int q330WebPortLocal;
  private  int q330BasePortLocal;
  private  String q330IPAdrLocal;
  private  int q330WebPortInternet;
  private  int q330BasePortInternet;
  private  String q330IPAdrInternet;
  private  int realmType=-1;
  private  double latitude, longitude, elevation;
  private PrintStream par;
  public String getXML() {return xmlorg;}
  public int getDP4WebPort() {return dp4WebPort;}
  public int getDP4BasePort() {return dp4BasePort;}
  public int getQ330WebPortDef() {return q330WebPortDef;}
  public int getQ330BasePortDef() {return q330BasePortDef;}
  public String getQ330IPAdrDef() {return q330IPAdrDef;}
  public int getQ330WebPortLocal() {return q330WebPortLocal;}
  public int getQ330BasePortLocal() {return q330BasePortLocal;}
  public String getQ330IPAdrLocal() {return q330IPAdrLocal;}
  public int getQ330WebPortInternet() {return q330WebPortInternet;}
  public int getQ330BasePortInternet() {return q330BasePortInternet;}
  public String getQ330IPAdrInternet() {return q330IPAdrInternet;}
  public String getDP4StationID() {return dp4StationID;}
  public String getDP4Type() {return dp4Type;}
  public String getDP4IPAdr() {return dp4IPAdr;}
  public String getQ330HexSerial() {return q330HexSerial;}
  public String getQ330TagID() {return q330TagID;}
  @Override
  public String toString() {
    return 
          "Q330: "+dp4StationID+" tag="+q330TagID+" "+q330IPAdrDef+" base="+q330BasePortDef+" web="+q330WebPortDef+" hex="+q330HexSerial+
          " DP4: type="+dp4Type+" base="+dp4BasePort+" web="+dp4WebPort+" ip="+dp4IPAdr+" coord: "+latitude+" "+longitude+" "+elevation;
  }
  public Q330ConfigServerXML(String xml, PrintStream parent) {
    par = parent;
    reload(xml);

  }
  public final void reload(String xml) {
    xmlorg= xml;
    String [] lines = xml.split("\n");
    int mode=0;
    for(String line: lines) {
      try {
        if(line.contains("<Station>")) 
          mode=1;
        if(line.contains("<Q330>")) 
          mode=3;
        if(line.contains("<DP4>")) 
          mode =2;
        if(line.contains("Longitude")) 
          longitude = Double.parseDouble(getField(line,"<Longitude>"));
        if(line.contains("Latitude")) 
          latitude = Double.parseDouble(getField(line,"<Latitude>"));
        if(line.contains("Elevation")) 
          elevation = Double.parseDouble(getField(line,"<Elevation>"));
        if(mode == 2) {
          if(line.contains("<WebPort>")) 
            dp4WebPort = Integer.parseInt(getField(line,"<WebPort>"));
          else if(line.contains("<StationID")) 
            dp4StationID=getField(line,"<StationID>");
          else if(line.contains("<Type>"))
            dp4Type=getField(line,"<Type>");
          else if(line.contains("<BasePort>")) 
            dp4BasePort=Integer.parseInt(getField(line,"<BasePort>"));
          else if(line.contains("<IPAddress>"))
            dp4IPAdr = getField(line, "<IPAddress>");
        }
        if(mode == 3) {
          if(line.contains("<RealmDefault>") || line.contains("<Ethernet>")) 
            realmType=0;
          if(line.contains("<RealmLocal>")) 
            realmType=1;
          if(line.contains("<RealmInternet>")) 
            realmType=2;
          if(realmType == 0) {
            if(line.contains("<WebPort>"))
              q330WebPortDef=Integer.parseInt(getField(line,"<WebPort>"));
            else if(line.contains("<BasePort>")) 
              q330BasePortDef=Integer.parseInt(getField(line,"<BasePort>"));
            else if(line.contains("<IPAddress>"))
              q330IPAdrDef = getField(line, "<IPAddress>");
          }
         if(realmType == 1) {   // Local
             if(line.contains("<WebPort>"))
              q330WebPortDef=Integer.parseInt(getField(line,"<WebPort>"));
            else if(line.contains("<BasePort>")) 
              q330BasePortDef=Integer.parseInt(getField(line,"<BasePort>"));
            else if(line.contains("<IPAddress>"))
              q330IPAdrDef = getField(line, "<IPAddress>");
          }
          if(realmType == 2) {      // Internet
            if(line.contains("<WebPort>"))
              q330WebPortInternet=Integer.parseInt(getField(line,"<WebPort>"));
            else if(line.contains("<BasePort>")) 
              q330BasePortInternet=Integer.parseInt(getField(line,"<BasePort>"));
            else if(line.contains("<IPAddress>"))
              q330IPAdrInternet = getField(line, "<IPAddress>");
          }
          else {
            if(line.contains("<TagID>"))
              q330TagID = getField(line, "<TagID>");
            else if(line.contains("<SerialNumber>"))
              q330HexSerial = getField(line, "<SerialNumber>");
          }
          if(line.contains("</Q330")) mode=4;
        }
      }
      catch(RuntimeException e) {
        par.println("RuntimeErr line="+line+" "+xmlorg);
        e.printStackTrace(par);
      }
    }
  }
  public final String getField(String line, String tag) {
    return line.trim().replaceAll(tag, "").replaceAll(tag.substring(0,1)+"/"+tag.substring(1), "");
  }
}