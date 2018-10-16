/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.adslserverdb;

//package gov.usgs.edge.template;
//import java.util.Comparator;
//import gov.usgs.adslservergui.InstrumentModelPanel;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.gui.Maskable;
import gov.usgs.anss.util.*;
import gov.usgs.anss.util.Util;
import java.sql.*;
import java.util.ArrayList;

/**
 * InstrumentType.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This InstrumentType templace for creating a MySQL database object.  It is not
 * really needed by the InstrumentTypePanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The InstrumentType should be replaced with capitalized version (class name) for the
 * file.  instrumentType should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeInstrumentType(list of data args) to set the
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
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(JDBConnectionOld.getConnection("DATABASE"),"table","fieldName");
  <br>   
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 *  <br>  // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class InstrumentType implements Maskable  //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeInstrumentType()
  public static ArrayList<InstrumentType> instrumentTypes;             // Vector containing objects of this InstrumentType Type
  
  /** Creates a new instance of InstrumentType */
  int ID;                   // This is the MySQL ID (should alwas be named "ID"
  String instrumentType;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // USER: All fields of file go here
  //  double longitude

  
  // Put in correct detail constructor here.  Use makeInstrumentType() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set.
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public InstrumentType(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeInstrumentType(rs.getInt("ID"), rs.getString("instrumentType")
                            // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeInstrumentType(), this argument list should be
  // the same as for the result set builder as both use makeInstrumentType()
  /** Create a row record from individual variables.
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   */
  public InstrumentType(int inID, String loc   //USER: add fields, double lon
    ) {
    makeInstrumentType(inID, loc       //, lon
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** Internally set all of the field in our data to the passed data. 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeInstrumentType(int inID, String loc    //USER: add fields, double lon
  ) {
    ID = inID;  instrumentType=loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnectionOld.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

    
  }
  /** Return the key name as the string.
   *@return the key name */
  public String toString2() { return instrumentType;}
  /** Return the key name as the string.
   *@return the key name */
  @Override
  public String toString() { return instrumentType;}
  // getter
  
  // standard getters
  /** Get the database ID for the row.
   *@return The database ID for the row*/
  @Override
  public int getID() {return ID;}
  /** Get the key name for the row.
   *@return The key name string for the row
   */
  public String getInstrumentType() {return instrumentType;}
  @Override
  public long getMask() {return 1L<<(ID-1);}
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((InstrumentType) a).getInstrumentType().compareTo(((InstrumentType) b).getInstrumentType());
    }
    public boolean equals(Object a, Object b) {
      if( ((InstrumentType)a).getInstrumentType().equals( ((InstrumentType) b).getInstrumentType())) return true;
      return false;
    }
//  }*/
  private static String seismometerString;
  private static String dasString;
  private static String serial;
  private static int das;
  private static String dasSerial;
  public static String getLastSerial() {return serial;}
  public static String getDASSerial() {return dasSerial;}
  public static String getDASString() {return dasString;}
  public static String getSeismometerString() {return seismometerString;}
  public static int getDAS() {return das;}
  public static int decodeInstrument(String channel, String instrumentType, String comment) {
    String net = channel.substring(0,2);
    serial="Unknown";
    das=0;
    dasSerial="Unknown";
    if(instrumentType.trim().equals("-")) return 0; // nothing to decode
    if(instrumentType.trim().equalsIgnoreCase("Unknown")) return 0; 
    if(net.equals("GD")) return 0;    // This is GPS displacement data
    // These networks only have a seismometer
    String s="AC_AI_AR_AS_AT_AV_BF_BK_BL_C1_CB_CD_CE_CH_CI_CN_CS_CT_CX_CZ_DK_DW_EE_EI_EN_ER_ES_FA_FN_FR_"+
            "G _GB_GE_GR_GT_H2_HE_HG_HL_HT_HU_IA_IC_II_IM_IP_IS_IU_JP_KC_KO_KR_KZ_LB_LI_"+
            "LX_MB_MN_NC_NJ_NL_NO_NR_NU_NZ_OE_ON_PG_PL_PM_PO_PS_RA_RE_RO_RS_SC_SF_SK_SP_SR_SV_"+
            "TR_TS_TT_TW_UK_UU_WM_WR_WY_XS_XX_Y7_ZD_";
    if(s.contains(net)) {
      if(net.equals("PO") && instrumentType.contains("S/N")) {
        //if(instrumentType.trim().equals("X")) return 0;   // This is an empty field
        serial=instrumentType.substring(instrumentType.indexOf("S/N")+4).trim();
      }
      if(net.equals("RO") && instrumentType.contains("AIM")) {  // They have some AIM24 following / but so many slashes
        das = decodeDas(channel,instrumentType);
      }
      if(instrumentType.contains("CMG") && instrumentType.contains("EDU")) das = decodeDas(channel, instrumentType);
      return decodeSeismometer(channel, instrumentType);
    }
    // models only separated by hyphen
    else if("NM_".contains(net) && !instrumentType.contains("=")) { // Non-inv style from NM network
      String [] p = instrumentType.trim().split("-");
      if(p.length >= 2) {
        das = decodeDas(channel, p[0]);
        return decodeSeismometer(channel, p[1]);
      }
      p = instrumentType.trim().split("/");
      if(instrumentType.contains("/") && !comment.contains("null")) {
        String [] p2 = comment.split(" ");
        if(p2.length == 2) {
          serial=p2[0];
          dasSerial=p2[1];
        }
      }
      if(p.length >= 2) das = decodeDas(channel, p[1]);
      return decodeSeismometer(channel, p[0]);

    }
    // models only separated by hyphen
    else if("7B_CW_MR_BE_BR_EC_EV_GO_IN_IO_KS_KY_MC_MI_ND_OH_OO_PF_S _TC_WA_WI_WU_YX_".contains(net) ) { 
      String [] p = instrumentType.trim().replaceAll("CMG-","CMG").replaceAll("llium-","illium").split("-");
      if(p.length >1) das = decodeDas(channel, p[1]);
      return decodeSeismometer(channel, p[0]);
    }
    // models only separated by commas
    else if("TX_".contains(net) ) { 
      String [] p = instrumentType.trim().replaceAll("CMG-","CMG").replaceAll("llium-","illium").split(",");
      if(p.length >1) das = decodeDas(channel, p[1]);
      return decodeSeismometer(channel, p[0]);
    } 
    // models only separated by semicolon
    else if("SE_".contains(net) ) { 
      String [] p = instrumentType.trim().replaceAll("CMG-","CMG").replaceAll("llium-","illium").split(";");
      if(p.length >1) das = decodeDas(channel, p[1]);
      return decodeSeismometer(channel, p[0]);
    }      
    // This type separates the seismometer from the digitizer with a slash, no serials
    else if(("1C_1D_3C_3D_3E_6C_6D_6E_7D_7E_7F_7J_8G_9B_AD_AE_AF_AP_GG_IE_MS_MM_MY_NI_OX_PB_PY_QK_RI_"
            + "RM_VE_VI_XU_XV_YW_ZC_ZW_").contains(net)) {
      String [] p = instrumentType.replaceAll("V/m/s","Vpmps").
              replaceAll("/VBB","-VBB").replaceAll("v/g","vpg").replaceAll("Volt/g","vpg").split("/");
      if(p.length >= 2) das = decodeDas(channel, p[1]);
      return decodeSeismometer(channel, p[0]);
    }
      // This type separates the seismometer from the digitizer with a slash, and has serial number in comment.
    else if("1B_2C_4A_5A_7A_9A_AB_AK_AU_C _ET_HK_LD_N4_NN_NV_OV_PE_PP_SB_SN_SS_TA_XY_ZM_".contains(net) &&
            !(channel.substring(2,Math.min(channel.length(),6)).equals("CHRN") || 
            channel.substring(2,Math.min(channel.length(), 6)).equals("ROC1"))) {// Two chile stations in Inv
      String [] p = instrumentType.replaceAll("V/m/s","Vpmps").
              replaceAll("/VBB","-VBB").replaceAll("v/g","vpg").replaceAll("Volt/g","vpg").split("/");
      if(p.length >= 2) das = decodeDas(channel, p[1]);
      if(!comment.contains("null") && !comment.contains("No Comment")) {
        String [] serials = comment.split(" ");
        if(serials.length == 2) {
          serial=serials[0];
          dasSerial = serials[1];
        }
        if(serials.length ==1) {
          serial=serials[0];
        }
      }
      return decodeSeismometer(channel, p[0]);
    }
    
    else if(net.equals("NQ")) {
      dasString="NetQuakes";
      das=getModelID(dasString);
      seismometerString="NetQuakes";
      if(!instrumentType.contains("=")) return getModelID("NetQuakes");
      String [] parts = instrumentType.trim().replaceAll("ES-T", "EST").split("=");

      if(parts.length == 1) { //UW network often breaks their own convention
        //das = decodeDas(channel,parts[0]);
        return decodeSeismometer(channel,parts[0]);   // no = sign, so just a seismoter
      }
      if(parts.length >= 2) { // seismometer at beginning digitizer at end
        String [] p = parts[parts.length-1].split("-"); // This is the digitizer
        if(p.length >= 2) {
          dasSerial =p[1];
          String dasType = p[0];
          das = decodeDas(channel, dasType);
        }
        p = parts[0].split("-");
        if(p.length >=2) {
          serial=p[1];
        }
        return decodeSeismometer(channel, p[0]);
      }      
    }
      // This type does SEISM-seial=[...]=DAS-serial and sometimes there is no =
    else if("AY_CC_NQ_UO".contains(net) ) {
      String [] parts = instrumentType.trim().replaceAll("ES-T", "EST").split("=");

      if(parts.length == 1) { //UW network often breaks their own convention
        //das = decodeDas(channel,parts[0]);
        return decodeSeismometer(channel,parts[0]);   // no = sign, so just a seismoter
      }
      if(parts.length >= 2) { // seismometer at beginning digitizer at end
        String [] p = parts[parts.length-1].split("-"); // This is the digitizer
        if(p.length >= 2) {
          dasSerial =p[1];
          String dasType = p[0];
          das = decodeDas(channel, dasType);
        }
        p = parts[0].split("-");
        if(p.length >=2) {
          serial=p[1];
        }
        return decodeSeismometer(channel, p[0]);
      }
    }
      // This type does SEISM-seial=[...]=DAS-serial and sometimes there is no =
    // Or they do seis/das with serials on comment separated by spaces
    else if("UW".contains(net) ) {
      if(instrumentType.indexOf("=") >0) {
        String [] parts = instrumentType.trim().replaceAll("ES-T", "EST").split("=");

        if(parts.length == 1) { //UW network often breaks their own convention
          //das = decodeDas(channel,parts[0]);
          return decodeSeismometer(channel,parts[0]);   // no = sign, so just a seismoter
        }
        if(parts.length >= 2) { // seismometer at beginning digitizer at end
          String [] p = parts[parts.length-1].split("-"); // This is the digitizer
          if(p.length >= 2) {
            dasSerial =p[1];
            String dasType = p[0];
            das = decodeDas(channel, dasType);
          }
          p = parts[0].split("-");
          if(p.length >=2) {
            serial=p[1];
          }
          return decodeSeismometer(channel, p[0]);
        }
      }
      else if(instrumentType.indexOf("/") > 0) {
        String [] p = instrumentType.replaceAll("V/m/s","Vpmps").
                replaceAll("/VBB","-VBB").replaceAll("v/g","vpg").replaceAll("Volt/g","vpg").split("/");
        if(p.length >= 2) das = decodeDas(channel, p[1]);
        if(!comment.contains("null") && !comment.contains("No Comment")) {
          String [] serials = comment.split(" ");
          if(serials.length == 2) {
            serial=serials[0];
            dasSerial = serials[1];
         }
          if(serials.length ==1) {
            serial=serials[0];
          }
        }
        return decodeSeismometer(channel, p[0]);
      }
    }
      // INV: This type does SEISM-seial=[...]=DAS-serial and sometimes there is no =
    else if("AG_AO_C _CM_CO_CU_GS_HV_IW_NE_NM_OK_PR_PT_TU_US_YC_".contains(net) ) {   // C CHRN is the only one here
      String [] parts = instrumentType.trim().replaceAll("ES-T", "EST").split("=");
      if(parts.length == 1) {
        return decodeSeismometer(channel,parts[0]);   // no = sign, so just a seismoter
      }
      if(parts.length >= 2) { // seismometer at beginning digitizer at end
//        String [] p = parts[parts.length-1].split("-"); // This is the digitizer
//        if(p.length >= 2) {
          dasSerial =parts[parts.length-1];
          String dasType = parts[parts.length-2];
          das = decodeDas(channel, dasType);
          if(net.equals("NM") && das == 0) {  // try the opposite way
            das = decodeDas(channel, parts[0]);
            Util.prt("NM reverse decode DAS="+parts[0]+" seiss="+dasType);
            if(das != 0) {
              String tmp = dasSerial;
              dasSerial = serial;
              serial = tmp;
              return decodeSeismometer(channel, dasType);
            }
          }
//        }
        //if(parts.length >=2) {
          serial=parts[1];
        //}
        return decodeSeismometer(channel, parts[0]);
      }
    }
    // colon delimited seismometer then DAS
    else if("AZ_BC_GY_HW_KN_MG_MX_NP_PN".contains(net)) {   // This network has colon or / separating seismometer and das no serials
      String [] parts = instrumentType.trim().split(":/");
      if(parts.length == 1) {
        return decodeSeismometer(channel,parts[0]);   // no = sign, so just a seismoter
      }
      if(parts.length >= 2) { // seismometer at beginning digitizer at end
        String [] p = parts[parts.length-1].split("-"); // This is the digitizer
        if(p.length >= 2) {
          dasSerial =parts[parts.length-1];
          String dasType = parts[parts.length-2];
          das = decodeDas(channel, dasType);
        }
        p = parts[0].split("-");
        if(p.length >=2) {
          serial=p[1];
        }
        return decodeSeismometer(channel, p[0]);
      }
      else return decodeSeismometer(channel, parts[0]);
    }
    // Colons but GFZ: at beginning of lines
    else if("SJ_ZE_".contains(net)) {   // This network has colon or / separating seismometer and das no serials
      String [] parts = instrumentType.trim().split(":/");
      if(parts.length >= 3) {
        return decodeSeismometer(channel,parts[2]);   // no = sign, so just a seismoter
      }
      else return decodeSeismometer(channel, parts[0]);
    }
    // This network has no structure, but both might be present - take a flyer
    else if("BL_CY_DR_EP_NB_PA_TJ_TM_ZZ".contains(net)) {
      das = decodeDas(channel, instrumentType);
      return decodeSeismometer(channel, instrumentType);
    }
    // These have a seismometer and serial number only separated by a dash
    else if("GL_".contains(net)) {
      String [] p = instrumentType.split("-");
      if(p.length >= 2) {
        serial = p[1].trim();
      }
      return decodeSeismometer(channel, instrumentType);
    }
    else {  // not work is not handled specifically return a das
      if(instrumentType.contains("/") || instrumentType.contains("=") || instrumentType.indexOf(":") > 0) {
        Util.prt(" ##### Looks like network ="+channel+" DAS could be decoded.  type="+instrumentType+" comment="+comment);
        SendEvent.debugEvent("InsTypeDecode", "Net="+channel+" DAS decode. "+instrumentType+"|"+comment, "InstrumentType");
      }
      else Util.prt("This network code does not have special "+channel+" "+instrumentType+" cmnt="+comment);
      das = decodeDas(channel, instrumentType);
      if(das > 0) Util.prt("Surprise as DAS is present!!!!! "+channel+" "+instrumentType+" cmnt="+comment);
      return decodeSeismometer(channel,instrumentType);
    }
    // Stations with = often have parts and serial numbers, decode them
    if(instrumentType.contains("=")) {
      String [] parts = instrumentType.split("=");
      serial="Unknown";
      if(parts.length == 1) { // This is a IU type station
        //parts = instrumentType.trim().split("\\s");
        Util.prt("Did not get a model for ="+channel+" "+instrumentType);

        return 0;
      }
      else if(parts.length >= 4) {
        serial = parts[1];
        // Now try dases
        String dasType="";
        if(parts.length >= 2) {
          dasSerial =parts[parts.length-1];
          dasType = parts[parts.length-2];
        }
        das = decodeDas(channel, dasType);
        return decodeSeismometer(channel, parts[0]);
      }
      else if(parts.length >=2) {   // seismometer only
        serial = parts[1];
        return decodeSeismometer(channel,parts[0]);
      }
    }
    Util.prt("Surprise!  Unhandled case="+channel+" "+instrumentType);
    return 0;
  }
  private static int  getModelID(String model) {
    if(JDBConnectionOld.getConnection("irserver") == null) return 0;
  /** Get the item corresponding to database ID.
   *@param model the Model name
   *@return The InstrumentModel row with this ID
   */
    if(InstrumentModel.instrumentModels == null) makeInstrumentModels();
    for(int i=0; i<InstrumentModel.instrumentModels.size(); i++) {
      if( InstrumentModel.instrumentModels.get(i).getInstrumentModel().contains(model)) return InstrumentModel.instrumentModels.get(i).getID();
    }
    return 0;
  }
  public static ArrayList<InstrumentType> getInstrumentTypeArrayList() {makeInstrumentTypes();return instrumentTypes;}
  // This routine should only need tweeking if key field is not same as table name
  public static void makeInstrumentTypes() {
    if (instrumentTypes != null) return;
    InstrumentType.makeInstrumentModels();
    instrumentTypes=new ArrayList<InstrumentType>(100);
    try {
      try (Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(false) // used for query
      ) {
        String s = "SELECT * FROM instrumenttype ORDER BY instrumenttype;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            InstrumentType loc = new InstrumentType(rs);
//        Util.prt("MakeInstrumentType() i="+instrumentTypes.size()+" is "+loc.getInstrumentType());
instrumentTypes.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeInstrumentTypes() on table SQL failed");
    }    
  }
  public static void makeInstrumentModels() {
    if (InstrumentModel.instrumentModels != null) return;
    InstrumentModel.instrumentModels=new ArrayList<InstrumentModel>(100);
    try {
      try (Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(false) // used for query
      ) {
        String s = "SELECT * FROM instrumentmodel,manufacturer WHERE manufacturer.ID=manufacturerid ORDER BY manufacturer,instrumentmodel;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            InstrumentModel loc = new InstrumentModel(rs);
            //Util.prt("MakeInstrumentModel() i="+instrumentTypes.size()+" is "+loc.getInstrumentModel());
            InstrumentModel.instrumentModels.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeInstrumentModels() on table SQL failed");
    }    
  }  
  public static InstrumentModel getInstrumentModelWithID(int id) {
    makeInstrumentModels();
    for(InstrumentModel model : InstrumentModel.instrumentModels) {
      if(model.getID() == id) return model;
    }
    return null;
  }
  private static int decodeDas(String channel, String type) {
    dasString = decodeDasString(channel,type);
    if(dasString.equals("")) return 0;
    return getModelID(dasString);

  }
  private static  String decodeDasString(String channel, String type) {
    //if(channel.substring(0,2).equals("NN") && (type.equals("220") || type.equals("g"))) return ""; // this is SL210/220 misguided
    if(channel.substring(0,2).equals("TA") && type.contains("Quanterra 33")) return ("Q330");
    if(channel.substring(0,2).equals("UW")) { 
      if(type.contains("Ralph") || type.contains("Wrmsn") ||
              type.contains("Wrmfk") || type.contains("Wrmkf")) return "";  // analog digitizers?
    }
    if(channel.substring(0,2).equals("CC")) {
      if(type.contains("Wrmcv") || type.contains("Wrmsn")  || type.contains("Wrmkf") || type.contains("Wrmbd")) return "";  // analog digitizers?
    }
    if(channel.substring(0,2).equals("NN")) {
      if(type.contains("Eworm") || type.contains("cusp") || type.contains("ping") || type.contains("Dummy")) return "";  // analog digitizers
    }
    if(channel.substring(0,2).equals("PB") && type.trim().equals("Quanterra"))
      return  ("Q330");   // its the only one they have, model cut off by field length
   if(channel.substring(0,2).equals("TA") && type.indexOf("Qua") == 0)
      return  ("Q330");   // its the only one they have, model cut off by field length

    if(type.contains("RT130")) return ("RT130");
    if(type.contains("RT-130")) return ("RT130");
    if(type.contains("RT24")) return ("RT24");
    if(type.contains("RT16")) return ("RT16");
    if(type.contains("Q330HR")) return ("Q330");
    if(type.contains("330HR")) return ("Q330");
    if(type.contains("Q330")) return ("Q330");
    if(type.contains("K2")) return  ("K2");
    if(type.contains("Etna")) return  ("Etna");
    if(type.contains("Granite")) return  ("Granite");
    if(type.contains("Basalt")) return  ("Basalt");
    if(type.contains("basalt")) return  ("Basalt");
    if(type.contains("Dolomite")) return  ("Dolomite");
    if(type.contains("dolomite")) return  ("Dolomite");
    if(type.contains("ROCK")) return  ("Rock");
    if(type.contains("Rock")) return  ("Rock");
    if(type.contains("rock")) return  ("Rock");
    if(type.contains("MtWhitney")) return  ("MtWhitney");
    if(type.contains("Whitney")) return  ("MtWhitney");
    if(type.contains("whitney")) return  ("MtWhitney");
    if(type.contains("Makalu")) return  ("Makalu");
    if(type.contains("makalu")) return  ("Makalu");
    if(type.contains("Orion")) return  ("Orion");
    if(type.contains("Taurus")) return  ("Taurus");
    if(type.contains("Trident")) return  ("Trident");
    if(type.contains("Ralph")) return  ("Ralph");
    if(type.contains("G5TD")) return  ("DM24");
    if(type.contains("IDS20")) return  ("IDS20");
    if(type.contains("IDS24")) return  ("IDS24");
    if(type.contains("RT72A")) return  ("RT72");
    if(type.contains("72A")) return  ("RT72");
    if(type.contains("REFTEK(RT24")) return "RT130";
    if(type.contains("REFTEK(RT16")) return "RT72";
    
    if(type.contains("RT130")) return  ("RT130");
    if(type.contains("NETQUAKE")) return ("NetQuakes");
    if(type.contains("NetQuake")) return ("NetQuakes");
    if(type.contains("NETQUAKE")) return ("NetQuakes");
    if(type.contains("netquake")) return ("NetQuakes");
    if(type.toUpperCase().contains("GEOSIG-AC")) return ("NetQuakes");
    if(type.toUpperCase().contains("GEOSIG")) return ("NetQuakes");

    if(type.contains("NetDAS")) return ("NetDAS");
    if(type.contains("NetDas")) return ("NetDAS");
    if(type.contains("netdas")) return ("NetDAS");
    if(type.contains("NETDAS")) return ("NetDAS");

    if(type.contains("Q980")) return ("Q680");
    if(type.contains("Q680")) return ("Q680");
    if(type.contains("Q380")) return ("Q680");
    if(type.contains("Q80")) return ("Q680");
    if(type.contains("Q730")) return ("Q730");
    if(type.contains("CMG6TD")) return ("DM24");
    if(type.contains("G6TD")) return ("DM24"); // UW
    if(type.contains("CD24")) return ("CD24");
    if(type.contains("DM24")) return ("DM24");
    if(type.contains("dm24")) return ("DM24");
    if(type.contains("dm-24")) return ("DM24");
    if(type.contains("DM-24")) return ("DM24");
    if(type.contains("DR24")) return ("DR24");
    if(type.contains("DM16")) return ("DM16");
    if(type.contains("4120")) return ("Q4120");
    if(type.contains("330")) return ("Q330");
    if(type.contains("GDAS")) return ("GDAS");
    if(type.contains("Quanterra 33") ) return ("Q330");
    if(type.contains("Quanterra 380") ) return ("Q680");
    if(type.contains("Qx80") ) return ("Q680");
    if(type.contains("Reftek 130") ) return ("RT130");
    if(type.contains("Nanometric Tr") ) return ("Trident");
    if(type.contains("Centaur")) return ("Centaur");
    if(type.contains("BlueBoxII") ) return ("BlueBoxII");
    if(type.contains("HRD24") ) return ("HRD24");
    if(type.contains("HRD-24") ) return ("HRD24");
    if(type.contains("MARS88") ) return ("MARS88");
    if(type.contains("SM24") ) return ("SM24");
    if(type.contains("AIM24") ) return ("AIM24");
    if(type.contains("AIM-24") ) return ("AIM24");
    if(type.contains("AIMS24") ) return ("AIM24");
    if(type.contains("AIMS-24") ) return ("AIM24");
    if(type.contains("CMG-EDU") ) return ("CMGEDU");
    if(type.contains("CMGEDU") ) return ("CMGEDU");
    if(type.contains("EDU") ) return ("CMGEDU");
    if(type.contains("GEDU") ) return ("CMGEDU");
    if(type.contains("EDAS24") ) return ("EDAS24");
    if(type.contains("EDAS-24") ) return ("EDAS24");
    if(type.contains("DL24") ) return ("DL24");
    if(type.contains("DL-24") ) return ("DL24");
    if(type.contains("SMA-1")) return ("SMA1");
    if(type.contains("CRA1")) return ("CRA1");
    if(type.contains("CRA-1")) return ("CRA1");
    if(type.contains("SMA1")) return ("SMA1");
    if(type.contains("SSA-1")) return ("SSA1");
    if(type.contains("SSA11")) return ("SSA1");
    if(type.contains("SSA-2")) return ("SSA2");
    if(type.contains("SSA2")) return ("SSA2");
    if(type.equals("NQ") ) return ("NetQuakes");
    if(type.contains("PS6_24")) return ("PS6");
    if(type.contains("PS6-24")) return ("PS6");
    if(type.contains("PS6")) return ("PS6");
    if(type.contains("PS-6")) return ("PS6");
    if(type.contains("6TD")) return ("DM24"); // UW

    Util.prt("decodeDas() did not work. ch="+channel+" type="+type);
    return "";
  }
  public static int decodeSeismometer(String channel, String type) {
    seismometerString = decodeSeismometerString(channel, type);
    if(seismometerString.equals("") ) return 0;
    return getModelID(seismometerString);
  }
  public static String decodeSeismometerString(String channel, String type) {
    if(type.trim().equals("-")) return "";
    if(type.trim().equals("UNKNOWN")) return "";
    if(type.trim().equals("No Abbreviation")) return"";
    if(type.contains("Canadian National Seismograph")) return ""; // no instrument -just verbage
    if(type.contains("AF AfricaArray")) return ""; // no instrument -just verbage
    if(type.contains("Mozambique National Seismograph")) return ""; // no instrument -just verbage
    if(type.contains("Canadian National Seismograph")) return ""; // no instrument -just verbage
    if(channel.substring(0,2).equals("BL") && type.contains("GC3EC"))
      return ("CMG3T");   // Another SWAG
    if(channel.substring(0,2).equals("MS") && type.contains("VBB")) return "";
    if(channel.substring(0,2).equals("WR") && type.trim().equals("Benioff"))
      return ("GB1051");  // this is a SWAG
    if(channel.substring(0,2).equals("LD") && type.charAt(0) == 'P' && type.contains("datalogger")) return "";
    if(channel.substring(0,2).equals("NN") && type.contains("Applied MEMS")) return "RT131";
    if(type.contains("EST-S") ) return ("EST");
    if(type.contains("EPISENSOR") ) return ("Episensor200"); // HV is ambiguous
    if(type.contains("CMG-1T")) return ("CMG1T");
    if(type.contains("CMG1")) return ("CMG1T");
    if(type.contains("CMG-6T")) return ("CMG6T");
    if(type.contains("CMG3TESP")) return ("CMG3ESP");
    if(type.contains("CMG-3T")) return ("CMG3T");
    if(type.contains("CMG3-T")) return ("CMG3T");
    if(type.contains("CMG3H")) return ("CMG3D");
    if(type.contains("CMG3V")) return ("CMG3D");
    if(type.contains("CMG3T") ) return ("CMG3T");
    if(type.contains("CMG3V") ) return ("CMG3D");
    if(type.contains("CMG3H") ) return ("CMG3D");
    if(type.contains("CMG3BH") ) return ("CMG3TB");
    if(type.contains("CMG3TB") ) return ("CMG3TB");
    if(type.contains("cmg3t")) return ("CMG3T");
    if(type.contains("CMG-3ESP")) return ("CMG3ESP");
    if(type.contains("CMG3-ESP")) return ("CMG3ESP");
    if(type.contains("CMG-3ESP")) return ("CMG3ESP");
    if(type.contains("CMG3ESP")) return ("CMG3ESP");
    if(type.contains("CMG-3")) return ("CMG3T");
    if(type.contains("CMG3H-N") ) return ("CMG3D");
    if(type.contains("CMG-40T")) return ("CMG40T");
    if(type.contains("CMG40T")) return ("CMG40T");
    if(type.contains("cmg40t")) return ("CMG40T");
    if(type.contains("CMG40")) return ("CMG40T");
    if(type.contains("CMG-40")) return ("CMG40T");
    if(type.contains("cmg40")) return ("CMG40T");
    if(type.contains("CMG-4T")) return ("CMG4T");
    if(type.contains("CM4T")) return ("CMG4T");
    if(type.contains("CMG5TEX") ) return ("CMG5T");
    if(type.contains("CMG5TIN") ) return ("CMG5T");
    if(type.contains("CMG5T")) return ("CMG5T");
    if(type.contains("cmg5t")) return ("CMG5T");
    if(type.contains("CMG-5")) return ("CMG5T");
    if(type.contains("CMG5")) return ("CMG5T");
    if(type.contains("5TD")) return ("CMG5T");
    if(type.contains("CMG4") ) return ("CMG4T");
    if(type.contains("Guralp 5T")) return ("CMG5T");
    if(type.contains("CMG6TD")) return ("CMG6T");
    if(type.contains("CMG-6T")) return ("CMG6T");
    if(type.contains("CMG6T")) return ("CMG6T");
    if(type.contains("ES-T")) return ("EST");
    if(type.contains("EST")) return ("EST");
    if(type.contains("EST-S")) return ("EST");
    if(type.contains("ES-D")) return ("ESDH");
    if(type.contains("Episensor 200")) return ("EPISENSOR200");
    if(type.contains("Episensor")) return ("EST");
    if(type.contains("episensor")) return ("EST");
    if(type.contains("EpiSensor")) return ("EST");
    if(type.contains("EPISENSOR")) return ("EPISENSOR200");
    if(type.contains("ES-D")) return ("ESDH");
    if(type.contains("FBA-K2")) return ("FBA23");
    if(type.contains("K2-FBA")) return ("FBA23");
    if(type.contains("FBA-D")) return ("FBA23");
    if(type.contains("FBA-11")) return ("FBA11");
    if(type.contains("FB-11")) return ("FBA11");
    if(type.contains("FBA-23")) return ("FBA23");
    if(type.contains("fba23")) return ("FBA23");
    if(type.contains("FBA-EST")) return ("EST");
    if(type.contains("FBA23")) return ("FBA23");
    if(type.contains("FBA11")) return ("FBA11");
    if(type.contains("FBA-3")) return ("FBA3");
    if(type.contains("FBA3")) return ("FBA3");
    if(type.contains("Geotech 8700")) return ("G8700");
    if(type.contains("Geotech 7505")) return ("G7505");
    if(type.contains("Geotech 23900")) return ("G23900");
    if(type.contains("23900")) return ("G23900");
    if(type.contains("Geotech 20171")) return ("G20171");
    if(type.contains("20171")) return ("G20171");
    if(type.contains("G20171")) return ("G23900");
    if(type.contains("GeospaceHS1")) return ("HS1");
    if(type.contains("Geospace-HS1")) return ("HS1");
    if(type.contains("HS-1")) return ("HS1");
    if(type.contains("HS1")) return ("HS1");
    if(type.contains("Geotech S-11")) return ("S11");
    if(type.contains("Geotech S-12")) return ("S12");
    if(type.contains("Geotech S-13")) return ("S13");
    if(type.contains("Geotech gs-21")) return ("GS21");
    if(type.contains("GS-21")) return ("GS21");

    if(type.contains("GS20")) return ("GS20");  // Geospace not Geotech.
    if(type.contains("GS-20")) return ("GS20");
    if(type.contains("gs20")) return ("GS20");
    if(type.contains("SF1500")) return "SF1500";
    if(type.contains("SF-1500")) return "SF1500";
    if(type.contains("sf1500")) return "SF1500";
    if(type.contains("SF2005")) return "SF2005";
    if(type.contains("SF-2005")) return "SF2005";
    if(type.contains("sf2005")) return "SF2005";



    if(type.contains("KS2000")) return ("KS2000");
    if(type.contains("KS-2000")) return ("KS2000");
    if(type.contains("KS2K")) return ("KS2000");
    if(type.contains("KS36000")) return ("KS36000");
    if(type.contains("KS-36000")) return ("KS36000");
    if(type.contains("KS54000") ) return ("KS54000");
    if(type.contains("KS-54000") ) return ("KS54000");
    if(type.contains("RANGER")) return ("SS1");
    if(type.contains("Ranger")) return ("SS1");
    if(type.contains("ranger")) return ("SS1");
    if(type.contains("Ranger SS1")) return ("SS1");
    if(type.contains("NetQuake")) return ("NetQuakes");
    if(type.contains("NETQUAKE")) return ("NetQuakes");
    if(type.contains("netquake")) return ("NetQuakes");
    if(type.contains("AC-63")) return ("NetQuakes");
    if(type.contains("CMG3")) return ("CMG3T");
    if(type.contains("SD-212")) return "SD-212";
    if(type.contains("SD212")) return "SD-212";
    if(type.contains("STS-1")) return ("STS1");
    if(type.contains("STS2-I") ) return ("STS2");
    if(type.contains("STS2-IHG") ) return ("STS2");
    if(type.contains("STS-2")) return ("STS2");
    if(type.contains("STS2")) return ("STS2");
    if(type.contains("sts2")) return ("STS2");
    if(type.contains("sts2.5")) return ("STS2");
    if(type.contains("STS2.5")) return ("STS2");
    if(type.contains("ST22P")) return ("STS2");
    if(type.contains("ST2G")) return ("STS2");
    if(type.contains("STS-2.5")) return ("STS2");
    if(type.toUpperCase().contains("STS-4B")) return ("STS2");
    if(type.toUpperCase().contains("STS4B")) return ("STS2");
    if(type.toUpperCase().contains("STS5")) return ("STS5");
    if(type.toUpperCase().contains("STS-5")) return ("STS5");

    if(type.contains("RT-131")) return ("RT131");
    if(type.contains("RT-130-ANSS_SEIS")) return ("RT131");
    if(type.contains("L4C")) return ("L4");
    if(type.contains("54000")) return ("KS54000");
    if(type.contains("TrilliumCompact")) return ("TrilliumCompact");
    if(type.contains("Trillium Compact")) return ("TrilliumCompact");
    if(type.contains("TRILLIUMCOMPACT")) return ("TrilliumCompact");
    if(type.contains("TRILLIUM COMPACT")) return ("TrilliumCompact");
    if(type.contains("trillium_compact")) return ("TrilliumCompact");
    if(type.contains("Trillium 240")) return ("Trillium240");
    if(type.contains("Trillium-240")) return ("Trillium240");
    if(type.contains("Trillium240")) return ("Trillium240");
    if(type.contains("TRILLM240")) return ("Trillium240");

    if(type.contains("Trilium240")) return ("Trillium240");
    if(type.contains("Trillium240")) return ("Trillium240");
    if(type.contains("TRILLIUM 240")) return ("Trillium240");
    if(type.contains("TRILLIUM-240")) return ("Trillium240");
    if(type.contains("TRILLIUM240")) return ("Trillium240");
    if(type.contains("Trillium 120")) return ("Trillium120");
    if(type.contains("Trilium 120")) return ("Trillium120");
    if(type.contains("Trillium 120")) return ("Trillium120");
    if(type.contains("Trillium-120")) return ("Trillium120");
    if(type.contains("Trillium120")) return ("Trillium120");
    if(type.contains("Trilium120")) return ("Trillium120");
    if(type.contains("Trilium120")) return ("Trillium120");
    if(type.contains("TRILLM120")) return ("Trillium120");
    if(type.contains("TRILLIUM 120")) return ("Trillium120");
    if(type.contains("TRILLIUM-120")) return ("Trillium120");
    if(type.contains("TRILLIUM120")) return ("Trillium120");
    if(type.contains("Trillium 40")) return ("Trillium40");
    if(type.contains("Trillium-40")) return ("Trillium40");
    if(type.contains("Trillium40")) return ("Trillium40");
    if(type.contains("TRILLM40")) return ("Trillium240");
    if(type.contains("Trilium40")) return ("Trillium40");
    if(type.contains("TRILLIUM 40")) return ("Trillium40");
    if(type.contains("TRILLIUM-40")) return ("Trillium40");
    if(type.contains("TRILLIUM40")) return ("Trillium40");
    if(type.contains("Trillium")) return ("Trillium40");
    if(type.contains("TRILLIUM")) return ("Trillium40");
    if(type.contains("BBVS, 60")) return "BBVS60";
    if(type.contains("BBVS, 120")) return "BBVS120";
    if(type.contains("BBVS-60")) return "BBVS60";
    if(type.contains("BBVS60")) return "BBVS60";
    if(type.contains("BBVS-120")) return "BBVS120";
    if(type.contains("BBVS120")) return "BBVS120";
    if(type.contains("PBB-200")) return "PBB200";
    if(type.contains("PBB200")) return "PBB200";
    if(type.toLowerCase().contains("chapar")) return "Chaparral";
    
    if(type.contains("Kinemetrics SV1")) return ("SV1");
    if(type.contains("Kinemetrics SH1")) return ("SH1");
    if(type.contains("Kirnos SKD")) return ("SKD");
    if(type.contains("PMD2023")) return ("2023");
    if(type.contains("Iongeo SM-6")) return ("SM6");
    if(type.contains("SM-6")) return "SM6";
    if(type.contains("WILCOXON")) return ("WilcoxonCI");
    if(type.contains("VSE")) return ("VSE355");
    if(type.contains("Benioff 1051")) return ("GB1051");
    if(type.contains("18300")) return ("G18300");
    if(type.contains("PA23")) return ("PA23");
    if(type.contains("731A")) return ("W731");
    if(type.contains("Wilcoxon-731")) return ("W731");
    if(type.contains("SF3000L")) return ("SF3000L");
    if(type.contains("Benioff 1101")) return ("GB1101");
    if(type.contains("Benioff 4681")) return ("GB4681");
    if(type.contains("Benioff 6201")) return ("GB6201");
    if(type.contains("SF3000L")) return ("SF3000L");
    if(type.contains("SF3000L")) return ("SF3000L");

    if(type.contains("TITAN")) return ("Titan");
    if(type.contains("Titan")) return ("Titan");
    if(type.contains("titan")) return ("Titan");
    if(type.contains("RANGER")) return ("SS1");
    if(type.contains("SH1SV1")) return ("SH1");
    if(type.contains("Sprengnether6000")) return ("S6000");
    if(type.contains("OYO-HS1")) return ("HS1");
    if(type.contains("Geotech S-750")) return ("S750");
    if(type.contains("Geotech T200")) return ("SL210");
    if(type.contains("G3ESP")) return ("CMG3ESP");
    if(type.contains("G3TNSN")) return ("CMG3T");
    if(type.contains("G40T")) return ("CMG40T");
    if(type.contains("G6TD")) return ("CMG6T");
    if(type.contains("G5TD")) return ("CMG5T"); // UW
    if(type.contains("G3TTA")) return ("CMG3T");
    if(type.contains("TR-240")) return ("Trillium240");
    if(type.contains("TR240")) return ("Trillium240");
    if(type.contains("TR-120")) return ("Trillium120");
    if(type.contains("t120")) return ("Trillium120");
    if(type.contains("T120")) return ("Trillium120");
    if(type.contains("TR120")) return ("Trillium120");
    if(type.contains("TR-40")) return ("Trillium40");
    if(type.contains("TR40")) return ("Trillium40");
    if(type.contains("WIL207")) return ("W731");
    if(type.contains("BBVS-120")) return ("BBVS120");
    if(type.contains("S-5100")) return ("SN5100");
    if(type.contains("TSA-100")) return ("TSA100");

    if(type.contains("T210")) return ("SL210");
    if(type.contains("L22")) return ("L22");
    if(type.contains("AC63")) return ("AC6x");
    if(type.contains("L4")) return ("L4");
    if(type.contains("L-4")) return ("L4");
    if(type.contains("L28")) return ("L28");
    if(type.contains("L28")) return ("L28");
    if(type.contains("L22")) return ("L22");
    if(type.contains("L22")) return ("L22");
    if(type.contains("L10")) return "L10";
    if(type.contains("L-10")) return "L10";
    if(type.contains("RT-131")) return ("RT131");
    if(type.contains("RT131")) return ("RT131");
    if(type.contains("RT-147")) return ("RT147");
    if(type.contains("rt147")) return ("RT147");
    if(type.contains("RT147")) return ("RT147");
    if(type.contains("Reftek 147")) return ("RT147");
    if(type.contains("REFTEK 147")) return ("RT147");
    if(type.contains("RT151")) return ("RT151");
    if(type.contains("RT-151")) return ("RT151");
    if(type.contains("Reftek 151")) return ("RT151");
    if(type.contains("Reftek-151")) return ("RT151");
    if(type.contains("RefTek 151")) return ("RT151");
    if(type.contains("RefTek-151")) return ("RT151");
    if(type.contains("REFTEK 151")) return ("RT151");
    if(type.contains("REFTEK-151")) return ("RT151");

    if(type.contains("HS-10")) return ("HS10");
    if(type.contains("HS10")) return ("HS10");
    if(type.contains("HS1")) return ("HS1");
    if(type.contains("SS-1")) return ("SS1");
    if(type.contains("S-12")) return ("S13");
    if(type.contains("S13")) return ("S13");
    if(type.contains("S-13")) return ("S13");
    if(type.contains("GS13")) return ("GS13"); 
    if(type.contains("GS-13")) return ("GS13");
    if(type.contains("SS-1")) return ("SS1");
    if(type.contains("WR-1")) return ("WR1");
    if(type.contains("ZM500") ) return ("ZM500");
    if(type.contains("HM500") ) return ("HM500");
    if(type.contains("DASE-LPHA") ) return ("HM500");
    if(type.contains("M2166") ) return ("M2166");
    if(type.contains("DASE-LPZA") ) return ("ZM500");
    if(type.contains("DASE-LPZA") ) return ("ZM500");
    if(type.contains("DASE-LPZA") ) return ("ZM500");
    
    if(type.contains("SP-400")) return "SP400";
    if(type.contains("SP400")) return "SP400";
    


    if(type.contains("KS5400")) return ("KS54000");
    if(type.contains("GS-11")) return ("GS11");
    if(type.contains("GS11")) return ("GS11");
    if(type.contains("GS21")) return ("GS21");
    if(type.contains("GS-21")) return ("GS21");
    if(type.contains("PS6")) return ("PS6");
    if(type.contains("AC-6")) return ("AC6x");



    if(type.contains("l22_")) return ("L22");
    if(type.contains("L22")) return ("L22");
    if(type.contains("L-22")) return ("L22");
    if(type.contains("l28_")) return ("L28");
    if(type.contains("EPIS")) return ("EST");
    if(type.contains("ES-U")) return ("ESU");
    if(type.contains("DJ-1")) return ("DJ1");
    if(type.contains("S-5007")) return ("S5007");
    if(type.contains("L15B")) return ("L15B");
    if(type.contains("STS1")) return ("STS1");
    if(type.contains("LE-3D")) return ("LE3D");
    if(type.contains("L3D")) return ("LE3D");
    if(type.contains("K213")) return ("k213");
    if(type.contains("SL210")) return ("SL210");
    if(type.contains("SL220")) return ("SL220");
    if(type.contains("SL-210")) return ("SL210");
    if(type.contains("SL-220")) return ("SL220");
    if(type.contains("CUSPM")) return ("");
    if(type.contains("CUSP3")) return ("CUSPM");
    if(type.contains("peppv")) return ("CUSP3");
    if(type.contains("ESP")) return ("CMG3ESP");
    if(type.contains("S-750")) return ("S750");
    if(type.contains("SSA-320")) return ("SSA320");
    if(type.contains("SSA-320")) return ("SSA320");
    if(type.contains("6480")) return ("6480");
    if(type.contains("CTS-1")) return ("CTS1");
    if(type.contains("JCZ-1")) return ("JSZ1");
    if(type.contains("SKM-3")) return ("SKM3");
    if(type.contains("SMA")) return ("RT130");
    if(type.contains("SM-3")) return ("SM3");
    if(type.contains("SS1")) return ("SS1");
    if(type.contains("CMG-EDU") ) return ("CMGEDU");
    if(type.contains("CMGEDU") ) return ("CMGEDU");
    if(type.contains("EDU") ) return ("CMGEDU");
    if(type.contains("GEDU") ) return ("CMGEDU");
    if(type.contains("FBAES") ) return ("EST");  // NZ
    if(type.contains("SBEPI") ) return ("EST");  // NZ
    if(type.contains("GNS Science SDP") ) return ("GNSSPD");  // NZ
    if(type.contains("Dlite") ) return ("3Dlite");  // NZ
    if(type.contains("SP3") ) return ("SP3");  // NZ
    if(type.contains("C5C") ) return ("CMG5T");  // KZ
    if(type.contains("SP3") ) return ("SP3");  // NZ
    if(type.contains("FBA1")) return ("FBA11");
    if(type.contains("G3T")) return ("CMG3T");
    if(type.contains("FBA-1")) return ("FBA11");
    if(type.contains("Duke Peter Malin") ) return ("Malin");  // NZ
    if(type.toLowerCase().contains("compact")) return ("TrilliumCompact");
    if(type.indexOf("NQ") == 0) return ("NetQuakes");
    if(type.contains("GT3134")) return "GT3134";
    if(type.equals("SL")) return "SL210";
   Util.prt("decodeSeismometer() failed ch="+channel+" type="+type);
    return "";
  }
 
}
