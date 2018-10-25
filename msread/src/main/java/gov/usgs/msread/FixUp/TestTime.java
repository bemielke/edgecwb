/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.msread.FixUp;
import java.sql.*;
import gov.usgs.anss.util.*;
import java.util.GregorianCalendar;
/**
 *
 * @author davidketchum
 */
public class TestTime {
public static void main(String [] args) {
  Util.setModeGMT();
  Util.prt("Starting");
  Util.setNoInteractive(true);
  try {
    new JDBConnectionOld("localhost","gf","dck","karen",false,"test");
    Connection C = JDBConnectionOld.getConnection("test");
    Statement stmt = C.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
    ResultSet rs = stmt.executeQuery("SELECT * FROM time");
    rs.next();
    java.sql.Date dt = rs.getDate("date");
    java.sql.Time tm = rs.getTime("time");
    java.sql.Date dttm = rs.getDate("datetime");
    java.sql.Time dtt = rs.getTime("datetime");
    java.sql.Timestamp ts = (Timestamp) rs.getObject("datetime");
    Util.prt("dt = "+dt+" "+dt.getTime()+" tim="+tm+" "+tm.getTime()+" dttm="+dttm+" "+dttm.getTime()+" dttt="+dtt.getTime()+" ts="+ts.toString());
    java.sql.Timestamp ts2 = rs.getTimestamp("datetime");
    Util.prt("ts2="+ts2);
    GregorianCalendar gdt = new GregorianCalendar();
    gdt.setTime(dt);
    Util.prt("Dgt.setTime(dt)="+Util.ascdate(gdt)+" "+Util.asctime2(gdt));
    gdt.setTimeInMillis(dt.getTime());
    
    Util.prt("Dgt.setTime(dt.getTime())="+Util.ascdate(gdt)+" "+Util.asctime2(gdt));
    gdt.setTime(dttm);
    Util.prt("Dgt.setTime(dttm)="+Util.ascdate(gdt)+" "+Util.asctime2(gdt));
    gdt.setTimeInMillis(dttm.getTime());
    Util.prt("Dgt.setTime(dttm.getTime())="+Util.ascdate(gdt)+" "+Util.asctime2(gdt));
    gdt.setTimeInMillis(dttm.getTime()+dtt.getTime());
    Util.prt("Dgt.setTime(dttm.getTime()+Dttt.getTime())="+Util.ascdate(gdt)+" "+Util.asctime2(gdt));
    ts2.setTime(ts2.getTime()+60000);
    rs.updateTimestamp("datetime", ts2);
    rs.updateRow();
    rs = stmt.executeQuery("SELECT * FROM time");
    rs.next();
    ts2 = rs.getTimestamp("datetime");
    Util.prt("ts2 readback = "+ts2+" "+ts2.getTime());
    ts2.setTime(-ts2.getTime());
    rs.updateTimestamp("datetime", ts2);
    rs.updateRow();
    rs = stmt.executeQuery("SELECT * FROM time");
    rs.next();
    ts2 = rs.getTimestamp("datetime");
    Util.prt("-ts2 readback = "+ts2+" "+ts2.getTime());

    ts2.setTime(ts2.getTime()-8640000000000L);
    rs.updateTimestamp("datetime", ts2);
    rs.updateRow();
    rs = stmt.executeQuery("SELECT * FROM time");
    rs.next();
    ts2 = rs.getTimestamp("datetime");
    Util.prt("-ts2-100000 days readback = "+ts2+" "+ts2.getTime());
    ts2.setTime(-ts2.getTime());
    rs.updateTimestamp("datetime", ts2);
    rs.updateRow();
    rs = stmt.executeQuery("SELECT * FROM time");
    rs.next();
    ts2 = rs.getTimestamp("datetime");
    Util.prt("ts2+100000 days readback = "+ts2+" "+ts2.getTime());

    Util.prt("Done");
    
  }
  catch(SQLException e) {
    Util.SQLErrorPrint(e, "During test");
  }

}
}
