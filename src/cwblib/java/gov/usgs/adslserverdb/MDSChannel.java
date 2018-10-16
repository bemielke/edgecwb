package gov.usgs.adslserverdb;


import gov.usgs.adslserverdb.InstrumentType;
import gov.usgs.anss.util.PNZ;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.ArrayList;

/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/**  This encapsulates all of the MDS data in a SAC response file 
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class MDSChannel {
  static DecimalFormat df6 = new DecimalFormat("0.0000E00");
  static DecimalFormat df5 = new DecimalFormat("0.00000");
  static DecimalFormat df6a = new DecimalFormat("0.000000");
  static DecimalFormat df1 = new DecimalFormat("0.0");
  double [] coord = new double[3];
  double [] orient = new double[3];
  double depth, rate;
  double sensSeed=0.;
  double sensCalc=0.;
  double a0Seed=0.;
  double a0Calc=0.;
  double instrumentGain=0.;
  String instrumentUnit,comment,instrumentType,longName,seedflags,inputUnit;
  String owner;
  String seedname,network,station,channel,location;
  GregorianCalendar effective = new GregorianCalendar();
  GregorianCalendar enddate = new GregorianCalendar();
  PNZ pnz;
  /**
   * 
   * @return The coordinates array (lat, long, elevation) like stasrv did
   */
  public double [] getCoord() {return coord;}
  /**
   * 
   * @return the orientation array (azimuth, dip, and depth of burial) per stasrv
   */
  public double [] getOrient() {return orient;}
  /**
   * 
   * @return Get the depth of burial, note this is already reflected in elevation!
   */
  public double getDepth() {return depth;}
  /**
   * 
   * @return Digitizing rate in Hz
   */
  public double getRate() {return rate;}
  /**
   * 
   * @return Sensitivity of the channel in the pass band counts per seismometer native domain per SEED
   */
  public double getSensitivitySEED() {return sensSeed;}
  /**
   * 
   * @return Sensitivity of the channel in the pass band counts per seismometer native domain per MDS calculation from SEED elements

   */
  public double getSensitivityCalc() {return sensCalc;}
  /** 
   * 
   * @return A0 per the seed value
   */
  public double getA0SEED() {return sensSeed;}
  /**
   * 
   * @return per the calculation from the poles and zeros.
   */
  public double getA0Calc() {return sensCalc;}
  /** r
   * 
   * @return A PNZ object with the poles, zeros, a0, etc.
   */
  public PNZ getPNZ() {return pnz;}
  /**
   * 
   * @return raw gain of the instrument only in volts per native domain
   */
  public double getInstrumentGain() {return instrumentGain;}
  /**
   * 
   * @return The raw instrument type from the seed volume (not a standardized one!)
   */
  public String getInstrumentTypeSEED() {return instrumentType;}
  /**
   * 
   * @return Station long description like Dugway, somem county, USA from the SEED volume
   */
  public String getLongName() {return longName;}
  /**
   * 
   * @return Start of this epoch
   */
  public GregorianCalendar getEfffective() {return effective;}
  /**
   * 
   * @return End of this epoch
   */
  public GregorianCalendar getEndDate() {return enddate;}
  /**
   * 
   * @return The 12 character NNSSSSSCCCLL with spaces in right most of each field if needed.
   */
  public String getSeedname() {return seedname;}
  /**
   * 
   * @return Two character network code, space padded.
   */
  public String getNetworkCode() {return network;}
  /**
   * 
   * @return 5 character station code space padded.
   */
  public String getStationCode() {return station;}
  /**
   * 
   * @return 3 character channel or component code space padded.
   */
  public String getChannelCode() {return channel;}
  /**
   * 
   * @return 2 character location code space padded 
   */
  public String getLocationCode() {return location;}
  /**
   * 
   * @return Seed flags are normally "C"ontinuous, "G"eophysical data, "T"riggered, etc. per SEED manual
   */
  public String getSEEDFlags() {return seedflags;}
  /**
   * 
   * @return in degrees
   */
  public double getLatitude() {return coord[0];}
  /**
   * 
   * @return in degrees
   */
  public double getLongitude() {return coord[1];}
  /**
   * 
   * @return in meters
   */
  public double getElevation() {return coord[2];}
  /**
   * 
   * @return in degrees clockwise from true north
   */
  public double getAzimuth() {return orient[0];}
  /**
   * 
   * @return of positive motion, up=-90, down=90 per SEED (Z is positive in downward direction)
   */
  public double getDip() {return orient[1];}
  /**
   * 
   * @return "UM" micrometers, "NM" nanometers
   */
  public String getInputUnit() {return inputUnit;}
  /**
   * 
   * @return User the IADSR standard instrument types to try to figure out unit based on network and SEED instrument type field
   */
  public String getSeismometerStandardType() {return InstrumentType.decodeSeismometerString(seedname, instrumentType);}
  
  /**
   * This is the native domain of the instrument
   * @return 'A'cceration, 'V'elocity, 'D'isplacement
   */
  public String getInstrumentUnit() {return instrumentUnit;}
  /**
   * 
   * @return Instrument comment from the SEED volume
   */
  public String getInstrumentCommentSEED() {return comment;}
  
  /** Create a MDSChannel from a single epoch section of a SAC response from the MDS -
   * This represents one epoch.  Note if you expect multiple epochs then use the static
   * function loadMDSResponse instead.
   * 
   * @param sacresponse One epoch section in SAC "cooked response" format
   */
  public MDSChannel(String sacresponse) {
    loadSACResponse(sacresponse);
  }
  /** Load this object with a single epoch of sac response.   Allow reuse of this object
   * to represent a new epoch without creating a new one.
   * 
   * @param sacresponse The single epoch of sac response.  
   */
  public final void loadSACResponse(String sacresponse) {
    try{
      BufferedReader in = new BufferedReader(new StringReader(sacresponse));
      pnz = new PNZ(sacresponse);
      String line ;
      while( (line = in.readLine()) != null ) {
        if(line.startsWith("* LAT-SEED")) 
          coord[0] = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* LONG-SEED")) 
          coord[1] = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* ELEV-SEED")) 
          coord[2] = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* AZIMUTH")) 
          orient[0] = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* DIP")) 
          orient[1] = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* DEPTH")) 
          depth = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* RATE")) 
          rate = Double.parseDouble(line.substring(15));
        else if(line.startsWith("CONSTANT")) break;
        else if(line.startsWith("* SENS-SEED")) 
          sensSeed = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* SENS-CALC")) 
          sensCalc = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* A0-SEED")) 
          a0Seed = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* A0-CALC")) 
          a0Calc = Double.parseDouble(line.substring(15));
        else if(line.startsWith("* OWNER")) 
          owner = line.substring(15).trim();
        else if(line.startsWith("* SEEDFLAGS")) 
          seedflags = line.substring(15).trim();
        else if(line.startsWith("* INPUT UNIT")) 
          inputUnit = Util.rightPad(line.substring(15).trim(),2).toString();
        else if(line.startsWith("* NETWORK")) 
          network = Util.rightPad(line.substring(15).trim(),2).toString();
        else if(line.startsWith("* STATION")) 
          station = Util.rightPad(line.substring(15).trim(),5).toString();
        else if(line.startsWith("* COMPONENT")) 
          channel = Util.rightPad(line.substring(15).trim(),3).toString();
        else if(line.startsWith("* LOCATION")) 
          location = Util.rightPad(line.substring(15).trim(),2).toString();
        else if(line.startsWith("* INSTRMNTUNIT")) 
          instrumentUnit = line.substring(15).trim();
        else if(line.startsWith("* INSTRMNTGAIN")) 
          instrumentGain = Double.parseDouble(line.substring(15).trim());
        else if(line.startsWith("* INSTRMNTCMNT")) 
          comment = line.substring(15).trim();
        else if(line.startsWith("* INSTRMNTTYPE")) 
          instrumentType = line.substring(15).trim();
        else if(line.startsWith("* DESCRIPTION")) 
          longName = line.substring(15).trim();
        else if(line.startsWith("* EFFECTIVE")) 
          effective.setTimeInMillis(Util.stringToDate2(line.substring(15).trim()).getTime());
        else if(line.startsWith("* ENDDATE")) 
          effective.setTimeInMillis(Util.stringToDate2(line.substring(15).trim()).getTime());
      }
    }
    catch(IOException e) {

    }
    orient[2]=depth;
    seedname = network+station+channel+location;
  }
  @Override
  public String toString() {return seedname+" "+coord[0]+" "+coord[1]+" "+coord[2]+" "+rate+" Hz "+orient[0]+" "+orient[1]+" "+orient[2]+" inst="+instrumentType;}
  /** This is expecting a MDS SAC listing complete with '* &lt;EOE&gt; and &lt;EOR&gt;, it will return a 
   * list of MDSChannels for the list
   * 
   * @param resp A string from an MDS response query (sac response format)
   * @param list An ArrayList<MDSChannels> resulting from parsing resp argument
   */
  public static void loadMDSResponse(String resp, ArrayList<MDSChannel> list) {
    try {
      synchronized(sb) {
        if(sb.length() > 0) sb.delete(0, sb.length());
        BufferedReader in = new BufferedReader(new StringReader(resp));
        String line;
        while( (line = in.readLine()) != null ) {
          if(line.indexOf("* <EOE>") == 0) {
            list.add(new MDSChannel(sb.toString()));
            sb.delete(0, sb.length());
          }
          else if(line.indexOf("* <EOR>") == 0) 
            break;
          else sb.append(line).append("\n");
        }
      }
    }
    catch(IOException e) {
      
    }
  }
    /** This is expecting a MDS SAC listing complete with * <EOE> and <EOR>, it will return a 
   * list of MDSChannels for the list
   * 
   * @param resp A string from an MDS response query (sac response format)
   * @param list An ArrayList<MDSChannels> resulting from parsing resp argument
   */
  private static char [] bytes = new char[1000];
  private static final StringBuilder sb = new StringBuilder(1000);
  public static void loadMDSResponse(StringBuilder resp, ArrayList<MDSChannel> list) {
    try {
      synchronized(sb) {
        if(sb.length() > 0) sb.delete(0, sb.length());
        if(bytes.length < resp.length()) bytes = new char[resp.length()*2];
        resp.getChars(0, resp.length(), bytes, 0);
        BufferedReader in = new BufferedReader(new CharArrayReader(bytes));
        String line;
        while( (line = in.readLine()) != null ) {
          if(line.indexOf("* <EOE>") == 0) {
            list.add(new MDSChannel(sb.toString()));
            sb.delete(0, sb.length());
          }
          else if(line.indexOf("* <EOR>") == 0) 
            break;
          else sb.append(line).append("\n");
        }
      }
    }
    catch(IOException e) {
      
    }
  }
  /** Testing routine
   * 
   * @param args 
   */
  public static void main(String [] args) {
    StaSrv srv = new StaSrv("cwbpub.cr.usgs.gov", 2052, 500);
    StringBuilder sb = new StringBuilder(1000);
    int len = srv.getSACResponse("USDUG  ...","2013,318-00:00:00", "nm", sb);
    ArrayList<MDSChannel> list = new ArrayList<MDSChannel>(10);
    MDSChannel.loadMDSResponse(sb,list);
    for(int i=0; i<list.size(); i++) Util.prt(list.get(i).toString());
    Util.prt("End of execution");
  }
}
