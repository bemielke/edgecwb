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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

/**
 * Channel.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Channel templace for creating a MySQL database object.  It is not
 * really needed by the ChannelPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The Channel should be replaced with capitalized version (class name) for the 
 * file.  channel should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeChannel(list of data args) to set the
 * local variables to the value.  The result set just uses the 
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 * 
 * <br> Notes on Enums :
 *<br> * data class should have code like :
 *<br>  import java.util.ArrayList;          /// Import for Enum manipulation
 *<br>    .
 * <br>  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *<br>        .
 *<br>        .
 *<br>     // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 *  <br>   
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 *  <br>  // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class MetaChannel     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeChannel()
  
  /** Creates a new instance of Channel */
  int ID;                   // This is the MySQL ID (should alwas be named "ID"
//  int edgeChannelID;
  String channel;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // USER: All fields of file go here
  //  double longitude

  int channelID;
  String orientation;
  String coordinates;
  String seedcoordinates;
  Double rate;
  String comment;
  String cookedresponse;
  String alias;
  String ircode;
  String otheralias;
  String respurl;
  String seedurl;
  String xmlurl;
  int flags;
  Timestamp effective;
  Timestamp endingDate;
  ArrayList<ChannelEpoch> epochs;
  

  
  // Put in correct detail constructor here.  Use makeChannel() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** reload an instance with this Result set 
   *@param rs The result set to load
   */
  public void reload(ResultSet rs) throws SQLException  {
        makeChannel(rs.getInt("ID"), 
            rs.getString("channel"),
            rs.getInt("channelID"),
            rs.getString("orientation"),
            rs.getString("coordinates"),
            rs.getString("seedcoordinates"),
            rs.getDouble("rate"),
            rs.getString("comment"),
            rs.getString("cookedresponse"),
            rs.getString("alias"),
            rs.getString("ircode"),
            rs.getString("otheralias"),
            rs.getString("respurl"),
            rs.getString("seedurl"),
            rs.getString("xmlurl"),
            rs.getInt("flags"),
            rs.getTimestamp("effective"),
            rs.getTimestamp("endingDate")
          
    );

  }
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet alreay SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public MetaChannel(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeChannel(rs.getInt("ID"), 
            rs.getString("channel"),
            rs.getInt("channelID"),
            rs.getString("orientation"),
            rs.getString("coordinates"),
            rs.getString("seedcoordinates"),
            rs.getDouble("rate"),
            rs.getString("comment"),
            rs.getString("cookedresponse"),
            rs.getString("alias"),
            rs.getString("ircode"),
            rs.getString("otheralias"),
            rs.getString("respurl"),
            rs.getString("seedurl"),
            rs.getString("xmlurl"),
            rs.getInt("flags"),
            rs.getTimestamp("effective"),
            rs.getTimestamp("endingDate")
          
    );
  }
  
  // Detail Constructor, match set up with makeChannel(), this argument list should be
  // the same as for the result set builder as both use makeChannel()
  /** create a row record from individual variables
   *@param inID The ID in the database, normally zero for this type of constructin
   *@param loc The key value for the database (same a name of database table)
   */
  public MetaChannel(int inID, String inchannel,   //USER: add fields, double lon
            int inchannelID,
            String inorientation,
            String incoordinates,
            String inseedcoordinates,
            Double inrate,
            String incomment,
            String incookedresponse,
            String inalias,
            String inircode,
            String inotheralias,
            String inrespurl,
            String inseedurl,
            String inxmlurl,
            int inflags,
            Timestamp ineffective,
            Timestamp inendingDate

    ) {
    makeChannel(inID, inchannel,       //, lon
            inchannelID,
            inorientation,
            incoordinates,
            inseedcoordinates,
            inrate,
            incomment,
            incookedresponse,
            inalias,
            inircode,
            inotheralias,
            inrespurl,
            inseedurl,
            inxmlurl,
            inflags,
            ineffective,
            inendingDate          
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passsed data 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeChannel(int inID, String inchannel,    //USER: add fields, double lon
            int inchannelID,
            String inorientation,
            String incoordinates,
            String inseedcoordinates,
            Double inrate,
            String incomment,
            String incookedresponse,
            String inalias,
            String inircode,
            String inotheralias,
            String inrespurl,
            String inseedurl,
            String inxmlurl,
            int inflags,
            Timestamp ineffective,
            Timestamp inendingDate      

  ) {
    ID = inID;  channel=(inchannel+"        ").substring(0,12);     // longitude = lon
            channelID = inchannelID;
            orientation = inorientation;
            coordinates = incoordinates;
            seedcoordinates = inseedcoordinates;
            rate = inrate;
            comment = incomment;
            cookedresponse = incookedresponse;
            alias = inalias;
            ircode = inircode;
            otheralias = inotheralias;
            respurl = inrespurl;
            seedurl = inseedurl;
            xmlurl = inxmlurl;
            flags = inflags;
            effective = ineffective;
            endingDate = inendingDate;     
    //USER:  Put asssignments to all fields from arguments here
    if(epochs == null) epochs = new ArrayList<ChannelEpoch>(1);
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

    
  }
  /** return the key name as the string
   *@return the key name */
  public String toString2() { return channel;}
  /** return the key name as the string
   *@return the key name */
  @Override
  public String toString() { return channel;}
  // getter
  
  // standard getters
  /** get the database ID for the row
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** get the key name for the row
   *@return The key name string for the row
   */
//;
  public String getChannel() { return channel;}
  
  public String getMetaChannel() { return channel;}
  
  public String getResponse(Timestamp eff) {
    if(eff.compareTo(effective) >= 0 && eff.compareTo(endingDate) <=0) {
      return cookedresponse;
    }
    if(epochs == null) return null;
    for(int i=0; i<epochs.size(); i++) {
      if(eff.compareTo(epochs.get(i).getEffective()) >=0 && eff.compareTo(epochs.get(i).getEndingDate()) <= 0) {
        return epochs.get(i).getCookedResponse();
      }
    }
    return null;
  }
  public String getCoordinates(Timestamp eff) {
    if(eff.compareTo(effective) >= 0 && eff.compareTo(endingDate) <=0) {
      return coordinates;
    }
    if(epochs == null) return null;
    for(int i=0; i<epochs.size(); i++) {
      if(eff.compareTo(epochs.get(i).getEffective()) >=0 && eff.compareTo(epochs.get(i).getEndingDate()) <= 0) {
        return epochs.get(i).getCoordinates();
      }
    }
    return null;
    
  }
  public String getSEEDCoordinates(Timestamp eff) {
    if(eff.compareTo(effective) >= 0 && eff.compareTo(endingDate) <=0) {
      return seedcoordinates;
    }
    if(epochs == null) return null;
    for(int i=0; i<epochs.size(); i++) {
      if(eff.compareTo(epochs.get(i).getEffective()) >=0 && eff.compareTo(epochs.get(i).getEndingDate()) <= 0) {
        return epochs.get(i).getSeedCoordinates();
      }
    }
    return null;

  }
  public String getOrientation(Timestamp eff) {
    if(eff.compareTo(effective) >= 0 && eff.compareTo(endingDate) <=0) {
      return orientation;
    }
    if(epochs == null) return null;
    for(int i=0; i<epochs.size(); i++) {
      if(eff.compareTo(epochs.get(i).getEffective()) >=0 && eff.compareTo(epochs.get(i).getEndingDate()) <= 0) {
        return epochs.get(i).getOrientation();
      }
    }
    return null;
    
  }
  public String getComment(Timestamp eff) {
    if(eff.compareTo(effective) >= 0 && eff.compareTo(endingDate) <=0) {
      return comment;
    }
    if(epochs == null) return null;
    for(int i=0; i<epochs.size(); i++) {
      if(eff.compareTo(epochs.get(i).getEffective()) >=0 && eff.compareTo(epochs.get(i).getEndingDate()) <= 0) {
        return epochs.get(i).getComment();
      }
    }
    return null;
    
  }
  public String getIRcode() {return ircode;}
  public String getAlias() {return alias;}
  public ArrayList<ChannelEpoch> getEpochs() { return epochs;}
  public String getSeedURL() {return seedurl;}
  public String getXMLURL() {return xmlurl;}
  public String getRespURL() {return respurl;}
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Channel) a).getChannel().compareTo(((Channel) b).getChannel());
    }
    public boolean equals(Object a, Object b) {
      if( ((Channel)a).getChannel().equals( ((Channel) b).getChannel())) return true;
      return false;
    }
//  }*/
  
}
