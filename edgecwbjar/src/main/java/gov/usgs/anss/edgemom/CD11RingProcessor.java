/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.OutOfOrderRTMS;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11.ChannelSubframe;
import gov.usgs.anss.cd11.CD11Frame;
import gov.usgs.anss.cd11.CanadaException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * The class processes data gathered into the ring file for sending via CD1.1.
 * Its command line comes from the caller. This code appears to be obsolete. DCK
 *
 * @author davidketchum
 */
public final class CD11RingProcessor extends EdgeThread {

  private int oorint;     // The OOR interval in seconds
  private final OutOfOrderRTMS oorProcessor;
  private String filename;
  private RawDisk ring;
  private int next;
  private int lastOut;
  private int recSize;
  private int maxRec;
  private byte[] hdr = new byte[512];
  private ByteBuffer bbhdr;
  private boolean dbg;
  private int nprocess;
  private String network;         // The network to assign data received from this source
  private String overrideStation; // If this not null, override the station name to be this for stations from this source.
  private boolean overrideLocation;
  private ChannelSender csend;

  private byte[] buf;
  private ByteBuffer bb;
  private StringBuilder tmpsb = new StringBuilder(50);

  @Override
  public StringBuilder getStatusString() {
    return statussb.append("#proc=").append(nprocess).append(" file=").append(filename).
            append(" next=").append(next).append(" last=").append(lastOut).append(" recsiz=").
            append(recSize).append(" maxrec=").append(maxRec);
  }

  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    prt(tag + " CD11RingProcess terminate called");
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("next=").append(next).append(" last=").append(lastOut).append(" recsiz=").
              append(recSize).append(" maxrec=").append(maxRec);
    }
    return sb;
  }

  public CD11RingProcessor(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    network = "IM";
    boolean nocsend = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-oorint")) {
        oorint = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-ring")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-fn")) {
        network = (args[i + 1] + "  ").substring(0, 2);
        i++;
      } else if (args[i].equals("-fs")) {
        overrideStation = (args[i + 1] + "      ").substring(0, 5);
        i++;
      } else if (args[i].equals("-fl")) {
        overrideLocation = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].charAt(0) == '>') ; else {
        prta(Util.clear(tmpsb).append("Unknown argument to CD11RingProcessor=").append(args[i]));
      }
    }
    bbhdr = ByteBuffer.wrap(hdr);
    // There has to be a ring file for us to start!!!
    for (;;) {
      try {
        ring = new RawDisk(filename, "r");
        readOutputHeader();

        ring.readBlock(hdr, 1, 512);
        bbhdr.position(0);
        next = bbhdr.getInt();
        if (next > maxRec) {
          prta(Util.clear(tmpsb).append(tag).append("On startup next was out of file max range - reset it ").
                  append(next).append(" >").append(maxRec));
          next = 0;
        } else {
          prta(Util.clear(tmpsb).append(tag).append("Next on startup=").append(next));
        }
        buf = new byte[recSize];      // Create input buffer of the correct size
        bb = ByteBuffer.wrap(buf);
        ring.close();
        ring = new RawDisk(filename, "rw");
        break;
      } catch (FileNotFoundException e) {
        prta(Util.clear(tmpsb).append(tag).append("Ring file not found =").
                append(filename).append(" wait for it to open"));
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      } catch (IOException e) {
        prt(Util.clear(tmpsb).append(tag).append("Ring file IOerror e=").append(e));
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "CD11", "CD11-" + tg);
    }

    oorProcessor = new OutOfOrderRTMS(oorint, tag, this);
    start();
  }

  private void writeHeader() throws IOException {
    bbhdr.position(0);
    bbhdr.putInt(next);
    ring.writeBlock(1, hdr, 0, 512);
  }

  private void readOutputHeader() throws IOException {
    ring.readBlock(hdr, 0, 512);
    bbhdr.position(0);
    lastOut = bbhdr.getInt();
    maxRec = bbhdr.getInt();
    recSize = bbhdr.getInt();
  }

  @Override
  public void run() {
    running = true;
    int dataNChan;
    int dataFrameMS;
    String nominalTime;
    GregorianCalendar nominal = new GregorianCalendar();
    GregorianCalendar g;
    GregorianCalendar g2 = new GregorianCalendar();
    int channelStringCount;
    byte[] timeScratch = new byte[20];
    ChannelSubframe csf = null;
    int[] samples = new int[400];
    String seedname;
    StringBuilder seednameSB = new StringBuilder(12);
    StringBuilder runsb = new StringBuilder(12);
    int dataQuality;
    int ioFlags;
    int clockQuality;
    int activity;
    int usec;
    while (!terminate) {
      lastOut = next;
      while (lastOut == next) {
        try {
          readOutputHeader();
        } catch (IOException e) {
          prta(Util.clear(runsb).append("IOError reading otput header e=").append(e));
          e.printStackTrace(getPrintStream());
        }
        if (terminate) {
          break;
        }
        if (lastOut == next) {
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
        }
      }
      if (terminate) {
        break;
      }
      String lastNominal = "";
      // Got data to process
      while (next != lastOut) {
        try {
          if (terminate) {
            break;
          }
          ring.readBlock(buf, next * ((recSize + 511) / 512) + 2, recSize);
          next++;
          if (next >= maxRec) {
            next = 0;
          }
          // Decode parts of the data frame header
          bb.position(0);
          dataNChan = bb.getInt();
          dataFrameMS = bb.getInt();   // Length of time represented by this data frame
          bb.get(timeScratch);         // Get 20 bytes of time
          nominalTime = new String(timeScratch);
          CD11Frame.fromCDTimeString(nominalTime, nominal, this);
          channelStringCount = bb.getInt();
          if (channelStringCount % 4 != 0) {
            channelStringCount += 2;
          }
          boolean cd10 = (dataNChan & 0xff0000) != 0;
          dataNChan = dataNChan & 0xffff;
          if (dbg) {
            prta(Util.clear(runsb).append(tag).append("Fr: data nchan=").append(Util.toHex(dataNChan)).
                    append(" len=").append(dataFrameMS).append(" ms ").append(nominalTime).
                    append(" ").append(Util.ascdatetime2(nominal, null)).
                    append(" channelStrCnt=").append(channelStringCount).
                    append(" blk=").append(next - 1).append(" cd10=").append(cd10));
          }
          bb.position(bb.position() + channelStringCount);
          if (!nominalTime.equals(lastNominal)) {
            // Process all of the channels
            for (int ich = 0; ich < dataNChan; ich++) {
              if (csf == null) {
                csf = new ChannelSubframe(bb, this);
              } else {
                csf.load(bb);
              }
              if (dbg) {
                prt(Util.clear(runsb).append(tag).append("ch=").append(ich).append(" ").append(csf.toString()));
              }
              if (bb.position() > recSize) {
                SendEvent.edgeSMEEvent("CD11BufShort", "Rec length too short " + filename + " len=" + bb.position() + " > " + recSize, this);
              }
              if (csf.getNsamp() > samples.length) {
                samples = new int[csf.getNsamp()];// self adjust size of this array
              }
              try {
                csf.getSamples(samples);
                if (overrideStation != null) {
                  seedname = network + overrideStation + csf.getStation().substring(5);
                } else {
                  seedname = network + csf.getStation();
                }
                if (overrideLocation) {
                  seedname = seedname.substring(0, 10) + "  ";
                }
                // Look at status bytes for stuff we can pass on
                byte[] status = csf.getStatusBytes();
                dataQuality = 0;
                ioFlags = 0;
                clockQuality = 0;
                activity = 0;
                if (status != null) {
                  if (status[0] == 1) {    // Status per 0.3 manual table 4.22
                    if ((status[1] & 2) != 0) {
                      dataQuality |= 16;     // Mark channel as padded
                    }
                    if ((status[1] & 4) != 0) {
                      dataQuality |= 2;       // Mark channel as clipped
                    }
                    if ((status[1] & 8) != 0) {
                      activity |= 1;         // Calibration underway
                    }
                    if ((status[3] & 4) == 0) {
                      ioFlags |= 32;         // Mark clock as locked
                    }
                    clockQuality = status[3] & 7;                    // Put GPS status at the bottom
                    for (int i = 8; i < 28; i++) {
                      if (status[i] == 0) {
                        status[i] = 32;
                      }
                    }
                    String lastLock = new String(status, 8, 20).trim();     // Get the last lock string
                    long cycles;
                    if (lastLock.length() < 19) {
                      cycles = 0;
                    } else {
                      prt(Util.clear(runsb).append(tag).append("lastLock.length=").append(lastLock.length()).
                              append(" ").append(lastLock).append(":"));
                      CD11Frame.fromCDTimeString(lastLock, g2, this);
                      long lockDiff = csf.getGregorianTime().getTimeInMillis() - g2.getTimeInMillis();
                      cycles = lockDiff / 3600000L;
                      if (cycles < 0 || cycles > 9) {
                        cycles = 9;
                      }
                    }
                    clockQuality |= cycles * 10;
                    usec = status[28] << 24 | status[29] << 16 | status[30] << 8 | status[31];
                    if (usec != 0) {
                      prta(Util.clear(runsb).append(tag).append("Found usec not zero =").append(usec).append(" ").append(csf));
                    }
                  }
                }

                g = csf.getGregorianTime();
                try {
                  MasterBlock.checkSeedName(seedname);
                } catch (IllegalSeednameException e) {
                  prta(Util.clear(runsb).append(tag).append("Bad seed channel name =").
                          append(seedname).append(" e=").append(e));
                  continue;
                }
                Util.clear(seednameSB).append(seedname);    // HACK : not ready to make seednames StringBuilders everywhere
                Util.rightPad(seednameSB, 12);

                oorProcessor.addBuffer(samples, csf.getNsamp(), seednameSB, g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR),
                        (int) ((g.getTimeInMillis() % 86400000L) / 1000L), (int) ((g.getTimeInMillis() % 1000L) * 1000), csf.getRate(),
                        activity, ioFlags, dataQuality, clockQuality);
                if (csend != null) {
                  csend.send(SeedUtil.toJulian(g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR)),
                          (int) (g.getTimeInMillis() % 86400000L), seednameSB,
                          csf.getNsamp(), csf.getRate(), csf.getNsamp() * 4);
                }
              } catch (CanadaException e) {
                prta(Util.clear(runsb).append(tag).append(" CanadaException: CD11R trying to do packet from ").
                        append(csf.toString()).append(" e=").append(e));
              } catch (RuntimeException e) {
                prt(Util.clear(runsb).append(tag).append(" Got Runtime  e=").append(e));
                e.printStackTrace(getPrintStream());
              }
            }
          } else {
            prt(Util.clear(runsb).append(tag).append(" **** Duplicate successive times.  Skip ").
                    append(nominalTime));
          }
          lastNominal = nominalTime;
          nprocess++;
        } catch (IOException e) {
          prta(Util.clear(runsb).append(tag).append("IOerror reading data block e=").append(e));
          e.printStackTrace(getPrintStream());
        }
      }
      // All caught up, write out next and wait a bit.
      try {
        writeHeader();
        if (terminate) {
          break;
        }
        sleep(1000);
      } catch (InterruptedException expected) {
      } catch (IOException e) {
        prta(Util.clear(runsb).append(tag).append("IOerror writing out header e=").append(e));
        e.printStackTrace(getPrintStream());
      }
    }
    oorProcessor.terminate();
    try {
      writeHeader();
    } catch (IOException expected) {
    }
    prta(Util.clear(runsb).append(tag).append(" has exited"));
    if (csend != null) {
      csend.close();
    }
    running = false;
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    Util.setNoInteractive(true);
    Util.setNoconsole(true);
    IndexFile.init();
    System.out.println("Node=" + IndexFile.getNode() + " dbserver=" + Util.getProperty("DBServer"));
    EdgeProperties.init();
    CD11RingProcessor cd11 = new CD11RingProcessor("-dbg -ring /data2/DRLN.ring -oorint 30000", "CD11Proc");
    for (;;) {
      Util.sleep(60000);
      Util.prt(cd11.getStatusString());
    }
  }
}
