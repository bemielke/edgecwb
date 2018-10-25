/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.guidb.NsnStation;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author davidketchum
 */
public class MakeQ330TemplateKeyFiles {

  private static final String[] comps = {"Z", "1", "2"};

  public static StringBuilder makeStationSupplementalConfig(TCPStation station, NsnStation nsn, int i) {
    //Util.prt("tcpstation="+station);
    StringBuilder sb = new StringBuilder(10000);

    // Now need to write out the template
    sb.append("# ===================================\n#  ").append(station.getQ330Stations()[i]).
            append("\n#=================================\n");
    sb.append("# GPS PowerMode=").append(nsn.getGPSConfig()).append(" seedNetwork=").
            append(nsn.getSeedNetwork()).append(" station=").append(station.getTCPStation()).append("\n");
    sb.append("GPS_Power_Mode=").append(nsn.getGPSConfig().equalsIgnoreCase("ON") ? "CONTINUOUS" : "MAX_TIME_OR_PLL_LOCK").append("\n");
    sb.append("GPS_Max_On_Time=30   # Minutes\n\n");
    sb.append("# === Ethernet Port ============= public=").
            append(station.getQ330InetAddresses()[i].toString().substring(1)).append(" private=").
            append(station.getQ330NatAddresses()[i].toString().substring(1)).append(" same=").
            append(station.getQ330InetAddresses()[i].toString().substring(1).equals(station.getQ330NatAddresses()[i].toString().substring(1))).
            append("\n");
    sb.append("Eth_IP_Address=").append(station.getQ330NatAddresses()[i].toString().substring(1)).append("\n");
    //sb.append("Eth_Data_Port=1\n");
    sb.append("Eth_POC_IP_Address=").append(station.getQ330InetAddresses()[i].toString().substring(1)).append("\n");
    sb.append("\n# === Serial Ports ================\n");
    sb.append("S2_Baler_Power_Mode=").append(nsn.getGPSConfig().equalsIgnoreCase("ON") ? "CONTINUOUS" : "DTR_CONTROL").append("\n");
    sb.append("\n# === Data Ports =================\n");
    for (int ii = 0; ii < 4; ii++) {
      sb.append("DP").append(ii + 1).append("_Network=").append(nsn.getSeedNetwork()).append("\nDP").
              append(ii + 1).append("_Station=").append(station.getTCPStation().replaceAll("%", "")).append("\n\n");
    }

    sb.append("# ==== Sensor Control Mapping ======\n");
    /*# <sensor>, <polarity>, <action>
      # sensor:   A, B
      # polarity: HIGH, LOW
      # action:   CALIBRATION, CENTERING, CAPACITIVE, LOCK, UNLOCK, AUX1, AUX2
      #
      # If there is not line for a definition, the existing definition in
      # the Q330 will be retained
      #
      # If a definition is supplied with no arguments, it will be set
      # to 0 (No action, active-low) in the mapping
      #
      # Examples:
      #Sensor_Control_1 = A, HIGH, CENTERING
      #Sensor_Control_2 =  Lock on CMG3
      #Sensor_Control_3 = Unlock on CMG3 (STS-2 Go to UVW mode if A High, calibration)
      #Sensor_Control_4 = A, HIGH, CALIBRATION
      #Sensor_Control_5 =
      #Sensor_Control_6 =
      #Sensor_Control_7 =
      #Sensor_Control_8 = */

    String seis = nsn.getSeismometerString();
    String massPositions = "Z12";
    if (seis.contains("STS-2")) {
      massPositions = "UVW";
    }
    String enable = nsn.getCalEnable().toUpperCase();
    if (enable.equalsIgnoreCase("SEISMOMETER")) {
      enable = "HIGH";
      if (seis.equals("CMG3T") || seis.equals("NSNCMG5") || seis.equals("CMG3ESP")
              || seis.equals("NSN-3T") || seis.equals("KS54000") || seis.equals("KS36000")) {
        enable = "LOW";
      }
    }
    if (enable.equals("ENABLE ON LOW")) {
      enable = "LOW";
    }
    if (enable.equals("ENABLE ON HIGH")) {
      enable = "HIGH";
    }
    sb.append("# seismometer=").append(seis).append(" massorient=").append(massPositions).
            append(" enable=").append(enable).append("\n");

    if (i == 0) {
      sb.append("Sensor_Control_1 = A, HIGH, CENTERING\n");
      sb.append("Sensor_Control_2 = \n");
      sb.append("Sensor_Control_3 = \n");
      sb.append("Sensor_Control_4 = A, HIGH, CALIBRATION\n");
      sb.append("Sensor_Control_5 = B, ").append(enable).append(", CENTERING\n");
      if (seis.contains("NSN-3T") || seis.contains("CMG3T") || seis.contains("CMG3ESP")) {
        sb.append("Sensor_Control_6 = B, HIGH, LOCK\n");
        sb.append("Sensor_Control_7 = B, HIGH, UNLOCK\n");
      } else {
        sb.append("Sensor_Control_6 = \n");
        sb.append("Sensor_Control_7 = \n");
      }
      sb.append("Sensor_Control_8 = B, ").append(enable).append(", CALIBRATION\n");

    } else if (i == 1) {
      sb.append("Sensor_Control_1 = A, ").append(enable).append(", CENTERING\n");
      sb.append("Sensor_Control_2 = \n");
      sb.append("Sensor_Control_3 = \n");
      sb.append("Sensor_Control_4 = A, ").append(enable).append(", CALIBRATION\n");
      sb.append("Sensor_Control_5 = \n");
      sb.append("Sensor_Control_6 = \n");
      sb.append("Sensor_Control_7 = \n");
      sb.append("Sensor_Control_8 = \n");

    }
    //sb.append("SET the CAL polarities base on seismometer
    /*sb.append("\n# === LCQs ==================\n"+
        "# === LCQs ========================\n"+
        "# LCQ>[dataport],[location],<channel>,[source],[rate],[telemeter]\n"+
        "#\n"+
        "# dataport:  1-4  (if blank, applies to all dataports)\n"+
        "# location:  wildcard expression (if blank, requires that there be no location code)\n"+
        "# channel:   wildcard exprsesion (cannot be blank)\n"+
        "# source:    1-6  (if blank, the current source is used)\n"+
        "# rate:      1, 10, 20, 40, 50, 100, 200 (if blank, the current rate is used)\n"+
        "# telemeter: on/off (if blank, the current state is kept)\n"+
        "#\n"+
        "# EXAMPLES:\n"+
        "\n"+
        "#LCQ > , *, BH?, , 40, on,,\n"+
        "# For all data ports for any location code where the channel name begins \n"+
        "# with BH, use the current source, change rate to 40 Hz, and switch telemetry on,,\n"+
        "\n"+
        "#LCQ > , , BC?, 4, 40, on,,\n"+
        "# For all data ports where the LCQ has no location code and channel name\n"+
        "# begins with BC, use channel 4 as the source, set the rate to 40 Hz, \n"+
        "# and switch telemetry on,,\n"+

        "#LCQ > 1, 10, BHZ, 4, 20, on,,\n"+
        "# For data port 1 where location code is 10 and channel is BHZ,\n"+
        "# use channel 4 as source, set rate to 20 Hz, and switch telemetry on,,\n");
     */
    sb.append("# comps=").append(comps[0]).append(comps[1]).append(comps[2]).append(" cont40=").
            append(nsn.getCont40()).append(" cont20=").append(nsn.getCont20()).append(" AUXAD=").
            append(nsn.hasAUXAD()).append(" sm=").append(nsn.hasLowGain()).append(" hg=").
            append(nsn.hasHighGain()).append(" CH2isBat=").append(nsn.auxCH2IsBattery()).append("\n");
    // Data port 1

    String statusLoc = (i == 0 ? "91" : "92");
    String mainLoc = (i == 0 ? "00" : "10");
    int mainOff = (i == 0 ? 4 : 1);
    sb.append("MsgLog>1,").append(statusLoc).append(",LOG\n");
    sb.append("TimeLog>1,").append(statusLoc).append(",ACE\n");
    sb.append("CfgStream>1,").append(statusLoc).append(",OCF\n");
    for (int ich = 0; ich < 3; ich++) {
      if (nsn.hasHighGain()) {
        sb.append("LCQ>1,").append(mainLoc).append(",BH").append(comps[ich]).
                append(",").append(ich + mainOff).append(",").append(nsn.getCont40() != 0 ? "40" : "20").
                append(",on,").append(mainLoc).append(",\n");
      } else if (nsn.hasLowGain()) {
        sb.append("LCQ>1,20,BN").append(comps[ich]).append(",").
                append(ich + 1).append(",40,on,20,\n");
      }
    }
    for (int ich = 0; ich < 3; ich++) {
      if (nsn.hasHighGain()) {
        sb.append("LCQ>1,").append(mainLoc).append(",LH").
                append(comps[ich]).append(",").append(ich + mainOff).append(",1,on,").
                append(mainLoc).append(",\n");
      }
    }
    for (int ich = 0; ich < 3; ich++) {
      if (nsn.hasHighGain()) {
        sb.append("LCQ>1,").append(mainLoc).append(",VH").append(comps[ich]).
                append(",,,on,").append(mainLoc).append(",\n");
      }
    }
    sb.append("LCQ>1,,").append(i == 0 ? "BC0" : "BC1").append(",").append(i == 0 ? "1" : "4").
            append(",").append(nsn.getCont40() != 0 ? "40" : "20").append(",on,,").
            append(i == 1 ? "BC1" : "").append("\n");
    for (int ich = 0; ich < 3; ich++) {
      sb.append("LCQ>1,").append(mainLoc).append(",VM").append(massPositions.substring(ich, ich + 1)).append(",,,on,,\n");
    }
    if (nsn.hasLowGain() && i == 0) // If it has a lowgain, turn it on
    {
      for (int ich = 0; ich < 3; ich++) {
        sb.append("LCQ>1,20,LN").append(comps[ich]).append(",").append(ich + 1).append(",1,on,,\n");
      }
    }
    if (i != 0) {
      sb.append("LCQ>1,30,LDO,4,1,on,,\n");
    }

    sb.append("LCQ>1,").append(statusLoc).append(",VKI,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>1,").append(statusLoc).append(",VEA,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>1,").append(statusLoc).append(",VEP,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>1,").append(statusLoc).append(",VEC,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>1,").append(statusLoc).append(",ACP,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>1,").append(statusLoc).append(",ACQ,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>1,").append(statusLoc).append(",ACO,,,on,").append(statusLoc).append(",\n");

    // do aux channels if present
    if (nsn.hasAUXAD() && i == 0) {
      sb.append("LCQ>1,90,VE1,,,on,,\n");
      if (nsn.auxCH2IsBattery()) {
        sb.append("LCQ>1,90,VE2,,,on,,\n");
      } else {
        sb.append("LCQ>1,00,VKV,,,on,,\n");
      }
      sb.append("LCQ>1,20,VKV,,,on,,\n");
      sb.append("LCQ>1,90,VKO,,,on,,\n");
      sb.append("LCQ>1,00,VII,,,on,,\n");
      sb.append("LCQ>1,20,VII,,,on,,\n");
      sb.append("LCQ>1,90,VEK,,,on,,\n");

    }

    // dataport 4
    sb.append("MsgLog>4,").append(statusLoc).append(",LOG\n");
    sb.append("TimeLog>4,").append(statusLoc).append(",ACE\n");
    sb.append("CfgStream>4,").append(statusLoc).append(",OCF\n");
    for (int ich = 0; ich < 3; ich++) {
      if (nsn.hasHighGain()) {
        sb.append("LCQ>4,").append(mainLoc).append(",BH").append(comps[ich]).
                append(",").append(ich + mainOff).append(",").append(nsn.getCont40() != 0 ? "40" : "20").
                append(",on,").append(mainLoc).append(",\n");
      } else if (nsn.hasLowGain()) {
        sb.append("LCQ>4,20,BN").append(comps[ich]).
                append(",").append(ich + 1).append(",40,on,20,\n");
      }
    }

    for (int ich = 0; ich < 3; ich++) {
      if (nsn.hasHighGain()) {
        sb.append("LCQ>4,").append(mainLoc).append(",LH").append(comps[ich]).
                append(",").append(ich + mainOff).append(",1,on,").append(mainLoc).append(",\n");
      }
    }
    for (int ich = 0; ich < 3; ich++) {
      if (nsn.hasHighGain()) {
        sb.append("LCQ>4,").append(mainLoc).append(",VH").append(comps[ich]).
                append(",,,on,").append(mainLoc).append(",\n");
      }
    }
    if (nsn.hasLowGain() && i == 0) {
      for (int ich = 0; ich < 3; ich++) {
        sb.append("LCQ>4,20,HN").append(comps[ich]).append(",").append(ich + 1).append(",200,on,,\n");
        sb.append("LCQ>4,20,LN").append(comps[ich]).append(",").append(ich + 1).append(",1,on,,\n");
      }
    }
    sb.append("LCQ>4,,").append(i == 0 ? "BC0" : "BC1").append(",").append(i == 0 ? "1" : "4").
            append(",").append(nsn.getCont40() != 0 ? "40" : "20").append(",on,,").append(i == 1 ? "BC1" : "").append("\n");
    for (int ich = 0; ich < 3; ich++) {
      sb.append("LCQ>4,").append(mainLoc).append(",VM").append(massPositions.substring(ich, ich + 1)).append(",,,on,,\n");
    }

    if (i != 0) {
      sb.append("LCQ>4,30,LDO,4,1,on,,\n");
    }
    sb.append("LCQ>4,").append(statusLoc).append(",VEA,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>4,").append(statusLoc).append(",VKI,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>4,").append(statusLoc).append(",VEP,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>4,").append(statusLoc).append(",VEC,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>4,").append(statusLoc).append(",ACP,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>4,").append(statusLoc).append(",ACQ,,,on,").append(statusLoc).append(",\n");
    sb.append("LCQ>4,").append(statusLoc).append(",ACO,,,on,").append(statusLoc).append(",\n");

    // do aux channels if present
    if (nsn.hasAUXAD() && i == 0) {
      sb.append("LCQ>4,01,VEB,,,on,,\n");
      if (nsn.auxCH2IsBattery()) {
        sb.append("LCQ>4,02,VEB,,,on,,\n");
      } else {
        sb.append("LCQ>4,01,VKV,,,on,,\n");
      }
      sb.append("LCQ>4,02,VKV,,,on,,\n");
      sb.append("LCQ>4,01,VKO,,,on,,\n");
      sb.append("LCQ>4,01,VII,,,on,,\n");
      sb.append("LCQ>4,02,VII,,,on,,\n");
      sb.append("LCQ>4,01,VEK,,,on,,\n");

    }

    /*
     *     if(i == 0) {
      // Data port 1
      sb.append("MsgLog>1,91,LOG\n");
      sb.append("TimeLog>1,91,ACE\n");
      sb.append("CfgStream>1,91,OCF\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,00,BH"+comps[ich]+
                ","+(ich+4)+","+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,00,LH"+comps[ich]+","+(ich+4)+",1,on,,\n");
        sb.append("LCQ>1,,BC0,1,"+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,00,VM"+massPositions.substring(ich,ich+1)+",,,on,,\n");
      if(nsn.hasLowGain())      // If it has a lowgain, turn it on
        for(int ich=0; ich<3; ich++)
          sb.append("LCQ>1,20,LN"+comps[ich]+","+(ich+1)+",1,on,,\n");
      sb.append("LCQ>1,91,VKI,,,on,,\n");
      sb.append("LCQ>1,91,VEA,,,on,,\n");
      sb.append("LCQ>1,91,VEP,,,on,,\n");
      sb.append("LCQ>1,91,VEC,,,on,,\n");
      sb.append("LCQ>1,91,ACP,,,on,,\n");
      sb.append("LCQ>1,91,ACQ,,,on,,\n");
      sb.append("LCQ>1,91,ACO,,,on,,\n");

      // do aux channels if present
      if(nsn.hasAUXAD()) {
        sb.append("LCQ>1,01,VEB,,,on,,\n");
        if(nsn.auxCH2IsBattery()) sb.append("LCQ>1,02,VEB,,,on,,\n");
        else sb.append("LCQ>1,01,VKV,,,on,,\n");
        sb.append("LCQ>1,02,VKV,,,on,,\n");
        sb.append("LCQ>1,01,VKO,,,on,,\n");
        sb.append("LCQ>1,01,VII,,,on,,\n");
        sb.append("LCQ>1,02,VII,,,on,,\n");
        sb.append("LCQ>1,01,VEK,,,on,,\n");

      }

      // dataport 4
      sb.append("MsgLog>4,91,LOG\n");
      sb.append("TimeLog>4,91,ACE\n");
      sb.append("CfgStream>4,91,OCF\n");
       for(int ich=0; ich<3; ich++)
        sb.append("LCQ>4,00,BH"+comps[ich]+
                ","+(ich+4)+","+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>4,00,LH"+comps[ich]+","+(ich+4)+",1,on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,00,VM"+massPositions.substring(ich,ich+1)+",,,on,,\n");
      if(nsn.hasLowGain())
        for(int ich=0; ich<3; ich++) {
          sb.append("LCQ>4,20,HN"+comps[ich]+","+(ich+4)+",200,on,,\n");
          sb.append("LCQ>4,20,LN"+comps[ich]+","+(ich+1)+",1,on,,\n");
        }
      sb.append("LCQ>4,,BC0,1,"+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>4,00,VM"+massPositions.substring(ich,ich+1)+",,,on,,\n");
      sb.append("LCQ>4,91,VEA,,,on,,\n");
      sb.append("LCQ>4,91,VKI,,,on,,\n");
      sb.append("LCQ>4,91,VEP,,,on,,\n");
      sb.append("LCQ>4,91,VEC,,,on,,\n");
      sb.append("LCQ>4,91,ACP,,,on,,\n");
      sb.append("LCQ>4,91,ACQ,,,on,,\n");
      sb.append("LCQ>4,91,ACO,,,on,,\n");

      // do aux channels if present
      if(nsn.hasAUXAD()) {
        sb.append("LCQ>4,01,VEB,,,on,,\n");
        if(nsn.auxCH2IsBattery()) sb.append("LCQ>4,02,VEB,,,on,,\n");
        else sb.append("LCQ>4,01,VKV,,,on,,\n");
        sb.append("LCQ>4,02,VKV,,,on,,\n");
        sb.append("LCQ>4,01,VKO,,,on,,\n");
        sb.append("LCQ>4,01,VII,,,on,,\n");
        sb.append("LCQ>4,02,VII,,,on,,\n");
        sb.append("LCQ>4,01,VEK,,,on,,\n");

      }

    }
    else if(i == 1) {   // This must be the HR
      // Data port 1
      sb.append("MsgLog>1,92,LOG\n");
      sb.append("TimeLog>1,92,ACE\n");
      sb.append("CfgStream>1,92,OCF\n");
       for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,10,BH"+comps[ich]+
                ","+(ich+1)+","+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,10,LH"+comps[ich]+","+(ich+1)+",1,on,,\n");
      sb.append("LCQ>1,,BC1,1,"+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>1,10,VM"+massPositions.substring(ich,ich+1)+",,,on,,\n");
      sb.append("LCQ>4,,LDO,4,1,on,,\n");
      // SOH channels
      sb.append("LCQ>1,92,VEA,,,on,,\n");
      sb.append("LCQ>1,92,VKI,,,on,,\n");
      sb.append("LCQ>1,92,VEP,,,on,,\n");
      sb.append("LCQ>1,92,VEC,,,on,,\n");
      sb.append("LCQ>1,92,ACP,,,on,,\n");
      sb.append("LCQ>1,92,ACQ,,,on,,\n");
      sb.append("LCQ>1,92,ACO,,,on,,\n");

            // do aux channels if present
      if(nsn.hasAUXAD()) {
        sb.append("LCQ>1,01,VEB,,,on,,\n");
        if(nsn.auxCH2IsBattery()) sb.append("LCQ>1,02,VEB,,,on,,\n");
        else sb.append("LCQ>1,01,VKV,,,on,,\n");
        sb.append("LCQ>1,02,VKV,,,on,,\n");
        sb.append("LCQ>1,01,VKO,,,on,,\n");
        sb.append("LCQ>1,01,VII,,,on,,\n");
        sb.append("LCQ>1,02,VII,,,on,,\n");
        sb.append("LCQ>1,01,VEK,,,on,,\n");
      }

      // dataport 4
      sb.append("MsgLog>4,92,LOG\n");
      sb.append("TimeLog>4,92,ACE\n");
      sb.append("CfgStream>4,92,OCF\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>4,10,BH"+comps[ich]+
                ","+(ich+1)+","+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>4,10,LH"+comps[ich]+","+(ich+1)+",1,on,,\n");
      sb.append("LCQ>4,,BC1,1,"+(nsn.getCont40() != 0? "40":"20")+",on,,\n");
      for(int ich=0; ich<3; ich++)
        sb.append("LCQ>4,10,VM"+massPositions.substring(ich,ich+1)+",,,on,,\n");
      // SOH channels
      sb.append("LCQ>4,92,VEA,,,on,,\n");
      sb.append("LCQ>4,92,VKI,,,on,,\n");
      sb.append("LCQ>4,92,VEP,,,on,,\n");
      sb.append("LCQ>4,92,VEC,,,on,,\n");
      sb.append("LCQ>4,92,ACP,,,on,,\n");
      sb.append("LCQ>4,92,ACQ,,,on,,\n");
      sb.append("LCQ>4,92,ACO,,,on,,\n");
    }
     */
    sb.append("# <End-of-Config>\n");
    return sb;
  }

  public static void main(String[] args) {
    String[] srchans = {"BHZ00", "BH100", "BH200", "LHZ00", "LH100", "LH200", "LNZ20", "LN120", "LN220", "HNZ20", "HN120", "HN220", "VHZ00", "VH100", "VH200"};
    String[] hrchans = {"BHZ10", "BH110", "BH210", "LHZ10", "LH110", "LH210", "LDO30"};
    Util.init("edge.prop");
    boolean makeChans = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-makechans")) {
        makeChans = true;
      }
      if (args[i].equals("-mysql")) {
        Util.setProperty("MySQLServer", args[i + 1]);
      }
    }
    long sendto = 0;
    long flags = 0;
    long hydraflags = 0;
    long mdsflags = 0;
    String gaptype = "";
    double rate = 0.;
    int expected = 0;
    long links = 0;
    int commgroupid = 0;
    int operatorid = 0;
    int protocolid = 0;
    int nsnnet = 0;
    int nsnnode = 0;
    int nsnchan = 0;
    Util.setProcess("MakeQ330TemplateKeyFiles");
    User user = new User("dkt");
    Util.setNoInteractive(true);
    DBConnectionThread dbanss = null;
    Statement stmt2 = null;
    try {
      dbanss = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss", true, false, "anss", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("anss")) {
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              if (!DBConnectionThread.waitForConnection("anss")) {
                Util.prta("Could not connect to MySQL abort run");
              }
            }
          }
        }
      }
      UC.setConnection(DBConnectionThread.getConnection("anss"));
      stmt2 = dbanss.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
    } catch (SQLException e) {
      Util.prt("Could not create stmt2 e=" + e);
      e.printStackTrace();
      System.exit(1);
    } catch (InstantiationException e) {
      Util.prt("instantiation exception e=" + e.getMessage());
      System.exit(1);
    }
    try {
      ResultSet rs = dbanss.executeQuery("SELECT * FROM anss.tcpstation WHERE q330!='' ORDER BY tcpstation");
      while (rs.next()) {

        TCPStation station = new TCPStation(rs);
        if (station.getTCPStation().contains("TEST")) {
          Util.prt(" go TEST");
        }
        NsnStation nsn;
        for (int i = 0; i < station.getNQ330s(); i++) {
          ResultSet rs2 = stmt2.executeQuery("SELECT * FROM anss.nsnstation WHERE nsnstation='"
                  + station.getQ330Stations()[0] + (i > 0 ? ":" + ((char) ('A' + i - 1)) : "") + "'");
          if (rs2.next()) {
            nsn = new NsnStation(rs2);
            //Util.prt("nsn="+nsn);
          } else {
            Util.prt("(***** error did not find an DAS Parms for " + station.getQ330Stations()[i]);
            System.err.println("(***** error did not find an DAS Parms for " + station.getQ330Stations()[i]);
            continue;
          }
          rs2.close();
          StringBuilder sb = makeStationSupplementalConfig(station, nsn, i);
          try {
            try (PrintStream out = new PrintStream("Python/Stations/supplemental.config." + station.getQ330Stations()[i])) {
              rs2 = stmt2.executeQuery("SELECT * FROM anss.q330config WHERE station='" + station.getQ330Stations()[i] + "' ORDER BY timestamp");
              boolean doUpdate = true;
              if (rs2.next()) {
                String config = rs2.getString("config");
                if (config.equals(sb.toString())) {
                  doUpdate = false;
                }
              }
              rs2.close();
              if (doUpdate) {
                stmt2.executeUpdate("INSERT INTO anss.q330config (station,timestamp,config,updated,created_by,created) VALUES ('"
                        + station.getQ330Stations()[i] + "',now(),'" + sb.toString() + "',now(), 0, now())");
              }
              if (makeChans) {
                if (!station.getSeedNetwork().equals("US")) {
                  continue;
                }
                String seedname = ("US" + station.getQ330Stations()[0] + "    ").substring(0, 7);
                String[] chans = (i == 0 ? srchans : hrchans);
                for (String chan : chans) {
                  rs2 = stmt2.executeQuery("SELECT * FROM edge.channel where channel='" + seedname.substring(0, 7) + chan + "'");
                  if (rs2.next()) {
                    int id = rs2.getInt("id");
                    rs2.close();
                    Util.prt("Found chan=" + seedname + chan);
                    String oldChan = chan.replaceAll("HN", "HL").replaceAll("1", "N").replaceAll("2", "E").substring(0, 3) + (i == 0 ? "" : "HR");
                    rs2 = stmt2.executeQuery("SELECT * FROM edge.channel where CHANNEL='" + seedname.substring(0, 7) + oldChan + "'");
                    if (rs2.next()) {
                      sendto = rs2.getLong("sendto");
                      flags = rs2.getLong("flags");
                      hydraflags = rs2.getLong("hydraflags");
                      mdsflags = rs2.getLong("mdsflags");
                      rate = rs2.getDouble("rate");
                      expected = rs2.getInt("expected");
                      gaptype = rs2.getString("gaptype");
                      links = rs2.getLong("links");
                      commgroupid = rs2.getInt("commgroupid");
                      operatorid = rs2.getInt("operatorid");
                      protocolid = rs2.getInt("protocolid");
                      nsnnet = rs2.getInt("nsnnet");
                      nsnnode = rs2.getInt("nsnnode");
                      nsnchan = rs2.getInt("nsnchan");
                    } else {
                      Util.prt("Old channel not found " + oldChan);
                    }
                    String s = "UPDATE edge.channel SET sendto=" + sendto
                            + ",flags=" + flags + ",links=" + links + ",commgroupid=" + commgroupid
                            + ",operatorid=" + operatorid + ",protocolid=" + protocolid + ",rate=" + rate + ","
                            + "nsnnet=" + nsnnet + ",nsnnode=" + nsnnode + ",nsnchan=" + nsnchan + ",expected=" + expected
                            + ",gaptype='" + gaptype + "',lastdata='2011-03-20',mdsflags=" + mdsflags
                            + ",hydraflags=" + hydraflags + ",updated=now() WHERE id=" + id;
                    Util.prt(s);
                    stmt2.executeUpdate(s);
                  } else {
                    rs2.close();
                    String oldChan = chan.replaceAll("HN", "HL").replaceAll("1", "N").replaceAll("2", "E").substring(0, 3) + (i == 0 ? "" : "HR");
                    rs2 = stmt2.executeQuery("SELECT * FROM edge.channel where CHANNEL='" + seedname.substring(0, 7) + oldChan + "'");
                    if (rs2.next()) {
                      sendto = rs2.getLong("sendto");
                      flags = rs2.getLong("flags");
                      hydraflags = rs2.getLong("hydraflags");
                      mdsflags = rs2.getLong("mdsflags");
                      rate = rs2.getDouble("rate");
                      expected = rs2.getInt("expected");
                      gaptype = rs2.getString("gaptype");
                      links = rs2.getLong("links");
                      commgroupid = rs2.getInt("commgroupid");
                      operatorid = rs2.getInt("operatorid");
                      protocolid = rs2.getInt("protocolid");
                      nsnnet = rs2.getInt("nsnnet");
                      nsnnode = rs2.getInt("nsnnode");
                      nsnchan = rs2.getInt("nsnchan");
                    } else {
                      Util.prt("Old channel not found " + oldChan);
                    }
                    String s = "INSERT INTO edge.channel (channel,sendto,flags,links,commgroupid,operatorid,protocolid,"
                            + "rate,nsnnet,nsnnode,nsnchan,expected,gaptype,lastdata,mdsflags,hydraflags,updated,created_by,created) VALUES ('"
                            + seedname.substring(0, 7) + chan + "'," + sendto + "," + flags + "," + links + "," + commgroupid + "," + operatorid + "," + protocolid + "," + rate + "," + nsnnet + "," + nsnnode + "," + nsnchan + "," + expected + ",'" + gaptype + "','2011-03-20'," + mdsflags + "," + hydraflags + ",now(),1,now())";
                    Util.prt(s);
                    stmt2.executeUpdate(s);
                  }
                  rs2.close();
                }
              }

              out.print(sb.toString());
            }
          } catch (IOException e) {
            Util.prt("**** could not write config file  for " + station.getQ330Stations()[i] + " e=" + e);
          }
        }

        File autocal = new File("Python/Stations/autocal.config." + station.getTCPStation());
        if (!autocal.exists()) {
          try (PrintStream auto = new PrintStream("Python/Stations/autocal.config." + station.getTCPStation())) {
            auto.println("Empty autocal");
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "SQL getting tcpstations");
      e.printStackTrace();
    } catch (IOException e) {
      Util.prt("Got error trying to write out Q330 config files e=" + e);
      e.printStackTrace();
    }
    Util.prta("Done");
    System.exit(0);
  }
}
