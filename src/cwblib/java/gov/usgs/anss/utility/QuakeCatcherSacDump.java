/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.utility;
import edu.sc.seis.TauP.SacTimeSeries;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import gov.usgs.anss.util.Util;

/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class QuakeCatcherSacDump {
  private static final DecimalFormat df2 = new DecimalFormat("00");
  private static final DecimalFormat df3 = new DecimalFormat("000");
  public static void main(String [] args) {
    int [] idata = new int[50000];
    boolean dbg=false;
    if(args.length == 0) {
      args = new String[3];
      args[0]=Util.fs+"Users"+Util.fs+"ketchum"+Util.fs+"TEMP"+Util.fs+"QuakeCatcher"+Util.fs+"2015126215531.QC.000031783.BN1.SAC";
      args[1]=Util.fs+"Users"+Util.fs+"ketchum"+Util.fs+"TEMP"+Util.fs+"QuakeCatcher"+Util.fs+"2015126215531.QC.000031783.BN2.SAC";
      args[2]=Util.fs+"Users"+Util.fs+"ketchum"+Util.fs+"TEMP"+Util.fs+"QuakeCatcher"+Util.fs+"2015126215531.QC.000031783.BN3.SAC";
    }
    GregorianCalendar g = new GregorianCalendar();
    for(int j=0; j<args.length; j++) {
      if(args[j].equals("-qc2ms")) continue;
      if(args[j].equals("-dbg")) dbg=true;
      try {
        SacTimeSeries sac = new SacTimeSeries();
        sac.read(args[j]);
        if(dbg) {
          Util.prt(sac.toString());
          sac.printHeader();
          for(int i=0; i<Math.min(sac.npts, 10); i++) Util.prt(i+"="+sac.y[i]);
          for(int i=sac.npts-10; i<sac.npts; i++) Util.prt(i+"="+sac.y[i]);
        }
        double mean=0.;
        for(int i=0; i<sac.npts; i++) mean += sac.y[i]/sac.npts;
        for(int i=0; i<sac.npts; i++) {sac.y[i] -= mean; idata[i] = (int) Math.round(sac.y[i]/100000.);}
        if(dbg) {
          for(int i=0; i<Math.min(sac.npts, 10); i++) Util.prt(i+" dmean="+sac.y[i]);
          for(int i=sac.npts-10; i<sac.npts; i++) Util.prt(i+" dmean="+sac.y[i]);
        }
        String station = sac.kstnm.trim();
        if(station.length() > 5) {
          if(station.charAt(0) == '0') station=station.substring(1);
          if(station.charAt(0) == '0') station=station.substring(1);
          if(station.length() > 5) 
            Util.prt("***** file="+args[j]+" station name is too long station="+sac.kstnm+"|");
        }
        if(sac.khole.charAt(0) == 0) sac.khole="";
        String seedname = (sac.knetwk+"  ").substring(0,2)+(station+"     ").substring(0,5)+
                (sac.kcmpnm+"   ").substring(0,3)+(sac.khole.trim().equals("-12345") ? "  " : (sac.khole+"  ").substring(0,2));
        g.set(sac.nzyear, sac.nzjday, sac.nzhour, sac.nzmin, sac.nzsec);
        g.setTimeInMillis(g.getTimeInMillis()/1000*1000+sac.nzmsec);
        int sec = (int) ((g.getTimeInMillis() % 86400000L)/1000L);
        int usec = (int) ((g.getTimeInMillis() % 1000)*1000);
        Util.prt(seedname+" "+Util.ascdatetime2(g)+" npts="+sac.npts+" mean="+Util.ef5(mean)+
                " lat="+Util.df25.format(sac.stla)+" lon="+Util.df25.format(sac.stlo)+" elev="+Util.df21.format(sac.stel));
        MakeRawToMiniseed mrtms = new MakeRawToMiniseed(seedname, sac.npts, idata, sac.nzyear, sac.nzjday,
            sec,usec,1./sac.delta,0,0,0,0, null);
        mrtms.writeToDisk(args[j]+".ms");
      }
      catch(IOException e) {
        e.printStackTrace();
      }
    }
  }
}