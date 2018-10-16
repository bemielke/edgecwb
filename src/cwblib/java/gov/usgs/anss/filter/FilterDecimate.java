/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.filter;
import gov.usgs.anss.util.Util;
/** This class represents the Filter and Decimation of a single channel of data.
 *
 * @author davidketchum
 */
public class FilterDecimate {
  int decimation;
  int nstage;
  short [] stage = new short[5];
  FilterStage [] stages;
  int delayMS;
  int input_rate, output_rate;
  private int [] output ;				/* max # of points to process */
  public int getDelayMS() {return delayMS;}
  /** Build up chain for stages to filter and decimate a times series from one frequency
   * to another
   *
   * @param inputRate The input rate in hz
   * @param outputRate The desired output rate in hz
   * @param heli If true, apply a helicorder IIR filter at 10 hz data
   */
  public FilterDecimate(int inputRate, int outputRate, boolean heli) {
    boolean dbg=false;
    input_rate=inputRate;
    output_rate=outputRate;

    int i=input_rate/output_rate;
    decimation=i;					/* set decimation factor */
    nstage=0;						/* initialize stage counter */
    if(i > 100 || i < 2) throw new RuntimeException("Decimation rates do not make sense - decimate by "+i);
    if(dbg) Util.prt("Compute decimate="+decimation+" in="+input_rate+" out="+output_rate);

		/* note for each supported rate to get to output rate 1 (which is output rate) */
		switch (i)
		{case 16:
			stage[nstage]=2;		/* a stage of two */
			nstage++;
		case 8:
			stage[nstage]=2;		/* a stage of two */
			nstage++;
		case 4:
			stage[nstage]=2;		/* a stage of two */
			nstage++;
		case 2 :
			stage[nstage]=2;		/* a stage of two */
			nstage++;
			break;
    case 200:
      stage[nstage]=2;
      nstage++;
    case 100:
      stage[nstage]=10;
      nstage++;
      stage[nstage]=10;
      nstage++;
      break;
		case 50:
			stage[nstage]=10;		/* a stage of ten */
			nstage++;
		case 5:
			stage[nstage]=5;		/* a stage of five */
			nstage++;
			break;
    case 80:
      stage[nstage]=2;
      nstage++;
		case 40:
			stage[nstage]=2;		/* a stage of two */
			nstage++;
		case 20:
			stage[nstage]=2;		/* a stage of two */
			nstage++;
		case 10:
			stage[nstage]=10;		/* a stage of ten */
			nstage++;
			break;
		case 25:
			stage[nstage]=5;
			nstage++;
			stage[nstage]=5;
			nstage++;
			break;
		default:
			Util.prt("SET filt unknown decimation="+i);
			throw new RuntimeException ("Unknown decimation factor ="+i);
		}


	if(heli)						/* add heli shaping stage */
	{	stage[nstage]=-1;
		nstage++;
	}

	if(dbg) Util.prt("Nstages="+nstage);
	delayMS=0;							/* initialize delayMS */
	int j=input_rate;
  stages = new FilterStage[nstage];
	for(i=0; i<nstage; i++)
	{	switch (stage[i])
		{	case 2:
				stages[i] = new Filt2();
				//delayMS += 30400/j;		/* delayMS amount in MS 30584 30468 avg=30526*/
				delayMS += 31500/j;		// (nfir-1)/2 method
				//delayMS += 30500/j;     // Bolton correation mthod
				j=j/2;
				break;
			case 10:
				stages[i] = new Filt10();
				//delayMS += 113720/j;    // original from VDL
				delayMS += 129500/j;    // (nfir-1)/2 method
				//delayMS += 120500/j;      // estimated from Bolton correlation
				j=j/10;
				break;
			case 5:
				stages[i] = new Filt5();
				//delayMS += 100000/j;	/* 5 hz to 1 add on delayMS in MS 99500 (19.9 sec)*/
				delayMS += 99500/j;     // (nfir-1)/2 method
				j = j/5;
				break;
			case -1:
				stages[i] = new FiltHeli();
				delayMS += 1;
				break;
			default:
				Util.prt("Bad stage found in set file="+stage[i]);
        throw new RuntimeException("Bad stage found in set file="+stage[i]);
		}
	}

  /*
    debug - dump out filter for inspection
  */
    if(dbg)	Util.prt("In="+input_rate+" out="+output_rate+" Nstages="+nstage+" total delayMS="+delayMS);

  }

  /** LH decimation was written to provide LH data to Hydra with smaller latencies in
   * 20 or more samples per time.  This might also be used by CWBQuery to make LH from BH or HH
   *
   */

  /** Given the decimates worth of data (if decimating 40 Hz to 1 Hz, the 40 samples), this
   * returns the one sample from the decimation.  All of the stages computed in the
   * setup are applied and all history needed for later is saved in the Filtering objects.
   *
   * Note for heli sampling from 40 to 10 Hz you present 4 data points each time.
   *
   * @param data A decimations worth of data (if decimating 40 Hz to 1, 40 samples)
   * @param off The offset in data where the samples start
   * @return
   */
  public int decimate(int [] data, int off) {
    int i,j,istage;
    boolean dbg=false;
    if(output == null) output= new int[decimation];
    i=decimation;				/* I will count down the decimation */
    System.arraycopy(data, off, output, 0, decimation);
    //memcpy(output,data,i*sizeof(long));/* move timeseris to output */
    for (istage=0; istage<nstage; istage++)/* for each filtering stage */
    {	switch (stage[istage])
      {
        case 2:
          if(dbg) Util.prt(istage+" Apply 2 decimation for i="+i);
          for(j=0; j<i; j+=2)
          {	output[j/2] = stages[istage].filt(output,j);
          }
          i = i/2;						/* half as much data */
          break;
        case 10:
          if(dbg) Util.prt(istage+" Apply 10 decimation for i="+i);
          for(j=0; j<i; j+=10)
          {	output[j/10] = stages[istage].filt(output,j);
           }
          i = i/10;
          break;
        case 5:
          if(dbg) Util.prt(istage+" Apply 5 decimation for i="+i);
          for(j=0; j<i; j+=5)
          {	output[j/5] = stages[istage].filt(output, j);
          }
          i = i/5;
          break;
        case -1:
          output[0]=stages[istage].filt(output,0);
          break;
        default :
          Util.prt("DECIMATE : bad filter stage="+stages[istage]);
      }
    }							/* end of each stage */
    if(i != 1) Util.prt("Filter stage divide down error !i="+i);
    return output[0];
  }
  public static void main(String args[]) {
    double f =.025;
    int rate=40;
    int outrate=1;
    boolean heli=false;
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-f")) {f  = Double.parseDouble(args[i+1]); i++;}
      if(args[i].equals("-i")) {rate  = Integer.parseInt(args[i+1]); i++;}
      if(args[i].equals("-o")) {outrate  = Integer.parseInt(args[i+1]); i++;}
      if(args[i].equals("-h")) heli=true;
    }
    int len=86400*rate;
    int [] data=new int[len];
    int [] output = new int[len];
    double df = 2.*3.1415926*(f/rate);

    for(int i=0; i<len; i++) {
      data[i] = (int) (Math.sin(i*df)*1000.+0.5);
    }
    StringBuilder sb = new StringBuilder(10000);
    for(int i=0; i<400; i++) {
      sb.append(data[i]).append(i % 20 == 19 ? "\n" + (i + "    ").substring(0, 4) + ":" : " ");
    }
    int j=0;
    FilterDecimate fd = new FilterDecimate(rate, outrate, heli);
    long beg = System.currentTimeMillis();
    for(int i=0; i<len; i=i+rate) {
      output[j++] = fd.decimate(data, i);
    }
    long elapsed=System.currentTimeMillis() -beg;
    sb.append("0   :");
    for(int i=0; i<j; i++) sb.append(output[i]).append(i % 20 == 19 ? "\n" + (i + "    ").substring(0, 4) + ":" : " ");
    Util.prt(sb.toString()+"\nelapsed="+elapsed);
  }
}
