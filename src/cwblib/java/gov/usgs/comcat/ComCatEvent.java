/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.comcat;

import gov.usgs.anss.net.Wget;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.UC;
import javax.xml.bind.JAXBException;
import java.util.Date;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import org.quakeml_1_2.*;

/**
 * This class returns QuakeML from the earthquake.usgs.gov website given an event ID from comcat
 * using the 'query' command (https://earthquake.usgs.gov/fdsnws/event/1/query?[PARAMETERS]. The
 * user only needs to supply up to the /event/1/query as its base URL. It supports most of the query
 * options as well. Seed the documentation of the query format at
 * https://earthquake.usgs.gov/fdsnws/event/1.
 *
 *
 * Normal Usage :
 * <pre>
 * 1) create the ComCatEvent object and optionally set the base URL to something like "https://earthquake.usgs.gov/fdsnws/"
 * 2) Optionally use setParams() to set the eventID,
 * 3) Optionally :
 * a) use setLatLongLimit() to pick a geographic region
 * b) us setLatLongRadius() to set a circular retions
 * c) use setStartEndTime() to set the start and end time
 * d) use setMaxDepth(), setMinDepth(), use setMaxMagnitude(), or setMinMagnitude() to limit the set.
 * e) use setFormat() if something other than quakeml format is needed.
 * f) Other options are setIncludeAllMagnitudes(), setIncludeAllOrigins(), setIncludeArrivals(), setIncludeDeletedEvents()
 * setIncludeSuperseded(), setCatalog(), setContributor()
 * 4) Call executeURL() to fetch the data from the FDSN server, this will return the length of the returned document
 * 5) call parseQuakeML() to marshal/parse the QuakeML document returned, this will return the number of events in the document
 * 6) For simple parsing, call getEvent(int n) for each event starting with zero and process the events using the getter functions
 * 7) call clearURL() to start over and setup  to start a new request
 * </pre> There are advanced usage methods to get the full QuakeML object or the preferred origin as
 * a JAXB object, or to get the full text of the QuakeML document.
 * <br>
 * Picks can be obtained by NSCL by calling "getPick(NSCl)" or an ArrayList of Simple picks is
 * available from getPicksList(); The other getter methods return preferred magnitudes, origin
 * information parse out from the document. Since there are many origins and magnitudes only the
 * preferred ones are picked apart. To process all of the origins or magnitudes get the origin or
 * magnitudes list as JAXB objects from the
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class ComCatEvent {

  public String baseURL = "https://earthquake.usgs.gov/fdsnws/";
  private final StringBuilder url = new StringBuilder(100).append(baseURL);
  private final StringBuilder sb = new StringBuilder(100);
  private final StringBuilder ans = new StringBuilder(100);
  private byte[] buf;
  private long len;
  private double preferredMag;
  private Magnitude preferredMagQML;
  private String preferredMagMethod;
  private Origin preferredOrigin;
  private final Date preferredOriginTime = new Date();
  private double latitude, longitude, depth;
  private String eventID;
  private boolean dbg;
  private Wget wget;
  private Quakeml qml;
  private final ArrayList<SimplePick> pickList = new ArrayList<>(10);

  @Override
  public String toString() {
    return eventID + " " + latitude + " " + longitude + " " + preferredMag + " " + preferredMagMethod
            + " qmllen=" + ans.length() + " #pick=" + pickList.size();
  }

  public double getPreferredMagnitude() {
    return preferredMag;
  }

  public Magnitude getPreferredMagnitudeQML() {
    return preferredMagQML;
  }

  public String getPreferredMagMethod() {
    return preferredMagMethod;
  }

  public long getPreferredOriginTime() {
    return preferredOriginTime.getTime();
  }

  public Origin getPreferredOrigin() {
    return preferredOrigin;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getDepth() {
    return depth;
  }

  public String getEventID() {
    return eventID;
  }

  /**
   *
   * @return The list of picks, if you modify anything on this list it is modified in this
   * ComCatEvent so be careful
   */
  public ArrayList<SimplePick> getPickList() {
    return pickList;
  }

  public int getNumberOfEvents() {
    if (qml == null) {
      return 0;
    }
    EventParameters eventparms = qml.getEventParameters();
    return eventparms.getEvents().size();
  }

  /**
   *
   * @return return the parsed quakeML document if the user wants something more detailed.
   */
  public Quakeml getQuakeMLDocument() {
    return qml;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   *
   * @return the original QuakeML document as an ASCII StringBuilder- this was parsed into a Quakeml
   * document object.
   */
  public StringBuilder getSB() {
    return ans;
  }

  //https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2014-01-01&endtime=2014-01-02
  /**
   * Create a new ComCatEvent with the given URL.
   *
   * @param baseURL User supplied URL, null to use USGS default of
   * "https://earthquake.usgs.gov/fdsnws/"
   */
  public ComCatEvent(String baseURL) {
    if (baseURL != null) {
      this.baseURL = baseURL;
    } else {
      this.baseURL = "https://earthquake.usgs.gov/fdsnws/";
    }
    clearURL(null);
  }

  /**
   * This is a convenience methods for the most common kind of querys from the Edge/CWB picking
   * system.
   *
   * @param format Set the document return type, normally this is null and "xml" is the format
   * @param start Set the start time for querying events (if &lt=0 , then do not use a time range)
   * @param endtime Set the end time for querying events
   * @param lat latitude combined with lng and radius to create a geographic restraint
   * @param lng longitude for circle restraint
   * @param radiuskm Radius in kilometers for circle restarting
   * @param minMag If &gt 0., then set a minimum magnitude
   * @param maxMag if &gt 0., then set a maximum magnitude
   */
  public final void setParams(String format, long start, long endtime,
          double lat, double lng, double radiuskm, double minMag, double maxMag) {
    clearURL(null);
    if (format != null) {
      url.append("format=").append(format);
    } else {
      url.append("format=xml");
    }
    if (start > 864000000) {
      url.append("&starttime=").append(makeURLDate(start));
      if (endtime > 864000000) {
        url.append("&endtime=").append(makeURLDate(endtime));
      }
    }
    if (radiuskm > 0.) {
      setLatLongRadius(lat, lng, radiuskm);
    }
    if (minMag > 0.) {
      setMinMagnitude(minMag);
    }
    if (maxMag > 0.) {
      setMaxMagnitude(maxMag);
    }

  }

  /**
   *
   * @param eventID The event ID like "us8123943", normally no other restraints can be used.
   */
  public void setEvent(String eventID) {
    this.eventID = eventID;
    if (eventID != null) {
      url.append("&eventid=").append(eventID.toLowerCase());
    }
  }

  /**
   *
   * @param start Start time as a long since 1970-01-01 in millis UTC
   * @param endtime End time
   */
  public void setStartEndTimes(long start, long endtime) {
    if (start > 0) {
      url.append("&starttime=").append(makeURLDate(start));
      if (endtime > 0) {
        url.append("&endtime=").append(makeURLDate(endtime));
      }
    }
  }

  /**
   *
   * @param cat The catalog name
   */
  public void setCatalog(String cat) {
    if (cat != null) {
      url.append("&catalog=").append(cat);
    }
  }

  /**
   * @param format Must be csv, geojson, kml, quakeml (def) text, or xml
   */
  public void setFormat(String format) {
    if (format == null) {
      format = "xml";
    }
    url.append("&format=");
    switch (format.toLowerCase()) {
      case "csv":
      case "geojson":
      case "kml":
      case "text":
      case "xml":
      case "quakeml":
        url.append(format.toLowerCase());
        break;
      default:
        url.append("quakeml");
        Util.prt("setFormat() to illegal type - must be csv, geojson, kml, text, xml, or quakeml =" + format);
        throw new java.lang.IllegalArgumentException("setFormat() not legal type - must be cvs, geojson, kml, text, xml or quakeml=" + format);
    }
  }

  /**
   *
   * @param cat The contributor name
   */
  public void setContributor(String cat) {
    if (cat != null) {
      url.append("&contributor=").append(cat);
    }
  }

  /**
   *
   * @param b If true, this parameter is on
   */
  public void setIncludeAllMagnitudes(boolean b) {
    url.append("&includeallmagnitudes=").append(b);
  }

  /**
   *
   * @param b If true, this parameter is on
   */
  public void setIncludeAllOrigins(boolean b) {
    url.append("&includeallorigins=").append(b);
  }

  /**
   *
   * @param b If true, this parameter is on
   */
  public void setIncludeArrivals(boolean b) {
    url.append("&includearrivals=").append(b);
  }

  /**
   *
   * @param b If true, this parameter is on
   */
  public void setIncludeDeletedEvents(boolean b) {
    url.append("&includedeleted=").append(b);
  }

  /**
   *
   * @param b If true, this parameter is on
   */
  public void setIncludeSuperseded(boolean b) {
    url.append("&includesuperseded=").append(b);
  }

  /**
   *
   * @param limit The maximum number of events to return
   */
  public void setLimit(int limit) {
    url.append("&limit=").append(limit);
  }

  /**
   *
   * @param d The maximum depth to include
   */
  public void setMaxDepth(double d) {
    url.append("&maxdepth=").append(d);
  }

  /**
   *
   * @param d The maximum magnitude to include
   */
  public void setMaxMagnitude(double d) {
    url.append("&maxmagnitude=").append(d);
  }

  /**
   *
   * @param d The minimum depth to include
   */
  public void setMinDepth(double d) {
    url.append("&mindepth=").append(d);
  }

  /**
   *
   * @param d The minimum magnitude to include
   */
  public void setMinMagnitude(double d) {
    url.append("&minmagnitude=").append(d);
  }

  /**
   *
   * @param cat The order of the returned quakes - must be time,time-asc, magnitude, magnitude-asc
   */
  public void setOrderBy(String cat) {
    if (cat != null) {
      url.append("&orderby=").append(cat.toLowerCase());
    }
  }

  /**
   *
   * @param lat latitude at center of circle of space
   * @param lng Longitude at the center of the circle of space
   * @param radiusKM radius in KM
   */
  public void setLatLongRadius(double lat, double lng, double radiusKM) {
    if (radiusKM > 0.) {
      url.append("&latitude=").append(lat).append("&longitude=").append(lng).append("&maxradiuskm=").append(radiusKM);
    }
  }

  /**
   * Set other FDSN query restrictions
   *
   * @param lowlat low limit of latitude
   * @param lowlong hight limit of longitude
   * @param highlat high limit of latitude
   * @param highlong hight limit of longitude
   */
  public void setLatLongLimit(double lowlat, double lowlong, double highlat, double highlong) {
    if (lowlat > highlat) {
      double t = lowlat;
      lowlat = highlat;
      highlat = t;
    }
    if (lowlong > highlong) {
      double t = highlong;
      highlong = lowlong;
      lowlong = t;
    }
    if (lowlat < -90. || lowlat > 90. || highlat < -90. || highlat > 90. || lowlong < -180. || lowlong > 180. || highlong < -180. || highlong > 180.) {
      throw new IllegalArgumentException("Latitude or longitude out of range lat=[" + lowlat + "-" + highlat + "] long=[" + lowlong + "-" + highlong + "]");
    }

    url.append("&minlatitude=").append(lowlat).append("&minlongitude=").append(lowlong).
            append("&maxlatitude=").append(highlat).append("&maxlongitude=").append(highlong);
  }

  /**
   * clear the URL builder to begin again
   *
   * @param baseURL The base url to use, if null use the old base URL from object creation
   */
  public final void clearURL(String baseURL) {
    if (baseURL == null) {
      Util.clear(url).append(this.baseURL).append("event/1/query?");
    } else {
      Util.clear(url).append(baseURL).append("event/1/query?");
    }
    pickList.clear();
    qml = null;
    Util.clear(ans);
    if (buf != null) {
      Arrays.fill(buf, (byte) 0);
    }
    wget = null;
  }

  /**
   * Execute the query after all options have been set
   *
   * @return
   * @throws IOException
   */
  public long executeURL() throws IOException {
    if (url.charAt(url.length() - 1) == '&') {
      url.deleteCharAt(url.length() - 1);   // remove any trailing ampersands
    }
    Util.stringBuilderReplaceAll(url, "?&", "?");
    wget = new Wget(url.toString());
    if (wget.getResponseCode() != HttpURLConnection.HTTP_OK) {
      throw new IOException("HTTP to " + url + " did not return HTTP_OK = " + wget.getResponseCode());
    }
    buf = wget.getBuf();
    len = wget.getLength();
    Util.clear(ans);
    Util.bufToSB(buf, 0, (int) len, ans);
    return len;
  }

  /**
   * Execute the query after all options have been set - the caller is responsible for the entire
   * URL
   *
   * @param url The full URL string to send to web services.
   * @return
   * @throws IOException
   */
  public long executeURL(String url) throws IOException {
    wget = new Wget(url);
    if (wget.getResponseCode() != HttpURLConnection.HTTP_OK) {
      throw new IOException("HTTP to " + url + " did not return HTTP_OK = " + wget.getResponseCode());
    }
    buf = wget.getBuf();
    len = wget.getLength();
    Util.clear(ans);
    Util.bufToSB(buf, 0, (int) len, ans);
    return len;
  }

  private StringBuilder makeURLDate(long time) {
    return Util.clear(sb).append(Util.stringBuilderReplaceAll(Util.ascdate(time), "/", "-")).append("T").append(Util.asctime(time));
  }

  public byte[] getBuf() {
    return buf;
  }

  /**
   * get the length of the returned Quakeml ASCII file
   *
   * @return The length in characters
   */
  public long getLength() {
    return ans.length();
  }

  /**
   * Take a user supplied StringBuilder and fill it with the returned ASCII.
   *
   * @param sb User string builder, if null, then an new StringBuilder is made
   * @return The resulting StringBuilder whether the users or a new one
   */
  public StringBuilder getSB(StringBuilder sb) {
    if (sb == null) {
      sb = new StringBuilder(ans.length());
    }
    Util.clear(sb);
    for (int i = 0; i < ans.length(); i++) {
      sb.append(ans.charAt(i));
    }
    return sb;
  }

  /**
   * Load the QuakeML String
   *
   * @param sb
   */
  public void loadQuakeML(StringBuilder sb) {
    if (buf == null) {
      buf = new byte[sb.length()];
    }
    if (buf.length < sb.length()) {
      buf = new byte[sb.length()];
    }
    Util.clear(ans).append(sb);
    len = sb.length();
    Util.stringBuilderToBuf(sb, buf);
  }

  /**
   * Take whatever is in the ASCII buffer and convert to a QuakeML document, parse out the stuff for
   * the position quake.
   *
   * @return
   * @throws JAXBException
   */
  public int parseQuakeML() throws JAXBException {
    if (qml == null) {
      try {
        preferredMag = -1.;
        ByteArrayInputStream qmlin = new ByteArrayInputStream(buf, 0, ans.length());
        JAXBContext jc = JAXBContext.newInstance("org.quakeml_1_2");
        Unmarshaller u = jc.createUnmarshaller();
        qml = (Quakeml) u.unmarshal(qmlin);
      } catch (JAXBException e) {
        e.printStackTrace();
        throw e;
      }
    }
    if (qml == null) {
      return -1;
    }
    EventParameters eventparms = qml.getEventParameters();
    return eventparms.getEvents().size();
  }

  /**
   * Get the nth event from the document and this event is returned as a JAXB event object. You can
   * use the returne object to get to all of the JAXB representation of the object.
   *
   * JAXB uses a lot of lists based on the QuakeML xsd definitions. You can see in this code how the
   * preferred information is picked out from the list of origins, mags, etc and how to process the
   * picks.
   *
   * @param nevent THe event number from zero to the number of events
   * @return The event object just positioned.
   */
  public Event getEvent(int nevent) {
    EventParameters eventparms = qml.getEventParameters();
    List<Event> events = eventparms.getEvents();
    Event event = events.get(nevent);
    String preferredMagID = event.getPreferredMagnitudeID();
    List<Magnitude> mags = event.getMagnitudes();
    eventID = event.getEventsource() + event.getEventid();
    for (Magnitude mag : mags) {
      if (mag.getPublicID().equalsIgnoreCase(preferredMagID)) {
        preferredMag = mag.getMag().getValue().doubleValue();
        preferredMagMethod = mag.getMethodID();
        if (preferredMagMethod == null) {
          preferredMagMethod = "Ml";
        }

        String[] possibles = preferredMagMethod.split("/");
        preferredMagMethod = possibles[possibles.length - 1];
        boolean ok = false;
        for (int i = 0; i < preferredMagMethod.length(); i++) {
          char c = preferredMagMethod.charAt(i);
          if (Character.isLetter(c)) {
            ok = true;
          }
        }
        if (!ok) {
          preferredMagMethod = possibles[possibles.length - 2];
        }
        preferredMagQML = mag;
        break;
      }
    }
    List<Origin> origins = event.getOrigins();
    preferredOrigin = null;
    for (Origin origin : origins) {
      Util.prt(event.getEventid() + " " + event.getPreferredOriginID() + " " + origin.getPublicID() + " " + (event.getPreferredOriginID().equalsIgnoreCase(origin.getPublicID())));
      if (event.getPreferredOriginID().equalsIgnoreCase(origin.getPublicID())) {
        preferredOrigin = origin;
      }
    }
    if (preferredOrigin != null) {
      preferredOriginTime.setTime(preferredOrigin.getTime().getValue().getTime());
      latitude = preferredOrigin.getLatitude().getValue().doubleValue();
      longitude = preferredOrigin.getLongitude().getValue().doubleValue();
      depth = preferredOrigin.getDepth().getValue().doubleValue() / 1000.;

      List<Pick> picks = event.getPicks();
      List<Arrival> arrivals = preferredOrigin.getArrivals();
      for (Arrival arrival : arrivals) {
        String pickID = arrival.getPickID();
        for (Pick pick : picks) {
          if (pick.getPublicID().equalsIgnoreCase(pickID)) {
            if (arrival.getPhase() != null) {
              if (arrival.getPhase().getValue().startsWith("P")) {
                WaveformStreamID waveid = pick.getWaveformID();     // net,station, channel, etc
                TimeQuantity pickTime = pick.getTime();
                StringBuilder nscl = new StringBuilder(12);
                Util.makeSeednameSB(waveid.getNetworkCode(), waveid.getStationCode(), waveid.getChannelCode(), waveid.getLocationCode(), nscl);
                String phase = "P";
                if (pick.getPhaseHint() != null) {
                  if (pick.getPhaseHint().getValue().startsWith("P")) {
                    phase = pick.getPhaseHint().getValue();
                  }
                } else {
                  phase = arrival.getPhase().getValue();
                }
                pickList.add(new SimplePick(nscl.toString(), pickTime.getValue().getTime(), phase));
                if (dbg) {
                  Util.prt(nscl + " "
                          + Util.ascdatetime2(pickTime.getValue().getTime()) + " " + phase);
                }
                break;
              }
            }
          }
        }
      }
    } else {
      List<Pick> picks = event.getPicks();
      for (Pick pick : picks) {
        WaveformStreamID waveid = pick.getWaveformID();     // net,station, channel, etc
        TimeQuantity pickTime = pick.getTime();
        StringBuilder nscl = new StringBuilder(12);
        Date d = new Date();
        Util.makeSeednameSB(waveid.getNetworkCode(), waveid.getStationCode(), waveid.getChannelCode(), waveid.getLocationCode(), nscl);
        d.setTime(pickTime.getValue().getTime());
        pickList.add(new SimplePick(nscl.toString(), pickTime.getValue().getTime(), pick.getPhaseHint().getValue()));
        if (dbg) {
          Util.prt(waveid.getNetworkCode() + "." + waveid.getStationCode() + "."
                  + waveid.getChannelCode() + "." + waveid.getLocationCode() + " "
                  + Util.ascdatetime2(pickTime.getValue().getTime()));
        }

      }
    }
    return event;
  }

  /**
   *
   * @param nscl A 12 character NSCL to fine
   * @return The pick time
   * @throws JAXBException If loading the QML document fails.
   */
  public SimplePick getPick(String nscl) throws JAXBException {
    if (qml == null) {
      parseQuakeML();
    }
    int lenmax = getNSCLLength(nscl);
    for (int i = 0; i < pickList.size(); i++) {
      if (Util.sbCompareTo(nscl, pickList.get(i).getNSCL(), lenmax) == 0) {
        return pickList.get(i);
      }
    }
    return null;
  }

  private int getNSCLLength(String nscl) {
    int l = nscl.length();
    if (l <= 0) {
      return 0;
    }
    for (int i = nscl.length() - 1; i <= 0; i--) {
      if (nscl.charAt(i) != '.' && nscl.charAt(i) != ' ') {
        l = i;
        break;
      }
    }
    return l;
  }

  public static void main(String[] args) {

    Util.init(UC.getPropertyFilename());
    Util.setModeGMT();
    ComCatEvent evt = new ComCatEvent(null);
    ComCatEvent evtpicks = new ComCatEvent(null);
    evt.clearURL(null);
    GregorianCalendar start = new GregorianCalendar(2017, 3, 1, 0, 0, 0);
    GregorianCalendar end = new GregorianCalendar();
    end.setTimeInMillis(System.currentTimeMillis());
    evt.setParams(null, start.getTimeInMillis(), end.getTimeInMillis(), 46.89, -112.55, 10., 2.0, 10.0);
    //evt.setLatLongLimit(46.7, -112.9, 47., -112.1);
    try {
      long len = evt.executeURL();
      int nevents = evt.parseQuakeML();
      Util.prt("len = " + len + " nevents=" + nevents);
      for (int i = 0; i < nevents; i++) {
        Event event = evt.getEvent(i);
        ArrayList<SimplePick> picks = evt.getPickList();
        Util.prt("pubid=" + event.getPublicID() + " source=" + event.getEventsource() + " eventid=" + event.getEventid() + " did=" + event.getDataid() + " #p=" + event.getPicks().size());
        Util.prt(evt.getEventID() + " " + Util.ascdatetime2(evt.getPreferredOriginTime()) + " "
                + evt.getLatitude() + " " + evt.getLongitude() + " " + evt.getDepth() + " " + evt.getPreferredMagnitude() + " "
                + evt.getPreferredMagMethod() + " #picks=" + picks.size());
        evtpicks.clearURL(null);
        evtpicks.setEvent(event.getEventsource() + event.getEventid());
        long lenPicks = evtpicks.executeURL();
        //long lenPicks = evtpicks.executeURL(event.getPublicID().replace("quakeml:", "https://"));
        int nev = evtpicks.parseQuakeML();
        evtpicks.getEvent(0);
        ArrayList<SimplePick> pick2 = evtpicks.getPickList();
        Util.prt("Get event by code=" + evtpicks.getEventID() + " len=" + lenPicks + " nev=" + nev + " #=" + pick2.size());
        //for (int j = 0; j < picks.size(); j++) {
        //Util.prt(j+":"+picks.get(j));
        //}
        String nscl = "MBCHMT EHZ";
        Util.prt(nscl + " " + evtpicks.getPick(nscl));
        nscl = "MBELMT EHZ";
        Util.prt(nscl + " " + evtpicks.getPick(nscl));
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (JAXBException e) {
      Util.prta("Got a JAXB error e=" + e);
    }
    evt.setDebug(true);
    evt.clearURL(null);
    evt.setEvent("us20008vhl");
    try {
      long len = evt.executeURL();
      //Util.prt(evt.getSB(null));
      try {
        int nevents = evt.parseQuakeML();
        for (int i = 0; i < nevents; i++) {
          Event event = evt.getEvent(i);
          Util.prt(evt.getEventID() + " " + Util.ascdatetime2(evt.getPreferredOriginTime()) + " "
                  + evt.getLatitude() + " " + evt.getLongitude() + " " + evt.getDepth() + " " + evt.getPreferredMagnitude() + " "
                  + evt.getPreferredMagMethod());
          ArrayList<SimplePick> picks = evt.getPickList();
          for (int j = 0; j < picks.size(); j++) {
            Util.prt(j + ":" + picks.get(j));
          }
          String nscl = "IUPET  BHZ00";
          Util.prt(nscl + " " + evt.getPick(nscl));
          nscl = "AKSPIA BHZ  ";
          Util.prt(nscl + " " + evt.getPick(nscl));
        }

        // Try a different on
        evt.clearURL(null);
        evt.setEvent("ci37828544");
        len = evt.executeURL();
        nevents = evt.parseQuakeML();
        for (int i = 0; i < nevents; i++) {
          Event event = evt.getEvent(i);
          Util.prt(evt.getEventID() + " " + Util.ascdatetime2(evt.getPreferredOriginTime()) + " "
                  + evt.getLatitude() + " " + evt.getLongitude() + " " + evt.getDepth() + " " + evt.getPreferredMagnitude() + " "
                  + evt.getPreferredMagMethod());
          ArrayList<SimplePick> picks = evt.getPickList();
          for (int j = 0; j < picks.size(); j++) {
            Util.prt(j + ":" + picks.get(j));
          }
          String nscl = "NP5229 HNZFS";
          Util.prt(nscl + " " + evt.getPick(nscl));
          nscl = "CIHLN  EHZ  ";
          Util.prt(nscl + " " + evt.getPick(nscl));
        }

      } catch (JAXBException e) {
        Util.prta("Got a JAXB error e=" + e);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
