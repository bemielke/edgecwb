/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.util;
import java.io.*;
/**
 *
 * @author davidketchum
 */
public class KMLReport {
  private static void setColor(StringBuilder kml, String tag, String icon) {
    kml.append("      <Style id=\"").append(tag).
            append("\">\n" + "        <IconStyle>\n" + "          <scale>1.0</scale>\n" + "          <Icon><href>http://maps.google.com/mapfiles/kml/").
            append(icon).append(".png</href></Icon>\n"+
               "        </IconStyle>\n");
    kml.append("     </Style>\n");

  }
  static public StringBuilder formatKML(String report) {
    String [] nets = {"US","IU","CU","II","GE","IM","G ","N4","TA","IW","NE"};
    //String[] icons = {"US#starry", "IU#squash", "CU#caribbean", "II#rasberry", "GE#rainbow", "IM#latte", "G #grid"};
    String[] icons = {"pushpin/red-pushpin", "pushpin/blue-pushpin", "pushpin/grn-pushpin", "pushpin/ltblu-pushpin", 
      "pushpin/purple-pushpin", "pushpin/pink-pushpin", "pushpin/wht-pushpin","pushpin/ylw-pushpin","pushpin/ylw-pushpin",
    "pushpin/red-pushpin","pushpin/red-pushpin"};
    //String [] icons = {"US#starry","IU#squash","CU#caribbean","II#rasberry","GE#rainbow","IM#latte","G #grid"};
    //String [] colors={"emerald","jade","purple","smoke","twillight","mocha","white","graphite","deepsea","radial"};
    String [] colors={
      "paddle/grn-blank", "paddle/wht-blank", "paddle/purple-blank", "paddle/ltblu-blank", "paddle/ylw-blank", "paddle/orange-blank", "paddle/pink-blank","paddle/blu-blank",
      "paddle/grn-circle","paddle/wht-circle","paddle/purple-circle","paddle/ltblu-circle","paddle/ylw-circle","paddle/orange-circle", "paddle/pick-circle","paddle/blu-circle",
      "paddle/grn-diamond","paddle/wht-diamond","paddle/purple-diamond","paddle/ltblu-diamond","paddle/ylw-diamond", "paddle/orange-diamond","paddle/pink-diamond","paddle/blu-diamond",
      "paddle/grn-stars","paddle/wht-stars","paddle/purple-stars","paddle/ltblu-stars","paddle/ylw-stars","paddle/orange-stars","paddle/pink-stars","paddle/blu-stars"};
    int indexLong=-1;
    int indexLat=-1;
    int indexStation=-1;
    int indexElev=-1;
    double lat;
    double lon;
    double elev;
    String icon= "VARY";
    StringBuilder kml = new StringBuilder(100000);
    kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    kml.append("<kml xmlns=\"http://earth.google.com/kml/2.2\">\n");
    kml.append("  <Document>\n  <name>Seismic Stations</name>\n    <open>1</open>\n");
    kml.append("    <LookAt><longitude>-100</longitude><latitude>44.5</latitude><altitude>0</altitude><range>7500000</range><tilt>0</tilt><heading>0</heading></LookAt>\n");
    if(icon.equals("VARY")) {
      for(int i=0; i<nets.length; i++) {
        setColor(kml, nets[i], icons[i].substring(icons[i].indexOf("#")+1));
      }
      for (String color : colors) {
        setColor(kml, color.toUpperCase(), color);
      }

    }
    else {
      setColor(kml, icon.toUpperCase(), icon);
    }
    kml.append("      <Folder>\n");
    int nextNet=0;
    String [] netsUsed= new String[5000];
    String lastStation=null;
    BufferedReader in = new BufferedReader(new StringReader(report));
    String chanList="";
    try {
      String line = in.readLine();
      String [] headers = line.split("\\|");
      for(int i=0; i<headers.length; i++) {
        if(headers[i].trim().equalsIgnoreCase("latitude")) indexLat=i;
        if(headers[i].trim().equalsIgnoreCase("longitude")) indexLong=i;
        if(headers[i].trim().equalsIgnoreCase("elevation")) indexElev=i;
        if(headers[i].trim().equalsIgnoreCase("elev")) indexElev=i;
        if(headers[i].trim().equalsIgnoreCase("station")) indexStation=i;
        if(headers[i].trim().equalsIgnoreCase("channel")) indexStation=i;
        if(headers[i].trim().equalsIgnoreCase("seedname")) indexStation=i;
      }
      if(indexLat == -1 || indexLong == -1 || indexStation == -1) {
        kml.append("Report format does not include Station, Latitude, and Longitude");
        return kml;
      }
      while ( (line = in.readLine()) != null) {
        String [] parts = line.split("\\|");
        if(parts.length < headers.length) continue;
        String net = parts[indexStation].trim().substring(0,2);
        String station = (parts[indexStation].trim()+"       ").substring(0,7);
        if(lastStation == null) lastStation=station;
        lat=0;
        lon =0;
        elev=0;
        try {
          lat = Double.parseDouble(parts[indexLat]);
          lon = Double.parseDouble(parts[indexLong]);
          if(indexElev != -1) elev = Double.parseDouble(parts[indexElev]);
        }
        catch(Exception e) {}

          //Util.prt(ithr+" chan="+chanList.get(k).getChannel());
          //if(chanList.get(k).getChannel().substring(0,5).equals("USBMO"))
          //  Util.prt(ithr+" BMO");
        if(!(parts[indexStation].trim()+"    ").substring(0,7).trim().equals(lastStation)) {
          lastStation = (parts[indexStation].trim()+"      ").substring(0,7).trim();
          String use=null;
          if(icon.equals("VARY")) {
            for (String net1 : nets) {
              if (net1.equals(station.substring(0,2))) {
                use=station.substring(0,2);
              }
            }
            if(use == null)
              for(int i=0; i<nextNet; i++)
                if(netsUsed[i].equals(station.substring(0,2))) use = colors[i % colors.length].substring(colors[i % colors.length].indexOf("#")+1);
            if(use == null) {
              use = colors[nextNet % colors.length].substring(colors[nextNet % colors.length].indexOf("#")+1);
              netsUsed[nextNet++]=station.substring(0,2);
            }
          }
          else use = icon.toUpperCase();
          Util.prt(" process station "+station+":"+" next is "+lastStation);
          kml.append( "       <Placemark>\n");
          kml.append("          <name>").append(station.substring(2,7).trim()).append("</name>\n");
          kml.append("          <Snippet maxLines=\"0\"></Snippet>\n");
          kml.append("          <description><![CDATA[<font face=\"Georgia\" size=\"4\"><table width=\"350\" cellpadding=\"2\" cellspacing=\"3\">\n").
                  append("<tr><th align=\"right\">Network Code </th><td>").append(net).append("</td></tr>\n").
                  append(chanList.length() > 6? "               <tr><th align=\"right\">Channels </th><td>"+chanList+"</td></tr>\n":"");
          for(int i=1; i<Math.min(parts.length,headers.length); i++) {
            kml.append("               <tr><th align=\"right\">").append(headers[i].trim()).append("</th><td>").
                    append(parts[i].trim()).append("</td></tr>\n");
          }
          kml.append("               </table></font>]]></description>\n");
          kml.append("          <LookAt>\n");
          kml.append("            <latitude>").append(lat).append("</latitude>\n");
          kml.append("            <longitude>").append(lon).append("</longitude>\n");
          kml.append("            <altitude>").append(elev).append("</altitude>\n");
          kml.append("            <range>50000</range>\n");
          kml.append("            <tilt>0</tilt>\n");
          kml.append("            <heading>0</heading>\n");
          kml.append("          </LookAt>\n");
          kml.append("          <styleUrl>#").append(use.toUpperCase()).append("</styleUrl>\n");
          kml.append("          <Point><coordinates>").append(lon).append(",").append(lat).
                  append(",").append(elev).append("</coordinates></Point>\n");
          chanList=(parts[indexStation]+"        ").substring(7,12).trim()+",";
          kml.append("        </Placemark>\n");
        }
        else chanList += (parts[indexStation]+"        ").substring(7,12).trim()+",";
      }

      // put on end of document stuff
      kml.append("	  </Folder>\n"+
          "      <ScreenOverlay>\n"+
          "        <name>USGS Logo</name>\n"+
          "        <Icon><href>http://earthquake.usgs.gov/images/ge/USGSlogo.png</href></Icon>\n"+
          "        <overlayXY x=\"1\" y=\"0\" xunits=\"fraction\" yunits=\"pixels\"/>\n"+
          "        <screenXY x=\"0.82\" y=\"30\" xunits=\"fraction\" yunits=\"pixels\"/>\n"+
          "        <rotationXY x=\"0\" y=\"0\" xunits=\"pixels\" yunits=\"pixels\"/>\n"+
          "        <size x=\"0\" y=\"0\" xunits=\"pixels\" yunits=\"pixels\"/>\n"+
          "      </ScreenOverlay>\n"+
          "   </Document>\n"+
          "</kml>\n");
    }
    catch(IOException e) {
      Util.prt("Could not read the report");
    }
    return kml;
  }
}
