/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;
/*
 * NsnStation.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 * This NsnStation template for creating a database database object.  It is not
 * really needed by the NsnStationPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the database record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *
 * The NsnStation should be replaced with capitalized version (class name) for the 
 * file.  nsnstation should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeNsnStation(list of data args) to set the
 * local variables to the value.  The result set just uses the 
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 * 
 * Notes on Enums :
 ** data class should have code like :
 * import java.util.ArrayList;          /// Import for Enum manipulation
 *   .
 *  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *       .
 *       .
 *    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 
 */


//import java.util.Comparator;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.gui.UC;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.FormUDP;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
//import gov.usgs.anss.net.*;

public class NsnStation     //implements Comparator
{
  static ArrayList qtypeEnum;
  static ArrayList stypeEnum;       // Station types
  static ArrayList seisEnum;            /// Seismometer ENUM
  /** Creates a new instance of NsnStation */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String nsnstation;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // All fields of file go here
  //  double longitude
  int nodeid, netid, startTbase, cont40, cont20, expbias, stationType, lowgain, highgain;
  int dac480, attn480, seismometer, quanterraType, hasConsole;
  int ncoin, ntrigfreq, freqtrig, smtriglevel;
  int auxad, auxADCH2Battery;
  double sntrig,hfsnr;
  int dasconfigID;
  String seedNetwork;
  String gpsConfig;
  String horizontals;
  String overrideTemplate;
  String calEnable;

  //int maxtbase, nheli, gainheli, gainheli2, gainheli3, dacchn, dacchn2, dacchn3;
  //int dacchn, filter, lpfilter, daclpchn, daclpgain, localloop;
  //double lprate, rate, bhzrate;
  
  // Put in correct detail constructor here.  Use makeNsnStation() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public NsnStation(ResultSet rs) throws SQLException {
    makeNsnStation(rs.getInt("ID"), rs.getString("nsnstation"),
                            // ,rs.getDouble(longitude)
       rs.getInt("nodeid"), rs.getInt("netid"), rs.getInt("startTbase"), 
       FUtil.isTrue(rs.getString("cont40")), 
       FUtil.isTrue(rs.getString("cont20")),rs.getInt("expbias"),
       rs.getString("stationType"), FUtil.isTrue(rs.getString("lowgain")), 
       FUtil.isTrue(rs.getString("Highgain")),
       rs.getInt("smtriglevel"),
       rs.getInt("dac480"), rs.getInt("attn480"), rs.getString("seismometer"), 
       rs.getString("quanterratype"), FUtil.isTrue(rs.getString("hasConsole")),
       rs.getInt("nsntrigid"),
       rs.getInt("dasconfigid"),rs.getString("seednetwork"), rs.getString("gpsconfig"),
       rs.getString("horizontals"), rs.getString("overridetemplate"),rs.getString("calenable"),
       rs.getInt("auxad"), rs.getInt("aux2isbattery")
    );
  }
  
  // Detail Constructor, match set up with makeNsnStation(), this argument list should be
  // the same as for the result set builder as both use makeNsnStation()
  public NsnStation(int inID, String loc ,  //, double lon
        int nd, int nt, int tbase, int c40, int c20, int exp,
        String st_type, int lgain, int hgain, int smtrig, int d480, int a480,
        String  seis,
        String qtype, int has_con, int trigID, int dasID, String net, String gps,
        String horiz, String overTemplate, String calenb, int aux, int aux2bat
    ) {
    makeNsnStation(inID, loc ,    //, lon
        nd, nt, tbase, c40, c20, exp,
        st_type, lgain, hgain, smtrig, d480,a480,seis,
        qtype, has_con, trigID, dasID, net, gps, horiz, overTemplate,calenb,
        aux, aux2bat
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  private void makeNsnStation(int inID, String loc ,   //, double lon
       int nd, int nt, int tbase, int c40, int c20, int exp,
        String st_type, int lgain, int hgain, int smtrig, int d480, int a480, 
        String seis,
        String qtype, int has_con, int trigID, int dasID, String net, String gps,
        String horiz, String overTemplate, String calenb, int aux, int aux2bat ) {
    ID = inID;  nsnstation=loc;     // longitude = lon
    // Put asssignments to all fields from arguments here
    if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("anss"), "nsnstation","quanterraType");
    if(stypeEnum == null) stypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("anss"),"nsnstation", "stationType");
    if(seisEnum == null) seisEnum=FUtil.getEnum(DBConnectionThread.getConnection("anss"),"nsnstation", "seismometer");
    
    netid=nt; nodeid=nd; startTbase=tbase; expbias=exp; 
    lowgain=lgain; highgain = hgain; smtriglevel=smtrig; dac480=d480; attn480=a480;
    cont20=c20; cont40=c40; hasConsole=has_con;
    seismometer = FUtil.enumStringToInt(seisEnum, seis);
    stationType = FUtil.enumStringToInt(stypeEnum, st_type);
    quanterraType = FUtil.enumStringToInt(qtypeEnum, qtype);
    auxad = aux;
    auxADCH2Battery = aux2bat;
    if(trigID == 0) {
      sntrig = 3.5;
      ncoin = 1;
      ntrigfreq = 4;
      freqtrig = 0x1e;
      hfsnr=999930.;
    } else {
        Util.prt("Something other that ID=0 for NSN triggers.  Not implemented="+trigID);
    }
    dasconfigID=dasID; seedNetwork=net;gpsConfig=gps; horizontals=horiz; overrideTemplate=overTemplate; calEnable=calenb;
   
    
  }
  public String toString2() { return nsnstation;}
  @Override
  public String toString() { return nsnstation;}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public String getNsnStation() {return nsnstation;}
  public int getQuanterraType(){return quanterraType;}
  public int getSeismometer() {return seismometer;}
  public String getQuanterraTypeString() {return qtypeEnum.get(quanterraType).toString();}
  public String getSeismometerString() {return seisEnum.get(seismometer).toString();}
  public int getNodeID() {return nodeid;}
  public int getNetID() {return netid;}
  public int getDasconfigID() {return dasconfigID;}
  public boolean hasLowGain() {return (lowgain != 0);}
  public boolean hasHighGain() {return (highgain != 0);}
  public int getCont40() {return cont40;}
  public int getCont20() {return cont20;}

  /** a member of the enum On or Cycled
   *
   * @return either On or Cycled
   */
  public String getGPSConfig() {return gpsConfig;}
  public String getSeedNetwork() {return seedNetwork;}
  public String getHorizontals() {return horizontals;}
  public String getOverrideTemplate() {return overrideTemplate;}
  /** get calenable setting
   *
   * @return Seismometer, Enable on Low or Enable on High
   */
  public String getCalEnable() {return calEnable;}
  public boolean hasAUXAD() {return (auxad != 0);}
  public boolean auxCH2IsBattery() {return (auxADCH2Battery != 0);}
  
  // All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((NsnStation) a).getNsnStation().compareTo(((NsnStation) b).getNsnStation());
    }
    public boolean equals(Object a, Object b) {
      if( ((NsnStation)a).getNsnStation().equals( ((NsnStation) b).getNsnStation())) return true;
      return false;
    }
//  }*/
   public void sendForm(String newname) {
    String [] hosts = UC.getFormHosts();
    for(int j=0; j<hosts.length; j++) {
      FormUDP u = new FormUDP(2098, hosts[j], newname);
      Util.prt("SendForm(2098) on " + nsnstation + " to host " + hosts[j]);
      u.append("nodeid", nodeid);
      u.append("netid", netid);
      u.append("startTbase", startTbase);
      u.append("expbias", expbias);
      u.append("stationType", stationType);
      u.append("dac480", dac480);
      u.append("attn480", attn480);
      u.append("seismometer", seismometer-1);
      u.append("quanterra_Type", quanterraType-1);


      u.append("cont40", (cont40 == 1 ? 1 : 0) );
      u.append("cont20", (cont20 == 1 ? 1 : 0) );
      u.append("lowgain", (lowgain == 1 ? 1 : 0) );
      u.append("highgain", (highgain == 1 ? 1 : 0) );
      u.append("has_console", (hasConsole == 1 ? 1 : 0) );

      // trigger based parameters
      u.append("sntrig", sntrig);
      u.append("ncoin", ncoin);
      u.append("ntrigfreq", ntrigfreq);
      u.append("hfsnr", hfsnr);
      int mask=1;

      for(int i=0; i<ntrigfreq+4; i++) {
        if( (mask<<i & freqtrig) != 0) u.append("freqtrig"+i, i);
        else u.append("freqtrig"+i, 0);
      }
      u.send();
      u.reset(); 
    }
  } 
}
