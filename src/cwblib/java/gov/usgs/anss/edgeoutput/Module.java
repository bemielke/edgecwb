/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import java.util.ArrayList;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;

/**
 * This class is used statically on each received TraceBuf, to allow assembly of frags, checking of
 * sequences for each institution, module, and port of receipt.
 *
 * In July 2010, it started doing the frags as the frag code was moved out of the TraceBufListener
 * when more frags had n packets.
 *
 * @author davidketchum
 */
public final class Module {

  private final int institution;
  private final int module;
  private int lastSeq;
  //int seq;
  private int npackets;     // track number of packets
  private int nseqerr;      // track sequence errors
  private int nwrongmod;    // Track number of wrong mods
  private int nwronginst;   // Track number of wrong institutions
  private int nbadfrag;   // Track number frag errors
  private final int port;
  private final int hash;
  private int frag;         // Track which frag was last put in the tracebuf
  private int off;          // Offset in tracebuf of the last frag put in
  private byte seq;         // output sequence only
  private final TraceBuf trace = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH * 4]);    // This tracebuf is used to build up the fragments or store the bytes
  static boolean dbg = false;
  private final static ArrayList<Module> modules = new ArrayList<>(20);

  private static final StringBuilder sbtmp = new StringBuilder(500);
  private final EdgeThread par;

  public int getHash() {
    return hash;
  }

  public static void setDebug(boolean t) {
    dbg = t;
  }

  public static StringBuilder getStatusSB() {
    Util.clear(sbtmp);
    for (int i = 0; i < modules.size(); i++) {
      if (modules.get(i) != null) {
        sbtmp.append("Modules[").append(i).append("] : ").append(modules.get(i).toString()).append("\n");
      }
    }
    return sbtmp;
  }

  public static String getStatus() {
    return getStatusSB().toString();
  }

  /**
   * process a UDP packet normally byte array with a tracebuf or fragment
   *
   * @param b BYte buffer with the tracebuf
   * @param pt Port - this might be a bad idea, but I think inst/module should be unique
   * @param par The parent process to provide logging output
   * @return
   */
  public static byte processSeqOut(byte[] b, int pt, EdgeThread par) {
    int mod = b[2];
    mod = mod & 255;
    int inst = b[0];
    inst = inst & 255;

    int index = ((inst * 256 + mod) << 16) + pt;
    Module theModule = null;
    for (Module module1 : modules) {
      if (module1.getHash() == index) {
        theModule = module1;
      }
    }
    if (theModule == null) {
      theModule = new Module(inst, mod, pt, par);
      modules.add(theModule);
      par.prta(Util.clear(sbtmp).append("MOD: Add Module inst=").append(inst).append(" mod=").append(mod).
              append(" port=").append(pt).append(" index=").append(Util.toHex(theModule.getHash())));
    }
    byte seq = theModule.getNextSequence();
    //par.prta("MOD: get seq i="+inst+" m="+mod+" pt="+pt+" index="+Util.toHex(index)+" mod="+theModule);
    return seq;
  }

  private byte getNextSequence() {
    if (seq == 127) {
      seq = -128;
    } else {
      seq++;
    }
    return seq;
  }

  /**
   * process a UDP packet normally byte array with a tracebuf or fragment
   *
   * @param b BYte buffer with the tracebuf
   * @param len Length of the received tracebuf
   * @param pt Port - this might be a bad idea, but I think inst/module should be unique
   * @param par The parent process to provide logging output
   * @return
   */
  public static TraceBuf process(byte[] b, int len, int pt, EdgeThread par) {
    int mod = b[2];
    mod = mod & 255;
    int inst = b[0];
    inst = inst & 255;

    int index = ((inst * 256 + mod) << 16) + pt;
    Module theModule = null;
    for (Module module1 : modules) {
      if (module1.getHash() == index) {
        theModule = module1;
      }
    }
    if (theModule == null) {
      theModule = new Module(inst, mod, pt, par);
      modules.add(theModule);
      par.prta(Util.clear(sbtmp).append("MOD: Add Module inst=").append(inst).append(" mod=").append(mod).
              append(" port=").append(pt).append(" hash=").append(Util.toHex(index)));
    }
    return theModule.process(b, len);
  }

  /**
   * Creates a new instance of Module
   *
   * @param inst The institution handled by this module instance
   * @param mod The module number handled by this instance
   * @param pt The port used by this module
   * @param parent The parent EdgeThread to use for logging
   */
  public Module(int inst, int mod, int pt, EdgeThread parent) {
    module = mod;
    institution = inst;
    port = pt;
    hash = ((inst * 256 + mod) << 16) + port;
    lastSeq = -1;
    par = parent;
    par.prta(Util.clear(sbtmp).append("MOD: Start Module = ").append(module).
            append(" inst=").append(institution).append(" port=").append(port).
            append(" hash=").append(Util.toHex(hash)));
  }

  public int getNpackets() {
    return npackets;
  }

  @Override
  public String toString() {
    return "Inst=" + Util.leftPad("" + institution, 3) + " Mod=" + Util.leftPad("" + module, 3) 
            + " pt=" + Util.leftPad("" + port, 5) + " hash=" + Util.toHex(hash)
            + " #seqerr=" + Util.leftPad("" + nseqerr, 3) + " #moderr=" + Util.leftPad("" + nwrongmod, 3)
            + " #insterr=" + Util.leftPad("" + nwronginst, 3) + " #fragerr=" + nbadfrag 
            + " lseq=" + Util.leftPad("" + lastSeq, 3) + " #pck=" + npackets;
  }

  private TraceBuf process(byte[] tb, int len) {

    if ((((int) tb[2]) & 255) != module) {
      nwrongmod++;
      par.prta(Util.clear(sbtmp).append("MOD: ****** Got wrong module ID=").append(tb[2]).
              append(" expect ").append(module).append(" ").append(Util.toAllPrintable(TraceBuf.getSeedname(tb))));
    }
    if ((((int) tb[0]) & 255) != institution) {
      nwronginst++;
      par.prta(Util.clear(sbtmp).append("MOD: ******* Got wrong institution ID=").append(tb[0]).
              append(" expect ").append(institution).append(" ").append(Util.toAllPrintable(TraceBuf.getSeedname(tb))));
    }
    npackets++;
    // is this the expected fragment
    if (dbg) {
      par.prta(Util.clear(sbtmp).append("MOD: got packet inst=").append(tb[0]).append(" ty=").append(tb[1]).
              append(" md=").append(tb[2]).append(" frg=").append(tb[3]).append(" sq=").append(tb[4]).append(" lst=").append(tb[5]));
    }
    if (tb[3] != frag) {
      par.prta(Util.clear(sbtmp).append("     MOD: ******** fragments out of order got ").
              append(tb[3]).append(" exp = ").append(frag).append(" inst=").append(tb[0]).
              append(" typ=").append(tb[1]).append(" mod=").append(tb[2]).
              append(" sq=").append(tb[4]).append(" lst=").append(tb[5]));
      nbadfrag++;
      frag = 0;       // This must be a new beginning of packet or its wrong!
      off = 0;        // blow off the remainder!
      return null;
    }
    frag = tb[3] + 1;      // set the next expected frag number

    // Send this trace buf to Hydra
    // Build the fragments up, or set the data
    if (off == 0) {
      trace.setData(tb, off, len);
    } else {  // Remove the 6 byte headder and append to the Trace buffer.
      len = len - 6;      // Do not load header again
      trace.setData(tb, 6, off, len);
    }

    if (dbg) {
      par.prta(Util.clear(sbtmp).append("MOD:Add off=").append(off).append(" rt=").append(trace.getRate()).
              append(" l=").append(len).append(" frg=").append(tb[3]).append(" ef=").append(tb[5]).
              append(" sq=").append(tb[4]).append(trace.toString()));
    }
    off += len;

    // check sequences
    if (lastSeq >= 0) {
      int seqtmp = tb[4];      // get the packet sequence number
      seqtmp = seqtmp & 255;
      if (tb[3] != 0) {      // its a frag, sequence should equals the last one
        if (lastSeq != seqtmp) {
          nseqerr++;
          par.prta(Util.clear(sbtmp).append("MOD: *** frag seqerr=").append(Util.leftPad("" + seqtmp, 3)).
                  append(" expect ").append(Util.leftPad("" + (lastSeq % 256), 3)).
                  append(" for inst=").append(Util.leftPad("" + institution, 3)).append(" module=").append(module).
                  append(" pt=").append(port).append(" frag=").append(tb[3]).append(" ").
                  append(Util.toAllPrintable(TraceBuf.getSeedname(tb))));
        }
      } else if ((lastSeq + 1) % 256 != seqtmp) {
        nseqerr++;
        par.prta(Util.clear(sbtmp).append("MOD: *** seqerr=").append(Util.leftPad("" + seqtmp, 3)).
                append(" expect ").append(Util.leftPad("" + (lastSeq + 1 % 256), 3)).
                append(" for inst=").append(Util.leftPad("" + institution, 3)).append(" module=").append(module).
                append(" pt=").append(port).append(" ").append(Util.toAllPrintable(TraceBuf.getSeedname(tb))));
      }
      lastSeq = seqtmp;
    } else {
      lastSeq = tb[4];
      lastSeq = lastSeq & 255;
      par.prta(Util.clear(sbtmp).append("MOD: Inst=").append(institution).append(" Module=").append(module).
              append(" init seq=").append(lastSeq).append(" ").append(Util.toAllPrintable(TraceBuf.getSeedname(tb))));
    }
    if (tb[5] == 0) {       // Wait for end of fragments (b[5]==0) is not a last of message
      if (dbg) {
        par.prta(Util.clear(sbtmp).append("MOD: return null - frag not complete ").append(trace.toStringBuilder(null)));
      }
      return null;           // tell it we are not yet ready
    }
    // to get here the packet is complete (tb[5] != 0, send it!
    off = 0;                        // done with this frament build up
    frag = 0;
    if (dbg) {
      par.prta(Util.clear(sbtmp).append("MOD: return full packet =").append(trace.toStringBuilder(null)));
    }
    return trace;
  }
}
