/*
 The TauP Toolkit: Flexible Seismic Travel-Time and Raypath Utilities.
 Copyright (C) 1998-2000 University of South Carolina

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 The current version can be found at
 <A HREF="www.seis.sc.edu">http://www.seis.sc.edu</A>

 Bug reports and comments should be directed to
 H. Philip Crotwell, crotwell@seis.sc.edu or
 Tom Owens, owens@seis.sc.edu

 */

package edu.sc.seis.TauP;
import gov.usgs.anss.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;

/*** Class that represents a sac file. All headers are have the same names
 * as within the Sac program. Can read the whole file or just the header
 * as well as write a file.
 *
 * Major modification by D.C. Ketchum USGS Feb 2008.  Change from using DataInputStreadm
 * and DataOutputStreams to using ByteBuffers to build up the bytes in a file or interpreting
 * them when reading them in.  Fixes misc problem with old method of dealing with
 * double encoding when swapping bytes around.  Changed all internal fields to double but keep
 * reading and writing of files as floats.
 *
 * @version 1.1 Wed Feb  2 20:40:49 GMT 2000
 * @author H. Philip Crotwell
 */
public class SacTimeSeries implements Cloneable,Comparable {
    public double delta = DOUBLE_UNDEF;
    public double depmin = DOUBLE_UNDEF;
    public double depmax = DOUBLE_UNDEF;
    public double scale = DOUBLE_UNDEF;
    public double odelta = DOUBLE_UNDEF;
    public double b = DOUBLE_UNDEF;    // In blob for GF
    public double e = DOUBLE_UNDEF;
    public double o = DOUBLE_UNDEF;
    public double a = DOUBLE_UNDEF;    // In blob for GF
    public double fmt = DOUBLE_UNDEF;
    public double t0 = DOUBLE_UNDEF;    // In blob for GF
    public double t1 = DOUBLE_UNDEF;    // In blob for GF
    public double t2 = DOUBLE_UNDEF;    // In blob for GF
    public double t3 = DOUBLE_UNDEF;    // In blob for GF
    public double t4 = DOUBLE_UNDEF;    // In blob for GF
    public double t5 = DOUBLE_UNDEF;    // In blob for GF
    public double t6 = DOUBLE_UNDEF;    // In blob for GF
    public double t7 = DOUBLE_UNDEF;    // In blob for GF
    public double t8 = DOUBLE_UNDEF;    // In blob for GF
    public double t9 = DOUBLE_UNDEF;    // In blob for GF
    public double f = DOUBLE_UNDEF;
    public double resp0 = DOUBLE_UNDEF;
    public double resp1 = DOUBLE_UNDEF;
    public double resp2 = DOUBLE_UNDEF;
    public double resp3 = DOUBLE_UNDEF;
    public double resp4 = DOUBLE_UNDEF;
    public double resp5 = DOUBLE_UNDEF;
    public double resp6 = DOUBLE_UNDEF;
    public double resp7 = DOUBLE_UNDEF;
    public double resp8 = DOUBLE_UNDEF;
    public double resp9 = DOUBLE_UNDEF;
    public double stla = DOUBLE_UNDEF;
    public double stlo = DOUBLE_UNDEF;
    public double stel = DOUBLE_UNDEF;
    public double stdp = DOUBLE_UNDEF;
    public double evla = DOUBLE_UNDEF;
    public double evlo = DOUBLE_UNDEF;
    public double evel = DOUBLE_UNDEF;
    public double evdp = DOUBLE_UNDEF;    // In blob for GF
    public double mag = DOUBLE_UNDEF;
    public double user0 = DOUBLE_UNDEF;
    public double user1 = DOUBLE_UNDEF;
    public double user2 = DOUBLE_UNDEF;
    public double user3 = DOUBLE_UNDEF;
    public double user4 = DOUBLE_UNDEF;
    public double user5 = DOUBLE_UNDEF;
    public double user6 = DOUBLE_UNDEF;
    public double user7 = DOUBLE_UNDEF;
    public double user8 = DOUBLE_UNDEF;
    public double user9 = DOUBLE_UNDEF;
    public double dist = DOUBLE_UNDEF;    // In blob for GF
    public double az = DOUBLE_UNDEF;
    public double baz = DOUBLE_UNDEF;
    public double gcarc = DOUBLE_UNDEF;
    public double sb = DOUBLE_UNDEF;
    public double sdelta = DOUBLE_UNDEF;
    public double depmen = DOUBLE_UNDEF;
    public double cmpaz = DOUBLE_UNDEF;
    public double cmpinc = DOUBLE_UNDEF;
    public double xminimum = DOUBLE_UNDEF;
    public double xmaximum = DOUBLE_UNDEF;
    public double yminimum = DOUBLE_UNDEF;
    public double ymaximum = DOUBLE_UNDEF;
    public double unused6 = DOUBLE_UNDEF;
    public double unused7 = DOUBLE_UNDEF;
    public double unused8 = DOUBLE_UNDEF;
    public double unused9 = DOUBLE_UNDEF;
    public double unused10 = DOUBLE_UNDEF;
    public double unused11 = DOUBLE_UNDEF;
    public double unused12 = DOUBLE_UNDEF;
    public int nzyear = INT_UNDEF;
    public int nzjday = INT_UNDEF;
    public int nzhour = INT_UNDEF;
    public int nzmin = INT_UNDEF;
    public int nzsec = INT_UNDEF;
    public int nzmsec = INT_UNDEF;
    public int nvhdr = 6;
    public int norid = INT_UNDEF;
    public int nevid = INT_UNDEF;
    public int npts = INT_UNDEF;      // in blob for GF
    public int nsnpts = INT_UNDEF;
    public int nwfid = INT_UNDEF;
    public int nxsize = INT_UNDEF;
    public int nysize = INT_UNDEF;
    public int unused15 = INT_UNDEF;
    public int iftype = INT_UNDEF;
    public int idep = INT_UNDEF;
    public int iztype = INT_UNDEF;
    public int unused16 = INT_UNDEF;
    public int iinst = INT_UNDEF;
    public int istreg = INT_UNDEF;
    public int ievreg = INT_UNDEF;
    public int ievtyp = INT_UNDEF;
    public int iqual = INT_UNDEF;
    public int isynth = INT_UNDEF;
    public int imagtyp = INT_UNDEF;
    public int imagsrc = INT_UNDEF;
    public int unused19 = INT_UNDEF;
    public int unused20 = INT_UNDEF;
    public int unused21 = INT_UNDEF;
    public int unused22 = INT_UNDEF;
    public int unused23 = INT_UNDEF;
    public int unused24 = INT_UNDEF;
    public int unused25 = INT_UNDEF;
    public int unused26 = INT_UNDEF;
    public int leven = INT_UNDEF;
    public int lpspol = INT_UNDEF;
    public int lovrok = INT_UNDEF;
    public int lcalda = INT_UNDEF;
    public int unused27 = INT_UNDEF;
    public String kstnm = STRING8_UNDEF;
    public String kevnm = STRING16_UNDEF;
    public String khole = STRING8_UNDEF;
    public String ko = STRING8_UNDEF;
    public String ka = STRING8_UNDEF;    // In blob for GF
    public String kt0 = STRING8_UNDEF;    // In blob for GF
    public String kt1 = STRING8_UNDEF;    // In blob for GF
    public String kt2 = STRING8_UNDEF;    // In blob for GF
    public String kt3 = STRING8_UNDEF;    // In blob for GF
    public String kt4 = STRING8_UNDEF;    // In blob for GF
    public String kt5 = STRING8_UNDEF;    // In blob for GF
    public String kt6 = STRING8_UNDEF;    // In blob for GF
    public String kt7 = STRING8_UNDEF;    // In blob for GF
    public String kt8 = STRING8_UNDEF;    // In blob for GF
    public String kt9 = STRING8_UNDEF;    // In blob for GF
    public String kf = STRING8_UNDEF;
    public String kuser0 = STRING8_UNDEF;
    public String kuser1 = STRING8_UNDEF;
    public String kuser2 = STRING8_UNDEF;
    public String kcmpnm = STRING8_UNDEF;    // In blob for GF
    public String knetwk = STRING8_UNDEF;
    public String kdatrd = STRING8_UNDEF;
    public String kinst = STRING8_UNDEF;

    public double[] y;
    public double[] x;
    public double[] real;
    public double[] imaginary;
    public double[] amp;
    public double[] phase;

    // undef values for sac
    public static double DOUBLE_UNDEF = -12345.0;
    public static float FLOAT_UNDEF = -12345.0f;
    public static int INT_UNDEF = -12345;
    public static String STRING8_UNDEF = "-12345  ";
    public static String STRING16_UNDEF = "-12345          ";

    /* TRUE and FLASE defined for convenience. */
    public static final int TRUE   =  1;
    public static final int FALSE  =  0;

    /* Constants used by sac. */
    public static final int IREAL  =  0;
    public static final int ITIME  =  1;
    public static final int IRLIM  =  2;
    public static final int IAMPH  =  3;
    public static final int IXY    =  4;
    public static final int IUNKN  =  5;
    public static final int IDISP  =  6;
    public static final int IVEL   =  7;
    public static final int IACC   =  8;
    public static final int IB     =  9;
    public static final int IDAY   = 10;
    public static final int IO     = 11;
    public static final int IA     = 12;
    public static final int IT0    = 13;
    public static final int IT1    = 14;
    public static final int IT2    = 15;
    public static final int IT3    = 16;
    public static final int IT4    = 17;
    public static final int IT5    = 18;
    public static final int IT6    = 19;
    public static final int IT7    = 20;
    public static final int IT8    = 21;
    public static final int IT9    = 22;
    public static final int IRADNV = 23;
    public static final int ITANNV = 24;
    public static final int IRADEV = 25;
    public static final int ITANEV = 26;
    public static final int INORTH = 27;
    public static final int IEAST  = 28;
    public static final int IHORZA = 29;
    public static final int IDOWN  = 30;
    public static final int IUP    = 31;
    public static final int ILLLBB = 32;
    public static final int IWWSN1 = 33;
    public static final int IWWSN2 = 34;
    public static final int IHGLP  = 35;
    public static final int ISRO   = 36;
    public static final int INUCL  = 37;
    public static final int IPREN  = 38;
    public static final int IPOSTN = 39;
    public static final int IQUAKE = 40;
    public static final int IPREQ  = 41;
    public static final int IPOSTQ = 42;
    public static final int ICHEM  = 43;
    public static final int IOTHER = 44;
    public static final int IGOOD  = 45;
    public static final int IGLCH  = 46;
    public static final int IDROP  = 47;
    public static final int ILOWSN = 48;
    public static final int IRLDTA = 49;
    public static final int IVOLTS = 50;
    public static final int INIV51 = 51;
    public static final int INIV52 = 52;
    public static final int INIV53 = 53;
    public static final int INIV54 = 54;
    public static final int INIV55 = 55;
    public static final int INIV56 = 56;
    public static final int INIV57 = 57;
    public static final int INIV58 = 58;
    public static final int INIV59 = 59;
    public static final int INIV60 = 60;


    public static final int  data_offset = 632;
    
    /** support deep cloning by making copies of any arrays in x, y, amp, phase, real or imaginary
     *@return the cloned object */
  @Override
    public Object clone() {
      try {
        Object obj = super.clone();    // clone the base fields
        if( y != null) {
          double [] tmp = new double[y.length];
          System.arraycopy(y, 0, tmp, 0, y.length);
          y = tmp;
        }
        if( x != null) {
          double [] tmp = new double[x.length];
          System.arraycopy(x, 0, tmp, 0, x.length);
          x = tmp;
        }
        if( real != null) {
          double [] tmp = new double[real.length];
          System.arraycopy(real, 0, tmp, 0, real.length);
          real = tmp;
        }
        if( imaginary != null) {
          double [] tmp = new double[imaginary.length];
          System.arraycopy(imaginary, 0, tmp, 0, imaginary.length);
          imaginary = tmp;
        }
        if( amp != null) {
          double [] tmp = new double[amp.length];
          System.arraycopy(amp, 0, tmp, 0, amp.length);
          amp = tmp;
        }
        if( phase != null) {
          double [] tmp = new double[phase.length];
          System.arraycopy(phase, 0, tmp, 0, phase.length);
          phase = tmp;
        }
        return obj;
      }
      catch(CloneNotSupportedException e2) {
        System.out.println("SacTimeSeries threw a clone not supported e="+e2);
        e2.printStackTrace();
      }
      return null;      // cloning must have failed
    } 
    
    
    public void read(String filename) throws IOException, FileNotFoundException {
      File file= new File(filename);
      read(file);
    }
    /*** reads the sac file specified by the filename. Only a very simple
     * check is made
     * to be sure the file really is a sac file. This will read sac files
     * of either byte order.
     * @throws FileNotFoundException if the file cannot be found
     * @throws IOException if it isn't a sac file or if it happens :)
     */
    public void read(File filename)
        throws FileNotFoundException, IOException {
      ByteBuffer buf;
      byte [] bf;
      RandomAccessFile file = new RandomAccessFile(filename,"r");
      long len = file.length();
      bf = new byte[(int) len];
      buf = ByteBuffer.wrap(bf);
      buf.order(ByteOrder.nativeOrder());
      file.readFully(bf);            // read in the entire file
      buf.position(316);      // position npts
      npts = buf.getInt();    // get npnts
      buf.position(340);
      iftype=buf.getInt();
      buf.position(420);
      leven=buf.getInt();
      if (leven == SacTimeSeries.FALSE || iftype == SacTimeSeries.IRLIM || 
        iftype == SacTimeSeries.IAMPH) npts = npts*2;     // got either x[] amp, real/imag
      if(len != npts*4+data_offset)  { // is it probably the right swaping?
        npts = swapBytes(npts);                     // does swaping make it better
        leven = swapBytes(leven);
        iftype = swapBytes(iftype);
        if (leven == SacTimeSeries.FALSE || iftype == SacTimeSeries.IRLIM || 
          iftype == SacTimeSeries.IAMPH) npts = npts*2;     // got either x[] amp, real/imag
        if(len != npts*4+data_offset) {
          throw new IOException(filename+
              " does not appear to be a sac file! npts("+npts+") + header("+data_offset+") !=  file length="+len+"\n  as linux: npts("+swapBytes(npts)+") + header("+data_offset+") !=  file length="+len);
        }
        // Switch the byte order
        if(buf.order() == ByteOrder.LITTLE_ENDIAN) buf.order(ByteOrder.BIG_ENDIAN);
        else buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(316);
        int n=buf.getInt();
        //System.out.println("File is in little endian order n="+n+" npts="+npts);
      }
      buf.position(0);
      readHeader(buf);
      readData(buf);
      file.close();
    }

  /*  public final static short swapBytes(short val){
        return (short)(((val & 0xff00) >> 8) + ((val & 0x00ff) << 8));
    }*/

   /* public final static double swapBytes(double val) {
      System.out.println("***** attempt to use swapbytes double!");
        return Float.intBitsToFloat(swapBytes(Float.floatToRawIntBits((float) val)));
    }*/

    public final static int swapBytes(int val) {
        return ((val & 0xff000000) >>> 24 ) +
            ((val & 0x00ff0000) >> 8 ) +
            ((val & 0x0000ff00) << 8 ) +
            ((val & 0x000000ff) << 24);

    }
 
/*    public final static double swapBytes(double val){
        return Double.longBitsToDouble(swapBytes(Float.floatToRawLongBits(val)));
    }

    public final static long swapBytes(long val){
        return ((val & 0xffl << 56) >>> 56) +
            ((val & 0xffl << 48) >> 40) +
            ((val & 0xffl << 40) >> 24) +
            ((val & 0xffl << 32) >> 8) +
            ((val & 0xffl << 24) << 8) +
            ((val & 0xffl << 16) << 24) +
            ((val & 0xffl << 8) << 40) +
            ((val & 0xffl) << 56);
    }
 **/
    /*** reads the header from the given stream. */
    public void readHeader(ByteBuffer dis)
        throws FileNotFoundException, IOException {
        delta = Float.intBitsToFloat(dis.getInt());  // 0
        depmin = Float.intBitsToFloat(dis.getInt());
        depmax = Float.intBitsToFloat(dis.getInt());
        scale = Float.intBitsToFloat(dis.getInt());
        odelta = Float.intBitsToFloat(dis.getInt());

        b = Float.intBitsToFloat(dis.getInt());      //20
        e = Float.intBitsToFloat(dis.getInt());
        o = Float.intBitsToFloat(dis.getInt());
        a = Float.intBitsToFloat(dis.getInt());
        fmt = Float.intBitsToFloat(dis.getInt());

        t0 = Float.intBitsToFloat(dis.getInt());      //40
        t1 = Float.intBitsToFloat(dis.getInt());
        t2 = Float.intBitsToFloat(dis.getInt());
        t3 = Float.intBitsToFloat(dis.getInt());
        t4 = Float.intBitsToFloat(dis.getInt());

        t5 = Float.intBitsToFloat(dis.getInt());      //60
        t6 = Float.intBitsToFloat(dis.getInt());
        t7 = Float.intBitsToFloat(dis.getInt());
        t8 = Float.intBitsToFloat(dis.getInt());
        t9 = Float.intBitsToFloat(dis.getInt());

        f = Float.intBitsToFloat(dis.getInt());      // 80
        resp0 = Float.intBitsToFloat(dis.getInt());
        resp1 = Float.intBitsToFloat(dis.getInt());
        resp2 = Float.intBitsToFloat(dis.getInt());
        resp3 = Float.intBitsToFloat(dis.getInt());

        resp4 = Float.intBitsToFloat(dis.getInt());  // 100
        resp5 = Float.intBitsToFloat(dis.getInt());
        resp6 = Float.intBitsToFloat(dis.getInt());
        resp7 = Float.intBitsToFloat(dis.getInt());
        resp8 = Float.intBitsToFloat(dis.getInt());

        resp9 = Float.intBitsToFloat(dis.getInt());  // 120
        stla = Float.intBitsToFloat(dis.getInt());   // 124
        stlo = Float.intBitsToFloat(dis.getInt());
        stel = Float.intBitsToFloat(dis.getInt());
        stdp = Float.intBitsToFloat(dis.getInt());

        evla = Float.intBitsToFloat(dis.getInt()); //140
        evlo = Float.intBitsToFloat(dis.getInt());
        evel = Float.intBitsToFloat(dis.getInt());
        evdp = Float.intBitsToFloat(dis.getInt());
        mag = Float.intBitsToFloat(dis.getInt());

        user0 = Float.intBitsToFloat(dis.getInt());  //160
        user1 = Float.intBitsToFloat(dis.getInt());
        user2 = Float.intBitsToFloat(dis.getInt());
        user3 = Float.intBitsToFloat(dis.getInt());
        user4 = Float.intBitsToFloat(dis.getInt());

        user5 = Float.intBitsToFloat(dis.getInt());  //180
        user6 = Float.intBitsToFloat(dis.getInt());
        user7 = Float.intBitsToFloat(dis.getInt());
        user8 = Float.intBitsToFloat(dis.getInt());
        user9 = Float.intBitsToFloat(dis.getInt());

        dist = Float.intBitsToFloat(dis.getInt()); // 200
        az = Float.intBitsToFloat(dis.getInt());
        baz = Float.intBitsToFloat(dis.getInt());
        gcarc = Float.intBitsToFloat(dis.getInt());
        sb = Float.intBitsToFloat(dis.getInt());

        sdelta = Float.intBitsToFloat(dis.getInt());   // 220
        depmen = Float.intBitsToFloat(dis.getInt());
        cmpaz = Float.intBitsToFloat(dis.getInt());
        cmpinc = Float.intBitsToFloat(dis.getInt());
        xminimum = Float.intBitsToFloat(dis.getInt());

        xmaximum = Float.intBitsToFloat(dis.getInt()); // 240
        yminimum = Float.intBitsToFloat(dis.getInt());
        ymaximum = Float.intBitsToFloat(dis.getInt());
        unused6 = Float.intBitsToFloat(dis.getInt());
        unused7 = Float.intBitsToFloat(dis.getInt());

        unused8 = Float.intBitsToFloat(dis.getInt());  // 260
        unused9 = Float.intBitsToFloat(dis.getInt());
        unused10 = Float.intBitsToFloat(dis.getInt());
        unused11 = Float.intBitsToFloat(dis.getInt());
        unused12 = Float.intBitsToFloat(dis.getInt());

        nzyear = dis.getInt();     // 280
        nzjday = dis.getInt();
        nzhour = dis.getInt();
        nzmin = dis.getInt();
        nzsec = dis.getInt();

        nzmsec = dis.getInt();     // 300
        nvhdr = dis.getInt();
        norid = dis.getInt();
        nevid = dis.getInt();
        npts = dis.getInt();      // 316

        nsnpts = dis.getInt();     // 320
        nwfid = dis.getInt();
        nxsize = dis.getInt();
        nysize = dis.getInt();
        unused15 = dis.getInt();

        iftype = dis.getInt();     // 340
        idep = dis.getInt();
        iztype = dis.getInt();
        unused16 = dis.getInt();
        iinst = dis.getInt();

        istreg = dis.getInt();     // 360
        ievreg = dis.getInt();
        ievtyp = dis.getInt();
        iqual = dis.getInt();
        isynth = dis.getInt();

        imagtyp = dis.getInt();    // 380
        imagsrc = dis.getInt();
        unused19 = dis.getInt();
        unused20 = dis.getInt();
        unused21 = dis.getInt();

        unused22 = dis.getInt();   // 400
        unused23 = dis.getInt();
        unused24 = dis.getInt();
        unused25 = dis.getInt();
        unused26 = dis.getInt();

        leven = dis.getInt();         // 420
        lpspol = dis.getInt();
        lovrok = dis.getInt();
        lcalda = dis.getInt();
        unused27 = dis.getInt();

        byte[] eightBytes = new byte[8];
        byte[] sixteenBytes = new byte[16];

        dis.get(eightBytes);   kstnm = new String(eightBytes);
        dis.get(sixteenBytes); kevnm = new String(sixteenBytes);

        dis.get(eightBytes);   khole = new String(eightBytes);
        dis.get(eightBytes);   ko = new String(eightBytes);
        dis.get(eightBytes);   ka = new String(eightBytes);

        dis.get(eightBytes);   kt0 = new String(eightBytes);
        dis.get(eightBytes);   kt1 = new String(eightBytes);
        dis.get(eightBytes);   kt2 = new String(eightBytes);

        dis.get(eightBytes);   kt3 = new String(eightBytes);
        dis.get(eightBytes);   kt4 = new String(eightBytes);
        dis.get(eightBytes);   kt5 = new String(eightBytes);

        dis.get(eightBytes);   kt6 = new String(eightBytes);
        dis.get(eightBytes);   kt7 = new String(eightBytes);
        dis.get(eightBytes);   kt8 = new String(eightBytes);

        dis.get(eightBytes);   kt9 = new String(eightBytes);
        dis.get(eightBytes);   kf = new String(eightBytes);
        dis.get(eightBytes);   kuser0 = new String(eightBytes);

        dis.get(eightBytes);   kuser1 = new String(eightBytes);
        dis.get(eightBytes);   kuser2 = new String(eightBytes);
        dis.get(eightBytes);   kcmpnm = new String(eightBytes);

        dis.get(eightBytes);   knetwk = new String(eightBytes);
        dis.get(eightBytes);   kdatrd = new String(eightBytes);
        dis.get(eightBytes);   kinst = new String(eightBytes);
    }


    /*** reads the data portion from the given stream.
     *  the Bytebuffer handles swapping
     * @param dis The byte buffer to read from
     */
    public void readData(ByteBuffer dis)
        throws FileNotFoundException, IOException {

        y = new double[npts];
        for (int i=0; i<npts; i++) {
            y[i] = Float.intBitsToFloat(dis.getInt());
        }


        if (leven == SacTimeSeries.FALSE ||
            iftype == SacTimeSeries.IRLIM ||
            iftype == SacTimeSeries.IAMPH) {
            x = new double[npts];
            for (int i=0; i<npts; i++) {
                x[i] = Float.intBitsToFloat(dis.getInt());
            }
            if (iftype == SacTimeSeries.IRLIM) {
                real = y;
                imaginary = x;
            }
            if (iftype == SacTimeSeries.IAMPH) {
                amp = y;
                phase = x;
            }
        }
    }
    /** get a byte buffer containing the "on disk" representation of this SAC file
     *@param bigEndian If true, file will be in big endian order, else little endian
     *@return The bytes that would make up the file 
     */
    public byte [] getBytes(boolean bigEndian) {
       int len = data_offset+npts*4;
       if (leven == SacTimeSeries.FALSE ||
            iftype == SacTimeSeries.IRLIM ||
            iftype == SacTimeSeries.IAMPH) len = data_offset+npts*8;
       // This buffer will contain the bytes for the file
       byte [] bf = new byte[len];
       ByteBuffer buf = ByteBuffer.wrap(bf);
       buf.position(0);
       if(bigEndian) buf.order(ByteOrder.BIG_ENDIAN);
       else buf.order(ByteOrder.LITTLE_ENDIAN);  // in network mode always use little endian
       writeHeader(buf);        // Stuff the header
       writeData(buf);          // Stuff the data
       return bf;
    }

    /*** writes this object out as a sac file.   The file is alway written in
     * the native byte order of the system writing the file.
     *@param filename A string filename or path to write*/
    public void write(String filename)
        throws FileNotFoundException, IOException {
        File fil = new File(filename);
        write(fil);
    }

    /*** writes this object out as a sac file.  The file is always written in 
     * the native byte order of the system writing the file. 
     *@param A File object with the file to write
     *@throws IOException if it is thrown writing the file
     */
    public void write(File file)
        throws  FileNotFoundException, IOException {
        RandomAccessFile fl = new RandomAccessFile(file, "rw");
         int len = data_offset+npts*4;
       if (leven == SacTimeSeries.FALSE ||
            iftype == SacTimeSeries.IRLIM ||
            iftype == SacTimeSeries.IAMPH) len = data_offset+npts*8;
       byte [] bf = new byte[len];
       ByteBuffer buf = ByteBuffer.wrap(bf);
       buf.position(0);
       buf.order(ByteOrder.nativeOrder());
       writeHeader(buf);
       writeData(buf);
       fl.write(bf);
       fl.close();
       //System.out.println("len="+len+" pos="+buf.position());

    }
    /** This routine writes data into the given byte buffer for the header portion.
     *The position in the bytebuffer
     *is moved from its current position forward by the length of the header.
     *@param dos The Byte buffer to put the data in.  
     */
    public void writeHeader(ByteBuffer dos) {
        dos.putInt(Float.floatToRawIntBits((float) delta));   //0
        dos.putInt(Float.floatToRawIntBits((float) depmin));
        dos.putInt(Float.floatToRawIntBits((float) depmax));
        dos.putInt(Float.floatToRawIntBits((float) scale));
        dos.putInt(Float.floatToRawIntBits((float) odelta));

        dos.putInt(Float.floatToRawIntBits((float) b));
        dos.putInt(Float.floatToRawIntBits((float) e));
        dos.putInt(Float.floatToRawIntBits((float) o));
        dos.putInt(Float.floatToRawIntBits((float) a));
        dos.putInt(Float.floatToRawIntBits((float) fmt));

        dos.putInt(Float.floatToRawIntBits((float) t0));
        dos.putInt(Float.floatToRawIntBits((float) t1));
        dos.putInt(Float.floatToRawIntBits((float) t2));
        dos.putInt(Float.floatToRawIntBits((float) t3));
        dos.putInt(Float.floatToRawIntBits((float) t4));

        dos.putInt(Float.floatToRawIntBits((float) t5));
        dos.putInt(Float.floatToRawIntBits((float) t6));
        dos.putInt(Float.floatToRawIntBits((float) t7));
        dos.putInt(Float.floatToRawIntBits((float) t8));
        dos.putInt(Float.floatToRawIntBits((float) t9));

        dos.putInt(Float.floatToRawIntBits((float) f));
        dos.putInt(Float.floatToRawIntBits((float) resp0));
        dos.putInt(Float.floatToRawIntBits((float) resp1));
        dos.putInt(Float.floatToRawIntBits((float) resp2));
        dos.putInt(Float.floatToRawIntBits((float) resp3));

        dos.putInt(Float.floatToRawIntBits((float) resp4)); // 100
        dos.putInt(Float.floatToRawIntBits((float) resp5));
        dos.putInt(Float.floatToRawIntBits((float) resp6));
        dos.putInt(Float.floatToRawIntBits((float) resp7));
        dos.putInt(Float.floatToRawIntBits((float) resp8));

        dos.putInt(Float.floatToRawIntBits((float) resp9));
        dos.putInt(Float.floatToRawIntBits((float) stla));
        dos.putInt(Float.floatToRawIntBits((float) stlo));
        dos.putInt(Float.floatToRawIntBits((float) stel));
        dos.putInt(Float.floatToRawIntBits((float) stdp));

        dos.putInt(Float.floatToRawIntBits((float) evla));
        dos.putInt(Float.floatToRawIntBits((float) evlo));
        dos.putInt(Float.floatToRawIntBits((float) evel));
        dos.putInt(Float.floatToRawIntBits((float) evdp));
        dos.putInt(Float.floatToRawIntBits((float) mag));

        dos.putInt(Float.floatToRawIntBits((float) user0));
        dos.putInt(Float.floatToRawIntBits((float) user1));
        dos.putInt(Float.floatToRawIntBits((float) user2));
        dos.putInt(Float.floatToRawIntBits((float) user3));
        dos.putInt(Float.floatToRawIntBits((float) user4));

        dos.putInt(Float.floatToRawIntBits((float) user5));
        dos.putInt(Float.floatToRawIntBits((float) user6));
        dos.putInt(Float.floatToRawIntBits((float) user7));
        dos.putInt(Float.floatToRawIntBits((float) user8));
        dos.putInt(Float.floatToRawIntBits((float) user9));

        dos.putInt(Float.floatToRawIntBits((float) dist));
        dos.putInt(Float.floatToRawIntBits((float) az));
        dos.putInt(Float.floatToRawIntBits((float) baz));
        dos.putInt(Float.floatToRawIntBits((float) gcarc));
        dos.putInt(Float.floatToRawIntBits((float) sb));

        dos.putInt(Float.floatToRawIntBits((float) sdelta));
        dos.putInt(Float.floatToRawIntBits((float) depmen));
        dos.putInt(Float.floatToRawIntBits((float) cmpaz));
        dos.putInt(Float.floatToRawIntBits((float) cmpinc));
        dos.putInt(Float.floatToRawIntBits((float) xminimum));

        dos.putInt(Float.floatToRawIntBits((float) xmaximum));
        dos.putInt(Float.floatToRawIntBits((float) yminimum));
        dos.putInt(Float.floatToRawIntBits((float) ymaximum));
        dos.putInt(Float.floatToRawIntBits((float) unused6));
        dos.putInt(Float.floatToRawIntBits((float) unused7));

        dos.putInt(Float.floatToRawIntBits((float) unused8));
        dos.putInt(Float.floatToRawIntBits((float) unused9));
        dos.putInt(Float.floatToRawIntBits((float) unused10));
        dos.putInt(Float.floatToRawIntBits((float) unused11));
        dos.putInt(Float.floatToRawIntBits((float) unused12));

        dos.putInt(nzyear);
        dos.putInt(nzjday);
        dos.putInt(nzhour);
        dos.putInt(nzmin);
        dos.putInt(nzsec);

        dos.putInt(nzmsec);
        dos.putInt(nvhdr);
        dos.putInt(norid);
        dos.putInt(nevid);
        dos.putInt(npts);

        dos.putInt(nsnpts);
        dos.putInt(nwfid);
        dos.putInt(nxsize);
        dos.putInt(nysize);
        dos.putInt(unused15);

        dos.putInt(iftype);
        dos.putInt(idep);
        dos.putInt(iztype);
        dos.putInt(unused16);
        dos.putInt(iinst);

        dos.putInt(istreg);
        dos.putInt(ievreg);
        dos.putInt(ievtyp);
        dos.putInt(iqual);
        dos.putInt(isynth);

        dos.putInt(imagtyp);
        dos.putInt(imagsrc);
        dos.putInt(unused19);
        dos.putInt(unused20);
        dos.putInt(unused21);

        dos.putInt(unused22);
        dos.putInt(unused23);
        dos.putInt(unused24);
        dos.putInt(unused25);
        dos.putInt(unused26);

        dos.putInt(leven);
        dos.putInt(lpspol);
        dos.putInt(lovrok);
        dos.putInt(lcalda);
        dos.putInt(unused27);

        if (kstnm.length() > 8) {kstnm = kstnm.substring(0,7); }
        while (kstnm.length() < 8) {kstnm += " "; }
        writeBytes(dos,kstnm);
        if (kevnm.length() > 16) {kevnm = kevnm.substring(0,15); }
        while (kevnm.length() < 16) { kevnm += " "; }
        writeBytes(dos,kevnm);

        if (khole.length() > 8) {khole = khole.substring(0,7); }
        while (khole.length() < 8) {khole += " "; }
        writeBytes(dos,khole);
        if (ko.length() > 8) {ko = ko.substring(0,7); }
        while (ko.length() < 8) {ko += " "; }
        writeBytes(dos,ko);
        if (ka.length() > 8) {ka = ka.substring(0,7); }
        while (ka.length() < 8) {ka += " "; }
        writeBytes(dos,ka);

        if (kt0.length() > 8) {kt0 = kt0.substring(0,7); }
        while (kt0.length() < 8) {kt0 += " "; }
        writeBytes(dos,kt0);
        if (kt1.length() > 8) {kt1 = kt1.substring(0,7); }
        while (kt1.length() < 8) {kt1 += " "; }
        writeBytes(dos,kt1);
        if (kt2.length() > 8) {kt2 = kt2.substring(0,7); }
        while (kt2.length() < 8) {kt2 += " "; }
        writeBytes(dos,kt2);

        if (kt3.length() > 8) {kt3 = kt3.substring(0,7); }
        while (kt3.length() < 8) {kt3 += " "; }
        writeBytes(dos,kt3);
        if (kt4.length() > 8) {kt4 = kt4.substring(0,7); }
        while (kt4.length() < 8) {kt4 += " "; }
        writeBytes(dos,kt4);
        if (kt5.length() > 8) {kt5 = kt5.substring(0,7); }
        while (kt5.length() < 8) {kt5 += " "; }
        writeBytes(dos,kt5);

        if (kt6.length() > 8) {kt6 = kt6.substring(0,7); }
        while (kt6.length() < 8) {kt6 += " "; }
        writeBytes(dos,kt6);
        if (kt7.length() > 8) {kt7 = kt7.substring(0,7); }
        while (kt7.length() < 8) {kt7 += " "; }
        writeBytes(dos,kt7);
        if (kt8.length() > 8) {kt8 = kt8.substring(0,7); }
        while (kt8.length() < 8) {kt8 += " "; }
        writeBytes(dos,kt8);

        if (kt9.length() > 8) {kt9 = kt9.substring(0,7); }
        while (kt9.length() < 8) {kt9 += " "; }
        writeBytes(dos,kt9);
        if (kf.length() > 8) {kf = kf.substring(0,7); }
        while (kf.length() < 8) {kf += " "; }
        writeBytes(dos,kf);
        if (kuser0.length() > 8) {kuser0 = kuser0.substring(0,7); }
        while (kuser0.length() < 8) {kuser0 += " "; }
        writeBytes(dos,kuser0);

        if (kuser1.length() > 8) {kuser1 = kuser1.substring(0,7); }
        while (kuser1.length() < 8) {kuser1 += " "; }
        writeBytes(dos,kuser1);
        if (kuser2.length() > 8) {kuser2 = kuser2.substring(0,7); }
        while (kuser2.length() < 8) {kuser2 += " "; }
        writeBytes(dos,kuser2);
        if (kcmpnm.length() > 8) {kcmpnm = kcmpnm.substring(0,7); }
        while (kcmpnm.length() < 8) {kcmpnm += " "; }
        writeBytes(dos,kcmpnm);

        if (knetwk.length() > 8) {knetwk = knetwk.substring(0,7); }
        while (knetwk.length() < 8) {knetwk += " "; }
        writeBytes(dos,knetwk);
        if (kdatrd.length() > 8) {kdatrd = kdatrd.substring(0,7); }
        while (kdatrd.length() < 8) {kdatrd += " "; }
        writeBytes(dos,kdatrd);
         if (kinst.length() > 8) {kinst = kinst.substring(0,7); }
         while (kinst.length() < 8) {kinst += " "; }
         writeBytes(dos,kinst);
 
     }
     private void writeBytes(ByteBuffer buf, String s) {
       buf.put(s.getBytes());
     }
     public void writeData(ByteBuffer dos) {
          // if complex, write out real and imaginary arrays
         if(iftype == SacTimeSeries.IRLIM) {
           for (int i=0; i<npts; i++) dos.putInt(Float.floatToRawIntBits((float) real[i]));
           for (int i=0; i<npts; i++) dos.putInt(Float.floatToRawIntBits((float) imaginary[i]));
           return;
         }
         // if and amplitude and phase write out both
         if(iftype == SacTimeSeries.IAMPH) {
           for (int i=0; i<npts; i++) dos.putInt(Float.floatToRawIntBits((float) amp[i]));
           for (int i=0; i<npts; i++) dos.putInt(Float.floatToRawIntBits((float) phase[i]));
           return;
         }
         
         // Its a time series, put out y
         for (int i=0; i<npts; i++) dos.putInt(Float.floatToRawIntBits((float) y[i]));
         // If not evenly sampled, put out x as well
         if (leven == SacTimeSeries.FALSE ) for (int i=0; i<npts; i++) dos.putInt(Float.floatToRawIntBits((float) x[i]));
    }
 
 
     public static final DecimalFormat decimalFormat = new DecimalFormat("#####.####");
 
     public static String format(String label, double f) {
         String s = label+" = ";
         String fString = decimalFormat.format(f);
         while (fString.length() < 8) {
             fString = " "+fString;
         }
         s = s+fString;
         while (s.length() < 21) {
             s = " "+s;
         }
         return s;
     }
 
     public static String formatLine(String s1, double f1, String s2, double f2, String s3, double f3, String s4, double f4, String s5, double f5) {
 
         return format(s1,f1)+
             format(s2,f2)+
             format(s3,f3)+
             format(s4,f4)+
             format(s5,f5);
     }
 
     public void printHeader() {
         System.out.println(formatLine("delta",delta,
                                       "depmin",depmin,
                                       "depmax",depmax,
                                       "scale",scale,
                                       "odelta",odelta));
 
         System.out.println(formatLine("b",b,
                                       "e",e,
                                       "o",o,
                                       "a",a,
                                       "fmt",fmt));
 
         System.out.println(formatLine("t0",t0,
                                       "t1",t1,
                                       "t2",t2,
                                       "t3",t3,
                                       "t4",t4));
 
         System.out.println(formatLine("t5",t5,
                                       "t6",t6,
                                       "t7",t7,
                                       "t8",t8,
                                       "t9",t9));
 
         System.out.println(formatLine("f",f,
                                       "resp0",resp0,
                                       "resp1",resp1,
                                       "resp2",resp2,
                                       "resp3",resp3));
 
         System.out.println(formatLine("resp4",resp4,
                                       "resp5",resp5,
                                       "resp6",resp6,
                                       "resp7",resp7,
                                       "resp8",resp8));
 
         System.out.println(formatLine("resp9",resp9,
                                       "stla",stla,
                                       "stlo",stlo,
                                       "stel",stel,
                                       "stdp",stdp));
 
         System.out.println(formatLine("evla",evla,
                                       "evlo",evlo,
                                       "evel",evel,
                                       "evdp",evdp,
                                       "mag",mag));
 
         System.out.println(formatLine("user0",user0,
                                       "user1",user1,
                                       "user2",user2,
                                       "user3",user3,
                                       "user4",user4));
 
         System.out.println(formatLine("user5",user5,
                                       "user6",user6,
                                       "user7",user7,
                                       "user8",user8,
                                       "user9",user9));
 
         System.out.println(formatLine("dist",dist,
                                       "az",az,
                                       "baz",baz,
                                       "gcarc",gcarc,
                                       "sb",sb));
 
         System.out.println(formatLine("sdelta",sdelta,
                                       "depmen",depmen,
                                       "cmpaz",cmpaz,
                                       "cmpinc",cmpinc,
                                       "xminimum",xminimum));
 
         System.out.println(formatLine("xmaximum",xmaximum,
                                       "yminimum",yminimum,
                                       "ymaximum",ymaximum,
                                       "unused6",unused6,
                                       "unused7",unused7));
 
         System.out.println(formatLine("unused8",unused8,
                                       "unused9",unused9,
                                       "unused10",unused10,
                                       "unused11",unused11,
                                       "unused12",unused12));
 
         System.out.println(formatLine("nzyear",nzyear,
                                       "nzjday",nzjday,
                                       "nzhour",nzhour,
                                       "nzmin",nzmin,
                                       "nzsec",nzsec));
 
         System.out.println(formatLine("nzmsec",nzmsec,
                                       "nvhdr",nvhdr,
                                       "norid",norid,
                                       "nevid",nevid,
                                       "npts",npts));
 
         System.out.println(formatLine("nsnpts",nsnpts,
                                       "nwfid",nwfid,
                                       "nxsize",nxsize,
                                       "nysize",nysize,
                                       "unused15",unused15));
 
         System.out.println(formatLine("iftype",iftype,
                                       "idep",idep,
                                       "iztype",iztype,
                                       "unused16",unused16,
                                       "iinst",iinst));
 
         System.out.println(formatLine("istreg",istreg,
                                       "ievreg",ievreg,
                                       "ievtyp",ievtyp,
                                       "iqual",iqual,
                                       "isynth",isynth));
 
         System.out.println(formatLine("imagtyp",imagtyp,
                                       "imagsrc",imagsrc,
                                       "unused19",unused19,
                                       "unused20",unused20,
                                       "unused21",unused21));
 
         System.out.println(formatLine("unused22",unused22,
                                       "unused23",unused23,
                                       "unused24",unused24,
                                       "unused25",unused25,
                                       "unused26",unused26));
 
         System.out.println(formatLine("leven",leven,
                                       "lpspol",lpspol,
                                       "lovrok",lovrok,
                                       "lcalda",lcalda,
                                       "unused27",unused27));
 
         System.out.println(
             " kstnm = "+kstnm+
                 " kevnm = "+kevnm+
                 " khole = "+khole+
                 " ko = "+ko);
 
         System.out.println(
             " ka = "+ka+
                 " kt0 = "+kt0+
                 " kt1 = "+kt1+
                 " kt2 = "+kt2);
         System.out.println(
             " kt3 = "+kt3+
                 " kt4 = "+kt4+
                 " kt5 = "+kt5+
                 " kt6 = "+kt6);
         System.out.println(
             " kt7 = "+kt7+
                 " kt8 = "+kt8+
                 " kt9 = "+kt9+
 
                 " kf = "+kf);
         System.out.println(
             " kuser0 = "+kuser0+
                 " kuser1 = "+kuser1+
                 " kuser2 = "+kuser2+
                 " kcmpnm = "+kcmpnm);
         System.out.println(
             " knetwk = "+knetwk+
                 " kdatrd = "+kdatrd+
                 " kinst = "+kinst);
     }
     
   /** this trims any filled data at the end of a buffer.  This option is used in the 
    *CWBQuery tool -sactrim option.  It resets SAC parameter npts as well are returning
    * the number of points trimmed to the user 
    *@param fill the fill value to trim from the end
    *@return the number of samples at the end which are filled.
    */
    public int trimNodataEnd(int fill) {
      double doubleFill = (double) fill;
      // start at the end and keep looking until a non-fill is found
      for(int i=npts-1; i>=0; i--) 
        if(y[i] != doubleFill) { 
          int ret = npts - i -1; 
          npts=i+1; 
          return ret;
        }
      int ret = npts;
      npts=0;
      return ret;
    }
    
    @Override
    public int compareTo(Object s) {
      SacTimeSeries sac = (SacTimeSeries) s;
      String seedname = (sac.knetwk+"  ").substring(0,2)+(sac.kstnm+"     ").substring(0,5)+
                (sac.kcmpnm+"   ").substring(0,3)+(sac.khole.trim().equals("-12345") ? "  " : (sac.khole+"  ").substring(0,2));
      String thisseedname = (knetwk+"  ").substring(0,2)+(kstnm+"     ").substring(0,5)+
                (kcmpnm+"   ").substring(0,3)+(khole.trim().equals("-12345") ? "  " : (khole+"  ").substring(0,2));
      int i = thisseedname.compareTo(seedname);
      if(i != 0) return i;
      long time = (nzyear - 1970)*366*86400000L+nzjday*86400000L+nzhour*3600000L+nzmin*60000L+nzsec*1000L+nzmsec;
      long time2 = (sac.nzyear - 1970)*366*86400000L+sac.nzjday*86400000L+sac.nzhour*3600000L+sac.nzmin*60000L+sac.nzsec*1000L+sac.nzmsec;

      return Long.compare(time, time2);
    }
   
    @Override
    public String toString() {
      return (knetwk+"  ").substring(0,2)+(kstnm+"     ").substring(0,5)+
                (kcmpnm+"   ").substring(0,3)+(khole.trim().equals("-12345") ? "  " : (khole+"  ").substring(0,2))+
              nzyear+","+nzjday+" "+nzhour+":"+nzmin+":"+nzsec+"."+nzmsec;
    }
 }
