/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.edge.config.RequestType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Timestamp;

/** This class uses a ConfigServer to gather information about the stations at a 
 * site and sets all of the continuous channels at the site fetch type.  Several
 * fetch types can be selected based on the instance numbers of the EdgeMom processes on 
 * the acquisition nodes and can be supplemented by IP address subnets of the Q330 stations.
 * An exclude RE can be used to make sure channels which should not be fetched and will be
 * added to the default ACE,
 * OCF, BC.*.
 * <p>
  * On computers with multiple EdgeMoms the I#N syntax sets the instance that is to be matched
 * with the Fetch type for that instance along with the RequestType.  The RequestTypes must already be
 * setup in the RequestType table using the RequestType panel.  These request types set the fetch table
 * to use, the method of fetching (set by the implementing class name), the IP and port to the Edge Nodes
 * and CWBs that would receive any fetched data.   Normally a request type is setup
 * for each instance so any fetched data received goes into the same instance as the real time data.
 * Each station processed from the ConfigServer as a Q330 station has its instance identified by 
 * connecting to the UdpChannel thread using a StatusSocketReader (this makes a list of
 * channels received their CPU, process, last received minute, latency etc al la ChannelDisplay).
 * <p>
 * Example:  Two EdgeMoms are running as instances 6#0 and 6#1.  They have MiniSeedServers running on ports
 * 10000 and 10001 respectively.  There are two RequestType setup Baler44, and Baler44B which send data
 * to the same computer at 6#0 and 6#1 on ports 10000 and 10001 respectively for the Edge computers and 
 * the same CWB if the data are outside of the realtime window..  The 
 * configuration for this program would be :
 * <PRE>
 * -subnets Q0/6#0/Baler44:Q1/6#1/Baler44B
 * </PRE>
 * This sets Q0 as the fetch type in each channel where the channel is part of 6#0 and sends any fetcher
 * output to the Baler44 RequestType which points to port 10000 and uses the Baler44 method of fetching
 * data from Q330s.  Similarly all channels for 6#1 are
 * marked for fetch type Q1 and send via the Baler44B RequestType to port 10001 and using
 * fetching method Baler44.  This handles all of the cases where the Edge/CWB computers which
 * acquire data from the stations can reach all subnets containing the Q330s.
 * <p>
 * LIMITING SUBNETS to certain computers - This can be really useful if Q330s are 
 * scattered about several (private) networking subnets that
 * are available only from certain computers.  Using the -subnet switch each subnet can be
 * given its own fetch type as well by adding the subnet and bits information to the instance and
 * RequestType above.  In the above :
 * <PRE>
 * -subnets Q0/6#0/Baler44/10.177.0.0/24:QA/6#0/Baler44/10.177.128.0/24:Q1/6#1/Baler44B
 * </PRE>
 * Divides up the Baler44 (6#0 instance) into two groups Q0 which is in subnet 10.177.0.0/24 
 * and the QA fetch type which goes to the same computer but is labeled QA.  This feature is not
 * often used as most installation the Edge/CWB computers have access to all Q330s.
 * <p>

 * <PRE>
 * switch    arg      Description 
 * -h      ip.adr    Of the ConfigServer
 * -p      port      Of the ConfigServer
 * -onetime          If present, exit after first configuration pass
 * -db    db.url     A Edge type URL to the edge.channel table to be updated (def=property DBServer)
 * -re      regexp   Only do stations matching this regular expression (def=none)
 * -exclude regexp   Do not update channels matching this regular expression plus ACE.*|OCF.*|BC.*)
 * -excludeStation nn-ssss:nn-ssss Exclude any station which starts with these ConfigServer stations name (CI-TEST matche TEST1, TEST2 etc)
 * -t      gaptype   Set the gap type to this (not usable with -subnet)
 * -subnet GP/I#N/rqstType[/subnet/bits]:G2/i#N/rqstType[/subnet2/bits2]....  Set gaptype GP to this subnet/bits for some number of subnets.
 * 
 *</PRE>
 * This class is useful to many users of the EdgeCWB software and was developed under a contract with
 * the California Institute of Technology in 2017.
 * 
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class FetcherConfigFromConfigServer extends EdgeThread {
  private boolean dbg;
  // ConfigServer parameters
  private String configHost;
  private int configPort;
  private String user;
  private String pw;
  
  //Flags and working variables
  private boolean oneTime;
  private int waitms=1800000;
  private DBConnectionThread db;
  private String statRE;
  private String excludeChanRE="ACE.*|OCF.*|BC.*";      // exclude
  private StatusSocketReader channels;
  private String [] excludeStations ;
  
  private final ArrayList<String> types = new ArrayList<>(5);
  private final ArrayList<String> instances = new ArrayList<>(5);
  private final ArrayList<Integer> subnets = new ArrayList<>(5);
  private final ArrayList<Integer> bits = new ArrayList<>(5);
  private final ArrayList<String> requestType = new ArrayList<>(5);
  private final ArrayList<RequestType> requestTypesDB = new ArrayList<>(5);
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
  @Override
  public void terminate() {terminate=true; interrupt();}
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }  
  public FetcherConfigFromConfigServer(String argline, String tag) {
    super(argline, tag);
    oneTime=false;
    String [] args = argline.split("\\s");
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equalsIgnoreCase("-h")) {configHost=args[i+1]; i++;}
      else if(args[i].equalsIgnoreCase("-p")) {configPort=Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equalsIgnoreCase("-u")) {user = args[i+1]; i++;}
      else if(args[i].equalsIgnoreCase("-pw")) {pw = args[i+1]; i++;}
      else if(args[i].equalsIgnoreCase("-onetime")) oneTime=true;
      else if(args[i].equalsIgnoreCase("-db")) {Util.setProperty("DBServer", args[i+1]); i++;}
      else if(args[i].equalsIgnoreCase("-status")) {Util.setProperty("StatusServer", args[i+1]); i++;}
      else if(args[i].equalsIgnoreCase("-re")) {statRE=args[i+1]; i++;}
      else if(args[i].equalsIgnoreCase("-exclude")) {excludeChanRE += "|"+args[i+1]; i++;}
      else if(args[i].equalsIgnoreCase("-excludestations")) {
        excludeStations = args[i+1].split(":");
        i++;
      }
      else if(args[i].equalsIgnoreCase("-t")) {
        if(!subnets.isEmpty()) prta("***** looks like both -t and -subnets is specified - this is not normal");
        types.add(args[i+1]);
        subnets.add(0);
        bits.add(0);
        i++;
      }
      else if(args[i].equalsIgnoreCase("-subnets")) {   // TT/I#N/ipadr[/bits]:TT/I#Nipadr[/bits]
        if(!types.isEmpty()) prta("***** looks like both -t and -subnets is specified - this is not normal");
        try {
          String [] parts = args[i+1].split(":");
          for(String part : parts) {
            String [] parts2=part.split("/");
            types.add(parts2[0]);
            instances.add(parts2[1]);
            requestType.add(parts2[2]);
            String subnet="0.0.0.0";
            if(parts2.length >= 4) subnet=parts2[3];
            String bit="";
            if(parts2.length >= 5) bit= parts2[4];
            int size;
            if(bit.trim().equals("")) size=33;
            else size = Integer.parseInt(bit);

            InetAddress inet = InetAddress.getByName(subnet);
            byte [] bytes = inet.getAddress();
            long adr=0;
            for(int k=0; k<4; k++) adr = (adr << 8) + ((int) bytes[k] & 0xFF);
            subnets.add((int) (adr & 0xFFFFFFFF)); 
            bits.add(size);
            if(dbg) 
              prt("Subnet2 : "+parts2[0]+" "+parts2[1]+" "+Util.toHex(adr)+" "+inet+"/"+size);
          }
        }
        catch(NumberFormatException | UnknownHostException e) {
          prta("Exception parsing -subnets argument should be TT/ipadr/size:TT/ipadr/size="+args[i+1]);
          e.printStackTrace(getPrintStream());
        }
        i++;
      }
      else if(args[i].substring(0,1).equals(">")) break;
      else prta("***** Unknown argument at i="+i+" arg="+args[i]+" argline="+argline);
    }

    running=true;
    start();
  }
  @Override
  public void run() {
    try {
      //prta("Start DB to "+Util.getProperty("DBServer"));
      db = new DBConnectionThread(Util.getProperty("DBServer"), "update",
              "edge",  true, false, "edgeFCFCS", getPrintStream());
      this.addLog(db);
      if(!db.waitForConnection())
        if(!db.waitForConnection())
          if(!db.waitForConnection())
            if(!db.waitForConnection()) {
              prta(tag+" Cannot setup connection to DBServer="+Util.getProperty("DBServer"));
              Util.exit("FetcerConfigFromConfigServer");
            }
      addLog(db);
    }
    catch(InstantiationException e) {
      prta("Impossible Instantiation problem");
      e.printStackTrace(getPrintStream());
    }
    Timestamp now = new Timestamp(System.currentTimeMillis());
    //("Start an SSR on "+Util.getProperty("StatusServer")+"/"+AnssPorts.CHANNEL_SERVER_PORT);
    channels = new StatusSocketReader(ChannelStatus.class, Util.getProperty("StatusServer"),AnssPorts.CHANNEL_SERVER_PORT, this);
    int nmsgs;
    int nmsgs2=-10000;
    for(;;) {
      try {sleep(4000);} catch(InterruptedException e) {}
      nmsgs = channels.getNmsgs();
      //prta("Wait for SSR nsmgs="+nmsgs+" "+nmsgs2+" diff="+(nmsgs - nmsgs2));
      if( (nmsgs - nmsgs2) < 10) break;
      nmsgs2=nmsgs;
    }
    //prta("StatusSocketReader stable at "+nmsgs+" channels configserver="+configHost+"/"+configPort+" excludes="+excludeChanRE+" "+user+"/"+pw);
    
    Object [] msgs = new Object[20000];
    // Connect to configserver
    Q330ConfigServer cs = new Q330ConfigServer(configHost, configPort,user, pw, getPrintStream());
    while(!terminate ) {
      
      try ( // Load the request type table
              ResultSet rs = db.executeQuery("SELECT * FROM edge.requesttype ORDER BY requesttype")) {
        requestTypesDB.clear();
        while(rs.next()) requestTypesDB.add(new RequestType(rs));
      }
      catch(SQLException e) {
        prta("Abort:  could not load edge.requesttype table! e="+e);
        Util.exit("DB Failure on requesttype table");
      }
      //prta("#requesttypes="+requestTypesDB.size());

      ArrayList<String> stations = cs.getStations();      // Get list of stations (NN-STAT form)
      
      for(String station : stations) {
        if(statRE != null) 
          if(!station.matches(statRE)) 
            continue;
        boolean found = false;
        for(String stat: excludeStations) {
          if(station.startsWith(stat)) {found=true; break;}
        }
        if(found) {
          prta("** skip station "+station+" its on the -excludestation list");
          continue;
        }       // Station is on exclude list
        String network = station.substring(0,2);
        String stationCode = station.substring(3).trim();
        String scnl = network+stationCode;
        
        Q330ConfigServerXML stat = cs.getXMLForStation(station);
        if(stat == null) {
          prta(station + " * does not have an XML");
          continue;
        }
        
         // Now see if it matches the Intstance
        nmsgs = channels.getObjects(msgs);
        ChannelStatus status = null;
        String inst="";
        for(int i=0; i<nmsgs; i++) {
          status = (ChannelStatus) msgs[i];
          if(status.getKey().substring(0,7).trim().equals(scnl)) {   // its the right network and station
            String channel = status.getKey().substring(7,10);
            if(channel.matches("[BHSE]HZ")) {
              if(status.getAge() < 30.) {
                if(status.getCpu().contains("#")) {
                  inst = status.getCpu().trim();
                  break;
                }
              }
            }
          }
        }
        if(inst.equals("")) continue;     // no instance, make no changes.
        prta(station+" instance="+inst+" status="+status+" "+(status != null && status.getAge() > 30.?"*":""));
        int mask=0;
        int ladr = 0;
        boolean instanceFound=false;
        // We know the instance so check which of the configuration matches instance and optional subnet
        for(int i=0; i<subnets.size(); i++) {
          if(inst.equals(instances.get(i))) {
            instanceFound = true;
            try {
              boolean subnetOK=true;
              if(bits.get(i) != 33) {
                mask=(int) (0xFFFFFFFFL << 32 - bits.get(i));      // high order bit set
                InetAddress adr = InetAddress.getByName(stat.getQ330IPAdrDef());

                byte [] bytes = adr.getAddress();
                ladr = 0;
                for(int k=0; k<4; k++) ladr = (ladr << 8) + ((int) bytes[k] & 0xFF);

                if((ladr & mask) != subnets.get(i)) subnetOK=false;
              }
              if(subnetOK) {   // does it match the subnet

                if(dbg)
                  prt(station+" Match found between "+inst+" ip="+stat.getQ330IPAdrDef()+" to "+subnets.get(i)+" "+bits.get(i)+" mask="+Util.toHex(mask)+" type="+types.get(i));
                String s = "UPDATE edge.channel SET gaptype='"+types.get(i)+"' WHERE channel regexp '^"+
                          station.replaceAll("-", "").trim()+"' AND lastdata > subdate(now(), INTERVAL 30 DAY)"+
                        (excludeChanRE != null?" AND NOT substring(channel, 8) REGEXP '"+excludeChanRE+"'":"");
                //prta("Set channel : "+s);
                db.executeUpdate(s);
                
                // Gather the data we will need to update the requeststation table
                int requestID = -1;
                for(RequestType type: requestTypesDB) {
                  if(type.getRequestType().equals(requestType.get(i))) {requestID=type.getID(); break;}
                }
                String requestIP = stat.getQ330IPAdrDef();
                int port = stat.getQ330BasePortDef();
                if(port == 5330 || port == 6330 || port == 7330 || port == 8330) port = port+51;
                String fetchType=types.get(i);
                
                // Here we need to maintain the request station so point this gap type to the right IP and port
                ResultSet rs = db.executeQuery("SELECT * FROM edge.requeststation WHERE requeststation ='"+station.replaceAll("-", "")+"'");
                if(rs.next()) {
                  prt(" update "+station+" requestip="+requestIP+"/"+port+" type="+fetchType);
                  now.setTime(System.currentTimeMillis());
                  rs.updateString("requestip",requestIP);
                  if(requestID > 0) rs.updateInt("requestid", requestID);
                  rs.updateInt("requestport", port);
                  rs.updateString("fetchtype", fetchType); 
                  rs.updateTimestamp("updated", now);
                  rs.updateRow(); // DEBUG: Do not do any updates!
                  rs.close();
                }
                else {
                  s = "INSERT INTO edge.requeststation (requeststation,requestid,"+
                          "fetchtype,throttle,requestip,requestport,channelre,updated,created_by,created) VALUES ("+
                          "'"+scnl+"',"+requestID+",'"+fetchType+"',10000,'"+requestIP+"',"+port+",'"+
                          (scnl+"      ").substring(0,7)+"...',now(),0,now())";
                  //prta("Insert requeststation: "+s);
                  rs.close();
                  db.executeUpdate(s);
                }
                break;          // Do not look past first match for configuration
              }
              else {
                prta(station+" ** does not match subnet. "+bits.get(i)+" msk="+Util.toHex(mask)+" ladr="+ladr);
              }
            }
            catch(SQLException e) {
              prta("Got SQL exception e="+e);
              e.printStackTrace(getPrintStream());
            } catch (UnknownHostException e) {
              prta("Unknown host exception e="+e);
              e.printStackTrace(getPrintStream());
            }  
          }       // if instance matches

        }         // loop on all subnets (configuration of Gap types, instances, etc.)
        if(!instanceFound) {
          prta(station +" *** Instance NOT found!  inst="+inst+" instances="+
                  instances.get(0)+"|"+(instances.size() > 1?instances.get(1)+"|":""));
        }
      }           // Loop over all the stations in the config server
      if(oneTime) break;      // leave the loop
      try {sleep(waitms);} catch(InterruptedException e) {}
      cs.getData(getPrintStream());
    }       // infinite loop on !terminate
    running=false;
    prta("FetcherConfigFromConfigServer is exiting!");
  }
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    Util.setProcess("FetcherConfigFromConfigServer");
    EdgeThread.setMainLogname("fetcherconfig");
    // Caltech import1.gps.caltech.edu:8133 timer/timer123
    if(args.length == 0) {
      FetcherConfigFromConfigServer fc = new FetcherConfigFromConfigServer(
            "-dbg -db localhost/3306:edge:mysql:edge -h import.gps.caltech.edu -p 8133 -u timer -pw timer123 -status localhost -onetime "+
            "-subnets Q0/6#0/Bal44_0:Q1/6#1/Bal44_1 -exclude L1Z.* -excludestations CI-DSN:CI-DSN3:CI-DSN5:CI-DSN6:CI-Q335:CI-TEST>fetcherconfig", "FCFCS");
      while(fc.isAlive()) Util.sleep(2000);
      System.exit(0);
    }
    String argline="";
    for (String arg : args) argline += arg + " ";
    FetcherConfigFromConfigServer fc = new FetcherConfigFromConfigServer(argline, "FCFCS");
    while(fc.isAlive()) Util.sleep(2000);
    System.exit(0);
  }
}
