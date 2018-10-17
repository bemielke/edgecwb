/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.adslserverdb;
import gov.usgs.adslserverdb.Stats;
import gov.usgs.adslserverdb.ADSLEpochs;
import gov.usgs.adslserverdb.ADSLEpoch;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.TreeMap;
import java.util.regex.PatternSyntaxException;

/** this class handles a single socket connection to the thread.  It is a pool thread so
 * it is created, and waits to be assigned a socket.  It runs until that socket is closed
 * or potentially timed out by the pool management in the outer class */
public class ADSLRequestHandler extends Thread {
  static TreeMap<String,Stats> stats = new TreeMap<String, Stats>();
  static int usedThreads;
  static int thrcount;
  static boolean terminate;
  static DecimalFormat df22 = new DecimalFormat("0.00");
  static DecimalFormat df4 = new DecimalFormat("0.0000");

  Socket s;
  String ip;
  long lastAction;
  int ntrans;
  int ithr;
  Timestamp tsnow;
  EdgeThread par;
  String inputLine;
  public static int getUsedThreads() {return usedThreads;}
  public static int getThreadCount() {return thrcount;}
  public void prt(String s) {if(par != null) par.prt(s); else Util.prt(s);}
  public void prta(String s) {if(par != null) par.prta(s); else Util.prta(s);}
  public static void terminate() {terminate=true;}
  public static void incConnCount(String ip) {
    Stats s2 = stats.get(ip);
    if(s2 == null) {
      s2 = new Stats(ip);
      stats.put(ip, s2);
    }
    s2.incConnCount();
  }

  /** create a socket handler.  If ss is not null, the socket will start handling right away
   *@param ss A socket to use on creation, null to set up a waiting pool socket
   * @param parent The EdgeThread to use for logging.
   */
  public ADSLRequestHandler(Socket ss, EdgeThread parent) {
    par=parent;
    s = ss;
    if(s == null) ip = "null";
    else {
      ip = s.getInetAddress().toString();
      usedThreads++;
      lastAction = System.currentTimeMillis();
    }
    inputLine=null;
    try {
      if(s != null) s.setTcpNoDelay(true);
    }
    catch(SocketException e){Util.prt("Socket error setting TCPNoDelay e="+e);}
    ithr=thrcount++;
    tsnow = new Timestamp(10000L);
    tsnow.setTime(System.currentTimeMillis());

    start();
  }

  @Override
  public String toString() {return "MDH: "+s+" idle="+(System.currentTimeMillis() - lastAction)+" #trans="+ntrans;}
  /**  assign a socket to this handler for processing, optionally, submit an existing line to bypass input from socket
   *
   * @param ss  the socket to attach
   * @param line If not null, process this as if it was read from the socket
   */
  public void assignSocket(Socket ss, String line) {
    usedThreads++;
    inputLine=line;
    lastAction = System.currentTimeMillis();
    s = ss;
    if(s == null) ip = "null";
    else ip = s.getInetAddress().toString();
    Stats s2 = stats.get(ip);
    if(s2 == null) {
      
    }
    try {
      if(s != null) s.setTcpNoDelay(true);
    }
    catch(SocketException e){Util.prt("Socket error setting TCPNoDelay e="+e);}
    interrupt();    // wake it up from sleeping
  }
  /** return the socket open on this thread.  If null, then no socket is assigned.
   *@return The open socket or null if not assigned. */
  public Socket getSocket() {return s;}
  /** get the time in millis of the last usage of this socket
   *@return the time in millis */
  public long getLastActionMillis() {return lastAction;}
  /** close the connection if it is open */
  public void closeConnection() {
    if(s == null) return;
    if(!s.isClosed()) {
      try {
        s.close();
      }
      catch(IOException e) {}
    }
    usedThreads--;
    s=null;
  }
  private String readLine(InputStream in) throws IOException {
    StringBuilder ss = new StringBuilder(100);
    boolean interrupted=false;
    for(;;) {
      try {
        int ch = in.read();
        if(ch == -1) return null;
        char chr = (char) ch;
        if(chr == '\n' || chr == '\r') {
          if(interrupted)Util.prta("***** interrupted line was completed as s="+ss);
          return ss.toString();
        }
        ss.append( chr);
      }
      catch(IOException e) {
        if(e.toString().indexOf("nterrupt") >= 0) {
          Util.prta("***** Interrupted exception getting line  line is "+ss);
          interrupted=true;
        }
        else throw e;
      }
    }
  }
  /** process a line of input
   * 
   * @param ss The socket to process
   * @param line The line of input
   * @throws IOException If one is thrown writting data to the socket
   */
  public void doLine(Socket ss, String ipadr, String line) throws IOException {
    ip=ipadr;
    try{
      ntrans++;
      lastAction = System.currentTimeMillis();
      OutputStream out = ss.getOutputStream();
      if(ADSLEpochs.getADSLEpochsObject() == null) {
          out.write(("ADSLEpochs currently disabled\n<EOR>\n").getBytes());
          return;

      }
      if(line.indexOf("SELECT") == 0 || line.indexOf("select") == 0) {
        ADSLEpochs md = ADSLEpochs.getADSLEpochsObject();
        try {
          String output = md.doSelect(line);
          out.write((output+"\n<EOR>\n").getBytes());
        }
        catch(SQLException e) {
          out.write((e+"\n<EOR>\n").getBytes());
        }
        return;
      }
      line = line.replaceAll("  ", " ");
      line = line.replaceAll("  ", " ");
      line = line.replaceAll("  ", " ");

      prta(ithr+" IRSH : process line ="+line+"| from "+ss.toString());
      if(line.length() <= 1) {Util.prt(ithr+" IRSH: line is null. skip it...."); return;}

      String [] word = line.split("//s");
      word[0] = (word[0]+"      ").substring(0,6);
      if(stats.get(ipadr) == null) {
        stats.put(ipadr, new Stats(ipadr));
      }
      if(word[0].charAt(0) < 'A' ||  word[0].charAt(0) > 'Z') {
        stats.get(ipadr).incNewCount();
        newFormat(line, ss.getOutputStream());
      }
      else {
        prta(ithr+" IRSH: process line old line format not yet supported");
        stats.get(ipadr).incOldCount();

        //oldFormat(line, out);
      }
      prta(ithr+" IRSH: Go to readLine()");
    }
    catch(RuntimeException e) {
      Util.prta(ithr+" IRS: Handler RuntimeException caught e="+e+" msg="+(e.getMessage() == null?"null":e.getMessage())+ss.toString());
      e.printStackTrace();
      try{
        ss.getOutputStream().write(("* Your query caused a RuntimeError e="+e.toString().replaceAll("\n","\n* ")+"\n").getBytes());
        ss.getOutputStream().write("* <EOR>\n".getBytes());
      }
      catch(IOException e2) {}
    }

  }
  @Override
  public void run() {
    String line;
    for(;;) {
      if(terminate) break;
      if(s == null) {   // No socket assigned, sleep some more
        try {sleep(100); } catch(InterruptedException e) {}
        continue;
      }
      try {
        //BufferedReader in = new BufferedReader(new InputStreamReader(ss.getInputStream()));
        while(!terminate) {
          // body of Handler goes here
          for(;;) {
            if(s.isClosed()) break;
            if(inputLine == null) line = readLine(s.getInputStream());
            else {line=inputLine;inputLine=null;}
            if(line == null) break;
            doLine(s, ip, line);
          }     // top of loop to read lines from socket
          break;
        }
      }
      catch(IOException e) {
        Util.SocketIOErrorPrint(e, "in Reading ADSL command", par.getPrintStream());
        try{sleep(100);} catch(InterruptedException e2) {}    // this might be a close in progress, let it finish before checking
        if(s != null)
          if(!s.isClosed())
            prta(ithr+" "+s+"IRSH: IOError on socket. close it="+s.isClosed());
      }
      catch(RuntimeException e) {
        Util.prt(ithr+" IRS: Handler RuntimeException caught e="+e+" msg="+(e.getMessage() == null?"null":e.getMessage())+s.toString());
        e.printStackTrace();
        try{
          s.getOutputStream().write(("* Your query caused a RuntimeError e="+e.toString().replaceAll("\n","\n* ")+"\n").getBytes());
          s.getOutputStream().write("* <EOR>\n".getBytes());
        }
        catch(IOException e2) {}
      }
      Util.prta(ithr+" "+s+" IRSH: MetaDataHandler closing socket");
      if(s != null) {
        if(!s.isClosed()) {
          try {
            s.close();
          }
          catch(IOException e) {
            Util.prta(ithr+" "+s+"IRSH: IOError closing socket");
          }
        }
        s = null;
        usedThreads--;
        if(terminate) break;
      }
    }   // For ever
  }     // End of run

  /**
   *
   * @param line String with the command line
   * @param out Outputstream to put out result
   * @throws IOException
   */
  public void newFormat(String line, OutputStream out) throws IOException {
    String [] word = line.split("\\s");
    tsnow.setTime(System.currentTimeMillis());
    String stationRegExp="";
    String beginDate="";
    String endDate="";
    String command="";
    String icon = "VARY";

    boolean ok=true;
    Timestamp end=new Timestamp(10000L);
    Timestamp begin=new Timestamp(10000L);
    boolean sac=false;
    boolean xml=false;
    boolean delaz=false;
    double lat=0.;
    double lng=0.;
    double within=0.;
    double mindeg=0.;
    String dur="0";
    double duration;
    if(word.length == 1 ) {
      if(word[0].equals("-h")) {
        out.write( (
            "-c l|a|c|s|d|k for 'l'ist channels, 'a'lias 'c'oordinates/orientation, 's'tation, 'd'escription,'k'ml\n"+
            "-c l -a A.D.S_REGEXP (list stations)\n"+
            "-c a -a A.D.S_REGEXP (return aliases matching the station)\n"+
            "-c s [-b date][-e date][-d dur[d][y]][-xml|-sac] -s regexp  (get station epochs - default is -sac and current date/time)\n"+
            "-c c|o [-b date][-e date][-d dur[d][y]][-xml|-sac] -s regexp (coordinates and orientation -default is -sac and current date/time)\n"+
            "-c k -s regexp [-icon value] value is the color designation for all placemarks(return kml file for the given stations)\n"+
            "date       formats are YYYY,DDD-HH:MM:SS or YYYY/DD/MM-HH:MM:SS times may be shortened or omitted for midnight\n"+
            "           If '-b all' is useded, then all epochs from 1970 to present will be allowed.\n"+
            "           If -b and -e are omitted, they default to the current time.\n"+
            "A.D.S_REGEXP  to specify an exact channel match us the full NNSSSSSCCCLL and put '-' or '_' for rerequired spaces\n"+
            "           for pattern matching use '.' for any character [ABC] if A, B or C is allowed in the position\n"+
            "           [A-Z] for this position can match any charater from A to Z inclusive\n"+
            "           examples : USDUG__[BL]H.00 would match network US station DUG all BH and LH channel a location '00'\n"+
            "           US[A-C]....BHZ..  would match network US station beginning with A, B or C for channel BHZ and any location\n"+
            "           optionally you can add a NOT MATCH regular expression like RE%notRE" +
            "           e.g. 'T.*^TA.*' matches all networks starting with T excludeing TA\n"+
            "-xml       Some commands have XML formatted output (r, l)\n"+
            "-delaz [mindeg:]maxDeg:Lat:Long [-s regexp] Return station list with Deg of the given latitude and longitude\n"+
            "-delazc [minDeg:]maxDeg:Lat:Long [-s regexp] Return channel list with Deg of the given latitude and longitude\n"+
            "\nOutput:\n"+
            "'* <EOE>'  represents End-of-Epoch in commands that generate mutliple epochs (r and c)\n"+
            "'* <EOR>'  represents End-of-Response to a input command\n"
            ).getBytes());

      }
    }
    if(word.length <= 2) {
      out.write("** Too few arguments to new format\n* <EOR>\n".getBytes());
      return;
    }

    for(int i=0; i<word.length; i++) {
      if(word[i].equals("-a")) {
        stationRegExp=word[i+1].replaceAll("_"," ");          // change _ to space
        stationRegExp=stationRegExp.replaceAll("-"," "); i++; // change - to space
      }
      else if(word[i].equals("-b")) {beginDate=word[i+1]; i++;}
      else if(word[i].equals("-e")) {endDate=word[i+1]; i++;}
      else if(word[i].equals("-d")) {dur=word[i+1]; i++;}
      else if(word[i].equals("-c")) {command=word[i+1]; i++;}
      else if(word[i].equals("-icon")) {icon = word[i+1]; i++;}
      else if(word[i].equals("-delaz") ){
        String [] parts = word[i+1].split(":");
        if(parts.length != 3 && parts.length != 4) {
          out.write("-delaz requires 3 arguments degree:latitude:longitude\n".getBytes());
        }
        within = Double.parseDouble(parts[parts.length-3]);
        lat = Double.parseDouble(parts[parts.length-2]);
        lng = Double.parseDouble(parts[parts.length-1]);
        if(parts.length == 4) mindeg = Double.parseDouble(parts[0]);
        delaz=true;
        Util.prta("Delaz args "+within+" deg lat="+lat+" "+lng);
        while(!ADSLEpochs.getADSLEpochsObject().isPopulated()) {
          Util.prta(ithr+" ADSL is not up/populated. wait");
          //out.write("** MetaDataServer not up.  Wait and try again\n* <EOR>\n".getBytes());
          try{sleep(2000);} catch(InterruptedException e) {}
        }
        i++;
      }

      else if(word[i].equalsIgnoreCase("-forceupdate")) {ADSLEpochs.getADSLEpochsObject().forceUpdate(); return;}
      else {
        out.write( ("Unknown ADSL switch to command="+word[i]+"\n").getBytes());
        ok=false;
      }
    }
    // if a begin date, set eff to this date
    if(beginDate.equalsIgnoreCase("all")) {
      begin.setTime(1000000L);
      end.setTime((68L*365L+16)*86400000L);   // about 12/30/2037
    }
    else if(!beginDate.equals("")) {
      begin.setTime(Util.stringToDate2(beginDate).getTime());
      if(begin.getTime() < 10000L) {
        out.write(("** Beginning date format is illegal ="+beginDate+" allowed formats are yyyy,ddd-hh:mm:ss or yyyy/mm/dd-hh:mm:ss\n").getBytes());
        ok=false;
      }
    }
    else begin.setTime(System.currentTimeMillis());


    // The end date can either be specified or it must be a duration.  If neither, treat as duration=0.
    if(beginDate.equalsIgnoreCase("all")){

    }
    else if(endDate.equals("")) {    // no date, must be a duration
      if(dur.equals("") ) end.setTime(begin.getTime());
      else {
        if(dur.endsWith("D") || dur.endsWith("d")) {
          duration = Double.parseDouble(dur.substring(0,dur.length()-1))*86400.-0.001;
        }
        else if(dur.endsWith("Y") || dur.endsWith("y")) {
          duration = Double.parseDouble(dur.substring(0,dur.length()-1))*86400.*364.25;
        }
        else {
          duration=Double.parseDouble(dur);
        }
        end.setTime(begin.getTime()+((long) (duration*1000.+0.5)));
      }
    }
    else {      // And end date was specified
      end.setTime(Util.stringToDate2(endDate).getTime());
      if(end.getTime() < 100000L) {
        out.write(("** End date format is illegal ="+endDate+"\n").getBytes());
        ok=false;
      }
    }      // Handle ad delazc command
   if(delaz) {
      stats.get(ip).incDelazs();
      ADSLEpochs md = ADSLEpochs.getADSLEpochsObject();
      ArrayList<ADSLEpoch> ret = md.getADSEpochByDelaz(stationRegExp,mindeg, within, lat,lng);
      if(command.charAt(0) == 'k') {
        StringBuilder kml = makeKMLStation(ret, icon, begin);
        out.write(kml.toString().getBytes(), 0, kml.toString().length());
        out.write("* <EOR>\n".getBytes());
      }
      else {
        out.write("Agncy.depl.stat   dist(deg) azimuth  latitude longitude\n".getBytes());
        for(int i=0; i<ret.size(); i++) {
          double [] diff = Distaz.distaz(lat,lng, ret.get(i).getLatitude(), ret.get(i).getLongitude());
          out.write((
             (ret.get(i).getAgency().trim()+"."+ret.get(i).getDeployment().trim()+"."+
                  ret.get(i).getStation()+"            ").substring(0,16)+
             Util.leftPad(df22.format(diff[1]),8)+" "+
             Util.leftPad(df22.format(diff[2]),8)+" "+
             Util.leftPad(df22.format(ret.get(i).getLatitude()),8)+" "+
             Util.leftPad(df22.format(ret.get(i).getLongitude()),8)+"\n").getBytes());
          //for(int i=0; i<ret.size(); i++) out.write((ret.get(i)+"\n").getBytes());
        }
        out.write("* <EOR>\n".getBytes());
      }
      return;
    }
    try {
      if(command.length() < 1) {
        out.write(("** Command must be r, a, c, l, d, o, k, or s us -h for help\n").getBytes());
        ok=false;
      }
      if( command.length() >=1 && !(command.charAt(0) == 'r' || command.charAt(0) == 'a' || command.charAt(0) == 'c' ||
            command.charAt(0) == 'd' || command.charAt(0) == 'o' ||
            command.charAt(0) == 'l' || command.charAt(0) == 's' || command.charAt(0) == 'k')) {
        out.write(("** Command must be r, a, c, l, d, o, k, or s use -h for help\n").getBytes());
        ok=false;
      }

      // verify that the parameters make some sense!
      if(stationRegExp.equals("")) {out.write(("* You must have a station/channel selector!\n").getBytes()); ok=false;}



      if(!ok) {out.write("* <EOR>\n".getBytes()); return;}       // Parameters do not make sense, return
      // The variables for this query are :
      //
      // begin A Timestamp with starting time of query
      // end   A Timestamp with ending tim eo query
      // stationRegExp String with timestamp for RegExpMatch
      // command String whose first character is "r"esponse,"o"rientation,"d"escription, "c"oordinates, "t"ranslate, "a"lias,
      //
      // The settings or modifiers are :
      //
      // allowWarn  Allow warning marked metadata
      // allowBad   All bad marked metadata
      //
      //
      ADSLEpochs md = null;
      while(md == null) {
        md = ADSLEpochs.getADSLEpochsObject();
        if(md == null) {
          Util.prta(ithr+" ADSL is not up");
          //out.write("** MetaDataServer not up.  Wait and try again\n* <EOR>\n".getBytes());
          try{sleep(2000);} catch(InterruptedException e) {}
        }
      }
      while(!md.isPopulated()) {
        Util.prta(ithr+" ADSL is not up/populated");
        //out.write("** MetaDataServer not up.  Wait and try again\n* <EOR>\n".getBytes());
        try{sleep(2000);} catch(InterruptedException e) {}
      }
      ADSLEpoch chan=null;
      ADSLEpoch station=null;
      char cmd = command.charAt(0);
      ArrayList<ADSLEpoch> statList;
      switch(cmd) {
      case 'k':
        statList = md.getADSLSingleEpoch(stationRegExp);

        StringBuilder kml=makeKMLStation(statList, icon, begin);
        if(kml != null) {
          out.write(kml.toString().getBytes());
          out.write("<!--* <EOR>> -->\n".getBytes());
        }
        else out.write("<!--* Request did not return any channels -->\n<!--* <EOR> -->\n".getBytes());
        break;
      case 'a':
        stats.get(ip).incAlias();
        statList = md.getADSEpochs(stationRegExp);
        int nout=0;
        if(statList.size() > 0) {
          for(int i=0; i<statList.size(); i++) {
            ArrayList<ADSLEpoch> epochs = statList;
            String alias =statList.get(i).userKey()+(statList.get(i).isAlias()? (statList.get(i).getAttributable() != 0?"@ ":" "):"! ")+": ";
            if( !(begin.compareTo(epochs.get(i).getLocationEnddate()) > 0 || end.compareTo(epochs.get(i).getLocationEffective()) <  0)) {
              ArrayList<ADSLEpoch> aliases = md.getADSLAliasStationID(epochs.get(i));
              for(int j=0; j<aliases.size(); j++)
                if(alias.indexOf(aliases.get(j).userKey()) < 0) 
                  alias += aliases.get(j).userKey()+(aliases.get(j).isAlias()?(aliases.get(j).getAttributable() != 0?"@ ":"  "):"! ");
              nout++;
            }
            out.write( (alias+"\n* <EOE>\n").getBytes());
          }
        }
        else out.write("** ADSL: unknown station\n".getBytes());
        out.write("* <EOR>\n".getBytes());
        break;
      case 'd':
        stats.get(ip).incDesc();
        statList = md.getADSEpochs(stationRegExp);
        nout=0;
        if(statList.size() > 0) {
          for(int i=0; i<statList.size(); i++) {
            ArrayList<ADSLEpoch> epochs = statList;
            if( !(begin.compareTo(epochs.get(i).getLocationEnddate()) > 0 || end.compareTo(epochs.get(i).getLocationEffective()) <  0)) {
              String desc=  epochs.get(i).userKey()+":"+
                      (epochs.get(i).getISCOverrideSitename().trim().equals("")? epochs.get(i).getSitename():epochs.get(i).getISCOverrideSitename())+
                      ":"+epochs.get(i).getSEEDChannels()+":"+epochs.get(i).getSEEDRates();
              out.write( (desc+"\n* <EOE>\n").getBytes());
              nout++;
            }
          }
          if(nout == 0) out.write( ("** ADSL: no ADSL found to match "+stationRegExp+" on "+begin.toString()+"\n").getBytes());
        }
        else {out.write( ("** ADSL: no channels found to match "+stationRegExp+"\n").getBytes());}
        out.write("* <EOR>\n".getBytes());
        break;
        /*stats.get(ipadr).incDesc();
          // If this is an IR code, use the IRCode collection to find the answer
            station = md.getStation(Util.rightPad(stationRegExp,7), tsnow);
            if(station != null) {
               if(station.getAlias() != null)
                 out.write( (station.getSeedSiteName()+"\n"+station.getSeedOwner()+"\n").getBytes());
               else
                 out.write("** MDS: unknown station\n".getBytes());
            }
            else
              out.write("** MDS: unknown station\n".getBytes());
          out.write("* <EOR>\n".getBytes());
          break;*/
        /*case 'ss':
          stats.get(ipadr).incStation();
          // If this is an IR code, use the IRCode collection to find the answer
          ArrayList<Station> stationList = md.getStationEpochs(Util.rightPad(stationRegExp,7), begin, end);
          if(stationList != null) {
            if(xml) {
               out.write("<Station>\n".getBytes());
               out.write( ("  <StationID>\n    <NetCode>"+stationList.get(0).getStation().substring(0,2)+"</NetCode>\n").getBytes());
               out.write( ("    <StaCode>"+stationList.get(0).getStation().substring(2).trim()+"</StaCode>\n").getBytes());
               out.write( ("    <IRcode>"+stationList.get(0).getIrcode().substring(2)+"</IRcode>\n").getBytes());
               out.write( ("    <OtherAlias>"+stationList.get(0).getOtheralias()+"</OtherAlias>\n").getBytes());
               out.write(  "  </StationID>\n".getBytes());
            }
            for(int i=0; i<stationList.size(); i++) {
               if(sac || (!sac && !xml)) out.write( getSACStationEpochString(stationList.get(i)).toString().getBytes());
               else if(xml) out.write(getXMLStationEpochString(stationList.get(i)).toString().getBytes());
            }
            if(xml) out.write("</Station>\n".getBytes());
          }
          else
            out.write("** MDS: unknown station\n".getBytes());
          out.write("* <EOR>\n".getBytes());
          break;*/
      case 'c':   // Coordinates
      //case 'o':
        String [] parts = stationRegExp.split("\\.");
        if(parts.length >= 4) statList = md.getADSLEpochs(stationRegExp);
        else statList = md.getADSEpochs(stationRegExp);
        stats.get(ip).incCoord();
        nout=0;
        if(statList.size() > 0) {
          for(int i=0; i<statList.size(); i++) {
            ArrayList<ADSLEpoch> epochs = statList;
            if( !(begin.compareTo(epochs.get(i).getLocationEnddate()) > 0 || end.compareTo(epochs.get(i).getLocationEffective()) <  0)) {
              String coord=  epochs.get(i).userKey()+":"+epochs.get(i).getLatitude()+" "+epochs.get(i).getLongitude()+" "+epochs.get(i).getElevation();
              out.write( (coord+"\n* <EOE>\n").getBytes());
              nout++;
            }
          }
          if(nout == 0) out.write( ("** ADSL: no channels found to match "+stationRegExp.replaceAll(" ","-")+" on "+begin.toString()+"\n").getBytes());
        }
        else {out.write( ("** ADSL: no channels found to match "+stationRegExp.replaceAll(" ","-")+"\n").getBytes());}
        out.write("* <EOR>\n".getBytes());
        break;
      case 'l':   // Station list
        stats.get(ip).incList();
        statList = md.getADSLSingleEpoch(stationRegExp);
        nout=0;
        if(statList.size() > 0) {
          for(int i=0; i<statList.size(); i++) {
            if(sac) out.write( (statList.get(i).userKey()+"\n").getBytes());
            else if(xml) out.write( ("<Channel><ChnCode>"+statList.get(i).userKey()+"</ChnCode></Channel>\n").getBytes());
            else out.write( (statList.get(i).userKey()+"\n").getBytes());
            nout++;
          }
          if(nout == 0) out.write( ("** ADSL: no channels found to match "+stationRegExp+" on "+begin.toString()+"\n").getBytes());
        }
        else {out.write( ("** ADSL: no channels found to match "+stationRegExp+"\n").getBytes());}
        out.write("* <EOR>\n".getBytes());
        break;
      default:
          Util.prta(ithr+" ** newFormat() got no selection in command select cmd="+cmd+" command="+command);
          out.write(("** No such command as '"+cmd+" try again.\n* <EOR>\n").getBytes());
      }
    }
    catch(PatternSyntaxException e) {
      Util.prt(ithr+" User pattern syntax error pattern="+stationRegExp);
      out.write(("* PatternSyntax error occurred. Pattern is '"+stationRegExp+"'\n").getBytes());
      out.write(("* Details="+(e.getMessage() == null?" None available": e.getMessage().replaceAll("\n","\n* "))+"\n").getBytes());
      out.write("* <EOR>\n".getBytes());
    }
    catch(RuntimeException e) {
      if(e != null) {
        if(e.getMessage() != null) {
          Util.prt(ithr+" Unhandled RuntimeException in ADSL Handler e="+e.getMessage());
          out.write(("* a runtime error occurred="+e.getMessage().replaceAll("\n","\n* ")).getBytes());
          out.write("\n* <EOR>\n".getBytes());
          e.printStackTrace();
        }
      }
    }
  }

  private StringBuilder makeKMLStation(ArrayList<ADSLEpoch> stationList, String icon, Timestamp beg) {
    GregorianCalendar begin = new GregorianCalendar();
    begin.setTimeInMillis(beg.getTime());
    stats.get(ip).incKML();
    String [] nets = {"US","IU","CU","II","GE","IM","G "};
    String [] icons = {"US#starry","IU#squash","CU#caribbean","II#rasberry","GE#rainbow","IM#latte","G #grid"};
    String [] colors={"emerald","jade","purple","smoke","twillight","mocha","white","graphite","deepsea","radial"};

    StringBuilder chans = new StringBuilder(21);
    StringBuilder kml = new StringBuilder(100000);
    int nextNet=0;
    String [] netsUsed= new String[100];
    if(stationList != null && stationList.size() >0) {
      kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      kml.append("<kml xmlns=\"http://earth.google.com/kml/2.2\">\n");
      kml.append("  <Document>\n  <name>Seismic Stations via ADSL</name>\n    <open>1</open>\n");
      kml.append("    <LookAt><longitude>-100</longitude><latitude>44.5</latitude><altitude>0</altitude><range>7500000</range><tilt>0</tilt><heading>0</heading></LookAt>\n");
      if(icon.equals("VARY")) {
        for(int i=0; i<nets.length; i++) {
          setColor(kml, nets[i], icons[i].substring(icons[i].indexOf("#")+1));
        }
        for(int i=0; i<colors.length; i++) {
          setColor(kml, colors[i].toUpperCase(), colors[i]);
        }

      }
      else {
        setColor(kml, icon.toUpperCase(), icon);
      }
      kml.append("      <Folder>\n");
      String station;
      for(int k=0; k<stationList.size(); k++) {   // not extra allowed so that last station is processed
        station = stationList.get(k).getKey();
        //Util.prt(ithr+" chan="+chanList.get(k).getChannel());
        //if(chanList.get(k).getChannel().substring(0,5).equals("USBMO"))
        //  Util.prt(ithr+" BMO");
        String use=null;
        if(icon.equals("VARY")) {
          for(int i=0; i<nets.length; i++) if(nets[i].equals(station.substring(5,13).trim())) use=station.substring(5,13).trim();
          if(use == null)
            for(int i=0; i<nextNet; i++)
              if(netsUsed[i].equals(station.substring(5,13).trim())) use = colors[i % 10].substring(colors[i % 10].indexOf("#")+1);
          if(use == null) {
            use = colors[nextNet % 10].substring(colors[nextNet % 10].indexOf("#")+1);
            netsUsed[nextNet++]=station.substring(5,13).trim();
          }
        }
        else use = icon.toUpperCase();
        //ArrayList<ADSLEpoch> staList = ADSLEpochs.getADSLEpochsObject().getADSLEpochs(station, begin, begin);
        //if(staList == null) continue;
        //if(staList.size() == 0) continue;
        Util.prt(ithr+" process station "+station+":"+stationList.get(k));
        kml.append( "       <Placemark>\n");
        kml.append("          <name>").append(station.substring(13,18).trim()).append("</name>\n");
        kml.append("          <Snippet maxLines=\"0\"></Snippet>\n");
        kml.append("          <description><![CDATA[<font face=\"Georgia\" size=\"4\"><table width=\"350\" cellpadding=\"2\" cellspacing=\"3\">\n" + "               <tr><th align=\"right\">Agency Code </th><td>").append(station.substring(0,5).trim()).append("</td></tr>\n" + "               <tr><th align=\"right\">Deployment Code </th><td>").append(station.substring(5,13).trim()).append("</td></tr>\n" + "               <tr><th align=\"right\">Location </th><td>").append(stationList.get(k).getSitename()).append("</td></tr>\n" + "               <tr><th align=\"right\">Latitude </th><td>").append(stationList.get(k).getLatitude()).append("</td></tr>\n" + "               <tr><th align=\"right\">Longitude </th><td>").append(stationList.get(k).getLongitude()).append("</td></tr>\n" + "               <tr><th align=\"right\">Elevation </th><td>").append(stationList.get(k).getElevation()).append("</td></tr>\n" + "               <tr><th align=\"right\">Channels </th><td>").append(stationList.get(k).getSEEDChannels()).append("</td></tr>\n" + "               <tr><th align=\"right\">Rates </th><td>").append(stationList.get(k).getSEEDRates()).append("</td></tr>\n" + "               <tr><th align=\"right\">StationID </th><td>").append(stationList.get(k).getStationID()).append("</td></tr>\n" + "               <tr><th align=\"right\">LocationID </th><td>").append(stationList.get(k).getLocationID()).append("</td></tr>\n"+
            "               </table></font>]]></description>\n");
        kml.append("          <LookAt>\n");
        kml.append("            <latitude>").append(stationList.get(k).getLatitude()).append("</latitude>\n");
        kml.append("            <longitude>").append(stationList.get(k).getLongitude()).append("</longitude>\n");
        kml.append("            <altitude>0</altitude>\n");
        kml.append("            <range>50000</range>\n");
        kml.append("            <tilt>0</tilt>\n");
        kml.append("            <heading>0</heading>\n");
        kml.append("          </LookAt>\n");
        kml.append("          <styleUrl>#").append(use.toUpperCase()).append("</styleUrl>\n");
        kml.append("          <Point><coordinates>").append(stationList.get(k).getLongitude()).append(",").append(stationList.get(k).getLatitude()).append(",").append(stationList.get(k).getElevation()).append("</coordinates></Point>\n");
        kml.append("        </Placemark>\n");
      }
      // put on end of document stuff
      kml.append("	  </Folder>\n"+
          "      <ScreenOverlay>\n"+
          "        <name>USGS Logo</name>\n"+
          "        <Icon><href>http://earthquake.usgs.gov/images/ge/USGSlogo.png</href></Icon>\n"+
          "        <overlayXY x=\"1\" y=\"0\" xunits=\"fraction\" yunits=\"pixels\"/>\n"+
          "        <screenXY x=\"0.82\" y=\"30\" xunits=\"fraction\" yunits=\"pixels\"/>\n"+
          "        <rotationXY x=\"0\" y=\"0\" xunits=\"pixels\" yunits=\"pixels\"/>\n"+
          "        <size x=\"0\" y=\"0\" xunits=\"pixels\" yunits=\"pixels\"/>\n"+
          "      </ScreenOverlay>\n"+
          "   </Document>\n"+
          "</kml>\n");
      return kml;
    }
    return null;
  }
  private void setColor(StringBuilder kml, String tag, String icon) {
    kml.append("      <Style id=\"").append(tag).
            append("\">\n" + "        <IconStyle>\n" + "          <scale>1.0</scale>\n" + "          <Icon><href>http://earthquake.usgs.gov/eqcenter/shakemap/global/shake/icons/")
            .append(icon).append(".png</href></Icon>\n"+
               "        </IconStyle>\n");
    kml.append("     </Style>\n");

  }

}       // end of class ADSLRequestHandler