/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package subspacedetector;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;

import gov.usgs.detectionformats.Correlation;
import gov.usgs.detectionformats.Utility;

import gov.usgs.hazdevbroker.Producer;

import org.apache.log4j.BasicConfigurator;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.GregorianCalendar;

/**
 * represents a CorrelationJSON message
 *
 * @author U.S. Geological Survey <jpatton at usgs.gov>
 */
public final class JSONCorrelationGenerator {

  private final String path;
  private final Producer hazdevProducer;
  private final String topic;
  private boolean dbg;
  private final EdgeThread par;
  public static final String BROKER_CONFIG = "HazdevBrokerConfig";
  public static final String TOPIC = "Topic";

  @Override
  public String toString() {
    return "JCG";
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * This constructor sets up a path for JSON Correlation messages to be written out to. path.
   *
   * @param outputPath Directory path to write the output JSON files (null means no files written!).
   * @param brokerConfig Path to broker config file (null means no broker!).
   * @param parent Parent for logging
   */
  public JSONCorrelationGenerator(String outputPath, String brokerConfig, EdgeThread parent) {

    par = parent;

    if (outputPath.equals("")) {
      this.path = null;
    } else {
      prta("Setting up JSON file output.\n");
      if (!outputPath.endsWith(Util.fs)) {
        this.path = outputPath + Util.fs;
      } else {
        this.path = outputPath;
      }
    }

    if (brokerConfig.equals("")) {
      this.hazdevProducer = null;
      this.topic = null;
    } else {
      prta("Setting up JSON broker output.\n");
      // init log4j
      BasicConfigurator.configure();

      // read the config file
      File configFile = new File(brokerConfig);
      BufferedReader configReader = null;
      StringBuilder configBuffer = new StringBuilder();

      try {
        configReader = new BufferedReader(new FileReader(configFile));
        String text;

        while ((text = configReader.readLine()) != null) {
          configBuffer.append(text).append("\n");
        }
      } catch (FileNotFoundException e) {
        prta(e.toString());
      } catch (IOException e) {
        prta(e.toString());
      } finally {
        try {
          if (configReader != null) {
            configReader.close();
          }
        } catch (IOException e) {
        }
      }

      // parse config file into json
      JSONObject configJSON = null;
      try {
        JSONParser configParser = new JSONParser();
        configJSON = (JSONObject) configParser.parse(configBuffer.toString());
      } catch (ParseException e) {
        prta(e.toString());
      }

      // nullcheck
      if (configJSON == null) {
        prta("Error, invalid json from configuration.");
        this.hazdevProducer = null;
        this.topic = null;
        return;
      }

      // get broker config
      JSONObject jsonConfig;
      if (configJSON.containsKey(BROKER_CONFIG)) {
        jsonConfig = (JSONObject) configJSON.get(BROKER_CONFIG);
      } else {
        prta("Error, did not find HazdevBrokerConfig in configuration.");
        this.hazdevProducer = null;
        this.topic = null;
        return;
      }

      // get topic
      if (configJSON.containsKey(TOPIC)) {
        topic = (String) configJSON.get(TOPIC);
      } else {
        prta("Error, did not find Topic in configuration.");
        this.hazdevProducer = null;
        this.topic = null;
        return;
      }

      // create producer
      this.hazdevProducer = new Producer(jsonConfig);
    }
  }

  /**
   * Write a pick to disk via this object. The return indicates whether the pick was written to
   * disk.
   *
   * @param agency Character string with agency id
   * @param author Character string with author name
   * @param pickTime The time of the correlation pick time
   * @param seedname NNSSSSSCCCLL for the channel being correlated
   * @param sequence Some sequence number for this this pick. This must be unique for each
   * correlation.
   * @param latitude in degrees
   * @param longitude in degrees
   * @param depth in km
   * @param phase A phase code like "P" or "S" limit 8 characters (null omit)
   * @param originTime The time of the correlation pick time
   * @param eventtype earthquake or blast (null omit)
   * @param corrleationvalue value representing the strength of the correlation
   * @param magnitude the relative magnitude of the correlation (<=0. omit);
   * @para
   * m snr Signal to noise ratio (<=0. omit);
   * @para
   * m zscore Z-Score of the detection (<=0. omit);
   * @para
   * m detectionThreshold The detection threshold (<=0. omit);
   * @para
   * m thresholdType How threshold was set (null omit);
   * @param laterr Latitude error
   * @param lonerr Longitude error
   * @param timeerr Time error
   * @param deptherr Depth error
   * @return True if the correlation was written, otherwise the correlation was not written and user
   * might want to resubmit later!
   */
  public synchronized boolean writeCorrelation(String agency, String author, GregorianCalendar pickTime, String seedname,
          int sequence, double latitude, double longitude, double depth, GregorianCalendar originTime,
          String phase, String eventtype, double corrleationvalue, double magnitude, double snr,
          double zscore, double detectionThreshold, String thresholdType, double laterr, double lonerr, double timeerr,
          double deptherr
  ) {
    // convert from double to Double so that we can
    // provide null for optional values
    Double dCorrleationvalue = corrleationvalue;
    Double dLatitude = latitude;
    Double dLongitude = longitude;
    Double dDepth = depth;
    Double dMagnitude = null;
    Double dLatitudeError = laterr;
    Double dLongitudeError = lonerr;
    Double dTimeError = timeerr;
    Double dDepthError = deptherr;
    if (magnitude > 0.) {
      dMagnitude = magnitude;
    }
    Double dSNR = null;
    if (snr > 0.) {
      dSNR = snr;
    }
    Double dZScore = null;
    if (zscore > 0.) {
      dZScore = zscore;
    }
    Double dDetectionThreshold = null;
    if (detectionThreshold > 0.) {
      dDetectionThreshold = detectionThreshold;
    }

    // build correlation object
    Correlation correlationObject = new Correlation(String.valueOf(sequence), seedname.substring(2, 7).trim(),
            seedname.substring(7, 10).trim(), seedname.substring(0, 2).trim(), (seedname.substring(10) + "--").substring(0, 2).replaceAll(" ", "-").trim(),
            agency, author, phase, pickTime.getTime(),
            dCorrleationvalue, dLatitude, dLongitude,
            originTime.getTime(), dDepth, dLatitudeError,
            dLongitudeError, dTimeError, dDepthError,
            eventtype, dMagnitude, dSNR,
            dZScore, dDetectionThreshold,
            thresholdType);

    // check if valid, then get string string
    String jsonString;
    if (correlationObject.isValid()) {
      jsonString = Utility.toJSONString(correlationObject.toJSON());
    } else {
      // uh oh
      prta(correlationObject.getErrors().toString());
      return (false);
    }

    prta("Write: " + jsonString);

    // only write out to disk if path configured
    if (path != null) {

      prta("Writing JSON file\n.");
      // build the correlation file name
      String FileName = path + author + String.valueOf(sequence) + "." + Utility.CORRELATIONEXTENSION;
      PrintStream outfile;

      try {
        // write the correlation out to file
        outfile = new PrintStream(FileName);
        outfile.print(jsonString);
        outfile.flush();
        outfile.close();
      } catch (FileNotFoundException ex) {
        // uh oh...
        prta(ex.toString());
        return false;
      }
    }

    // only send to broker if configured
    if ((hazdevProducer != null) && (topic != null)) {
      prta("Sending to broker.\n");
      // send message
      hazdevProducer.sendString(topic, jsonString);
    }

    return true;
  }

}
