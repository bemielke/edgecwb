/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.utility.MakeRawToMiniseed;
import gov.usgs.anss.waveserver.WaveServerClient;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sharper
 */
public class WaveServerRequest extends Fetcher {
	int blocksize;
  Subprocess sp;
  String station;
	public WaveServerRequest(String[] args) throws SQLException {
		super(args);
		station = getSeedname().substring(2,7).trim()+getSeedname().substring(10).trim();
		
	}
	@Override
  public void closeCleanup() {
    
  }
	
	@Override
  public void startup() {
    if(localBaler) return;    // local WaveServerRequest, do not try Baler stuff.
    checkNewDay();
  }

	
	@Override
	public ArrayList<MiniSeed> getData(FetchList fetch) {
		ArrayList<MiniSeed> mss = new ArrayList<>(1);
		GregorianCalendar begin = new GregorianCalendar();
		begin.setTimeInMillis(fetch.getStart().getTime()+fetch.getStartMS());
    
		
		GregorianCalendar end = new GregorianCalendar();
		end.setTimeInMillis(fetch.getStart().getTime()+fetch.getStartMS()+(long)(fetch.getDuration()*1000));
    GregorianCalendar gc = new GregorianCalendar();
		
    MakeRawToMiniseed maker = new MakeRawToMiniseed();
		/*Timestamp st = FUtil.stringToTimestamp(begin.replaceAll("-", " ").replaceAll("/","-"));
    Timestamp en = FUtil.stringToTimestamp(end.replaceAll("-", " ").replaceAll("/","-"));
    begin.setTimeInMillis(st.getTime());
    end.setTimeInMillis(en.getTime());*/
		
		
		ArrayList<TimeSeriesBlock> tbs = new ArrayList<>(100);
		TraceBuf tb;
		
			
			WaveServerClient wsc = new WaveServerClient(host, port );
			try {
				tbs.clear();
			
				wsc.getSCNLRAWAsTimeSeriesBlocks("101", seedname, begin,end, true, tbs);
			} catch (IOException ex) {
				ex.printStackTrace();
			
			}
			long expected=0;
			for (TimeSeriesBlock tb1 : tbs) {
				tb = (TraceBuf) tb1;
				
			
				if(expected > 0 && Math.abs(expected - tb.getTimeInMillis()) > 1000./tb.getRate()) maker.flush();
				gc.setTimeInMillis(tb.getTimeInMillis());
				long usec = tb.getTimeInMillis()*1000;
				int year = gc.get(Calendar.YEAR);
				int doy = gc.get(Calendar.DAY_OF_YEAR);
				int sec = (int) (usec % 86400000000L /1000000);  // milliseconds into this day
				usec = usec % 1000000;                      // microseconds left over
				maker.loadTSIncrement(tb.getSeedNameString().substring(0,12)+"",
								tb.getNsamp(), tb.getData(), year, doy, sec, (int) usec, tb.getRate(),
								0, 0, 0, 1);
			}
		

		maker.flush();
		return maker.getBlocks();
			
  }
	
	public static void main(String[] args) {
		Util.setModeGMT();
		Util.init("edge.prop");
		GregorianCalendar test = new GregorianCalendar();
		test.setTimeInMillis(test.getTime().getTime() - 900000);
		String date = Util.ascdatetime(test).toString();
		String yyyymmdd = date.substring(0,10).trim();
		String hhmmss = date.substring(10).trim();
		args = ("-s USWVOR-BHZ00 -b "+yyyymmdd+" "+hhmmss+" -d 900 -1 -t GP -h 137.227.224.97 -p 16002").split("\\s");
		
		
    boolean single=false;
    // set the single mode flag
    for(int i=0; i<args.length; i++) if(args[i].equals("-1")) single = true;
    
    // Try to start up a new requestor
    try {
      WaveServerRequest wsr = new WaveServerRequest(args);
      if(single) {
        ArrayList<MiniSeed> mss = wsr.getData(wsr.getFetchList().get(0));
        if(mss == null) Util.prt("NO DATA was returned for fetch="+wsr.getFetchList().get(0));
        else if(mss.isEmpty()) Util.prt("Empty data return - normally leave fetch open "+wsr.getFetchList().get(0));
        else {
          try (RawDisk rw = new RawDisk("tmp.ms", "rw")) {
            for(int i=0; i<mss.size(); i++) {
              rw.writeBlock(i, mss.get(i).getBuf(), 0, 512);
              Util.prt(mss.get(i).toString());
            }
            rw.setLength(mss.size()*512L);
          }
        }
        System.exit(0);
      }
      else {      // Do fetch from table mode 
        wsr.startit();
        while(wsr.isAlive()) Util.sleep(1000);
      }
    }
    catch(IOException e) {
      Util.prt("IOError="+e);
      e.printStackTrace();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Impossible in test mode");
    }


	}
}
