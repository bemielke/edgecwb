
/*
 * Copyright 2010, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package gov.usgs.anss.seed;
//import gov.usgs.anss.edge.*;
import java.util.GregorianCalendar;
import java.util.Calendar;
import gov.usgs.anss.util.Util;
/**
 *  Class for decoding or encoding Steim2-compressed data blocks
 *  to or from an array of integer values.  Change to an object from all
 * by D.C. ketchum March 2011.  This allows internal storage for results and
 * temporary arrays rather than continuously allocating an discarding them on
 * each decode.
 * <p>
 * Steim compression scheme Copyrighted by Dr. Joseph Steim.<p>
 * <dl>
 * <dt>Reference material found in:</dt>
 * <dd>
 * Appendix B of SEED Reference Manual, 2nd Ed., pp. 119-125
 * <i>Federation of Digital Seismic Networks, et al.</i>
 * February, 1993
 * </dd>
 * <dt>Coding concepts gleaned from code written by:</dt>
 * <dd>Guy Stewart, IRIS, 1991</dd>
 * <dd>Tom McSweeney, IRIS, 2000</dd>
 * </dl>
 *
 * @author Philip Crotwell (U South Carolina)
 * @author Robert Casey (IRIS DMC)
 * @author Dave Ketchum (USGS)
 * @version 03/04/2011
 */
public class Steim2Object {

  private int xminus1;
  private boolean dbg;
  private boolean strictRIC=false;
  private boolean traceBackErrors;
  private String reverseError;
  private String sampleCountError;
  private final int [] frameNsamp = new int[64];
  private final int [] frameReverse = new int[64];
  private final int [] frameForward = new int[64];
  private int[] samples ;
  private int end;
  private final int[] tempSamples = new int[106];
  private int current;                      // number of samples in last decomp()
  private StringBuffer sb;
  public  void setStrictRIC(boolean t) {strictRIC=t;}
  public  String getDebugString() {return sb.toString();}
  public void setTracebackErrors(boolean t) {traceBackErrors=t;}
  public void setDebug(boolean t) {dbg=t;}
  /** return if the last decode had a reverse integration error, this error does not cause a SteimException
   *@return true if last decode had a reverse integeration error*/
   public boolean hadReverseError() {return (reverseError != null);}
  /** get some text documenting the last reverse integration error
   *@return The text describing the reverse integration error*/
   public String getReverseError() {return (reverseError == null? "": reverseError);}
  /** return if the last decode had a reverse integration error, this error does not cause a SteimException
   *@return true if last decode had a reverse integeration error*/
   public boolean hadSampleCountError() {return (sampleCountError != null);}
  /** get some text documenting the last sample count error
   *@return The text describing the reverse integration error*/
   public String getSampleCountError() {return (sampleCountError == null? "": sampleCountError);}
   public int getXminus1() {return xminus1;}
  /** return an array with the number of samples in each frame of the last decode
   *@return An array with the number of samples in each of the last decode*/
   public int [] getFrameNsamp() {return frameNsamp;}
  /** return an array with the value of the reverse integration constant for each frame in last decode
   *@return An array with the reverse integration constants from last decode */
   public int [] getFrameReverse() {return frameReverse;}
  /** return an array with the value of the reverse integration constant for each frame in last decode
   *@return An array with the reverse integration constants from last decode */
   public int [] getFrameForward() {return frameForward;}
  /** return an array with the value of the reverse integration constant for each frame in last decode
   *@return An array with the reverse integration constants from last decode */
   public int  getRIC() {return end;}
   /** get the samples from the last decmpression
    *
    * @return The samples
    */
   public int[] getSamples() {return samples;}
   /** Return number of samples in last decomp (last returned value from decomp()
    *
    * @return The number of samples
    */
   public int getNsamp() {return current;}
	/**
	 *  Decode the indicated number of samples from the provided byte array and return
   * a success code.  The buffering for the decode is interal, so the user has to ask
   *  for the resulting array of ints seaparate.  This allows the limiting of creation of
   *  little arrays of ins from occuring on each invocation.  Being differencing
	 *  compression, there may be an offset carried over from a previous data
	 *  record.  This offset value can be placed in <b>bias</b>, otherwise leave
	 *  the value as 0.
	 *  @param b input byte array to be decoded
	 *  @param numSamples the number of samples that can be decoded from array
	 *  <b>b</b>
	 *  @param swapBytes if true, swap reverse the endian-ness of the elements of
	 *  byte array <b>b</b>.
	 *  @param bias the first difference value will be computed from this value.
	 *  If set to 0, the method will attempt to use the X(0) constant instead.
	 *  @return int array of length <b>numSamples</b> This is an internal array and the user can use it but it will change each call.
	 *  @throws SteimException - encoded data length is not multiple of 64
	 *  bytes.
	 */
  public synchronized boolean decode(byte[] b, int numSamples, boolean swapBytes, int bias) throws SteimException {
		if (b.length % 64 != 0) {
		  throw new SteimException("encoded data length is not multiple of 64 bytes (" + b.length + ")");
		}
    dbg=false;
    for(int i=0; i<63; i++) {frameReverse[i]=2147000000; frameNsamp[i] = 0;frameForward[i]=-2147000000;}
    reverseError=null;
    sampleCountError=null;
		if(samples == null) samples = new int[numSamples*2];
    if(samples.length < numSamples ) samples = new int[numSamples*2];
    if(numSamples == 0) return true;

		int numFrames = b.length / 64;
    if(numFrames < 1 || numFrames > 64) throw new SteimException("# frames unknown = "+numFrames);
		current = 0;
		int start;
    //int end=0;
		int firstData;
		int lastValue = 0;
    int ntemp;

    if(dbg ){
      if(sb == null) sb=new StringBuffer(10000);
      else sb.delete(0,  sb.length()-1);
    }
    int lastCurrent=0;
		//System.err.println("DEBUG: number of samples: " + numSamples + ", number of frames: " + numFrames + ", byte array size: " + b.length);
		for (int i=0; i< numFrames; i++ ) {
			//System.err.println("DEBUG: start of frame " + i);
			ntemp = extractSamples(b, i*64, swapBytes, tempSamples);   // returns only differences except for frame 0
			firstData = 0; // d(0) is byte 0 by default
			if (i==0) {   // special case for first frame
				lastValue = bias; // assign our X(-1)
        xminus1=lastValue;
				// x0 and xn are in 1 and 2 spots
				start = tempSamples[1];  // X(0) is byte 1 for frame 0
				end = tempSamples[2];    // X(n) is byte 2 for frame 0
				firstData = 3; // d(0) is byte 3 for frame 0
				//System.err.println("DEBUG: frame " + i + ", bias = " + bias + ", x(0) = " + start + ", x(n) = " + end);
				// if bias was zero, then we want the first sample to be X(0) constant
        if(ntemp < 4)
          Util.prt("   **** Bad tempsamples (<3) in first frame!="+tempSamples.length);
        else if (bias == 0) {
          lastValue = start - tempSamples[3];  // X(-1) = X(0) - d(0)
          xminus1 = lastValue;
        }
			}
			//System.err.print("DEBUG: ");
			for (int j = firstData; j < ntemp && current < numSamples; j++) {
				samples[current] = lastValue + tempSamples[j];  // X(n) = X(n-1) + d(n)
				lastValue = samples[current];
				//System.err.print("d(" + (j-firstData) + ")" + tempSamples[j] + ", x(" + current + ")" + samples[current] + ";");
				current++;
			}
			//System.err.println("DEBUG: end of frame " + i);
      if(i == 0) frameNsamp[i] = current;
      else frameNsamp[i] = current - lastCurrent;   // number of samples in this frame
      if(current > 0) frameReverse[i] = samples[current-1];
      frameForward[i] = samples[lastCurrent];
      lastCurrent = current;
      if(current >= numSamples) break;                // no need to process the empty frames
		}  // end for each frame...
    if(current <= 0) {
      //System.out.println("Steim2 illegal # current");
      throw new SteimException("Steim2 found no samples in block");
    }
    if(samples[current-1] != end && (end != 0 || strictRIC)) {       // if end is zero, presume it was never set and hence is not an error
      reverseError="Steim2 RIC error is="+samples[current-1]+"!="+end+" expected at "+(current-1);
      //System.out.println("Steim2 RIC error is="+samples[current-1]+"!="+end+" rev constant");
      if(traceBackErrors)
        new RuntimeException("Steim2 RIC err (non-fatal) is="+samples[current-1]+"!="+end+" rev constant").printStackTrace();
      return false;
    }
    if(current != numSamples) {
      sampleCountError="Steim2 sample count error got "+current+" expected "+numSamples;
      //System.out.println(sampleCountError);
      if(traceBackErrors)
        new RuntimeException("Steim2 sample Count err (non-fatal) is="+current+" expected"+numSamples).printStackTrace();
      return false;
    }
		return true;
	}

	/**
	 * Abbreviated, zero-bias version of decode().
	 *  Decode the indicated number of samples from the provided byte array and
	 *  return an integer array of the decompressed values.  Being differencing
	 *  compression, there may be an offset carried over from a previous data
	 *  record.  This offset value can be placed in <b>bias</b>, otherwise leave
	 *  the value as 0.
	 *  @param b input byte array to be decoded
	 *  @param numSamples the number of samples that can be decoded from array
	 *  <b>b</b>
	 *  @param swapBytes if true, swap reverse the endian-ness of the elements of
	 *  byte array <b>b</b>.
	 *  If set to 0, the method will attempt to use the X(0) constant instead.
	 *  @return int array of length <b>numSamples</b> This is an internal array and the user can use it but it will change each call.
	 *  @throws SteimException - encoded data length is not multiple of 64
	 *  bytes.
	 *
	 * @see edu.iris.Fissures.codec.Steim2#decode(byte[],int,boolean,int)
	 */
	public  boolean decode(byte[] b, int numSamples, boolean swapBytes) throws SteimException {
		// zero-bias version of decode
		return decode(b,numSamples,swapBytes,0);
	}
  /** this does not do anything.  Use RawToMiniSeed if you know what that is.  if not
   * ask IRIS why you cannot encode this way!
   *
   * @param samples input data
   * @return The frames of encoded data.
   */
	public  byte[] encode(int[] samples) {
		byte[] b = new byte[0];
		return b;
	}

	/**
	 * Extracts differences from the next 64 byte frame of the given compressed
	 * byte array (starting at offset) and returns those differences in an int
	 * array.
	 * An offset of 0 means that we are at the first frame, so include the header
	 * bytes in the returned int array...else, do not include the header bytes
	 * in the returned array.
	 * @param bytes byte array of compressed data differences
	 * @param offset index to begin reading compressed bytes for decoding
	 * @param swapBytes reverse the endian-ness of the compressed bytes being read
	 * @param temp integer array of difference (and constant) values to return decopressed data in
   * @return The number of values valid in temp
	 */
	protected  int extractSamples(byte[] bytes,
			int offset,
			boolean swapBytes, int [] temp)
	{
		/* get nibbles */
		int nibbles = Utility.bytesToInt(bytes[offset],
				bytes[offset+1],
				bytes[offset+2],
				bytes[offset+3],
				swapBytes);
		int currNibble;
    if(dbg) sb.append("Key=").append(Integer.toHexString(nibbles)).append("\n");
		int dnib;
		int tempInt;
		int currNum = 0;
		for (int i=0; i<16; i++) {
			currNibble = (nibbles >> (30 - i*2 ) ) & 0x03;
			switch (currNibble) {
				case 0:
					//System.out.println("0 means header info");
					// only include header info if offset is 0 and in first 3 words (forward and rev constant)
					if (offset == 0 && currNum <= 3) { // added currNum < 3 12/06 to fix concatenated block problem
						temp[currNum++] = Utility.bytesToInt(bytes[offset+(i*4)],
								bytes[offset+(i*4)+1],
								bytes[offset+(i*4)+2],
								bytes[offset+(i*4)+3],
								swapBytes);
            if(dbg) sb.append("Word ").append(i).append(" is hdr ").append(Integer.toHexString(temp[currNum - 1])).append("\n");
					}
					break;
				case 1:
					//System.out.println("1 means 4 one byte differences");
					temp[currNum++] = Utility.bytesToInt(bytes[offset+(i*4)]);
					temp[currNum++] = Utility.bytesToInt(bytes[offset+(i*4)+1]);
					temp[currNum++] = Utility.bytesToInt(bytes[offset+(i*4)+2]);
					temp[currNum++] = Utility.bytesToInt(bytes[offset+(i*4)+3]);
          if(dbg) sb.append("Word ").append(i).append(" is 8 byte ns=").append(currNum - 4).
                  append("diff=").append(bytes[offset + (i * 4)]).append(" ").
                  append(bytes[offset + (i * 4) + 1]).append(" ").append(bytes[offset + (i * 4) + 2]).
                  append(" ").append(bytes[offset + (i * 4) + 3]).append(" val=").
                  append(temp[currNum - 4]).append(" ").append(temp[currNum - 3]).append(" ").
                  append(temp[currNum - 2]).append(" ").append(temp[currNum - 1]).append("\n");
					break;
				case 2:
					tempInt = Utility.bytesToInt(bytes[offset+(i*4)],
							bytes[offset+(i*4)+1],
							bytes[offset+(i*4)+2],
							bytes[offset+(i*4)+3],
							swapBytes);
					dnib = (tempInt >> 30) & 0x03;
					switch (dnib) {
						case 1:
							//System.out.println("2,1 means 1 thirty bit difference");
							temp[currNum++] = (tempInt << 2) >> 2;
              if(dbg) sb.append("Word ").append(i).append(" is 02 dnib=").append(dnib).
                      append(" ns=").append(currNum - 1).append("diff(30 bit)=").
                      append(temp[currNum - 1]).append("\n");
							break;
						case 2:
							//System.out.println("2,2 means 2 fifteen bit differences");
							temp[currNum++] = (tempInt << 2) >> 17;  // d0
							temp[currNum++] = (tempInt << 17) >> 17; // d1
              if(dbg) sb.append("Word ").append(i).append(" is 02 dnib=").append(dnib).
                      append(" ns=").append(currNum - 2).append("diff(15 bit)=").
                      append(temp[currNum - 2]).append(" ").append(temp[currNum - 1]).append("\n");
							break;
						case 3:
							//System.out.println("2,3 means 3 ten bit differences");
							temp[currNum++] = (tempInt << 2) >> 22;  // d0
							temp[currNum++] = (tempInt << 12) >> 22; // d1
							temp[currNum++] = (tempInt << 22) >> 22; // d2
              if(dbg) sb.append("Word ").append(i).append(" is 02 dnib=").append(dnib).
                      append(" ns=").append(currNum - 3).append("diff(10 bit)=").
                      append(temp[currNum - 3]).append(" ").append(temp[currNum - 2]).
                      append(" ").append(temp[currNum - 1]).append("\n");
							break;
						default:
							//System.out.println("default");
					}
					break;
				case 3:
					tempInt = Utility.bytesToInt(bytes[offset+(i*4)],
							bytes[offset+(i*4)+1],
							bytes[offset+(i*4)+2],
							bytes[offset+(i*4)+3],
							swapBytes);
					dnib = (tempInt >> 30) & 0x03;
					// for case 3, we are going to use a for-loop formulation that
					// accomplishes the same thing as case 2, just less verbose.
					int diffCount = 0;  // number of differences
					int bitSize = 0;    // bit size
					int headerSize = 0; // number of header/unused bits at top
					switch (dnib) {
						case 0:
							//System.out.println("3,0 means 5 six bit differences");
							headerSize = 2;
							diffCount = 5;
							bitSize = 6;
							break;
						case 1:
							//System.out.println("3,1 means 6 five bit differences");
							headerSize = 2;
							diffCount = 6;
							bitSize = 5;
							break;
						case 2:
							//System.out.println("3,2 means 7 four bit differences, with 2 unused bits");
							headerSize = 4;
							diffCount = 7;
							bitSize = 4;
							break;
						default:
							//System.out.println("default");
					}
					if (diffCount > 0) {
						for (int d=0; d<diffCount; d++) {  // for-loop formulation
							temp[currNum++] = ( tempInt << (headerSize+(d*bitSize)) ) >> (((diffCount-1)*bitSize) + headerSize);
						}
            if(dbg) {
              String s ="Word "+i+" is 03 dnib="+dnib+" ns="+(currNum-4)+" diff("+bitSize+" bit)=";
              for(int ii=0; ii<diffCount; ii++) s = s + temp[currNum+ii-diffCount]+" ";
              sb.append(s).append("\n");
            }
					}
			}
		}
		return currNum;
	}

	/**
	 *  method for testing the decode() method.
	 * @param args not used
	 * @throws SteimException from called method(s)
	 */
	public  void main(String[] args) throws SteimException {
    Util.setModeGMT();
    GregorianCalendar ending = new GregorianCalendar(2006,3,15,23,59);
    Util.prt(Util.ascdate(ending)+" "+Util.asctime2(ending)+" ms="+ending.getTimeInMillis());
    int dom = ending.get(Calendar.DAY_OF_MONTH);
    long time=ending.getTimeInMillis();
    int ms=60002;
    //end.setTimeInMillis(time+ms);
    ending.add(Calendar.MILLISECOND, ms);
    Util.prt(Util.ascdate(ending)+" "+Util.asctime2(ending)+
        " ms="+ending.getTimeInMillis()+" df="+(ending.getTimeInMillis()-time));
    Util.prt("Dom = "+dom+" end dom="+ending.get(Calendar.DAY_OF_MONTH));
		byte[] b = new byte[64];
		int[] temp;

		for (int i=0; i< 64 ; i++) {
			b[i] = 0x00;
		}
		b[0] = 0x01;
		b[1] = (byte)0xb0;
		System.out.println(b[1]);
		b[2] = (byte)0xff;
		b[3] = (byte)0xff;

		b[4] = 0;
		b[5] = 0;
		b[6] = 0;
		b[7] = 0;

		b[8] = 0;
		b[9] = 0;
		b[10] = 0;
		b[11] = 0;

		b[12] = 1;
		b[13] = 2;
		b[14] = 3;
		b[15] = 0;

		b[16] = 1;
		b[17] = 1;
		b[18] = 0;
		b[19] = 0;

		b[20] = 0;
		b[21] = 1;
		b[22] = 0;
		b[23] = 0;
		temp = Steim2.decode(b, 17, false);
	}

}
