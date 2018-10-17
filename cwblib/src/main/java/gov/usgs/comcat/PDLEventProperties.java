/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.comcat;

import gov.usgs.anss.util.Util;
import java.util.Map;
import java.util.Set;

/**
 * This class encapsulates the information in the Header of a PDL event - this information is always
 * returned on a PDL event notification though picks are not part of this. I think these are the
 * fields in the indexer. This class is used in the QubeProcess in SMGetter to parse the PDL
 * messages into a Qube message for the rest of the system.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class PDLEventProperties {

  private double azimuthalGap;
  private double depth;
  private String depthType;
  private double errorEllipseAzimuth;
  private double errorEllipseIntermediate;
  private double errorEllipseMajor;
  private double errorEllipseMinor;
  private double errorEllipsePlunge;
  private double errorEllipseRotation;
  private String evaluationStatus;
  private String eventType;
  private String eventParametersPublicID;
  private String eventSource;
  private String eventSourceCode;
  private String eventTime;
  private long eventTimeLong;
  private double eventtimeError;
  private double horizontalError;
  private double latitude;
  private double longitude;
  private double latitudeError;
  private double longitudeError;
  private double magnitude;
  private double magnitudeError;
  private int magnitudeNumberOfStationsUsed;
  private String magnitudeSource;
  private String magnitudeType;
  private double minimumDistance;
  private int numberPhasesUsed;
  private String originSource;
  private String pdlClientVersion;
  private String quakemlMagnitudePublicID;
  private String quakemlOriginPublicID;
  private String quakemlPublicID;
  private String reviewStatus;
  private double standardError;
  private double verticalError;
  private int version;
  private Map<String, String> prop;

  private double getDouble(String key) {
    String value = prop.get(key);
    try {
      if (value != null) {
        return Double.parseDouble(value);
      }
    } catch (NumberFormatException expected) {
    }
    return Double.MIN_VALUE;
  }

  private int getInt(String key) {
    String value = prop.get(key);
    try {
      if (value != null) {
        return Integer.parseInt(value);
      }
    } catch (NumberFormatException expected) {
    }
    return Integer.MIN_VALUE;
  }

  public PDLEventProperties(Map<String, String> prop) {
    this.prop = prop;
    reload(prop);
  }

  public final void reload(Map<String, String> prp) {
    prop = prp;
    azimuthalGap = getDouble("azimuthal-gap");
    depth = getDouble("depth");
    depthType = prop.get("depth-type");
    errorEllipseAzimuth = getDouble("error-ellipse-azimuth");
    errorEllipseIntermediate = getDouble("error-ellipse-intermediate");
    errorEllipseMajor = getDouble("error-ellipse-major");
    errorEllipseMinor = getDouble("error-ellipse-minor");
    errorEllipsePlunge = getDouble("error-ellipse-plunge");
    errorEllipseRotation = getDouble("error-ellipse-rotation");
    evaluationStatus = prop.get("evaluation-status");
    eventType = prop.get("event-type");
    eventParametersPublicID = prop.get("eventParametersPublicID");
    eventSource = prop.get("eventsource");
    eventSourceCode = prop.get("eventsourcecode");
    eventTime = prop.get("eventtime");
    try {
      eventTimeLong = Util.stringToDate2(eventTime.replaceAll("Z", "")).getTime();
    } catch (Exception e) {
      eventTimeLong = Integer.MIN_VALUE;
    }
    eventtimeError = getDouble("eventtime-error");
    horizontalError = getDouble("horizontal-error");
    latitude = getDouble("latitude");
    longitude = getDouble("longitude");
    latitudeError = getDouble("latitude-error");
    longitudeError = getDouble("longitude-error");
    magnitude = getDouble("magnitude");
    magnitudeError = getDouble("magnitude-error");
    magnitudeNumberOfStationsUsed = getInt("magnitude-num-stations-used");
    magnitudeSource = prop.get("magnitude-source");
    magnitudeType = prop.get("magnitude-type");
    minimumDistance = getDouble("minimum-distance");
    numberPhasesUsed = getInt("num-phases-used");
    originSource = prop.get("origin-source");
    pdlClientVersion = prop.get("pdl-client-version");
    quakemlMagnitudePublicID = prop.get("quakeml-magnitude-publicid");
    quakemlOriginPublicID = prop.get("quakeml-origin-publicid");
    quakemlPublicID = prop.get("quakeml-publicid");
    reviewStatus = prop.get("review-status");
    standardError = getDouble("standard-error");
    verticalError = getDouble("vertical-error");
    version = getInt("version");
  }
  private final StringBuilder sb = new StringBuilder(10);

  public StringBuilder dumpProperties() {
    Util.clear(sb);
    Set<String> keys = prop.keySet();
    for (String key : keys) {
      sb.append(key).append("=").append(prop.get(key)).append("\n");
    }
    return sb;
  }

  public int getMagnitudeNumberOfStationsUsed() {
    return magnitudeNumberOfStationsUsed;
  }

  public int getNumberPhasesUsed() {
    return numberPhasesUsed;
  }

  public long getOriginTimeLong() {
    return eventTimeLong;
  }

  public String getOriginTimeString() {
    return eventTime;
  }

  public String getDepthType() {
    return depthType;
  }

  public String getEvaluationStatus() {
    return evaluationStatus;
  }

  public String getEventType() {
    return eventType;
  }

  public String getEventParametersPublicID() {
    return eventParametersPublicID;
  }

  public String getEventSource() {
    return eventSource;
  }

  public String getEventSourceCode() {
    return eventSourceCode;
  }

  public String getOriginSource() {
    return originSource;
  }

  public String getPDLClientVersion() {
    return pdlClientVersion;
  }

  public String getQuakemlMagnitudePublicID() {
    return quakemlMagnitudePublicID;
  }

  public String getQuakemlOriginPublicID() {
    return quakemlOriginPublicID;
  }

  public String getQuakemlPublicID() {
    return quakemlPublicID;
  }

  public String getReviewStatus() {
    return reviewStatus;
  }

  public String getMagnitudeSource() {
    return magnitudeSource;
  }

  public String getMagnitudeType() {
    return magnitudeType;
  }

  public double getAzimuthalGap() {
    return azimuthalGap;
  }

  public double getDepth() {
    return depth;
  }

  public double getErrorEllipseAzimuth() {
    return errorEllipseAzimuth;
  }

  public double getErrorEllipseIntermediate() {
    return errorEllipseIntermediate;
  }

  public double getErrorEllipseMajor() {
    return errorEllipseMajor;
  }

  public double getErrorEllipseMinor() {
    return errorEllipseMinor;
  }

  public double getErrorEllipsePlunge() {
    return errorEllipsePlunge;
  }

  public double getErrorEllipseRotation() {
    return errorEllipseRotation;
  }

  public double getEventtimeError() {
    return eventtimeError;
  }

  public double getHorizontalError() {
    return horizontalError;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getLatitudeError() {
    return latitudeError;
  }

  public double getLongitudeError() {
    return longitudeError;
  }

  public double getMagnitude() {
    return magnitude;
  }

  public double getMagnitudeError() {
    return magnitudeError;
  }

  public double getMinimumDistance() {
    return minimumDistance;
  }

  public double getStandardError() {
    return standardError;
  }

  public double getVerticalError() {
    return verticalError;
  }
}
