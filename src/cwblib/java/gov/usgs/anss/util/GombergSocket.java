/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.util;
import java.net.*;
import java.io.*;

/**
 *  This class extends the standard ServerSocket to packetize the stream
 * of bytes according to GombergPacket rules.  This is basically a two byte 
 * header of ESC, STX (23, 3), 
 *<br>
  * <br>a short with the packet length (little endian)
 * <br> a byte with routing, node, and channel IDs
 * <br> a byte with packet sequence
 * <br> an 6 byte NsnTimeCode 
 * <br> So a 14 byte overall header.  This checks that the syncs are correct,
 * that the packetsequences are sequential (no rollbacks on this yet!),
 *  and hands the data buffers over as GombergPacket objects.
 * <br>
 * Created on December 19, 2003, 4:34 PM
 * @author  ketchum
 */
public class GombergSocket extends Socket {
  static final int INBUFSIZE=8192;
  static final byte ESC=27;
  static final byte STX=3;
  byte [] gb;
  byte[] in;
  int pnt=0;
  int inpnt=0;
	boolean inhibit=false;
	boolean sync=false;
	int nbexpect=0;
	int lastseq= -1;
  Socket s;

    /** Creates a new instance of GombergServerSocket
     @param host The host name - must be DNS translatable
     @param port The port on which to listen for GombergPackets
     *@exception IOException If the Socket() setup does not work.
     */
    public GombergSocket(String host, int port) throws IOException {
      in = new byte[INBUFSIZE];
      gb = new byte[2048];
      s = new Socket(host, port);
    }
    /** return a Gomberg packet from the line
     *@return The gomberg packet.  If the header is not right return null
     */
    public GombergPacket getPacket() {
      if(gb[0] == ESC && gb[1] == STX) return new GombergPacket(gb);
      else return null;
    }
/**
	serialToGomberg takes data in buf of len and builds a gomberg packet
	the struct stg contains the state of this stream so this routine may
	be used on more than one stream at the same time.

	struct stg	contains state information
	buf			contains new data to run though the packetizer
	len			 number of bytes in buf
	encoded if true, the packet is in printable ascii encoding
	return	Description
	0				Nothing, packet being built and nothing to report
	1				Success gb contains the gomberg packet without the chksum
	2				Success but sequence # appears out of order lastseq last one
	-1			Chksum error, 

*/
    private int serialToGomberg( char [] buf, int len,  boolean encoded)
    {	// Warning : all static variables should be in the stg struct!!!!!

      int nbytes;										/* temp variable containing # of bytes in in[] */
      int ifnd;										/* used in memchr as pointer to lead ins */
      int i, err;											/* temporary indices */
      boolean  outseq;
      boolean dbgstg=true;

      //err=read(ttpath,&in[inpnt],nbyte)) == 0
      // the in[] is used as a ring buffer for raw input, move buf to in
      err = len;
      if( inpnt+len >= INBUFSIZE) {
        System.arraycopy(buf, 0, in, inpnt, INBUFSIZE - inpnt);
        System.arraycopy(buf, INBUFSIZE - inpnt, in, 0,  len - (INBUFSIZE - inpnt));
      }
      else System.arraycopy(buf, 0, in, inpnt, len);
    ///*
      if(dbgstg) {
        Util.prt("          Got "+err); 	
        /*for(i=0; i< (err>20 ? 20 : err); i++) 
        (( i% 20) == 19) ?Util.prt("in[inpnt+i]") : 
                sprintf(pfbuf,"%2x ",in[inpnt+i]);
        ppr();
        sprintf(pfbuf,"\n");	ppr();*/
      }
      inpnt += err;						/* adjust for new bytes in buffer*/
      if(inpnt >= INBUFSIZE) inpnt -= INBUFSIZE;	/* wrap around ring buffer */

      // nbytes is the number of bytes currently in the in[] array
    synced:
     while(true)  {
      nbytes=(inpnt >= pnt) ? 
        inpnt - pnt : inpnt - pnt + INBUFSIZE;
      if(dbgstg) Util.prt("RCV start 1 nb="+nbytes+" in="+inpnt+" pnt="+pnt+" sync="+sync);


      if(sync) {						/* if we are synced up */
    ///*
        if(dbgstg) Util.prt("RCV : SYNC : inpnt="+inpnt+" pnt="+pnt+" nbytes="+nbytes+" nbexpect="+nbexpect);
        if(nbexpect > 0) {					/* do we know what to expect ? */
          if(nbytes >= nbexpect) {		/* have we satisfied the packet len? */
            if(pnt + nbexpect < INBUFSIZE) 
              System.arraycopy(in, pnt, gb, 0, nbexpect);/* move GB packet*/
            else {				/* packet spans ringn buffer boundary */
              System.arraycopy(in, pnt, gb, 0, INBUFSIZE - pnt);		/* move first */
              System.arraycopy( in, 0, gb, INBUFSIZE - pnt, nbexpect - INBUFSIZE + pnt);
            }
            if(lastseq == -1) {				/* set initial packetseq */
              if(gb[7] == 0) lastseq=-1;/* is it on wrap*/
              else lastseq = unsigned(gb[7]) - 1;/* no,set to previous*/
            }
            outseq=false;
            if( ((lastseq+1) % 256) != unsigned(gb[7]) && 
              !inhibit && nbexpect > 10) {					/* is it expected */
              if(!encoded ) 
                Util.prt(Util.asctime()+" STG : Out of seq. expect="+lastseq+1+" got="+in[index(pnt+7)]);
              outseq=true;
            }					/* o.k. how many bytes expected*/
            pnt += nbexpect;		/* point to next packet */
            nbexpect =- 1;			/* do not yet know size of packet */
            if(pnt >= INBUFSIZE) pnt -= INBUFSIZE;
            if(outseq) return 2;
            else return 1;					// No check sum, return one when done

          }		// nbytes > nbexpect

        // if(nbexpect > 0) else we need to get an nbexpect
        } else {					/* start of packet, check out leadins/size*/
          if(nbytes >= 10 ) {
            if(in[pnt] != ESC || in[index(pnt+1)] != STX) {
              sync=false;						/* not expected lead in bytes */
              Util.prt(Util.asctime()+" RCV : Leadins not right "+in[pnt]+" "+ in[index(pnt+1)]);
              nbexpect=0;					/* indicate we are lost */
              continue;
            } else {						/* lead ins ok,compute length*/

              if(encoded) {
                nbexpect = 
                  ( unsigned(in[index(pnt+2)]) - 32) * 16 +
                  ( unsigned(in[index(pnt+3)]) - 32) +
                  ( unsigned(in[index(pnt+5)]) - 32) * 256;
              //	diag_printf_m("encoded nbytes=%d b=%d %d %d %d\n",
              //		nbexpect, in[index(pnt+2)],in[index(pnt+3)],
              //		in[index(pnt+4)],in[index(pnt+5)]);
              }
              else nbexpect = unsigned(in[index(pnt+2)]) + 256 * (unsigned(in[index(pnt+3)]) & 7);
              if(dbgstg) Util.prt(Util.asctime()+" RCV : NBEXPECT="+nbexpect+" inpnt="+inpnt+" pnt="+pnt);


              if(nbexpect < 8 || nbexpect > 2048) {/*is it in range*/
                sync=false;					/* we have lost sync */
                Util.prt(Util.asctime()+" RCV : Sync lost bad nbytes="+nbexpect);
                pnt+=8;					/* look for new packet */
                if(pnt >=INBUFSIZE) pnt-=INBUFSIZE;/*wrap ring*/
              }
              inhibit= (in[index(pnt+3)] & 0x80) != 0;	/* is inhibit on */
              if(inhibit) 
              {	if(dbgstg) Util.prt("RCV : Inhibit on seq="+unsigned(in[index(pnt+7)]));
                lastseq=unsigned(in[index(pnt+7)])-1;
              }
              continue;

            }		// if(in(p\\[pnt] == ESC && in[pnt+1] == STX
          }			// if nbytes > 8
        }				// else if(nbexpect > 8)
    /*
      Not synced up.  Look for the ESC STX
    */
      } else {								/* if not synced */
        while(nbytes > 0) {					/* while we have unexamined bytes */
          int nchk=nbytes;
          if( (pnt+nbytes) > INBUFSIZE) nchk=INBUFSIZE-pnt;/* limit to end*/
          ifnd=-1;
          for(i =0; i<nchk; i++) {
              if(in[i] == ESC) { ifnd = i; break;}
          }
          //ifnd=memchr(&in[pnt],ESC,nchk);	/* find next ESC character */
          if(dbgstg) Util.prt(Util.asctime()+" RCV start 2");
          if(ifnd != -1) {
            i=ifnd;

            if(dbgstg) Util.prt("RCV : try to sync pnt="+pnt+"nbytes="+nbytes+" i="+i);

            if(i < nbytes-2) {
              if(in[pnt+i+1] == STX) {	/* possible sync up */
                pnt+=i;					/* set new start point */
                if(pnt >= INBUFSIZE) pnt-=INBUFSIZE;/* wrap ring */
                sync=true;					/* we think we are synced up */
                if(dbgstg) Util.prt(Util.asctime()+" RCV : Synced UP.. nbytes="+nbytes+
                        " pnt="+pnt+" skipped="+i);
                nbexpect=-1;			/* have not looked at header */
                continue synced;
              } else {
                Util.prt(Util.asctime()+" RCV : Not a STX pnt="+pnt+" ipnt="+inpnt+" i="+i);
                pnt += i+1;				/* Point beyond ESC */
                nbytes -= i+1;			/* reduce space to look through */
                continue synced;			// see if another esc is in 
              }
            } else {
              if(dbgstg) Util.prt(Util.asctime()+
                " RCV : ESC found in last byte pnt="+pnt+" inpnt="+inpnt+" i="+i);
              nbytes=0;				/* force out till more chars in */
            }
          } else {

            if(dbgstg) Util.prt(Util.asctime()+
                " RCV : No esc found in nb="+nbytes+" pnt="+pnt+" inpnt="+inpnt);
            pnt += nchk;					/* increment to new search spot */
            nbytes -= nchk;						/* esc not found */
            if(pnt >= INBUFSIZE) pnt -= INBUFSIZE;
          }
        }
        if(pnt >= INBUFSIZE) pnt-=INBUFSIZE;	/* wrap ring if needed */
    ///*
        if(dbgstg) Util.prt(Util.asctime()+
            " RCV : end sync loop sync="+sync+" pnt="+pnt+" inpnt="+inpnt);
      }						// end else (not synced)
      return 0;
     }      // while(true) for synced lable
    }
    
    // java bytes are always signed, so this creates an unsigned in from a byte
    private int unsigned(byte b) {return ((int) b) & 0xff;}
    
    private int index(int i) { return (INBUFSIZE > i ? i : i - INBUFSIZE);}
    
  /** Unit test
   *@param args Command line args - ignored*/   
  public static void main(String [] args) {
    NsnTime tc = new NsnTime(2002, 211, 01, 34, 56, 999, 0);
    byte [] buf = new byte[2048];
    for(int i=0; i<2048; i++) buf[i]=(byte) i;
    
    try {
      GombergPacket gb = new GombergPacket( (short) 2034, (byte) 1, (byte) 4, 
      (byte) 126, (byte) 0, tc, buf);
      Util.prt("Gb ="+gb);
      buf = gb.getAsBuf();
      StringBuffer s = new StringBuffer(100);
      for(int i=0; i<30; i++) s.append(Util.toHex(buf[i])).append(" ");
      Util.prt("as buf="+s);
      GombergPacket gb2 = new GombergPacket(buf);
      Util.prt("gb2="+gb2);
      GombergSocket gs = new GombergSocket ("gldketchum.cr.usgs.gov", 7990);
      while(true) {
          s.delete(0,s.length());
          gb = gs.getPacket();
          buf = gb.getAsBuf();
          for(int i=0; i<30; i++) s.append(Util.toHex(buf[i])).append(" ");
          Util.prt("as buf="+s);
      }
      //System.exit(0);
    }
    catch(IOException e) {
      Util.prt("IOException thrown testing GombergSocket");
    }
  } 

}
