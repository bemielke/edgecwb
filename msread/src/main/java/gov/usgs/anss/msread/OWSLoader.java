/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.msread;
import gov.usgs.anss.waveserver.MenuItem;
import gov.usgs.anss.waveserver.WaveServerClient;
import gov.usgs.anss.util.Util;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.util.Iterator;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edge.RawInputClient;
//import gov.usgs.anss.waveserver.*;
import java.io.IOException;
/** Take data from a GEOMAG Oracle Wave Server, and put it in a rawinputserver in a Edge/CWB
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class OWSLoader {
  WaveServerClient wsc;
  RawInputClient rawout;
  GregorianCalendar now = new GregorianCalendar();
  
  int [] data = new int[10000];
  public WaveServerClient getWaveServerClient() {return wsc;}
  public OWSLoader(String argsline) {
    Util.setModeGMT();
    // parse the command line owsip:owport:edgeIP:edgeport
    String [] args = argsline.split(":");
    int owsPort = Integer.parseInt(args[1]);
    int edgePort = Integer.parseInt(args[3]);
    wsc = new WaveServerClient(args[0], owsPort);    // Create client to OWS
    rawout = new RawInputClient("OWS", args[2], edgePort, args[2], edgePort); // Create client to send raw to CWB

  }
  public MenuItem getMenuSCNL(String channel) throws IOException {
     MenuItem item = wsc.getMENUSCNL("OWSL",channel);      // for grins look up the channel
     return item;
  }
  
  public void convertChannel(String channelIn, GregorianCalendar st, GregorianCalendar end, String chOut) {
    try {
      String channel = (channelIn.replaceAll("-"," ")+"       ").substring(0,12);
      StringBuilder channelOut = new StringBuilder();
      if(chOut == null) channelOut.append(channel);
      else if(chOut.length() < 1) channelOut.append(channel);
      else 
        channelOut.append( (chOut.replaceAll("-"," ")+"       ").substring(0,12));
      ArrayList<TraceBuf> tbs = new ArrayList<TraceBuf>(1000);
      long endAt = end.getTimeInMillis();
      while(st.getTimeInMillis() < endAt) {
        if(endAt - st.getTimeInMillis() > 86400000L) {
          end.setTimeInMillis(st.getTimeInMillis() + 86399999L);    // do it one day at a time
        }
        else end.setTimeInMillis(endAt);
        wsc.getSCNLRAW("OWSL", channel, st, end, true, tbs);
        Util.prt("Req for "+Util.ascdatetime2(st)+"-"+Util.ascdatetime2(end)+" ret size="+tbs.size());
        
        for (TraceBuf tb : tbs) {
          Util.prt("Insert "+tb);
          if(tb.getTimeInMillis() < st.getTimeInMillis() && tb.getLastTimeInMillis() > st.getTimeInMillis()) {// spans begin day boundar
            int offset = (int) Math.round((tb.getTimeInMillis() - st.getTimeInMillis())/1000.*tb.getRate());
            now.setTimeInMillis(tb.getTimeInMillis() + (long) (offset/tb.getRate()*1000.));
            if(tb.getNsamp() - offset > 0) {
              Util.prt("Start of day offset="+offset+" ns reduced to "+(tb.getNsamp() - offset)+" from "+tb.getNsamp()+" "+Util.ascdatetime2(now));
              System.arraycopy(tb.getData(), offset, data, 0, tb.getNsamp()-offset);
              rawout.send(channelOut, tb.getNsamp()-offset, data, now, tb.getRate(), 0, 0, 0, 0);
            }
          }
          else if(tb.getTimeInMillis() < end.getTimeInMillis() && tb.getLastTimeInMillis() > end.getTimeInMillis()) {// spans end of day boundar
            int nsamp = (int) ((end.getTimeInMillis() - tb.getTimeInMillis())/1000.*tb.getRate());
            Util.prt("End of day packet shorten nsamp to "+nsamp+ " From "+tb.getNsamp()+" "+Util.ascdatetime2(tb.getTimeInMillis()));
            rawout.send(channelOut, nsamp, tb.getData(), tb.getGregorianCalendar(), tb.getRate(), 0, 0, 0, 0);
          }
          else {
            rawout.send(channelOut, tb.getNsamp(), tb.getData(), tb.getGregorianCalendar(), tb.getRate(), 0, 0, 0, 0);
          }// in the middle
        }
        wsc.freeMemory(tbs);
        tbs.clear();
        st.setTimeInMillis(st.getTimeInMillis() + 86400000L);  // Advance to new starting time
      }
    }
    catch(IOException e) {
      e.printStackTrace();
      System.exit(0);
    }
  }
  public void close() {
      wsc.closeSocket();
      rawout.close();
  }
  public static void main(String [] args) {
    Util.setModeGMT();
    try {
      OWSLoader ows = new OWSLoader("localhost:16025:localhost:7972");    // THisl ooks obsolete.
      ows.getWaveServerClient().getMENU("OWS");
      Iterator<MenuItem> itr = ows.getWaveServerClient().getMenuTreeMap().values().iterator();
      GregorianCalendar st = new GregorianCalendar();
      GregorianCalendar end = new GregorianCalendar();
      while(itr.hasNext()) {
        MenuItem item = itr.next();
        if(item.getSeedname().substring(10).equals("R0")) {
          String chOut = item.getSeedname().substring(0,10)+"RM";
          st.setTimeInMillis(item.getStartInMillis()-500);
          end.setTimeInMillis(item.getEndInMillis()+1000);
          Util.prt("Start "+item.getSeedname()+"->"+chOut+" "+Util.ascdatetime2(st)+"-"+Util.ascdatetime2(end));
          ows.convertChannel(item.getSeedname(), st, end, chOut);
        }
      }
    }
    catch(IOException e) {
      e.printStackTrace();
      System.exit(0);
    }
    // parse the command line owsip:owport:channel:yyyy-mm-dd:yyyy-mm-dd:edgeIP:edgeport
    Util.prt("End of execution");
  }
}
