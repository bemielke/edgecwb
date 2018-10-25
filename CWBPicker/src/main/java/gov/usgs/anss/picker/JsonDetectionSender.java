/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import java.io.IOException;
import gov.usgs.anss.util.Util;
import gov.usgs.detectionformats.Pick;
import gov.usgs.detectionformats.Beam;
import gov.usgs.detectionformats.Site;
import gov.usgs.detectionformats.Source;
import gov.usgs.detectionformats.Filter;
import gov.usgs.detectionformats.Associated;
import gov.usgs.detectionformats.Amplitude;
import gov.usgs.detectionformats.Correlation;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.TreeMap;
import java.util.ArrayList;
import gov.usgs.hazdevbroker.*;
import java.util.GregorianCalendar;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
/** This class implements various ways a JSON message can be configuration to be Sent out.
 * Initial version just makes file on a path.
 * <pre>
 * -json method1&method2&method3
 * 
 * where each method consists of key;arg1;arg2;arg3...
 * 
 * method      Format
 * path       path;[SOME PATH TO DIRECTORY] - write JSON methods as files on this path
 * kafka      kafka;type;server:port
 *            type matches detection-formats like 'beam' or 'pick'
 *            server is like : 'igskcicgvmkafka.cr.usgs.gov/9092' Note: slash is changed to ':' in the server tags
 * </pre>
 * 
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class JsonDetectionSender {
	public static final String BROKER_CONFIG = "HazdevBrokerConfig";
  private final TreeMap<String, Producer>  kafkas = new TreeMap();
  private String path=null;
  private EdgeThread par;
  private long npicks,nbeams;
  private String pickTopic;     // default kafka topic for picks (can be overriden in method call)
  private String beamTopic;
  private String corrTopic;
  private boolean noOutput;     // If set true, messages are not actually sent to JSON/kafka
  private final String kafkaJSON = // the default JSON for setting up a Kafka service
          "{\n" +
              "	\"HazdevBrokerConfig\": {\n" +
              "		\"Type\":\"ProducerConfig\",\n" +
              "		\"Properties\":{\n" +
              "		  \"client.id\":\"$TYPE.edge\",\n" +
              "			\"group.id\":\"default.producer\",\n" +
              "			\"bootstrap.servers\":\"$URL\",\n" +
              "			\"retries\":\"0\"\n" +
              "		}\n" +
              "	},\n" +
              "\n" +
              "	\"Topic\":\"$TYPE-edge\"\n" +
        "}\n";
  java.util.Date pTime = new java.util.Date();     // scratch for making beam dates
  java.util.Date beamEndTimeDate = new java.util.Date();
  java.util.Date correlationTimeDate = new java.util.Date();     // scratch for making beam dates
  java.util.Date correlationOriginTimeDate = new java.util.Date();
  private StringBuilder sb = new StringBuilder(10);
  @Override
  public String toString() {return toStringBuilder().toString();}
  public StringBuilder toStringBuilder() {
    return Util.clear(sb).append(" #picks=").append(npicks).append(" #beams=").append(nbeams).
            append("#kafkas=").append(kafkas.size()).append(" path=").append(path);}
  public void setNooutput(boolean t) {noOutput=t;}
  
  /** Constructor;  see 
   * 
   * @param argline See class definition for syntax 
   * @param parent  Somewhere to log messages.
   */
  public JsonDetectionSender(String argline, EdgeThread parent) {
    String [] args = argline.split("\\s");
    par = parent;
    parent.prta("JsonDetectionSender : argline ="+argline);
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-json")) {
        String [] methods = args[i+1].split("&");
        for (String method : methods) {
          if (method.startsWith("path")) {
            parent.prta("JsonDetectionSender: setup PATH="+method);
            String[] parts = method.split("[;:]");
            path = parts[1];        
            Util.chkFilePath(path+Util.FS+"test.tmp");  //create the path if it does not exist
          } 
          else if(method.startsWith("kafka")) {   // Kafka uses JSON message for configuration
            String [] parts = method.split("[;:]");
            String type=parts[1];
            String server=parts[2].replaceAll("/",":");
            String jsonConfig = kafkaJSON;
            if(type.contains("pick")) pickTopic = type+"-edge";
            else if(type.contains("beam")) beamTopic = type+"-edge";
            else if(type.contains("corr")) corrTopic = type+"-edge";
            else parent.prta("***** Unknown topic type="+type+" does not contain a valid message type");
            jsonConfig = jsonConfig.replaceAll("\\$TYPE", type).replaceAll("\\$URL", server);
            parent.prta("JsonDetectionSender: setup Kafka for type="+type+" url="+server);
            parent.prt("JsonConfig: \n"+jsonConfig);
            parent.prt("JsonString="+jsonConfig);
            // parse config file into json
            JSONObject configJSON = null;
            try {
              JSONParser configParser = new JSONParser();
              configJSON = (JSONObject) configParser.parse(jsonConfig);
            } catch (ParseException e) {
              e.printStackTrace(parent.getPrintStream());
            }

            // nullcheck
            if (configJSON == null) {
              System.out.println("Error, invalid json from configuration.");
              System.exit(1);
            }

            // get broker config
            JSONObject brokerConfig = null;
            if (configJSON.containsKey(BROKER_CONFIG)) {
              brokerConfig = (JSONObject) configJSON.get(BROKER_CONFIG);
            } else {
              parent.prta(
                  "JsonDetectionSender: **** Error, did not find HazdevBrokerConfig in configuration. type="+type+" url="+server);
            }

            // get topic - this seems sensless
            String topic = "$TYPE-edge".replaceAll("\\$TYPE", type);
            if (configJSON.containsKey("Topic")) {
              topic = (String) configJSON.get("Topic");
            } else {
              parent.prta("JsonDetectionSender: **** Error, did not find Topic in configuration.type="+type+" url="+server);
            }

            // create producer
            Producer m_Producer = new Producer(brokerConfig);
            kafkas.put(type.substring(0,4), m_Producer);

          }
          else {
            par.prta("JsonDetectionSender: **** Unknown method to JsonPickSender " + method);
          }
        }
        i++;
      }
    }
  }

    /**
   * 
   * @param p A pick to send
   * @return  true if the pick was sent!
   */
  public synchronized boolean sendPick(Pick p) {
    return sendPick(p, null);
  }

  /**
   * 
   * @param p A pick to send
   * @param overrideTopic The kafka topic to use instead of the default one (null use default one)
   * @return  true if the pick was sent!
   */
  public synchronized boolean sendPick(Pick p, String overrideTopic) {
    boolean ret = false;
     // Try to send to kafka
    Producer m_producer = kafkas.get("pick");
    if(m_producer != null) {
      par.prta("JsonDetectionSender: sendPick kafka "+p.getID()+" "+
              p.getSite().getNetwork()+"."+p.getSite().getStation()+"."+
              p.getSite().getChannel()+"."+p.getSite().getLocation()+" "+
              Util.ascdatetime2(p.getTime().getTime())+" "+p.getPhase()+" "+p.getOnset()+" "+p.getPolarity()+
              (p.getBeam() != null?" Has Beam":"")+(p.getAssociationInfo() != null?" Has Assoc":"")+" "
              +(overrideTopic != null?overrideTopic:pickTopic));
      if(!noOutput) {
        m_producer.sendString( (overrideTopic != null ? overrideTopic: pickTopic),p.toJSON().toJSONString());
      }
      ret=true;
    }

    // Do somthing to save the pick
    if(path != null) {
      try {
        par.prta("JsonDetectionSender: sendPick path "+p.getID()+" "+
                p.getSite().getNetwork()+"."+p.getSite().getStation()+"."+
                p.getSite().getChannel()+"."+p.getSite().getLocation()+" "+
                Util.ascdatetime2(p.getTime().getTime())+" "+p.getPhase()+" "+p.getOnset()+" "+p.getPolarity());
        Util.writeFileFromSB(path+Util.FS+p.getPicker()+"_"+p.getID()+".json", p.toJSON().toJSONString());
        ret=true;
      }
      catch(IOException e) {
        e.printStackTrace(par.getPrintStream());
      }
    }
    return ret;
  }
  /** Write a pick to disk via this object.  The return indicates whether the pick was written to disk.  This
   * call omits all beam and associated parameters.
   * 
   * @param pickID If this is updating a pickID, provide it.
   * @param overrideTopic If not null, this overrides the kafka topic
   * @param agency Character string with agency id
   * @param author Character string with author name 
   * @param pickTime The time of the pick 
   * @param seedname NNSSSSSCCCLL for the channel being picked 
   * @param amplitude in um (<=0. omit) must have this to create an amplitude with period
   * @param period in seconds (<=0. omit)
   * @param phase A phase code like "P" or "S" limit 8 characters (null omit)
   * @param error_window This is the error window half width  (<0 omit)
   * @param polarity up or down (null omit)
   * @param onset impulsive, emergent, questionable (null omit)
   * @param pickerType  manual, raypicker, lomax, earthworm, other.  (null omit) We can add others if we need them 
   * @param hipassFreq Frequency in Hz of high pass filter on picker (<=0. omit) to have a Filter entry one frequency must not be omited
   * @param lowpassFreq Frequency in Hz of low pass filter on picker (<=0. omit)
   * @param snr The estimated signal to noise ratio (<=0. omit);
   * @return True if the pick was written, otherwise the pick was not written and user might want to resubmit later!
   */
  public synchronized boolean sendPick(String pickID, String overrideTopic, String agency, String author, GregorianCalendar pickTime, String seedname, 
          double amplitude, double period,
          String phase,  double error_window, String polarity, 
          String onset, String pickerType, double hipassFreq, double lowpassFreq, double snr

         ) {
    return sendPick(pickID, overrideTopic, agency, author, pickTime, seedname, amplitude, 
            period, phase, error_window, polarity,onset,
            pickerType, hipassFreq,lowpassFreq, snr,
            -1,0.,0.,0.,0.,0.,      // The beam parameters are omitted
            0.,0.,0.,0.);     // The associated stuff is omited
  }
  /** Write a pick to disk via this object.  The return indicates whether the pick was written to disk. This
   * call includes beam, but does not include associated parameters.
   * 
   * @param pickID If this is updating a pickID, provide it.
   * @param overrideTopic If not null, this overrides the kafka topic
   * @param agency Character string with agency id
   * @param author Character string with author name 
   * @param pickTime The time of the pick 
   * @param seedname NNSSSSSCCCLL for the channel being picked 
   * @param amplitude in um (<=0. omit) must have this to create an amplitude with period
   * @param period in seconds (<=0. omit)
   * @param phase A phase code like "P" or "S" limit 8 characters (null omit)
   * @param error_window This is the error window half width  (<0 omit)
   * @param polarity up or down (null omit)
   * @param onset impulsive, emergent, questionable (null omit)
   * @param pickerType  manual, raypicker, lomax, earthworm, other.  (null omit) We can add others if we need them 
   * @param hipassFreq Frequency in Hz of high pass filter on picker (<=0. omit) to have a Filter entry one frequency must not be omited
   * @param lowpassFreq Frequency in Hz of low pass filter on picker (<=0. omit)
   * @param snr The estimated signal to noise ratio (<=0. omit);
   * @param backazm  Beam back azimuth in degress (<=0. omit) To have a beam you must have a backazm.
   * @param backazmErr Beam back azimuth error estimate in degrees(<=0. omit)
   * @param slowness Beam Slowness value(<=0. omit)
   * @param slownessErr Beam slowness error(<=0. omit)
   * @param powerRatio Beam power ratio(<=0. omit)
   * @param powerRatioErr Beam power error estimate (<=0. omit)
   * @return True if the pick was written, otherwise the pick was not written and user might want to resubmit later!
   */
  public synchronized boolean sendPick(String pickID, String overrideTopic, String agency, String author, GregorianCalendar pickTime, String seedname, 
          double amplitude, double period,
          String phase,  double error_window, String polarity, 
          String onset, String pickerType, double hipassFreq, double lowpassFreq, double snr,
          double backazm, double backazmErr, double slowness, double slownessErr, double powerRatio, double powerRatioErr
         ) {
    return sendPick(pickID, overrideTopic, agency, author, pickTime, seedname, amplitude, period, phase, error_window, polarity,onset,
            pickerType, hipassFreq,lowpassFreq, snr,
            backazm, backazmErr, slowness, slownessErr, powerRatio, powerRatioErr,
            0., 0., 0., 0.);     // The associated stuff is omited
  }
     /** Write a pick to disk via this object.  The return indicates whether the pick was written to disk.
   * 
   * @param pickID If this is updating a pickID, provide it.
   * @param overrideTopic If not null, this overrides the kafka topic
   * @param agency Character string with agency id
   * @param author Character string with author name 
   * @param pickTime The time of the pick 
   * @param seedname NNSSSSSCCCLL for the channel being picked 
   * @param amplitude in um (<=0. omit) must have this to create an amplitude with period
   * @param period in seconds (<=0. omit)
   * @param phase A phase code like "P" or "S" limit 8 characters (null omit)
   * @param error_window This is the error window half width  (<0 omit)
   * @param polarity up or down (null omit)
   * @param onset impulsive, emergent, questionable (null omit)
   * @param pickerType  manual, raypicker, lomax, earthworm, other.  (null omit) We can add others if we need them 
   * @param hipassFreq Frequency in Hz of high pass filter on picker (<=0. omit) to have a Filter entry one frequency must not be omited
   * @param lowpassFreq Frequency in Hz of low pass filter on picker (<=0. omit)
   * @param snr The estimated signal to noise ratio (<=0. omit);
   * @param backazm  Beam back azimuth in degress (<=0. omit) To have a beam you must have a backazm.
   * @param backazmErr Beam back azimuth error estimate in degrees(<=0. omit)
   * @param slowness Beam Slowness value(<=0. omit)
   * @param slownessErr Beam slowness error(<=0. omit)
   * @param powerRatio Beam power ratio(<=0. omit)
   * @param powerRatioErr Beam power error estimate (<=0. omit)
   * @param distance Associated distance to epicenter (<=0. omit) to have an association, you must have a distance
   * @param azimuth Associated azimuth to epicenter in degrees(<=0. omit)
   * @param residual Associated residual in seconds(<=0. omit)
   * @param sigma Associated sigma?(<=0. omit)
   * @return True if the pick was written, otherwise the pick was not written and user might want to resubmit later!
   */
  public synchronized boolean sendPick(String pickID, String overrideTopic, String agency, String author, GregorianCalendar pickTime, String seedname, 
          double amplitude, double period,
          String phase,  double error_window, String polarity, 
          String onset, String pickerType, double hipassFreq, double lowpassFreq, double snr,
          double backazm, double backazmErr, double slowness, double slownessErr,
            double powerRatio, double powerRatioErr, double distance, double azimuth,double residual, double sigma
         ) { 


    // build pick object, assume the beam, filtering, amplitude are not present.
    Beam beam = null;
    ArrayList<Filter> filter=null;
    Associated assoc=null;
    
    // Create the amplitude object
    Amplitude amp = null;
    if(amplitude > 0. || snr > 0. || period > 0.) 
      amp = new Amplitude(amplitude > 0.?Util.roundToSig(amplitude,4):null, 
            period > 0.?Util.roundToSig(period,4):null,
            snr > 0.?Util.roundToSig(snr,4):null);
    
    // If there is a back azimuth create a beam object
    if(backazm >= 0.) 
      beam = new Beam(Util.roundToSig(backazm,4), 
            backazmErr > 0.?Util.roundToSig(backazmErr,4):null, 
            slowness >= 0.?Util.roundToSig(slowness,4):null, 
            slownessErr > 0.?Util.roundToSig(slownessErr,4):null, 
            powerRatio>0.?Util.roundToSig(powerRatio,4):null, 
            powerRatioErr>0.?Util.roundToSig(powerRatioErr,4):null
          );
    // If either low or high pass is present, create the filter list.
    if(lowpassFreq>0. || hipassFreq > 0.) {
      Filter f = new Filter(
            hipassFreq>0.?Util.roundToSig(hipassFreq,4):null,
            lowpassFreq>0.?Util.roundToSig(lowpassFreq,4):null);
      filter = new ArrayList<>(1);
      filter.add(f);
    }
    
    // If there is a distance create an associated object
    if(distance > 0.) 
      assoc = new Associated(phase, 
            Util.roundToSig(distance,8),
            azimuth > 0.?Util.roundToSig(azimuth,4):null,
            residual > 0.?Util.roundToSig(residual,4):null,
            sigma > 0. ? Util.roundToSig(sigma,4):null);
    
    // The location code is null if empty, so create it, and then use the seedname to create a Site object.
    String location = seedname.substring(10).trim();// (seedname.substring(10)+"--").substring(0,2).replaceAll(" ","-").trim();
    Site site = new Site(seedname.substring(2,7).trim(),
        seedname.substring(7,10).trim(),seedname.substring(0,2).trim(),
        location.equals("")?null:location);
    
    // The arrival time has to be a java.util.Date - populate it
    pTime.setTime(pickTime.getTimeInMillis());
    //par.prta(seedname+" "+Util.ascdatetime2(pickTime)+" beam="+(beam != null?beam.getPowerRatio()+" "+Util.roundToSig(powerRatio,4):"null")+" amp="+(amp != null?amp.getAmplitude()+" "+Util.roundToSig(amplitude,4):"null")+
    //        " filter="+(filter!=null?filter.get(0).getHighPass()+"/"+Util.roundToSig(hipassFreq,4)+" "+filter.get(0).getLowPass()+"/"+Util.roundToSig(lowpassFreq, 4):"null"));
    // the source is just the agency and author
    Source source = new Source(agency,author);
    Pick pickObject = new Pick(pickID, site, pTime,
            source, phase, polarity, onset, pickerType, 
            filter, amp, beam, assoc);

    // check if valid, then get string string
    String jsonString = null;
    if (pickObject.isValid()) 
      sendPick(pickObject, overrideTopic);
    else {
      // uh oh
      par.prta ("Bad pickObject ** pid="+pickID+" "+seedname+" "+Util.ascdatetime2(pickTime)+"/"+error_window+" a/p="+amplitude+"/"+period+
          " f-h/l"+hipassFreq+"/"+lowpassFreq+" bazm="+backazm+"/"+backazmErr+" slw="+slowness+"/"+slownessErr+" beam pwr="+powerRatio+"/"+powerRatioErr+
          " a-d/az/res/sig="+distance+"/"+azimuth+"/"+residual+"/"+sigma); 
      for(String s: pickObject.getErrors()) 
        par.prt("PickErr: "+s);
      if(pickObject.getBeam() != null) 
        for(String s: pickObject.getBeam().getErrors()) 
          par.prt("BeamErr: "+s);
      if(pickObject.getAmplitude() != null) 
        for(String s:pickObject.getAmplitude().getErrors()) 
          par.prt("AmpErr : "+s);
      if(pickObject.getAssociationInfo() != null) 
        for(String s :pickObject.getAssociationInfo().getErrors()) 
          par.prt("AssocErr: "+s);
      if(pickObject.getSource() != null) 
        for(String s : pickObject.getSource().getErrors()) 
          par.prt("SourceErr: "+s);
      if(pickObject.getFilterList()!= null) 
        for(String s : pickObject.getAssociationInfo().getErrors()) 
          par.prt("FilterErr: "+s);
      new RuntimeException("Bad JSON").printStackTrace(par.getPrintStream());
      return (false); 
    }
    

    return true;     
  }  

  /*
   * 
   * @param beam A beam message to send, will be sent to kafka using 'beam-edge' topic
   * @return 
   */
/*  public synchronized boolean sendBeam(Beam beam) {
    boolean ret=false;
     // Try to send to kafka
    Producer m_producer = kafkas.get("beam");
    if(m_producer != null) {
      par.prta("JsonDetectionSender: sendBeam kafka "+beam.getID()+" "+
              beam.getSite().getNetwork()+"."+beam.getSite().getStation()+"."+
              beam.getSite().getChannel()+"."+beam.getSite().getLocation()+" "+
              Util.ascdatetime2(beam.getStartTime().getTime())+"-"+Util.ascdatetime2(beam.getEndTime().getTime())+
              " "+beam.getBackAzimuth()+" "+beam.getSlowness()+" "+beamTopic);
      m_producer.sendString(beamTopic,beam.toJSON().toJSONString());
      ret = true;
    }    

    // If there is a path, put it out.
    if(path != null) {
      try {
      par.prta("JsonDetectionSender: sendBeam path "+beam.getID()+" "+
              beam.getSite().getNetwork()+"."+beam.getSite().getStation()+"."+
              beam.getSite().getChannel()+"."+beam.getSite().getLocation()+" "+
              Util.ascdatetime2(beam.getStartTime().getTime())+"-"+Util.ascdatetime2(beam.getEndTime().getTime())+
              " "+beam.getBackAzimuth()+" "+beam.getSlowness());        
      Util.writeFileFromSB(path+Util.FS+"BEAM_"+beam.getID()+".json", beam.toJSON().toJSONString());
        ret=true;
      }
      catch(IOException e) {
        e.printStackTrace(par.getPrintStream());
      }
    }
    return ret;
  }*/
  
  /* Write a pick to disk via this object.  The return indicates whether the pick was written to disk.
   * 
   * @param beamID The beam ID for JSON
   * @param agency Character string with agency id
   * @param author Character string with author name 
   * @param beamTime The time of the beam 
   * @param beamEndTime The time the beam has ended
   * @param seedname NNSSSSSCCCLL for the channel the beam was generated from
   * @param version A version of the beam, normally 0
   * @param backazimuth This is the back azimuth of the beam
   * @param backazimutherror This is the error of the back azimuth of the beam (<0 omit)
   * @param slowness slowness of the beam 
   * @param slownesserror This is the slowness error of the beam (<=0. omit)
   * @param topicExt extension to the 'beam-edge' topic to be used to make kafka topic
   * @return True if the pick was beam was written , otherwise the beam was not written and user might want to resubmit later!
   */
  /*public synchronized boolean sendBeam(String beamID, String agency, String author, long beamTime, long beamEndTime, String seedname, int version,
          double backazimuth, double backazimutherror, double slowness, double slownesserror
          ) 
  { 
       // convert from double to Double so that we can
      // provide null for optional values
      Double dBackAzimuth = backazimuth;
      Double dSlowness = slowness;
      Double dBackAzimuthError = null;
      if (backazimutherror > 0.) dBackAzimuthError = backazimutherror;    
      Double dSlownessError = null;
      if (slownesserror > 0.) dSlownessError = slownesserror;
            
      beamTimeDate.setTime(beamTime);
      beamEndTimeDate.setTime(beamEndTime);
      // build beam object
      Beam beamObject = new Beam(beamID, seedname, seedname.substring(2,7).trim(), 
              seedname.substring(7,10).trim(), seedname.substring(0,2).trim(),
              (seedname.substring(10)+"--").substring(0,2).replaceAll(" ","-").trim(), 
              agency, author, 
              beamTimeDate, beamEndTimeDate, 
              dBackAzimuth, dBackAzimuthError, 
              dSlowness, dSlownessError);     
      
      // check if valid, then get string string
      if (beamObject.isValid()) 
         sendBeam(beamObject);
      else {
          // uh oh
          par.prta (beamObject.getErrors().toString());
          return (false); 
      }
      return true;   
  }  */
  public synchronized boolean sendCorrelation(Correlation correlation, String overrideTopic) {
    boolean ret=false;
     // Try to send to kafka
    Producer m_producer = kafkas.get("corr");
    if(m_producer != null) {
      par.prta("JsonDetectionSender: sendCorrelation kafka "+correlation.getID()+" "+
              correlation.getSite().getNetwork()+"."+correlation.getSite().getStation()+"."+
              correlation.getSite().getChannel()+"."+correlation.getSite().getLocation()+" "+
              Util.ascdatetime2(correlation.getTime().getTime())+"-"+
              Util.ascdatetime2(correlation.getHypocenter().getTime().getTime())+" "+
              correlation.getMagnitude()+" "+
              correlation.getCorrelation()+" "+correlation.getSNR()+" "+corrTopic);
      if(!noOutput) {
        m_producer.sendString((overrideTopic != null? overrideTopic:corrTopic),correlation.toJSON().toJSONString());
      }
      ret = true;
    }    

    // If there is a path, put it out.
    if(path != null) {
      try {
      par.prta("JsonDetectionSender: sendCorrelation path "+correlation.getID()+" "+
              correlation.getSite().getNetwork()+"."+correlation.getSite().getStation()+"."+
              correlation.getSite().getChannel()+"."+correlation.getSite().getLocation()+" "+
              Util.ascdatetime2(correlation.getTime().getTime())+"-"+
              Util.ascdatetime2(correlation.getHypocenter().getTime().getTime())+
              correlation.getMagnitude()+" "+
              correlation.getCorrelation()+" "+correlation.getSNR());
        
      Util.writeFileFromSB(path+Util.FS+"CORR_"+correlation.getID()+".json", correlation.toJSON().toJSONString());
        ret=true;
      }
      catch(IOException e) {
        e.printStackTrace(par.getPrintStream());
      }
    }
    return ret;
  }  
  public synchronized boolean sendCorrelation(String correlationID, String overrideTopic, 
          String agency,String author,
          long time, double timeError, String seedname, int version, String phase, double correlation, double latitude,
          double longitude, long originTime, double depth, double latitudeError, double longitudeError,
          double depthError, String eventType, 
          double magnitude, double snrin, double zScorein, double thresholdin, String thresholdType) {
    correlationTimeDate.setTime(time);
    correlationOriginTimeDate.setTime(originTime);
    Double mag = null;
    if(magnitude > 0.) mag = magnitude;
    Double tError=null;
    if(timeError > 0.) tError = timeError;
    Double latError=null;
    if(latitudeError > 0.) latError = latitudeError;
    Double longError=null;
    if(longitudeError > 0.) longError = longitudeError;
    Double depError=null;
    if(depthError > 0.) depError = depthError;
    Double snr = null;
    if(snrin > 0.) snr = snrin;
    Double threshold = null;
    if(thresholdin > 0.) threshold = thresholdin;
    Double z = null;
    if(zScorein > 0.) z = zScorein;
    if(eventType.equalsIgnoreCase("QurarryBlast")) {// TODO - remove this when JSON supports all types
      eventType = "blast";
    }
    else {
      eventType = "earthquake";
    }
    
    Correlation correlationObject = new Correlation(correlationID, 
            seedname.substring(2,7).trim(), 
            seedname.substring(7,10).trim(), seedname.substring(0,2).trim(),
            (seedname.substring(10)+"--").substring(0,2).replaceAll(" ","-").trim(), 
            agency, author, phase, correlationTimeDate, correlation, latitude, longitude, correlationOriginTimeDate,
            depth, latError, longError, tError, depError, eventType, magnitude, snr, z, threshold, thresholdType);
    // check if valid, then get string string
    if (correlationObject.isValid()) 
       sendCorrelation(correlationObject, overrideTopic);
    else {
        // uh oh
        par.prta (correlationObject.getErrors().toString());
        return (false); 
    }
    return true;   
  }
}
