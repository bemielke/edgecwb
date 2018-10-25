/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.metadatagui;
//package gov.usgs.edge.template;
//import java.util.Comparator;

import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Response.java If the name of the class does not match the name of the field
 * in the class you may need to modify the rs.getSTring in the constructor.
 *
 * This Response templace for creating a MySQL database object. It is not really
 * needed by the ResponsePanel which uses the DBObject system for changing a
 * record in the database file. However, you have to have this shell to create
 * objects containing a MySQL record for passing around and manipulating. There
 * are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of
 * data.
 * <br>
 * The Response should be replaced with capitalized version (class name) for the
 * file. response should be replaced with the lower case version (for
 * variables).
 *
 * Both the constructors use the "makeResponse(list of data args) to set the
 * local variables to the value. The result set just uses the
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding
 * to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only
 * once(static)
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data
 * base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public class Response //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeResponse()

    /**
     * Creates a new instance of Response
     */
    int ID;                   // This is the MySQL ID (should alwas be named "ID"
    String channel;             // This is the main key which should have same name
    // as the table which it is a key to.  That is
    // Table "category" should have key "category"

    // USER: All fields of file go here
    //  double longitude
    double dip;
    double azimuth;
    double depth;
    int flags;
    String cookedresponse;
    String units;
    double a0;
    double a0calc;
    double a0freq;
    double sensitivity;
    double sensitivityCalc;
    double latitude;
    double longitude;
    double elevation;
    Timestamp effective;
    Timestamp endingDate;
    String instrumentType;
    double rate;

    //difference Tolerances
    public static final double DIP_TOLERANCE = 1.0d; //degrees
    public static final double AZIMUTH_TOLERANCE = 1.0d; //degrees
    public static final double DEPTH_TOLERANCE = 1.0d; // meters
    public static final double RATE_TOLERANCE = .001d; //ratio
    public static final double A0_TOLERANCE = .0001d; //ratio
    public static final double A0CALC_TOLERANCE = .0001d; //ratio
    public static final double A0FREQ_TOLERANCE = .0001d; //ratio
    public static final double SENSITIVITY_TOLERANCE = .001d; //ratio 
    public static final double SENSITIVITY_CALC_TOLERANCE = .001d; //ratio
    public static final double LATITUDE_TOLERANCE = .0004d; //degrees
    public static final double LONGITUDE_TOLERANCE = .0004d; //degrees
    public static final double ELEVATION_TOLERANCE = 1.0d; //meters
    public static final int EFFECITVE_TOLERANCE = 60000; //60 seconds;
    public static final int ENDING_TOLERANCE = 60000; //60 seconds;

    // Put in correct detail constructor here.  Use makeResponse() by filling in all
    // the rs.get???("fieldName") for all of the arguments
    /**
     * Create a instance of this table row from a positioned result set
     *
     * @param rs The ResultSet already SELECTED
     * @exception SQLException SQLException If the ResultSet is Improper,
     * network is down, or database is down
     */
    public Response(ResultSet rs) throws SQLException {  //USER: add all data field here
        makeResponse(rs.getInt("ID"), rs.getString("channel"),
                // ,rs.getDouble(longitude)
                rs.getDouble("dip"),
                rs.getDouble("azimuth"),
                rs.getDouble("depth"),
                rs.getInt("flags"),
                rs.getString("cookedresponse"),
                rs.getString("units"),
                rs.getDouble("a0"),
                rs.getDouble("a0calc"),
                rs.getDouble("a0freq"),
                rs.getDouble("sensitivity"),
                rs.getDouble("sensitivityCalc"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getDouble("elevation"),
                rs.getTimestamp("effective"),
                rs.getTimestamp("endingDate"),
                rs.getString("instrumenttype"), rs.getDouble("rate")
        );
    }

    // Detail Constructor, match set up with makeResponse(), this argument list should be
    // the same as for the result set builder as both use makeResponse()
    /**
     * create a row record from individual variables
     *
     * @param inID The ID in the database, normally zero for this type of
     * constructin
     * @param loc The key value for the database (same a name of database table)
     */
    public Response(int inID, String loc, //USER: add fields, double lon
            double dip,
            double azimuth,
            double depth,
            int flags,
            String cookedresponse,
            String units,
            double a0,
            double a0calc,
            double a0freq,
            double sensitivity,
            double sensitivityCalc,
            double latitude,
            double longitude,
            double elevation,
            Timestamp effective,
            Timestamp endingDate,
            String instrumentType, double rate
    ) {
        makeResponse(inID, loc, //, lon
                dip,
                azimuth,
                depth,
                flags,
                cookedresponse,
                units,
                a0,
                a0calc,
                a0freq,
                sensitivity,
                sensitivityCalc,
                latitude,
                longitude,
                elevation,
                effective,
                endingDate, instrumentType, rate
        );
    }

    // This routine transfers to the local variables of this object all of the data variables
    // associated with this file.
    /**
     * internally set all of the field in our data to the passsed data
     *
     * @param inID The row ID in the database
     * @param loc The key (same as table name)
   *
     */
    private void makeResponse(int inID, String loc, //USER: add fields, double lon
            double indip,
            double inazimuth,
            double indepth,
            int inflags,
            String incookedresponse,
            String inunits,
            double ina0,
            double ina0calc,
            double ina0freq,
            double insensitivity,
            double insensitivityCalc,
            double inlatitude,
            double inlongitude,
            double inelevation,
            Timestamp ineffective,
            Timestamp inendingDate,
            String inst, double rt
    ) {
        ID = inID;
        channel = loc;     // longitude = lon
        //USER:  Put asssignments to all fields from arguments here
        dip = indip;
        azimuth = inazimuth;
        depth = indepth;
        flags = inflags;
        cookedresponse = incookedresponse;
        units = inunits;
        a0 = ina0;
        a0calc = ina0calc;
        a0freq = ina0freq;
        sensitivity = insensitivity;
        sensitivityCalc = insensitivityCalc;
        latitude = inlatitude;
        longitude = inlongitude;
        elevation = inelevation;
        effective = ineffective;
        endingDate = inendingDate;
        instrumentType = inst;
        rate = rt;
        // ENUM example:
        //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
        //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
        // Boolean example where ENUM "True", "False
        // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

    }

    /**
     * return the key name as the string
     *
     * @return the key name
     */
    public String toString2() {
        return channel;
    }

    /**
     * return the key name as the string
     *
     * @return the key name
     */
    @Override
    public String toString() {
        return effective.toString();
    }

    public String toStringFull() {
        return channel + " eff=" + effective.toString().substring(0, 16)
                + " to " + endingDate.toString().substring(0, 16) + " rt=" + rate + " " + latitude + " " + longitude + " " + elevation + " " + depth;
    }
    // getter

    // standard getters
    /**
     * get the database ID for the row
     *
     * @return The database ID for the row
     */
    public int getID() {
        return ID;
    }

    /**
     * get the key name for the row
     *
     * @return The key name string for the row
     */

    //USER:  All field getters here
    //  public double getLongitude() { return longitude;}
    public String getChannel() {
        return channel;
    }

    public String getResponse() {
        return channel;
    }

    public double getDip() {
        return dip;
    }

    public double getAzimuth() {
        return azimuth;
    }

    public double getDepth() {
        return depth;
    }

    public int getFlags() {
        return flags;
    }

    public String getCookedresponse() {
        return cookedresponse;
    }

    public String getUnits() {
        return units;
    }

    public double getA0() {
        return a0;
    }

    public double getA0calc() {
        return a0calc;
    }

    public double getA0freq() {
        return a0freq;
    }

    public double getSensitivity() {
        return sensitivity;
    }

    public double getSensitivityCalc() {
        return sensitivityCalc;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getElevation() {
        return elevation;
    }

    public Timestamp getEffective() {
        return effective;
    }

    public Timestamp getEndingDate() {
        return endingDate;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public double getRate() {
        return rate;
    }

    // This is needed if you need to implement Comparator
    /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Response) a).getResponse().compareTo(((Response) b).getResponse());
    }
    public boolean equals(Object a, Object b) {
      if( ((Response)a).getResponse().equals( ((Response) b).getResponse())) return true;
      return false;
    }
//  }*/
    public boolean diff(Response r1, StringBuilder sb, boolean strict) {
        boolean error = false;
        
        if (Math.abs(effective.getTime() - r1.getEffective().getTime()) > 60000
                || Math.abs(endingDate.getTime() - r1.getEndingDate().getTime()) > 60000) {
            sb.append("* Different epoch times Effective: ").append(toString2() + ":").
                    append(Util.toDOYString(effective)).append("-").append(Util.toDOYString(r1.getEffective())).append(" EndingDate:").
                    append(Util.toDOYString(endingDate)).append("-").append(Util.toDOYString(r1.getEndingDate())).append("\n");
            error = true;
        }
        error |= UtilGUI.diffDoubles(dip, r1.getDip(), DIP_TOLERANCE, 
            toString2() + ":" + toString() + " Dip:", sb);
        error |= UtilGUI.diffDoubles(azimuth, r1.getAzimuth(), AZIMUTH_TOLERANCE, 
            toString2() + ":" + toString() + " Azimuth:", sb);        
        error |= UtilGUI.diffDoubles(depth, r1.getDepth(), DEPTH_TOLERANCE, 
            toString2() + ":" + toString() + " Depth:", sb);
        error |= UtilGUI.diffInts(flags, r1.getFlags(), 0, toString2() + ":" + toString()+" Flags:", sb);
        //TODO cookedresponse
        error |= UtilGUI.compareDoubles(rate, r1.getRate(), RATE_TOLERANCE, 
            toString2() + ":" + toString() + " Rate:", sb);
        error |= UtilGUI.diffStrings(instrumentType, r1.getInstrumentType(), strict, toString2() + ":" + toString()+" InstrumentType:", sb);
        //error |= UtilGUI.diffStrings(channelComment, s1.getSeedurl(), true, toString()+" SeedUrl:", sb);
        //error |= UtilGUI.compareDoubles(instrumentgain, r1.getRate(), RATE_TOLERANCE, 
            //toString() + " Rate:", sb);
        error |= UtilGUI.diffStrings(units, r1.getUnits(), strict, toString2() + ":" + toString()+" Units:", sb);
        error |= UtilGUI.compareDoubles(a0, r1.getA0(), A0_TOLERANCE, 
            toString2() + ":" + toString() + " a0:", sb);
        error |= UtilGUI.compareDoubles(a0calc, r1.getA0calc(), A0CALC_TOLERANCE, 
            toString2() + ":" + toString() + " a0calc:", sb);
        error |= UtilGUI.compareDoubles(a0freq, r1.getA0freq(), A0FREQ_TOLERANCE, 
            toString2() + ":" + toString() + " a0freq:", sb);
        error |= UtilGUI.compareDoubles(sensitivity, r1.getSensitivity(), SENSITIVITY_TOLERANCE, 
            toString2() + ":" + toString() + " Sensistivity:", sb);
        error |= UtilGUI.compareDoubles(sensitivityCalc, r1.getSensitivityCalc(), SENSITIVITY_CALC_TOLERANCE, 
            toString2() + ":" + toString() + " SensistivityCalc:", sb);
        error |= UtilGUI.diffDoubles(latitude, r1.getLatitude(), LATITUDE_TOLERANCE, 
            toString2() + ":" + toString() + " Latitiude:", sb);
        error |= UtilGUI.diffDoubles(longitude, r1.getLongitude(), LONGITUDE_TOLERANCE, 
            toString2() + ":" + toString() + " Longitude:", sb);
        error |= UtilGUI.diffDoubles(elevation, r1.getElevation(), ELEVATION_TOLERANCE, 
            toString2() + ":" + toString() + " Elevation:", sb);
                        
        return !error;
    }

}
