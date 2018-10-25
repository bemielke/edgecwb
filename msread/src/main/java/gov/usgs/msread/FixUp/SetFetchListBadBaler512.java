/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * AMMToAAM.java
 *
 * Created on October 30, 2007, 3:23 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.msread.FixUp;
import gov.usgs.anss.mysql.MySQLConnectionThreadOld;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * FixBadBaler512.java
 *
 * Created on October 25, 2007, 12:00 PM
 * This class was used to fix up a problem when a baler was misconfigured at AAM to be
 * station AMM.  All rerequests put AMM data in the Edge/CWB and the data needs to be renamed
 * so that it will show up.  The index records are bit harder!
 *
 * @author davidketchum
 */
public class SetFetchListBadBaler512 {
   public static void main( String [] args) {
     Util.setModeGMT();
    MySQLConnectionThreadOld mysql;
    // To get here we are in "rawMode
    mysql = MySQLConnectionThreadOld.getThread("edge");
    if(mysql == null) {
      try { 
        mysql = new MySQLConnectionThreadOld("gacqdb","fetcher","vdl","nop240",
            true,false,"edge");
        MySQLConnectionThreadOld.waitForConnection("edge");
      }
      catch(InstantiationException e) {
        Util.prta("InstantiationException opening edge database e="+e.getMessage());
        System.exit(1);
      }
    }
    try {
      String [] arglist = new String[8];
      arglist[0]="-s"; arglist[2]="-b"; arglist[4]="-d"; arglist[6]="-t"; arglist[7]="null";
      // Use this statement for modifications
      Statement stmt = mysql.getConnection("edge").createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
          ResultSet.CONCUR_UPDATABLE);
      ResultSet rs = mysql.executeQuery("SELECT ID,start,start_ms,seedname,duration,updated,type,status FROM fetcher.fetchlist "+
          //"WHERE FIELD(right(left(seedname,7),5),'AAM','ANWB','BBGH','BCIP','GOGA','GRGR','ISCO','JFWS','KSU1','HLID','OGNE','TGUH','GTBY')>0 "+
          "WHERE " +
          "(left(seedname,7)='USAAM' and start>'2007-09-13') OR "+
          "(left(seedname,7)='CUBBGH' and start>'2006-07-14') OR "+
          "(left(seedname,7)='CUBCIP' and start>'2006-12-05') OR "+
          "(left(seedname,7)='USGOGA' and start>'2006-07-09') OR "+
          "(left(seedname,7)='CUGRGR' and start>'2006-12-09') OR "+
          "(left(seedname,7)='USHLID' and start>'2007-10-19') OR "+
          "(left(seedname,7)='USISCO' and start>'2007-04-19') OR "+
          "(left(seedname,7)='USJFWS' and start>'2006-07-22') OR "+
          "(left(seedname,7)='USKSU1' and start>'2006-06-01') OR "+
          "(left(seedname,7)='USOGNE' and start>'2007-06-11') OR "+
          "(left(seedname,7)='CUTGUH' and start>'2006-09-14') OR "+
          "(left(seedname,7)='CUANWB' and start>'2007-04-25') OR "+
          "(left(seedname,7)='CUGTBY' and start>'2006-12-06') "+
          "ORDER BY start");
      int nok=0;
      int nreset=0;
      int completedOK=0;
      int completedReset=0;
      while(rs.next()) {
        double covered=0.;
        long start=rs.getTimestamp("start").getTime()+rs.getInt("start_ms");
        long end = start + (long) (rs.getDouble("duration")*1000.);
        arglist[1]=(rs.getString("seedname")+"     ").substring(0,12);
        arglist[5]=rs.getDouble("duration")+"";
        arglist[3]=rs.getTimestamp("start").toString().substring(0,19);
        Util.prta("query "+arglist[0]+" "+arglist[1]+" "+arglist[2]+" "+arglist[3]+" "+
            arglist[4]+" "+arglist[5]+" "+arglist[6]+" "+arglist[7]);
        ArrayList<ArrayList<MiniSeed>> mss = EdgeQueryClient.query(arglist);
        int pct=0;
        if(mss != null ) {        // was something returned
          for(int i=0; i<mss.size(); i++) {
            ArrayList<MiniSeed> blks= mss.get(i);
            for(int j=0; j<blks.size(); j++) {
              MiniSeed ms = blks.get(j);
              long st = ms.getTimeInMillis();
              long en = ms.getNextExpectedTimeInMillis();
              if(st <= start ) {
                if(en > start) {
                  if(en > end) {covered += (end-start)/1000.; start = end;}
                  else { covered += (en - start)/1000.; start = ms.getNextExpectedTimeInMillis();}
                  start = st;
                } // else data is entirely before the gap
              }
              else {
                if(start < end) {
                  if(en > end) { covered += (end - st)/1000.; start=end;}
                  else {covered += (en - st)/1000.; start = ms.getNextExpectedTimeInMillis();}
                } //else this block is after the gap
              }
            }
          }
          pct = (int) (covered*100./rs.getDouble("duration"));
          Util.prt("covered="+covered+" of "+rs.getDouble("duration")+" "+pct+" %");
        }
        if(pct < 85) {
          Util.prt("     **** reenable "+rs.getString("seedname")+" "+rs.getInt("ID")+" "+rs.getTimestamp("start")+
              " for "+rs.getDouble("duration")+" "+rs.getString("status"));
          stmt.executeUpdate("UPDATE fetcher.fetchlist set status='open' WHERE ID="+rs.getInt("ID"));
          if(rs.getString("status").equals("completed")) completedReset++;
          nreset++;
        }
        else {
          Util.prt("Do not need to refetch pct="+pct+" "+rs.getString("seedname")+" "+rs.getTimestamp("start")+
              " for "+rs.getDouble("duration")+" "+rs.getString("status"));
          if(rs.getString("status").equals("completed")) completedOK++;
          nok++;
        }
      }
      Util.prt("Nok="+nok+" #reset="+nreset+" completeOK="+completedOK+" completedReset="+completedReset);
      nok++;
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"SQLError getting list or updating open");
    }
    System.exit(1);   // We have done the holdings
  }
  
   

}
