/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwb;

import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgemom.EdgeMom;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgemom.Version;
import gov.usgs.anss.util.Util;

/**
 * This is the main dispatcher class for the the unified CWB project for all
 * code.
 *
 * This is always called with the first argument a command, the second argument
 * a instance, with optional arguments :Q
 * <PRE>
 * argument
 * -max:lower:upper  Run with -XX:NewSize=lower and -XX:MaxNewSize=upper, def=lower=100, upper=200
 *                   Note: this is ignored here as the bash script handles it
 * -test             Run from Jars directory and not from bin - handled by bash script
 * args              All of these args are passed to the program
 *
 * </PRE>
 *
 * @author U.S. Geological Survey ketchum at usgs.gov
 */
public class CWB {

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoInteractive(true);
    EdgeThread.setMainLogname("unknown");
    for(int i=0; i<args.length;i++) {
      if (args[i].equals("-version")) {
        System.out.println(Version.version);
        EdgeMom.main(args);
        System.exit(0);
      }
    }
    String command;
    String instance = "^";
    String memory = null;
    boolean dumpIt = false;
    String extraProp = null;
    if (args.length < 1) {
      System.out.println("*** CWB() requires at least one argument!");
      System.exit(0);
    }
    if (args.length >= 2) {
      System.out.println("CWB: dispatcher cmd=" + args[0] + " instance=" + args[1]);
    }
    command = args[0];
    int start = 1;
    if (args.length >= 2) {
      if (!args[1].startsWith("-")) {
        instance = args[1].trim();
        if (instance.equalsIgnoreCase("null")) {
          instance = "^";
        }
        start = 2;
      }
    }
    if (args.length >= 3) {
      if (!args[2].startsWith("-")) {
        start = 3;
        memory = args[2];
      }
    }
    for (int i = start; i < args.length; i++) {
      if (args[i].contains("-max")) {
        start = i + 1;
      } else if (args[i].equals("-test")) {
        start = i + 1;
      } else if (args[i].equals("-tag")) {
        start = i + 1;
      } else if (args[i].equals("-!")) {
        dumpIt = true;
      }
      else if (args[i].equals("-prop")) {
        extraProp = args[i+1];
        i++;
      }
      else if (args[i].equals("-version")) {
        System.out.println(Version.version);
        System.exit(0);
      }
    }
    String[] argsout = new String[Math.max(0, args.length - start) + 2];

    argsout[0] = "-instance";
    argsout[1] = instance;
    EdgeThread.setInstance(instance);
    Util.init("edge.prop");           // always do the default prop
    Util.loadProperties("edge.prop"); // Old school, just incase
    Util.loadProperties("edge_" + Util.getSystemName().trim() + ".prop");//  server props
    if (!instance.equals("^")) {
      System.out.println("Load instance properties : EDGEMOM/edge_" + instance.replaceAll("Q", "#") + ".prop");
      Util.prtProperties(System.out);
      Util.loadProperties("EDGEMOM/edge_" + instance.replaceAll("Q", "#") + ".prop");  // instance based edge props
      Util.prtProperties(System.out);
    } // Then do the instance one
    if(extraProp != null) {
      Util.loadProperties(extraProp);
    }
    System.out.println(Util.ascdatetime(null) + " cmd=" + command + " instance=" + instance + " mem=" + memory + " start=" + start);
    String originalNode = Util.getProperty("Node");
    //if(originalNode == null) 
    System.out.println("OriginalNode=" + originalNode + " Node now=" + instance
            + " log=" + Util.getProperty("logfilepath") + " DBServer=" + Util.getProperty("DBServer")
            + " node=" + Util.getProperty("Node") + " instance=" + instance);
    if (originalNode == null) {
      Util.exit("CWB - the Node property is not defined!");
    }
    if (!instance.equals("^")) {
      Util.setProperty("Instance", instance);
    } else {
      Util.setProperty("Instance", "0#0");
    }
    for (int i = start; i < args.length; i++) {
      argsout[i - start + 2] = args[i];
      System.out.println("args[" + (i - start + 2) + "]=" + argsout[i - start + 2]);
    }  //remove the command
    for (int i = 0; i < argsout.length; i++) {
      Util.prt("Argsout[" + i + "]=" + argsout[i]);
    }
    if (dumpIt) {
      Util.prtProperties(System.out);
      System.out.println("systemname=" + Util.getSystemName() + " systemnumber=" + Util.getSystemNumber()
              + " Node(prop)=" + Util.getNode() + ":" + Util.getProperty("Node"));
      System.out.println("Index.getNode()=" + IndexFile.getNode() + " so files yyyy_ddd_" + IndexFile.getNode());
      System.exit(0);
    }
    command = command.toLowerCase();
    if (command.equals("query")) {
      gov.usgs.anss.cwbqueryclient.EdgeQueryClient.main(argsout);
    } else if (command.equals("cwbquery")) {
      gov.usgs.anss.cwbqueryclient.EdgeQueryClient.main(argsout);
    } else if (command.equals("edgemom")) {
      gov.usgs.anss.edgemom.EdgeMom.main(argsout);
    } else if (command.equals("querymom")) {
      gov.usgs.cwbquery.QueryMom.main(argsout);
    } else if (command.equals("cmd") || command.equals("commandstatusserver")) {
      gov.usgs.alarm.CommandStatusServer.main(argsout);
    } else if (command.equals("dfw") || command.equals("dailyfilewriter")) {
      gov.usgs.anss.edgemom.DailyFileWriter.main(argsout);
    } else if (command.equals("ewexport")) {
      gov.usgs.anss.ewexport.EWExport.main(argsout);
    } else if (command.equals("fetcher")) {
      gov.usgs.anss.fetcher.FetcherMain.main(argsout);
    } else if (command.equals("fetchserver")) {
      gov.usgs.anss.fetcher.FetchServerMain.main(argsout);
    } else if (command.equals("holdingsconsolidate")) {
      gov.usgs.anss.net.HoldingsConsolidate.main(argsout);
    } else if (command.equals("liss") || command.equals("lissserver")) {
      gov.usgs.anss.liss.LISSServer.main(argsout);
    } else if (command.equals("cwbconfig")) {
      gov.usgs.alarm.MakeVsatRoutes.main(argsout);
    } else if (command.equals("moveholdingshist")) {
      gov.usgs.edge.config.MoveToHistory.main(argsout);
    } else if (command.equals("makeholdingssummary")) {
      gov.usgs.edge.config.MoveToHistory.main(argsout);
    } else if (command.contains("dbserve") || command.equals("pocmysql")) {
      gov.usgs.alarm.POCServer.main(argsout);
    } else if (command.equals("tcpholdings")) {
      gov.usgs.anss.net.TcpHoldings.main(argsout);
    } else if (command.equals("udpchannel")) {
      gov.usgs.alarm.UdpChannel.main(argsout);
    } else if (command.equals("rrpclient")) {
      gov.usgs.anss.net.RRPClient.main(argsout);
    } else if (command.equals("rrpserver")) {
      gov.usgs.anss.net.RRPServer.main(argsout);
    } else if (command.equals("alarm")) {
      gov.usgs.alarm.Alarm.main(argsout);
    } else if (command.equals("slconfig")) {      // Is this a good idea?  Normally run SeedLinkConfig builder in EdgeMom
      gov.usgs.anss.edgemom.SeedLinkConfigBuilder.main(argsout);
    } else {
      System.out.println("key word is not known to CWB dispatcher =" + command);
    }

  }
}
