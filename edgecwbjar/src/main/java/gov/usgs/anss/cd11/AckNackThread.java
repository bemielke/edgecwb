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
package gov.usgs.anss.cd11;

import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;

/**
 * this class sends the gapslist/frames set to the sender every minute. Acks must be enabled, since
 * we must recieve and Ack before sending one for to avoid the "implied gap" problem (sender low seq
 * less than receiver low seq is an impled gap). The acks are based on a GapList object shared with
 * the main code.
 *
 * This is strictly CD1.1 implementation of the gaplist/frame set Acknack packet
 *
 * @author davidketchum
 */
public class AckNackThread extends Thread {

  private boolean terminate;
  private final String tag2;
  private final OutputStream out;
  private final Socket sd;
  private final CD11Frame ack;
  private byte[] gapbuf = new byte[10200];
  private ByteBuffer bb;
  private int nack;
  private byte[] frameSet; // raw bytes with frameset tag
  private GapList gaps;     // The gaps buffer or for CD1.1 nomenclature the frameset
  private final EdgeThread par;
  private boolean enableAcks;
  private boolean dbg;
  private final StringBuilder tmpsb = new StringBuilder(50);

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void setDebug(boolean t) {
    prta(tag2 + " AckNack debug now=" + t);
    dbg = t;
  }

  public void terminate() {
    close();
    terminate = true;
    interrupt();
  }

  public void setCreatorDestination(StringBuilder cr, StringBuilder dest, int auth) {
    ack.setCreatorDestination(cr, dest, auth);
  }

  public void setEnableAcks(boolean t) {
    enableAcks = t;
  }

  public AckNackThread(String tg, Socket o, StringBuilder creator, StringBuilder destination,
          int auth, GapList gp, EdgeThread parent) throws IOException {
    tag2 = tg + "[" + getId() + "]:";
    sd = o;
    out = o.getOutputStream();
    bb = ByteBuffer.wrap(gapbuf);
    gaps = gp;
    par = parent;
    // Build up the frame set
    ack = new CD11Frame(10000, 100, creator, destination, 0, -1, auth, parent); // Note: this sets the frame set
    prta(Util.clear(tmpsb).append(tag2).append(" AckNack set up ").
            append(creator).append(":").append(destination).append(" on ").append(o));
    prta(Util.clear(tmpsb).append(tag2).append(" AckNack initial range=").append(gp.toStringBuilder(null)));
    this.setDaemon(true);       // So we can exit
    start();

  }

  /**
   * this connection is being terminated. Send a last ack and an alert frame
   *
   */
  public synchronized void close() {
    if (gaps != null) {
      long lastSeq = gaps.getHighestSeq();
      long firstSeq = gaps.getLowestSeq();
      if (gaps != null) {
        prt("Write out final gaps");
        gaps.writeGaps(true);
      }
      if (gaps.gapBufferSizeNeeded() > gapbuf.length) {
        gapbuf = new byte[gaps.gapBufferSizeNeeded() * 2];
      }
      ack.loadAckNak(firstSeq, lastSeq, gaps.getGapBuffer(gapbuf), gapbuf);
      prta(Util.clear(tmpsb).append(tag2).append(" Final ack sent ").append(firstSeq).
              append("-").append(lastSeq).append(" #gaps=").append(gaps.getGapCount()));
    }
    if (ack != null) {
      int len = ack.getOutputBytesLength();   // Make sure our buffer is big enough
      if (len > gapbuf.length) {
        gapbuf = new byte[len * 2];
        bb = ByteBuffer.wrap(gapbuf);
        prt(Util.clear(tmpsb).append(tag2).append(" ***** AckNack increasing buf size to ").append(len * 2));
      }
      len = ack.getOutputBytes(bb);

      if (!sd.isClosed()) {
        try {
          out.write(gapbuf, 0, len);
        } catch (IOException e) {
          prta(Util.clear(tmpsb).append(tag2).append("AckNack got writing last ack IOError =").append(e));
        }
        ack.loadAlert("Terminated");
        prta(tag2 + " Alert packet sent");
        len = ack.getOutputBytes(bb);
        try {
          out.write(gapbuf, 0, len);
        } catch (IOException e) {
          prta(Util.clear(tmpsb).append(tag2).append("AckNack got writing alert IOError =").append(e));
        }
      }
    }
    gaps = null;
    terminate = true;
  }

  //int tries;
  public void sendAck() {
    //tries++;
    //if(!enableAcks && tries<10) {prta(tag2+" sendAck() not enabled yet.  Wait for enable tries="+tries); return;}
    if (ack == null || gaps == null) {
      prta(Util.clear(tmpsb).append(tag2).append(" sendAck() with NULL ack=").append(ack).
              append(" or gaps=").append(gaps));
      return;
    }
    if (gaps.getLowestSeq() < 0 || gaps.getHighestSeq() < 0) {
      prta(Util.clear(tmpsb).append(tag2).append(" sendAck() questionable.  Sequences not yet initialized! last=").
              append(gaps.getHighestSeq()).append(" 1st=").append(gaps.getLowestSeq()));

    }
    if (sd.isClosed()) {
      prta(Util.clear(tmpsb).append("  ").append(tag2).append(" *** Ack not sent - socket is closed! terminate"));
      terminate = true;
    } else {
      if (gaps.gapBufferSizeNeeded() > gapbuf.length) {
        gapbuf = new byte[gaps.gapBufferSizeNeeded() * 2];
      }
      ack.loadAckNak(Math.max(gaps.getLowestSeq(), 0), gaps.getHighestSeq(), gaps.getGapBuffer(gapbuf), gapbuf);
      if (ack.getBody().length + 200 > gapbuf.length) {
        prta(Util.clear(tmpsb).append(" ").append(tag2).append(" sendAck() increas buffer size to ").
                append(ack.getBody().length));
        gapbuf = new byte[ack.getBody().length + 200];
        bb = ByteBuffer.wrap(gapbuf);
      }
      int len = ack.getOutputBytes(bb);
      //if(dbg)
      prta(Util.clear(tmpsb).append("  ").append(tag2).append(" sendAck() ").append(gaps.getLowestSeq()).
              append("-").append(gaps.getHighestSeq()).append(" #gaps=").append(gaps.getGapCount()).
              append(" #pkgap=").append(gaps.getGapPacketCount()).append(" len=").append(len));
      //ack.getFrameSetString()+" "+ack.toOutputString());
      try {
        //if(nack % 200 == 0) for(int i=0; i<len; i++) prta("Ack : "+i+" "+gapbuf[i]);
        out.write(gapbuf, 0, len);
        gaps.writeGaps(false);
        nack++;
      } catch (IOException e) {
        prta(Util.clear(tmpsb).append(tag2).append("AckNack got IOError =").append(e));
      }
    }
  }

  @Override
  public void run() {
    int loop = 0;
    while (!terminate) {
      try {
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        if (terminate) {
          break;
        }
        if ((loop++ % 60) == 0) {
          sendAck();
        }
        // Send out the ack packet
      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append(tag2).append("AckNack got runtime exception e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
    }
    prta(Util.clear(tmpsb).append(tag2).append(" ack/nack thread exited ").append(terminate));
  }

}
