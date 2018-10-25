/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import java.io.*;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This static class is used to interrogate SeedLink servers for stations and
 * build lists of stations matching a network code. Used initially to make lists
 * of stations in 'N4' network code so new stations could be discovered.
 *
 * <PRE>
 * switch  arg      Description
 * -h   URL/Port Use this seedlink server (e.g. rtserve.iris.washington.edu/18000)
 *               Note: the / is converted to a colon for the slinktool -Q command.
 * -s   NNSSSSS  This is the Network then station code i.e USAGMN, regular
 *               expressions are allowed, US|UW  or PTKHU|GSOK.|USAH.D
 *               NOTE: underscores are converted to spaces
 * -c   CCCLL    This is the channel the location code, again regular
 *               expressions are allowed, HN.|HH.|BH.00|LH.   Note: the location
 *               code comes after the channel and when tested the two are
 *               put together as one word.
 *               The output will only group channels using "?" if there is a wildcard
 *               used with -c.  So if all channels are wanted with grouping
 *               using -c . will provide that functionality
 *               NOTE: underscores are converted to spaces
 * !!! If either -s or -c is not used it is treated as a wild card and all
 *        stations or channels are included.  If both are not included then
 *        everything found from the slinktool -Q query is included.
 * -f  file      Write out this output file
 * -1            Create the file and then exit, the "one time" flag.
 *
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class SeedLinkConfigBuilder extends EdgeThread {

//  private List<String> currentConfig;
//  private List<String> newConfig = new ArrayList<>();
  private StringBuilder currentConfig = new StringBuilder(1000);
  private StringBuilder newConfig = new StringBuilder(1000);
  private String seedLinkURL;
  private String outputfile;
  private Pattern stationPattern = null;
  private Pattern channelPattern = null;
  private Pattern channelWildcardPattern = null;
  private boolean hasChannelWilds = false;
  private boolean oneTime;
  private final boolean dbg;
  private final StringBuilder testChanBuilder = new StringBuilder(5);
  private final StringBuilder inputLine = new StringBuilder(70);
  private final StringBuilder station = new StringBuilder(7);
  private final StringBuilder channel = new StringBuilder(5);
  private final StringBuilder oldStation = new StringBuilder(7);
  private final StringBuilder oldChannel = new StringBuilder(5);
  private final StringBuilder outputLine = new StringBuilder(70);
  private final StringBuilder location = new StringBuilder(2);
  private EdgeThread parentThread;
  private final CountDownLatch readyCountDown;

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    monitorsb.setLength(0);
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    statussb.setLength(0);
    return statussb;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  public SeedLinkConfigBuilder(String argline, String tg) {
    this(null, argline, tg);
  }

  public SeedLinkConfigBuilder(EdgeThread parentThread, String argline, String tg) {
    super(argline, tg);
    if (parentThread != null) {
      readyCountDown = new CountDownLatch(1);
    } else {
      readyCountDown = null;
    }
    this.parentThread = parentThread;
    String[] args = argline.split("\\s");
    dbg = false;
    if (dbg) {
      prta("SeedlinkConfigBuilder debug has been set!!!!");
    }
    seedLinkURL = "rtserve.iris.washington.edu:18000";
//    net = "N4";
    outputfile = "/Users/NB/tmp/test.setup";
    String station = null;
    String channel = null;
    if (dbg) {
      station = "HV|XU";
      channel = ".";

    }
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-h":
          seedLinkURL = args[i + 1].replaceAll("/", ":");
          i++;
          break;
        case "-s":
          station = args[i + 1].replaceAll("_", " ");
          i++;
          break;
        case "-c":
          channel = args[i + 1].replaceAll("_", " ");
          i++;
          break;
        case "-f":
          outputfile = args[i + 1];
          i++;
          break;
        case "-1":
          oneTime = true;
          break;
      }
    }
    if (station == null) {
      station = ".*";
    } else {
      station = "^(" + station + ").*";
    }
    StringBuilder patBuilder = new StringBuilder();
    if (channel == null) {
      channel = ".*";
      channelWildcardPattern = Pattern.compile(".*");
    } else {
      patBuilder.append("(");
      String[] channelPats = channel.split("\\|");
      for (String channelPat : channelPats) {
        if (channelPat.matches("(.*[.].*|.*[*].*)")) {
          hasChannelWilds = true;
          if (patBuilder.length() > 1) {
            patBuilder.append(".*|");
          }
          patBuilder.append(channelPat.trim());
        }
      }
      patBuilder.append(".*)");
      channel = "^(" + channel + ").*";
    }
    try {
      stationPattern = Pattern.compile(station);
      channelPattern = Pattern.compile(channel);
      if (hasChannelWilds) {
        channelWildcardPattern = Pattern.compile(patBuilder.toString());
      }
    } catch (PatternSyntaxException e) {
      prta("SeedLinkConfigBuilder:  Error!  Invalid regex pattern parameter.  " + e.getDescription());
      running = false;
      return;
    }

    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    running = true;
    boolean isSame;
    while (!terminate) {
      try {
        isSame = makeFileSeedLink(seedLinkURL, outputfile, stationPattern, channelPattern);
        if (!isSame || dbg) {
          prta("SLBC: File change detected same=" + isSame);
        } else {
          prta("SLBC: Files are the same");
        }
        if (oneTime) {
          break;     // This is a one time run
        }
        if (readyCountDown != null) {
          readyCountDown.countDown();
        }
        try {
          if (dbg) {
            sleep(10000);
          } else {
            sleep(3600000);
          }
        } catch (InterruptedException expected) {
        }
      } catch (Exception e) {
        e.printStackTrace(getPrintStream());
      }
      if (parentThread != null) {
        if (!parentThread.isAlive()) {
          break;
        }
      }
    }
    running = false;
    prta("SLCB: exiting");
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly  

  /**
   * When this thread is started by another thread this method is used to make
   * sure that this thread has had at least one pass at building the
   * configuration file.
   *
   * @param timeOutSeconds time to wait for start in seconds
   * @return
   */
  public boolean waitForStart(long timeOutSeconds) {
    if (readyCountDown == null) {
      return false;
    }
    boolean started;
    try {
      started = readyCountDown.await(timeOutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      started = false;
    }
    if (!started) {
      Util.prt("SeedLinkConfigBuilder:waitForStart:: Warning!!! readyCountDown await timed out or was interrupted. ");
    }
    return started;
  }

  /**
   * Create a SeedLink config file for a given network from the given seedlink
   * server.
   *
   * @param seedlinkHost The URL for the seedlink (e.g.,
   * rtserve.iris.washington.edu:18000).
   * @param outFile The output file name to create.
   * @param stationPattern The network and station code to match.
   * @param channelPattern The channel name to match.
   * @return True if the file is the same as the last time.
   *
   * Expects the following format in return from slinktool -Q seedlinkHost: HV
   * AHUD 01 BDF D 2018-08-13 08:55:07 - 2018-08-13 18:25:11 HV AHUD 02 BDF D
   * 2018-08-13 08:55:07 - 2018-08-13 18:25:06
   */
  public boolean makeFileSeedLink(String seedlinkHost, String outFile, Pattern stationPattern, Pattern channelPattern) {
    boolean isSame = false;
    BufferedReader stdInput;
    BufferedReader stdError = null;
    Matcher stationMatcher;
    Matcher channelMatcher;
    Matcher channelWildMatcher;

//    String[] fields = new String[4];
    try {
      if (dbg) {
        prta("SLBC: Start subprocess bin/slinktool -Q " + seedlinkHost);
        FileReader fr = new FileReader("/Users/NB/tmp/slcTest.txt");
        stdInput = new BufferedReader(fr);
      } else {
        //For this method to work properly the output from slinktool must be alphabetical.
        //currently it is, but if for some reason it isn't this call could be replaced with
        //a script that calls slinktool and then pipes to output to sort.  A script would
        //be necessary becasue the .exec call that .Subprocess uses does not directly dump the
        //string to a shell, so essentially only one command can actually be run and things
        //like pipes can't be used.  However a single script can be called that can do all that.
        gov.usgs.anss.util.Subprocess sp = new gov.usgs.anss.util.Subprocess("bin/slinktool -Q " + seedlinkHost);
//          gov.usgs.anss.util.Subprocess sp = new gov.usgs.anss.util.Subprocess("/Users/bmielke/testit " + seedlinkHost);
        try { // Wait a maximum of 3 minutes for a response
          if (sp.waitFor(120000, this) == -99) {
            prta(tag + "SLP: *** reload timed out! ");
            return true;
          }
        } catch (InterruptedException e) {
          prta("SLBC: slinktool load interrupted");
          return true;
        }
        stdInput = new BufferedReader(new StringReader(sp.getOutput()));
        stdError = new BufferedReader(new StringReader(sp.getErrorOutput()));
      }
      String line;
      newConfig.setLength(0);
      newConfig.append("#Automatically created by SeedLinkClient ConfigBuilder\n");
      oldStation.setLength(0);
      oldChannel.setLength(0);
      station.setLength(0);
      channel.setLength(0);
      outputLine.setLength(0);
      boolean firstPass = true;
      while (Util.stringBuilderReadline(stdInput, inputLine) > 0) {
        if (inputLine.length() < 15) {
          continue;
        }
        //for stringBuilder the following "fields" are use        
        //fields[0] = line.substring(0,2);  NN
        //fields[1] = line.substring(3,8);  SSSSS        
        //fields[2] = line.substring(9,11); LL
        //fields[3] = line.substring(12,15); CCC
        station.setLength(0);
        stationMatcher = stationPattern.matcher(station.append(inputLine.subSequence(0, 2)).
                append(inputLine.subSequence(3, 8)));

        if (stationMatcher.matches()) {
          testChanBuilder.setLength(0);
          //channelMatcher = channelPattern.matcher(fields[3] + fields[2]);
          channelMatcher = channelPattern.matcher(testChanBuilder.append(inputLine.subSequence(12, 15)).
                  append(inputLine.subSequence(9, 11)));
          if (channelMatcher.matches()) {
            if (firstPass) {
              outputLine.append(station.subSequence(0, 2)).append(" ").append(station.subSequence(2, 7)).append(" ");
              oldStation.append(station);
              firstPass = false;
            }
            channel.setLength(0);
            channel.append(inputLine.subSequence(9, 11)).append(inputLine.subSequence(12, 15));
            //newConfig.add(fields[0] + " " + fields[1] + " " + fields[2] + fields[3]);
            if (Util.compareTo(station, oldStation) != 0 && outputLine.length() > 0) {
              newConfig.append(outputLine).append("\n");
              outputLine.setLength(0);
              outputLine.append(station.subSequence(0, 2)).append(" ").append(station.subSequence(2, 7)).append(" ");
              oldStation.setLength(0);
              oldStation.append(station);
              oldChannel.setLength(0);
            }
//            if (channel.charAt(0) != ' ') {
            location.setLength(0);
            if (channel.charAt(0) != ' ') {
              location.append(" ").append(channel.subSequence(0, 2));
            } else {
              location.append(" ");
            }

            if (hasChannelWilds) {
              channelWildMatcher = channelWildcardPattern.matcher(testChanBuilder);
              if (channelWildMatcher.matches()) {
                if (Util.sbCompareTo(channel, oldChannel, 4) == 0) {
                  if (oldChannel.charAt(4) != '?') {
                    outputLine.replace(outputLine.length() - 1, outputLine.length(), "?");
                    oldChannel.replace(4, 5, "?");
                  }
                } else {
                  outputLine.append(location).append(channel.subSequence(2, 5));
                  oldChannel.setLength(0);
                  oldChannel.append(channel);
                }
              } else {
                outputLine.append(location).append(channel.subSequence(2, 5));
                oldChannel.setLength(0);
                oldChannel.append(channel);
              }
            } else {
              outputLine.append(location).append(channel.subSequence(2, 5));
              oldChannel.setLength(0);
              oldChannel.append(channel);
            }
          }
        }
      }

      newConfig.append(outputLine).append("\n");

      if (!dbg) {
        while ((line = stdError.readLine()) != null) { //returns errors from slinktool command
          prta(tag + "stderr=" + line);
        }
      }
      stdInput.close();
      if (Util.compareTo(newConfig, currentConfig) == 0) {
        isSame = true;
      } else {
        isSame = false;
        File f = new File(outFile);       // Get ready to move the old file
        if (f.exists()) {
          f.renameTo(new File(outFile + "_old"));
        }
        Util.writeFileFromSB(outFile, newConfig);
        try {
          gov.usgs.anss.util.Subprocess sp = new gov.usgs.anss.util.Subprocess("diff " + outFile + " " + outFile + "_old");
          sp.waitFor();
          prta("Diffs:\n" + sp.getOutput() + sp.getErrorOutput());
        } catch (InterruptedException e) {
          prta("Got InterruptedException trying to do diff");
        } catch (RuntimeException e) {
          prta("Got Runtime error doing diff e=" + e);
          e.printStackTrace(getPrintStream());
        }
        currentConfig.setLength(0);
        currentConfig.append(newConfig);
      }
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
    } catch (RuntimeException e) {
      e.printStackTrace(getPrintStream());
    }
    return isSame;
  }

  /**
   * This main setups the network code, SeedLink URL, filename and network. It
   * then calls the routine to get the information every 10 minutes.
   *
   * @param args
   */
  public static void main(String[] args) {
    Util.setProcess("slconfig");
    String argline = "";
    if (args.length == 0) {
      argline = "-1 -s US.* -c .H[ZNE12] -h 137.227.224.97/18000 -f temp.setup";
    } else {
      for (String arg : args) {
        argline = argline + " " + arg;
      }
    }
    try {
      SeedLinkConfigBuilder scb = new SeedLinkConfigBuilder(argline, "SLCB");
      for (;;) {
        if (!scb.isAlive()) {
          break;
        }
        Util.sleep(1000);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
