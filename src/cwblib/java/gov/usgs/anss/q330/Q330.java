/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.q330;
import gov.usgs.anss.util.Util;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.BitSet;

/** Various utility methods useful for checking things from a Q330 (dump header, compute CRC,etc)
 * No objects of this type should be created.
 *
 * @author davidketchum
 */
public class Q330 {
  
  /** Creates a new instance of Q330
   * @param buf
   * @param len */
  public Q330(byte buf, int len) {
    
  }

  /** dump some information about the header represented by the bytes in buf.
   *@param buf The buffer with some data from a Q330
   *@param off The offset in this buffer that starts the Q330 header
   *@param len The length of the buffer for meaningful data
   *@return A String representing the data
   */
  static public String dumpHeader(byte [] buf, int off, int len) {
    ByteBuffer b = ByteBuffer.wrap(buf, off, len-off);
    if(len < 12) return "DumpHeader: is too short="+len;
    int crc=b.getInt();
    byte command=b.get();
    byte version = b.get();
    short length = b.getShort();
    short seq = b.getShort();
    short ackNumber= b.getShort();
    StringBuilder sb = new StringBuilder(100);
    sb.append("l=").append(((length+12)+"   ").substring(0,4)).append(" sq=").append((((int)seq & 0xffff)+"    ").substring(0,5)).append(" ack#=").append((((int)ackNumber & 0xffff)+"    ").substring(0,5));
    
    switch (((int)command & 0xff)) {
      case 0:
        int dseq=b.getInt();
        sb.append(" <-Data seq=").append(dseq).append(" ").append(dseq & 0xffff).append(" ").append(dseq >> 16 & 0xffff);
        break;
      case 6:
        sb.append(" <-Fill seq=").append(b.getInt());
        break;
      case 0xa:
        short newThrottle=b.getShort();
        short spare = b.getShort();
        int bits31 = b.getInt(); 
        int bits63 = b.getInt();
        int bits95 = b.getInt();
        int bits127 = b.getInt();
        sb.append(" ->ACK ").append(Integer.toHexString(bits31)).append(" ").
                append(Integer.toHexString(bits63)).append(" ").append(Integer.toHexString(bits95)).
                append(" ").append(Integer.toHexString(bits127));
        sb.append(" ").append(acklist(((int)ackNumber & 0xffff),bits31,bits63,bits95,bits127));
        if(newThrottle > 0) sb.append(" New Throttle=").append(newThrottle);
        break;
      case 0xB:
        sb.append(" ->Open Data Port");
        break;
      case 0xa0:
        sb.append(" <-Comand Ack");
        break;
      case 0x10:
        sb.append(" ->Req Serv Registration");
        break;
      case 0xa1:
        sb.append(" <-Server Challenge ").append(Integer.toHexString(b.getInt())).append(" ").
                append(Integer.toHexString(b.getInt())).append(" server ip=").append(b.get()).
                append(".").append(b.get()).append(".").append(b.get()).append(".").
                append(b.get()).append(" Port=").append(b.getShort());//+" Reg#="+b.getShort());
        break;
      case 0x11:
        sb.append(" ->Server Response s/n=").append(Long.toHexString(b.getLong()));
          //+" challenge="+
          //  Long.toHexString(b.getLong())+" srv ip="+
          //  b.get()+"."+b.get()+"."+b.get()+"."+b.get()+"/"+b.getShort()+" reg#="+b.getShort());
        break;
      case 0xa2:
        sb.append(" <-Command Error code=").append(b.getShort());
        break;
      case 0x12:
        sb.append(" ->Delete Server s/n=").append(Long.toHexString(b.getLong()));
        break;
      case 0x13:
        sb.append(" ->Set Auth Codes s/n=").append(Long.toHexString(b.getLong()));
        break;
      case 0x14:
        sb.append(" ->Poll for S/N mask=").append(Integer.toHexString((int)b.getShort())).
                append(" match=").append(Integer.toHexString((int) b.getShort()));
        break;
      case 0xa3:
        sb.append(" <-My S/N = ").append(Long.toHexString(b.getLong()));
        break;
      case 0x15:
        sb.append(" ->Set Physical Interfaces");
        break;
      case 0x16:
        sb.append(" ->Request Physical Interfaces");
        break;
      case 0xa4:
        sb.append(" <-Physical Interfaces ");
        break;
      case 0x17:
        sb.append(" ->Set Data Port");
        break;
      case 0x18:
        sb.append(" ->Request Data Port");
        break;
      case 0xa5:
        sb.append(" <-Data Port Response");
        break;
      case 0x19:
        sb.append(" ->Control Q330 operation =").append(Integer.toHexString(((int)b.get() & 0xff)));
        break;
      case 0x1a:
        sb.append(" ->Set Global Programming");
        break;
      case 0x1b:
        sb.append(" ->Request Global Programming");
        break;
      case 0xa6:
        sb.append(" <-Global Programming response");
        break;
      case 0x1c:
        sb.append(" ->Request Fixed Values after Reboot");
        break;
      case 0xa7:
        sb.append(" <-Fixed Values after reboot Response");
        break;
      case 0x1d:
        sb.append(" ->Set Manufacturer's Area");
        break;
      case 0x1e:
        sb.append(" ->Request Manufacturer's Area");
        break;
      case 0xa8:
        sb.append(" <-Manufacturer's Area Response");
        break;
       case 0x1f:
        sb.append(" ->Request Status");
        break;
      case 0xa9:
        sb.append(" <-Status Response Acq=").append(Integer.toHexString((int) b.getShort())).
                append(" clkqual=").append(Integer.toHexString((int) b.getShort()));
        break;
      case 0x20:
        sb.append(" ->Write to Status Ports");
        break;
      case 0x21:
        sb.append(" ->Set VCO =").append(b.getShort());
        
        break;
      case 0x22:
        sb.append(" ->Pulse Sensor control bits=").append(Integer.toHexString((int) b.getShort())).
                append(" duration=").append(b.getShort()).append("*10ms");
        break;
      case 0x23:
        sb.append(" ->Start QCAL330 calibration");
        break;
      case 0x24:
        sb.append(" ->Stop Calibration");
        break;
      case 0x25:
        sb.append(" ->Request Routing Table");
        break;
      case 0x26:
        sb.append(" ->Modify Routing Table");
        break;
      case 0xaa:
        sb.append(" <-Routing Table Response");
      case 0x27:
        sb.append(" ->Request Thread names");
        break;
      case 0xab:
        sb.append(" <-Thread Name Response");
        break;
      case 0x28:
        sb.append(" ->Request GPS ID Strings");
        break;
      case 0xac:
        sb.append(" <-GPS ID Strings response");
        break;
      case 0x29:
        sb.append(" ->Send CNP message");
        break;
      case 0xad:
        sb.append(" <-CNP Reply message");
        break;
      case 0x2a:
        sb.append(" ->Set Real Time Clock");
        break;
      case 0x2b:
        sb.append(" ->Set Output Bits");
        break;
      case 0x2c:
        sb.append(" ->Set Slave Processor Parms");
        break;
      case 0x2d:
        sb.append(" ->Request Slave Processor Parms");
        break;
      case 0xae:
        sb.append(" <-Slave Processor Parms Response");
        break;
      case 0x2e:
        sb.append(" ->Set Sensor Control Mapping");
        break;
      case 0x2f:
        sb.append(" ->Request Sensor Control Mapping");
        break;
      case 0xaf:
        sb.append(" <-Sensor Control Mapping");
        break;
      case 0x30:
        sb.append(" ->Send User Message");
        break;
      case 0x33:
        sb.append(" ->Set Web Server Link");
        break;
      case 0x34:
        sb.append(" ->Request Combination Packet");
        break;
      case 0xb1:
        sb.append(" <-Combination packet response");
        break;
      case 0x35:
        sb.append(" ->Request Digitzier Cal packet");
        break;
      case 0xb2:
        sb.append(" <-Digitizer Cal Packet response");
        break;
      case 0x36:
        sb.append(" ->Request CNP Device Info");
        break;
      case 0xb3:
        sb.append(" <-CNP Device Info Response");
        break;
      case 0x37:
        sb.append(" ->Set Device Options");
        break;
      case 0x38:
        if(b.getShort() == 0) {
          if(len > 16) sb.append(" ->Ping Request ID=").append(b.getShort()).append(" dt=").append(b.getInt());
          else sb.append(" ->Ping Request ID=").append(b.getShort());
        }
        else  {
          if(len > 16) sb.append(" <-Ping Reply   ID=").append(b.getShort()).append(" dt=").append(b.getInt());
          else sb.append(" ->Ping Reply   ID=").append(b.getShort());
          
        }
        break;
      case 0x41:
        sb.append(" ->Request Memory Contents adr=").append(b.getInt()).append(" count=").
                append(b.getShort()).append(" type=").append(b.getShort());
        break;
      case 0xb8:
        sb.append(" <-Memory Contents Response adr=").append(b.getInt()).append(" count=").
                append(b.getShort()).append(" type=").append(b.getShort());
        break;
      default:
        sb.append(" <>Not Programmed command =").append(Integer.toHexString(((int) command & 0xff)));
        break;
    }
    return sb.toString();
    
  }
  private static String acklist(int base,int b1,int b2,int b3,int b4) {
    boolean continuous=false;
    BitSet bs = new BitSet(64);
    int mask=1;
    base &= 0xffff;
    StringBuilder sb = new StringBuilder(100);
    for(int i=0; i<32; i++ ) {
      if((b1 & mask) != 0) bs.set(i);
      if((b2 & mask) != 0) bs.set(i+32);
      if((b3 & mask) != 0) bs.set(i+64);
      if((b4 & mask) != 0) bs.set(i+96);
      mask = mask << 1;
    }
    int start=0;
    int end=0;
    for(int i=0; i<64; i++) {
      if(continuous) {
        if(bs.get(i)) {
          end=i;
        }
        else {
          continuous = false;
          if(start == end) sb.append(",").append(start+base);
          else sb.append(",").append(start+base).append("-").append(end+base);
        }
      } else {          // Not continuous
        if(bs.get(i)) {
          continuous=true;
          start=i;
        }
      }
    }
    if(continuous) {
      if(start == end) sb.append(",").append(base+start);
      else sb.append(",").append(start+base).append("-").append(end+base);
    }
    return sb.toString().substring(1);
  }
  /** calculate a Q330 CRC on the buffer starting at offset.  
   * The first 4 bytes are skipped as they generally the CRC itself.
   *@param buf Array of bytes on which to calculate
   *@param off The offset at which to start computing
   *@param len The length to the end of the data
   */
  static int [] p_crctable;
  static public byte [] calcCRC(byte [] buf, int off, int len) {
    int tdata;
    int accum;
  
    if(p_crctable == null) {
      p_crctable = new int[256];
      for(int count=0; count<256; count++) {
        tdata = count << 24;
        accum=0;
        for(int bits=1; bits<=8; bits++) {
          if((tdata ^ accum) < 0) accum=(accum<<1) ^ 1443300200;
          else accum = accum <<1;
          tdata = tdata <<1;
        }
        p_crctable[count] = accum;
        //Util.prt(count+" "+accum);
      }
    }
    
    int crc=0;
    for(int i=off; i<len; i++) {
      //Util.prt(len-i+3+" "+crc+" i="+((int) (((byte) ((crc <<24)  &0xff) ^ buf[i]) & 0xff))+
      //    " 1st="+(crc & 0xff)+" 2nd="+ buf[i]);
      crc = (crc << 8) ^ p_crctable[(int) (((byte) ((crc >>24) &0xff) ^ buf[i]) & 0xff)];
    }
    byte [] b = new byte[4];
    b[0]= (byte) ((crc >> 24) &0xff);
    b[1]= (byte) ((crc >> 16) &0xff);
    b[2]= (byte) ((crc >> 8) &0xff);
    b[3]= (byte) (crc &0xff);
    return b;
  }
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.prt(Util.asctime());
    /*
    // Test the CRC code with two sniffed packets
    int [] b1 = {0xb6,0x1e,0xb6,0xd8,0x38,0x02,0,4,0,16,0,0,0,0,0,16};
    int [] b2 = {0xd7,0x3c,0x89,0xa8,0x38,2,0,8,0,0,0,16,0,1,0,16,0,0,0,0x16};
    byte [] b = new byte[b1.length];
    for(int i=0; i<b1.length; i++) b[i]=(byte) (b1[i] & 0xff);
    byte [] chksum=Q330.calcCRC(b, 4, b.length);
    Util.prt("chksum="+b[0]+" "+b[1]+" "+b[2]+" "+b[3]+" "+chksum[0]+" "+chksum[1]+" "+chksum[2]+" "+chksum[3]);
    b = new byte[b2.length];
    for(int i=0; i<b2.length; i++) b[i]=(byte) (b2[i] & 0xff);
    chksum=Q330.calcCRC(b, 4, b.length);
    Util.prt("chksum="+b[0]+" "+b[1]+" "+b[2]+" "+b[3]+" "+chksum[0]+" "+chksum[1]+" "+chksum[2]+" "+chksum[3]);
    */
    String inputfile="wmok.raw";
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-f")) inputfile=args[i+1];
    }
    
    int i=0;
    int len=0;
    byte [] buf = new byte[68];
    if(inputfile != null) {
      FileInputStream in=null;
      try {
        in = new FileInputStream(inputfile);
      }
      catch(FileNotFoundException e) {
        Util.prt("Input file not found="+inputfile);
      }
      /*
      try {

        byte [] big = new byte[2048];
        len=in.read(big,0, 2048);
        Util.prt("big len="+len);
        String ll="";
        int last=0;
        for(i=0; i<800; i++) if(big[i] == 69 && big[i+1] == 0) {
          Util.prt("69 at "+i+" l="+big[i+3]+" "+(big[i+3]-28)+" "+(i-last));
          last=i;
        }
        for(i=0; i<2048; i++) {
          if(i%20 == 0) {Util.prt(ll); ll=(i+"   ").substring(0,4)+":";}
          ll+=" "+(big[i]+"   ").substring(0,4);
        }
      }
      catch(IOException e) {
        Util.prt("Cannot read line from file");
        System.exit(1);
      }
      try {in.seek(0);} catch(IOException e) {Util.prt("IOerror initial seek"); System.exit(1);}
      */
      //Tcpdump tcpd = new Tcpdump(in);
      //Util.prt("End-of-execution");
    }
  }
}
