package kmi.utilities.dave;

import java.util.*;
import java.io.*;
import java.text.*;
import kmi.tools.*;

/**
 * This is a class to hold data for a file to be stored in COSMOS format. This code is
 * currently set up for COSMOS v1.20, although it is intended to be flexible enough to
 * grow and adapt as the format does.<p>
 * The class is set up to read the contents of the various headers from an input file,
 * along with several deployment specific variables. Variables and header fields are
 * defined within the code based on index number (say of integer values) or based on
 * line number and column position within the headers.<p>
 * The class provides generic methods for setting the values without in most cases
 * knowing the details of the variable itself.<p>
 * See COSMOSTESTER.JAVA for an example.<p>
 *
 * v1.1  Add reader of KMI generated COSMOS event files readFile(filename)()<p>
 *
 * Copyright:    Copyright (c) 2006, Kinemetrics, Inc.
 * @author DM Pumphrey
 * @author LR Emery
 * @version 1.1
 * I think this class is obsolete.  It was only in the K2COSMOS
 */
public class COSMOSDataHolder
{
   // Class variables
   String errorVal;                          // Error string returned from some functions
   ArrayList _textHeader = null;             // Text header
   ArrayList _integerHeader = null;          // Integer header
   ArrayList _realHeader = null;             // Real header
   ArrayList _commentHeader = null;          // Comment header
   ArrayList _dataHeader = null;             // Data header
   ArrayList _trailer = null;                // Trailer
   ArrayList _hdrVars = null;                // Header variables
   ArrayList _unitModes = null;              // Unit modes
   FileBasedTempDataArray _data = null;      // Data array
   final int MAX_ARRAY=100000;
   final int RAMSAMPLES=800000;  // Maximum number of in-RAM samples
   int[] _intValue = null;                   // Integer values
   double[] _realValue = null;               // Real values
   double _dataScale = 1.0;                  // Data scaling factor
   int _samplecount;                         // Sample count
   int _sps;                                 // Samples per second

   //  read variables   ----------------------------------
   //   from Text header
   String recorderName = "";            // RECORDERNAME
   int recorderSN;                      // RECORDERSN
   int recorderChans;                   // RECORDERCHANS
   String recordID = "";                // RECORDID

   //   from integer field
   int recorderType;                    // RECORDERTYPE
   int recorderBits;                    // RECORDERBITS
   String startTime = "";               //  START TIME STRING OF *
   int year;                            // STARTYEAR  *
   int julianDay;                       // STARTJULIAN  *
   int month;                           // STARTMONTH  *
   int dayOfMonth;                      // STARTDAYOFMONTH  *
   int hour;                            // STARTHOUR  *
   int minute;                          // STARTMINUTE  *
   int timeQuality;                     // TIMEQUALITY
   int timeSource;                      // TIMESOURCE
   int sensorType;                      // SENSORTYPEI
   int sensorSN;                        // SENSORSN

   //   from real field
   double recorderLSB;                  // RECORDERLSB  uV/count
   double recorderFS;                   // RECORDERFULLSCALE
   double preevent;                     // PREEVENTSECS
   double postevent;                    // POSTEVENTSECS
   double seconds;                      // STARTSECOND  *  x.xxx
   double utcOffset;                    // UTCOFFSET
   double samplePeriod;                 // SAMPLEINTERVAL
   double recordLength;                 // RECORDERLENR
   double sensorNatFreq;                // SENSORNATFREQ
   double sensorDamping;                // SENSORDAMPING
   double sensorSens;                   // SENSORSENS
   double sensorGain;                   // SENSORGAIN
   double sampleIntervalMS;             // SAMPLEINTERVALMS
   double seriesLength;                 // SERIESLENGTH

   //  from data header
   int unitCode;                        // UNITCODE
                                        //
// --------------------------------------------------------------------

   // Header and variable types
   final int HDR_UNKNOWN        = 0;
   final int HDR_TEXT           = 1;
   final int HDR_INTEGER        = 2;
   final int HDR_REAL           = 3;
   final int HDR_COMMENT        = 4;
   final int HDR_DATA           = 5;
   final int HDR_TRAILER        = 6;
   final int VAL_INTEGER        = 7;
   final int VAL_REAL           = 8;

   // Header data types
   final int TYPE_UNKNOWN       = -1;
   final int TYPE_STRING        = 0;
   final int TYPE_INTEGER       = 1;
   final int TYPE_REAL1         = 2;
   final int TYPE_REAL2         = 3;
   final int TYPE_REAL3         = 4;
   final int TYPE_REAL4         = 5;

   final String VAL_VERSION     = "01.20";   // COSMOS version
   final int VAL_UNKNOWN        = -999;      // Unknown data default
   final int VAL_NUMINTS        = 100;       // Number of integers
   final String VAL_FMTINTS     = "10I8";    // Integer format
   final int VAL_NUMREALS       = 100;       // Number of reals
   final String VAL_FMTREALS    = "6F13.6";  // Real format

   /**
   Constructor. Creates a COSMOSDataHolder object.
   @param samplecount - Number of samples to be given to object
   @param sps - Sample rate (samples per second)
   */
   public COSMOSDataHolder(int samplecount, int sps)
   {
      // Save passed variables locally
      _samplecount = samplecount;
      _sps = sps;

      // Allocate headers and other arrays
      _textHeader = new ArrayList();
      _integerHeader = new ArrayList();
      _realHeader = new ArrayList();
      _commentHeader = new ArrayList();
      _dataHeader = new ArrayList();
      _trailer = new ArrayList();
      _hdrVars = new ArrayList();
      _unitModes = new ArrayList();
   }

   /**
    * Constructor, Creates a COSMOSDataHolder object for reading.
    * No passed arguments, will be read from file
    */
   public COSMOSDataHolder()
   {
      // Allocate headers and other arrays
      _textHeader = new ArrayList();
      _integerHeader = new ArrayList();
      _realHeader = new ArrayList();
      _commentHeader = new ArrayList();
      _dataHeader = new ArrayList();
      _trailer = new ArrayList();
      _hdrVars = new ArrayList();
      _unitModes = new ArrayList();
   }


   /**
   Clean up routine. Kind of like a destructor.
   */
   public void cleanup()
   {
	if (_data != null)
	{
		_data.cleanup();
		_data = null;
	}
   }

   /**
   Return error value as a string.
   @return Error string
   */
   public String error()
   {
      return errorVal;
   }

   /**
   Define data and header variables that are used by the class.
   */
   private void defineVars()
   {
      // Allocate numeric arrays
      _intValue = new int[VAL_NUMINTS];
      _realValue = new double[VAL_NUMREALS];

      // Define header variables
      defineHeaderVar("PHYSDATATYPE1", HDR_TEXT, TYPE_STRING, 1, 1, 26);
      defineHeaderVar("COSMOSVERSION", HDR_TEXT, TYPE_STRING, 1, 36, 5);
      defineHeaderVar("TEXTHDRLENGTH", HDR_TEXT, TYPE_INTEGER, 1, 47, 2);
      defineHeaderVar("AGENCYFIELD1", HDR_TEXT, TYPE_STRING, 1, 62, 18);
      defineHeaderVar("EQNAME", HDR_TEXT, TYPE_STRING, 2, 1, 40);
      defineHeaderVar("EQDATETIME", HDR_TEXT, TYPE_STRING, 2, 41, 40);
      defineHeaderVar("HYPOCTRLAT", HDR_TEXT, TYPE_REAL3, 3, 12, 7);
      defineHeaderVar("HYPOCTRLON", HDR_TEXT, TYPE_REAL3, 3, 22, 8);
      defineHeaderVar("HYPOCTRDEP", HDR_TEXT, TYPE_INTEGER, 3, 35, 3);
      defineHeaderVar("SOURCEAGENCY", HDR_TEXT, TYPE_STRING, 3, 40, 7);
      defineHeaderVar("MAGNITUDEINFO", HDR_TEXT, TYPE_STRING, 3, 47, 34);
      defineHeaderVar("ORIGINDATETIME", HDR_TEXT, TYPE_STRING, 4, 9, 23);
      defineHeaderVar("SOURCEAGENCY", HDR_TEXT, TYPE_STRING, 4, 36, 6);
      defineHeaderVar("AGENCYFIELD2", HDR_TEXT, TYPE_STRING, 4, 43, 38);
      defineHeaderVar("NETWORKNUMBER", HDR_TEXT, TYPE_INTEGER, 5, 10, 3);
      defineHeaderVar("STATIONNUMBER", HDR_TEXT, TYPE_INTEGER, 5, 14, 6);
      defineHeaderVar("NETWORKCODE", HDR_TEXT, TYPE_STRING, 5, 26, 2);
      defineHeaderVar("STATIONCODE", HDR_TEXT, TYPE_STRING, 5, 29, 6);
      defineHeaderVar("NETWORKABBREV", HDR_TEXT, TYPE_STRING, 5, 36, 5);
      defineHeaderVar("STATIONNAME", HDR_TEXT, TYPE_STRING, 5, 41, 40);
      defineHeaderVar("STALATITUDE", HDR_TEXT, TYPE_REAL4, 6, 8, 8);
      defineHeaderVar("STALONGITUDE", HDR_TEXT, TYPE_REAL4, 6, 16, 10);
      defineHeaderVar("SITEGEOLOGY", HDR_TEXT, TYPE_STRING, 6, 41, 40);
      defineHeaderVar("RECORDERNAME", HDR_TEXT, TYPE_STRING, 7, 11, 7);
      defineHeaderVar("RECORDERSN", HDR_TEXT, TYPE_INTEGER, 7, 21, 5);
      defineHeaderVar("RECORDERCHANS", HDR_TEXT, TYPE_INTEGER, 7, 28, 2);
      defineHeaderVar("STATIONCHANS", HDR_TEXT, TYPE_INTEGER, 7, 39, 3);
      defineHeaderVar("SENSORTYPE", HDR_TEXT, TYPE_STRING, 7, 58, 8);
      defineHeaderVar("SENSORSN", HDR_TEXT, TYPE_INTEGER, 7, 69, 7);
      defineHeaderVar("RECORDSTART", HDR_TEXT, TYPE_STRING, 8, 17, 28);
      defineHeaderVar("TIMEQUALITY", HDR_TEXT, TYPE_INTEGER, 8, 49, 1);
      defineHeaderVar("RECORDID", HDR_TEXT, TYPE_STRING, 8, 59, 22);
      defineHeaderVar("STATIONCHNUM", HDR_TEXT, TYPE_INTEGER, 9, 9, 3);
      defineHeaderVar("STATIONCHDIR", HDR_TEXT, TYPE_STRING, 9, 13, 8);
      defineHeaderVar("RECORDERCHNUM", HDR_TEXT, TYPE_INTEGER, 9, 33, 3);
      defineHeaderVar("SENSORLOCATION", HDR_TEXT, TYPE_STRING, 9, 47, 34);
      defineHeaderVar("RAWRECORDLENR", HDR_TEXT, TYPE_REAL3, 10, 20, 8);
      defineHeaderVar("RAWMAXVAL", HDR_TEXT, TYPE_STRING, 10, 45, 10);
      defineHeaderVar("RAWMAXTIME", HDR_TEXT, TYPE_REAL3, 10, 60, 8);
      defineHeaderVar("PROCESSEDINFO", HDR_TEXT, TYPE_STRING, 11, 11, 30);
      defineHeaderVar("PROCESSEDMAXVAL", HDR_TEXT, TYPE_STRING, 11, 48, 19);
      defineHeaderVar("PROCESSEDMAXTIME", HDR_TEXT, TYPE_REAL3, 11, 69, 8);
      defineHeaderVar("LOWFREQ3DBHZ", HDR_TEXT, TYPE_REAL2, 12, 22, 5);
      defineHeaderVar("LOWFREQ3DBSEC", HDR_TEXT, TYPE_REAL1, 12, 45, 6);
      defineHeaderVar("HIGHFREQ3DBHZ", HDR_TEXT, TYPE_REAL1, 12, 68, 5);
      defineHeaderVar("UNKNOWNI", HDR_TEXT, TYPE_INTEGER, 13, 65, 7);
      defineHeaderVar("UNKNOWNR", HDR_TEXT, TYPE_REAL1, 13, 73, 7);

      defineHeaderVar("INTHDRCOUNT", HDR_INTEGER, TYPE_INTEGER, 1, 1, 4);
      defineHeaderVar("INTHDRLENGTH", HDR_INTEGER, TYPE_INTEGER, 1, 38, 3);
      defineHeaderVar("INTHDRFORMAT", HDR_INTEGER, TYPE_STRING, 1, 57, 10);

      defineHeaderVar("REALHDRCOUNT", HDR_REAL, TYPE_INTEGER, 1, 1, 4);
      defineHeaderVar("REALHDRLENGTH", HDR_REAL, TYPE_INTEGER, 1, 35, 3);
      defineHeaderVar("REALHDRFORMAT", HDR_REAL, TYPE_STRING, 1, 54, 10);

      defineHeaderVar("COMMENTLENGTH", HDR_COMMENT, TYPE_INTEGER, 1, 1, 4);

      defineHeaderVar("DATAPOINTS", HDR_DATA, TYPE_INTEGER, 1, 1, 8);
      defineHeaderVar("PHYSDATATYPE2", HDR_DATA, TYPE_STRING, 1, 10, 16);
      defineHeaderVar("RAWRECORDLENI", HDR_DATA, TYPE_INTEGER, 1, 35, 4);
      defineHeaderVar("UNITDESCR", HDR_DATA, TYPE_STRING, 1, 52, 7);
      defineHeaderVar("UNITCODE", HDR_DATA, TYPE_INTEGER, 1, 60, 2);
      defineHeaderVar("DATAFORMAT", HDR_DATA, TYPE_STRING, 1, 72, 9);

      defineHeaderVar("STATIONCHNUM", HDR_TRAILER, TYPE_INTEGER, 1, 21, 3);
      defineHeaderVar("PHYSDATATYPE3", HDR_TRAILER, TYPE_STRING, 1, 25, 12);

      // Define integer fields
      defineValue("PROCESSINGSTAGE", VAL_INTEGER, 1);
      defineValue("PHYSDATATYPE4", VAL_INTEGER, 2);
      defineValue("UNITCODE", VAL_INTEGER, 3);
      defineValue("COSMOSVERSIONI", VAL_INTEGER, 4);
      defineValue("TRIGGERTYPE", VAL_INTEGER, 5);
      defineValue("STATIONNUMBER", VAL_INTEGER, 8);
      defineValue("NETWORKNUMBER", VAL_INTEGER, 11);
      defineValue("RECORDERTYPE", VAL_INTEGER, 30);
      defineValue("RECORDINGMEDIUM", VAL_INTEGER, 31);
      defineValue("RECORDERSN", VAL_INTEGER, 32);
      defineValue("RECORDERCHANS", VAL_INTEGER, 33);
      defineValue("RECORDERBITS", VAL_INTEGER, 35);
      defineValue("EFFECTIVEBITS", VAL_INTEGER, 36);
      defineValue("TRIGGERNUMBER", VAL_INTEGER, 38);
      defineValue("STARTYEAR", VAL_INTEGER, 40);
      defineValue("STARTJULIANDAY", VAL_INTEGER, 41);
      defineValue("STARTMONTH", VAL_INTEGER, 42);
      defineValue("STARTDAYOFMONTH", VAL_INTEGER, 43);
      defineValue("STARTHOUR", VAL_INTEGER, 44);
      defineValue("STARTMINUTE", VAL_INTEGER, 45);
      defineValue("TIMEQUALITY", VAL_INTEGER, 46);
      defineValue("TIMESOURCE", VAL_INTEGER, 47);
      defineValue("STATIONCHNUM", VAL_INTEGER, 50);
      defineValue("RECORDERCHNUM", VAL_INTEGER, 51);
      defineValue("SENSORTYPEI", VAL_INTEGER, 52);
      defineValue("SENSORSN", VAL_INTEGER, 53);
      defineValue("SENSORAZIMUTH", VAL_INTEGER, 54);

      // Define real fields
      defineValue("STALATITUDE", VAL_REAL, 1);
      defineValue("STALONGITUDE", VAL_REAL, 2);
      defineValue("STAELEVATION", VAL_REAL, 3);
      defineValue("RECORDERLSB", VAL_REAL, 22);
      defineValue("RECORDERFULLSCALE", VAL_REAL, 23);
      defineValue("PREEVENTSECS", VAL_REAL, 24);
      defineValue("POSTEVENTSECS", VAL_REAL, 25);
      defineValue("STARTSECOND", VAL_REAL, 30);
      defineValue("UTCOFFSET", VAL_REAL, 32);
      defineValue("SAMPLEINTERVAL", VAL_REAL, 34);
      defineValue("RAWRECORDLENR", VAL_REAL, 35);
      defineValue("V1MEAN", VAL_REAL, 36);
      defineValue("SENSORNATFREQ", VAL_REAL, 40);
      defineValue("SENSORDAMPING", VAL_REAL, 41);
      defineValue("SENSORSENS", VAL_REAL, 42);
      defineValue("SENSORFSINV", VAL_REAL, 43);
      defineValue("SENSORFSING", VAL_REAL, 44);
      defineValue("SENSORGAIN", VAL_REAL, 47);
      defineValue("SENSOROFFSETN", VAL_REAL, 50);
      defineValue("SENSOROFFSETE", VAL_REAL, 51);
      defineValue("SENSOROFFSETV", VAL_REAL, 52);
      defineValue("SAMPLEINTERVALMS", VAL_REAL, 62);
      defineValue("SERIESLENGTH", VAL_REAL, 63);
      defineValue("SERIESMAXVAL", VAL_REAL, 64);
      defineValue("SERIESMAXTIME", VAL_REAL, 65);
      defineValue("SERIESAVERAGE", VAL_REAL, 66);
      defineValue("V1GFACTOR", VAL_REAL, 88);

      // Define unit types and descriptors
      new unitMode(50, 0, "Raw acceleration counts",  "raw accel.   pts", "counts", "10I8");
      new unitMode(51, 1, "Uncorrected Acceleration", "acceleration pts", "volts", "6F13.9");
      new unitMode(52, 1, "Uncorrected Acceleration", "acceleration pts", "mvolts", "6F13.6");
      new unitMode(02, 1, "Uncorrected Acceleration", "acceleration pts", "g", "6F13.9");
      new unitMode(11, 1, "Uncorrected Acceleration", "acceleration pts", "mg", "6F13.6");
      new unitMode(12, 1, "Uncorrected Acceleration", "acceleration pts", "ug", "6F13.3");
      new unitMode(04, 1, "Uncorrected Acceleration", "acceleration pts", "cm/sec2", "8F10.3");
      new unitMode(07, 1, "Uncorrected Acceleration", "acceleration pts", "in/sec2", "8F10.4");
      new unitMode(10, 1, "Uncorrected Acceleration", "acceleration pts", "gal", "8F10.3");
   }  //  private void defineVars()

   /**
   Preset and check validity of variables that are used by the class.<p>
   These are doing the first basic checks of style and setting the most basic
   defaults such as the values for unknown.<p>
   Some of these values may be overridden by .set commands in the style file.
   @return true if initialized properly, false if not.
   */
   private boolean presetVars()
   {
      int ix;

      // COSMOS version check
      if (!getHeaderString("COSMOSVERSION").equals(VAL_VERSION))
      {
         errorVal = "COSMOS version does not match";
         return false;
      }
      // Text header length
      setHeaderInteger("TEXTHDRLENGTH", _textHeader.size());
      // Comment header length
      if ((_commentHeader.size() < 2) || (_commentHeader.size() > 5))
      {
         errorVal = "Must define 1-4 comment lines";
         return false;
      }
      setHeaderInteger("COMMENTLENGTH", _commentHeader.size()-1);

      // Define "unknown"
      setHeaderInteger("UNKNOWNI", VAL_UNKNOWN);
      setHeaderReal("UNKNOWNR", VAL_UNKNOWN);

      // Define numeric formats
      setHeaderString("INTHDRFORMAT", "("+VAL_FMTINTS+")");
      setHeaderString("REALHDRFORMAT", "("+VAL_FMTREALS+")");

      // Pre-initialize numeric fields
      for (ix=0; ix<VAL_NUMINTS; ix++)
      {
         _intValue[ix] = Integer.MAX_VALUE;
      }
      for (ix=0; ix<VAL_NUMREALS; ix++)
      {
         _realValue[ix] = Double.MAX_VALUE;
      }

      // Default to counts (V0)
      setHeaderInteger("UNITCODE", 50);

      return true;
   }  //  private boolean presetVars()

   /**
   Initialize and check validity of variables that are used by the class.<p>
   For the most part, this just allocates and loads the integer and real arrays
   with the defaults that may or may not have been changed by .set commands in
   the style file.<p>
   After this, some integer and real values may be set by .set commands.
   @return true if initialized properly, false if not.
   */
   private boolean initializeVars()
   {
      int ix;

      // Get user defined value of unknown
      int idef = getHeaderInteger("UNKNOWNI");
      // Set count and number of lines
      setHeaderInteger("INTHDRCOUNT", VAL_NUMINTS);
      int perline = Misc.atoi(getHeaderString("INTHDRFORMAT").substring(1));
      int lines = (VAL_NUMINTS + (perline/2))/perline;
      setHeaderInteger("INTHDRLENGTH", lines);

      for (ix=0; ix<VAL_NUMINTS; ix++)
      {
         if (_intValue[ix] == Integer.MAX_VALUE)
         {
            _intValue[ix] = idef;
         }
      }

      // Get user defined value of unknown
      double rdef = getHeaderReal("UNKNOWNR");
      // Set count and number of lines
      setHeaderInteger("REALHDRCOUNT", VAL_NUMREALS);
      perline = Misc.atoi(getHeaderString("REALHDRFORMAT").substring(1));
      lines = (VAL_NUMREALS + (perline/2))/perline;
      setHeaderInteger("REALHDRLENGTH", lines);

      for (ix=0; ix<VAL_NUMREALS; ix++)
      {
         if (_realValue[ix] == Double.MAX_VALUE)
         {
            _realValue[ix] = rdef;
         }
      }

      // Preset some values for all files I will create
      setHeaderString("PHYSDATATYPE3", "acceleration");     // Acceleration
      setHeaderInteger("PHYSDATATYPE4", 1);                 // Acceleration

      setHeaderInteger("COSMOSVERSIONI",
         (int)(Misc.atof(VAL_VERSION)*100.0));              // COSMOS version
      setHeaderInteger("RECORDINGMEDIUM", 3);               // Solid state recording

      setHeaderInteger("DATAPOINTS", _samplecount);         // Number of samples

      // Set raw record length
      setHeaderReal("RAWRECORDLENR", (double)_samplecount/(double)_sps);
      setHeaderInteger("RAWRECORDLENI", _samplecount/_sps);

      return true;
   }  //  private boolean initializeVars()

   /**
   Get a header string
   @return Header string
   */
   public String getHeader()
   {
	 String rbuf = new String();
	 for(int ix=0; ix<_textHeader.size(); ix++)
	 {
		rbuf += (String)_textHeader.get(ix);
		rbuf += "\n";
	 }
	 rbuf += "Comments:";
	 rbuf += "\n";
	 rbuf += "\n";
	 for(int ix=0; ix<_commentHeader.size(); ix++)
	 {
		rbuf += (String)_commentHeader.get(ix);
		rbuf += "\n";
	 }
	 return rbuf;
   }

   /**
   Define a header variable.
   @param varname - Variable name (can exist in multiple places if same type)
   @param varhdr - Header type
   @param vartyp - Variable type
   @param hdrline - Header line (starts at 1)
   @param hdrix - Header line index (starts at 1)
   @param varlen - Variable field length
   */
   private void defineHeaderVar(String varname, int varhdr, int vartyp, int hdrline, int hdrix, int varlen)
   {
      new headerVar(varname, varhdr, vartyp, hdrline-1, hdrix-1, varlen);
   }

   /**
   Define an integer or real value (not in the headers)
   @param varname - Variable name (can exist in multiple places if same type)
   @param vartype - Variable type
   @param index - Variable index
   */
   private void defineValue(String varname, int vartype, int index)
   {
      if (vartype == VAL_INTEGER)
      {
         new headerVar(varname, HDR_INTEGER, vartype, index-1, 0, 0);
      }
      if (vartype == VAL_REAL)
      {
         new headerVar(varname, HDR_REAL, vartype, index-1, 0, 0);
      }
   }

   /**
   Get a header variable type.
   @param varname - Variable name
   @return int value of vartype
   */
   private int getVarType(String varname)
   {
      return ((headerVar)_hdrVars.get(0)).getVarType(varname);
   }

   /**
   Get a header string.
   @param varname - Variable name
   @return String value, null if not found
   */
   public String getHeaderString(String varname)
   {
      return ((headerVar)_hdrVars.get(0)).getString(varname);
   }

   /**
   Get a header integer.
   @param varname - Variable name
   @return int value, 0 if not found
   */
   public int getHeaderInteger(String varname)
   {
      return ((headerVar)_hdrVars.get(0)).getInteger(varname);
   }

   /**
   Get a header real.
   @param varname - Variable name
   @return real value, 0 if not found
   */
   public double getHeaderReal(String varname)
   {
      return ((headerVar)_hdrVars.get(0)).getReal(varname);
   }


   /**
    * Get one sample of A/D count from _data array
    * @param index - index to sample, 0..XXX
    * @return int A/C count of sample
    */
   public int getSampleinADC(int index)
   {
      return _data.get(index);
   }



   /**
   Set an integer value in the header
   @param varname - Variable name
   @param val - Value to set
   */
   public void setHeaderInteger(String varname, int val)
   {
      boolean gotit = false;
      // If in the numeric values
      if (varname.startsWith("INT"))
      {
         int aix = Misc.atoi(varname.substring(3));
         if ((aix >= 1) && (aix <= VAL_NUMINTS))
         {
            _intValue[aix-1] = val;
            gotit = true;
         }
      }
      if (!gotit)
      {
         // Else in the headers
         ((headerVar)_hdrVars.get(0)).setInteger(varname, val);
      }
   }

   /**
   Set a real value in the header
   @param varname - Variable name
   @param val - Value to set
   */
   public void setHeaderReal(String varname, double val)
   {
      boolean gotit = false;
      // If in the numeric values
      if (varname.startsWith("REAL"))
      {
         int aix = Misc.atoi(varname.substring(4));
         if ((aix >= 1) && (aix <= VAL_NUMREALS))
         {
            _realValue[aix-1] = val;
            gotit = true;
         }
      }
      if (!gotit)
      {
         // Else in the headers
         ((headerVar)_hdrVars.get(0)).setReal(varname, val);
      }
   }

   /**
   Set a string value in the header
   @param varname - Variable name
   @param val - Value to set
   */
   public void setHeaderString(String varname, String val)
   {
      ((headerVar)_hdrVars.get(0)).setString(varname, val);
   }

   /**
   Set the data array for the actual samples.
   @param arr - Sample array
   */
   public void setData(FileBasedTempDataArray arr)
   {
      _data = arr;
   }

	/**
	Make a filename from components. Note that you must limit length of string components yourself if you care.
	@param ctime - Time in seconds since 1/1/1970 GMT.
	@param stationName - Station name
	@param channelNo - Channel number (not index)
	@param channelName - ChannelName
   @param julian - Julian Day convention
	@return String - Constructed filename
	*/
	public String makeFileName(int ctime, String stationName, int channelNo, String channelName, boolean julian)
	{
		String ofname;

      // Create a calendar object
		Calendar p_tm = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
      // Set to passed time
		p_tm.setTime(new Date((long)ctime*1000));

      // Include year
	   ofname = Misc.itoa(p_tm.get(Calendar.YEAR), 4);
      // Include month/day or julian day
      if (julian)
      {
   	   ofname = ofname + Misc.itoa(p_tm.get(Calendar.DAY_OF_YEAR), 3);
      }
      else
      {
	      ofname = ofname + Misc.itoa(p_tm.get(Calendar.MONTH)+1, 2)
	  	      + Misc.itoa(p_tm.get(Calendar.DAY_OF_MONTH), 2);
      }
      int stage = getHeaderInteger("PROCESSINGSTAGE");
      // Add hour, minute, second, station name, channel number, channel name
	  ofname = ofname + Misc.itoa(p_tm.get(Calendar.HOUR_OF_DAY), 2)
	  	   + Misc.itoa(p_tm.get(Calendar.MINUTE), 2)
	  	   + Misc.itoa(p_tm.get(Calendar.SECOND), 2)
	  	   + "." + stationName
	  	   + "." + Misc.itoa(channelNo, 3)
	  	   + "." + channelName + ".v" + stage;
	  ofname = strReplace(ofname, " ", "_");

	  return ofname;
	}
    String strReplace(String orig, String oldStr, String newStr)
    {
        int rix = 0;
        int slen = oldStr.length();
        while(rix >= 0)
        {
            rix = orig.indexOf(oldStr);
            if(rix >= 0)
            {
                orig = orig.substring(0, rix) + newStr + orig.substring(rix+slen);
            }
        }
        return(orig);
    }

   /**
   Pad a string to the given length
   @param inval - String to be padded
   @param len - Length I want
   */
   private String padTo(String inval, int len)
   {
      StringBuffer x = new StringBuffer(inval);
      while (x.length() < len)
      {
         x.append(' ');
      }
      return x.toString();
   }


   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   /**
    * Read a COSMOS .V0 file into the COSMOSDataHolder
    *
    * @param fileName - BufferedReader of input file
    * @return true if read OK, false if not
    */
   public boolean readFile(String fileName) {
   BufferedReader _ifile = null;
   int headerState = HDR_UNKNOWN;
   int lineCount = 0;
   int itemCount = 0;
   int itemsPerLine = 0;
//   int lineNo = 0;
   String xx = "";
   String sx1 = "";
   String sx2 = "";
   String sx3 = "";
   String sx4 = "";
   String sx5 = "";
   String sx6 = "";


      defineVars();                     // create keyword lists

      UserMsg.log("---  start readFile()");

      try {
         _ifile = new BufferedReader(new FileReader(fileName));
         UserMsg.log(fileName);

         // read in text header  -------------------------------------
         headerState = HDR_TEXT;

         //  line #0  (1)
         xx = _ifile.readLine();
//         UserMsg.debug(xx);

         //  cosmos format version
         sx2 = xx.substring(35,40);       // begin@, nextAfter,  0..79(80)
//         UserMsg.debug("'"+sx2+"'");

         if ( !sx2.equals(VAL_VERSION) ) {
           UserMsg.log("Invalid COSMOS file version, "+sx2);
           cleanup();
           _ifile.close();
           return false;
         }

         // get # of lines in text header
         lineCount = Misc.atoi(xx.substring(46,48));
//         UserMsg.debug("linecount= "+lineCount);

         // already have first line, put it in _textHeader
         sx1 = padTo(xx, 80);
         _textHeader.add(sx1);

         for ( int i = 0; i < (lineCount -1); i++ ) {
           xx = _ifile.readLine();
           sx1 = padTo(xx, 80);
           _textHeader.add(sx1);
         }

         // look at some of the header items
         recorderName = getHeaderString("RECORDERNAME");
         recorderSN = getHeaderInteger("RECORDERSN");
         recorderChans = getHeaderInteger("RECORDERCHANS");
         recordID = getHeaderString("RECORDID");
         UserMsg.log(recorderName+" s/n "+recorderSN+"  ID: '"+recordID+"'");

         // read in integer header  line   ----------------------------------
         headerState = HDR_INTEGER;

         xx = _ifile.readLine();
//         UserMsg.debug(xx);

         itemCount = Misc.atoi(xx.substring(0,4));
         lineCount = Misc.atoi(xx.substring(37, 40));
//         UserMsg.debug("items= "+itemCount+"  lines= "+lineCount);

         // check integer format
         sx1 = xx.substring(56, 67);
         sx2 = sx1.trim();                       // remove leading or trailing white space
//         UserMsg.debug("Iformat= '"+sx1+"'  trimmed Iformat= '"+sx2+"'");

         if ( ( !sx2.equals("("+VAL_FMTINTS+")") ) || ( itemCount != VAL_NUMINTS ) || ( lineCount != 10  ) ) {
            UserMsg.log("Invalid integer format, "+sx2);
            cleanup();
            _ifile.close();
            return false;
         }
         if ( sx2.equals("("+VAL_FMTINTS+")") ) {
            itemsPerLine = 10;
         }

         //  put integer header in _integerHeader
         sx1 = padTo(xx, 80);
         _integerHeader.add(sx1);

//         UserMsg.debug("ready to put data in _integerHeader");

         //  put integer data in _integerHeader
         int idx = 0;
         for ( int i = 0; i < (lineCount); i++ ) {
           xx = _ifile.readLine();
//           UserMsg.debug("line #"+i+" = "+xx);
           int start = 0; int end = 8; int interval = 8;
           for ( int j = 0; j < (itemsPerLine); j++ ) {
//              UserMsg.debug("idx= "+idx+" start= "+start+" end= "+end);
              _intValue[idx] = Misc.atoi(xx.substring(start,end));
              idx++;
              start += interval;
              end += interval;
           }
         }

         // look at some of the integer items
         year = getHeaderInteger("STARTYEAR");
         julianDay = getHeaderInteger("STARTJULIANDAY");
         month = getHeaderInteger("STARTMONTH");
         dayOfMonth = getHeaderInteger("STARTDAYOFMONTH");
         hour = getHeaderInteger("STARTHOUR");
         minute = getHeaderInteger("STARTMINUTE");
         // get the rest after read the reals

         timeQuality = getHeaderInteger("TIMEQUALITY");
         timeSource = getHeaderInteger("TIMESOURCE");

         // read in real header line  ----------------------------------
         headerState = HDR_REAL;

         xx = _ifile.readLine();
//         UserMsg.debug(xx);

         itemCount = Misc.atoi(xx.substring(0,4));
         lineCount = Misc.atoi(xx.substring(35, 37));
//         UserMsg.debug("items= "+itemCount+"  lines= "+lineCount);

         // check real format
         sx1 = xx.substring(53, 64);

         sx2 = sx1.trim();                       // remove leading or trailing white space
//         UserMsg.debug("Rformat= '"+sx1+"'  trimmed Rformat= '"+sx2+"'");
         if ( ( !sx2.equals("("+VAL_FMTREALS+")") ) || ( itemCount != VAL_NUMREALS ) || ( lineCount != 17  ) ) {
            UserMsg.log("Invalid real format, "+sx2);
            cleanup();
            _ifile.close();
            return false;
         }

         if ( sx2.equals("("+VAL_FMTREALS+")") ) {
            itemsPerLine = 6;
         }

         //  put real header line in _realHeader
         sx1 = padTo(xx, 80);
         _realHeader.add(sx1);

         //  put real data in _realHeader
         idx = 0;
         for ( int i = 0; i < (lineCount); i++ ) {
           xx = _ifile.readLine();
//           UserMsg.debug("line #"+i+" = "+xx);
           int start = 0; int end = 13; int interval = 13;
           for ( int j = 0; j < (itemsPerLine); j++ ) {
//              UserMsg.debug("idx= "+idx+" start= "+start+" end= "+end);
              _realValue[idx] = Misc.atod(xx.substring(start,end));
              idx++;
              if ( idx == itemCount ) {
               break;
              }
              start += interval;
              end += interval;
           }
         }

         //  look at some of the reals
         seconds = getHeaderReal("STARTSECOND");
         startTime = month+"/"+dayOfMonth+"/"+year+"  ("+julianDay+")  "+hour+":"+minute+":"+seconds;
         UserMsg.log(startTime);

         recorderLSB = getHeaderReal("REACODERLSB");
         recorderFS = getHeaderReal("RECORDERFULLSCALE");
         preevent = getHeaderReal("PREEVENTSECS");
         postevent = getHeaderReal("POSTEVENTSECS");
         utcOffset = getHeaderReal("UTCOFFSET");
         samplePeriod = getHeaderReal("SAMPLEINTERVAL");
         recordLength = getHeaderReal("RAWRECORDLENR");

         _sps = (int)( 1.0 / samplePeriod);
         _samplecount = (int)(recordLength * _sps);
         UserMsg.log("period= "+samplePeriod+"  length= "+recordLength+"  sps= "+ _sps+"  #sams= "+ _samplecount);

         //  read some of the sensor parameters
         sensorType = getHeaderInteger("SENSORTYPEI");
         sensorSN = getHeaderInteger("SENSORSN");
         sensorNatFreq = getHeaderReal("SENSORNATFREQ");
         sensorDamping = getHeaderReal("SENSORDAMPING");
         sensorSens = getHeaderReal("SENSORSENS");
         sensorGain = getHeaderReal("SENSORGAIN");
         sampleIntervalMS = getHeaderReal("SAMPLEINTERVALMS");
         seriesLength = getHeaderReal("SERIESLENGTH");

         // read in comment header line   --------------------------------------
         headerState = HDR_COMMENT;

         xx = _ifile.readLine();
//         UserMsg.debug(xx);

         lineCount = Misc.atoi(xx.substring(0,4));
//         UserMsg.debug("lines= "+lineCount);

         //  put comment header line in _commentHeader
         sx1 = padTo(xx, 80);
         _commentHeader.add(sx1);

         //  put comment data in _commentHeader
         for ( int i = 0; i < (lineCount); i++ ) {
           xx = _ifile.readLine();
           sx1 = padTo(xx, 80);
           _realHeader.add(sx1);
         }

         // read in the data,  in counts  ------------------------------------
         headerState = HDR_DATA;

         // Create an array to store the data
         try
         {
            _data = new FileBasedTempDataArray("samples", RAMSAMPLES, MAX_ARRAY);
         }
         catch(IOException e)
         {
            UserMsg.log("Unable to open a data array for reading");
            cleanup();
            _ifile.close();
            return false;
         }


         xx = _ifile.readLine();
//         UserMsg.debug(xx);

         itemCount = Misc.atoi(xx.substring(0,8));           // data points

         // check unitcode
         unitCode = Misc.atoi(xx.substring(59, 61));
         if ( unitCode != 50 ) {
            UserMsg.log("Invalid unitcodeformat, "+unitCode);
            cleanup();
            _ifile.close();
            return false;
         }

         // check format
         sx1 = xx.substring(71, 80);

         sx2 = sx1.trim();                       // remove leading or trailing white space
//         UserMsg.debug("Rformat= '"+sx1+"'  trimmed Rformat= '"+sx2+"'");
         if ( sx2.equals("("+VAL_FMTINTS+")") )  {
            itemsPerLine = 10;
         }
         else {
            UserMsg.log("Illegal data format, "+sx2);
            cleanup();
            _ifile.close();
            return false;
         }  //  check out data format

         lineCount = (int)(Math.ceil(itemCount / itemsPerLine));
//         UserMsg.debug("items= "+itemCount+"  lines= "+lineCount+"  items/line= "+itemsPerLine);

         //  put data header line in _dataHeader
         sx1 = padTo(xx, 80);
         _dataHeader.add(sx1);

         //  put data in _data array
         idx = 0;
         for ( int i = 0; i < (lineCount); i++ ) {
         int start;
         int end;
         int interval;
           xx = _ifile.readLine();
//           UserMsg.debug("line #"+i+" = "+xx);
           start = 0; end = 8; interval = 8;    // integers
           for ( int j = 0; j < (itemsPerLine); j++ ) {
              int ii = Misc.atoi(xx.substring(start,end));
              _data.set(ii, idx);
//              UserMsg.debug("idx= "+idx+" start= "+start+" end= "+end+"  ii= "+ii);

              idx++;
              if ( idx == itemCount ) {
                 break;
              }
              start += interval;
              end += interval;
           }
         }

         UserMsg.log("---  end readFile()");

         _ifile.close();

         return true;
      }
      catch( IOException e ) {
            UserMsg.error("IOException - unable to open File ");
         // do nothing for now
         return false;
      }

   }  //  public boolean readFile(String fileName)

   private String getSubstring(String s, int beginat, int endat) {
      return(s.substring(beginat-1, endat-1));
   }


   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!


   /**
   Write the COSMOS data out to the opened file handle.
   @param ofile - Output file handle - BufferedWriter
   @return true if written OK, false if not
   */
   public boolean write(BufferedWriter ofile)
   {
      int ix;
      int hix;
      int lcnt;
      int elength;
      int llength;
      int places;
      String wstr;
      String format;
      StringBuffer wbuf;

      // Set final scaling values
      setScaling();

      try
      {
         // Write text header
         for (hix=0; hix<_textHeader.size(); hix++)
         {
            ofile.write((String)_textHeader.get(hix));
            ofile.newLine();
         }
         // Write integer header
         for (hix=0; hix<_integerHeader.size(); hix++)
         {
            ofile.write((String)_integerHeader.get(hix));
            ofile.newLine();
         }

         lcnt = 0;
         wbuf = new StringBuffer();
         // Set integer format
         format = getHeaderString("INTHDRFORMAT");
         llength = Misc.atoi(format.substring(1));
         elength = Misc.atoi(format.substring(format.indexOf('I')+1));
         // Output Integer values
         for (ix=0; ix<VAL_NUMINTS; ix++)
         {
            wstr = ""+_intValue[ix];
            while (wstr.length() < elength)
            {
               wstr = " " + wstr;
            }
            wbuf.append(wstr);
            if (++lcnt >= llength)
            {
               ofile.write(wbuf.toString());
               ofile.newLine();
               lcnt = 0;
               wbuf.setLength(0);
            }
         }
         if (lcnt > 0)
         {
            ofile.write(wbuf.toString());
            ofile.newLine();
         }

         // Output real header
         for (hix=0; hix<_realHeader.size(); hix++)
         {
            ofile.write((String)_realHeader.get(hix));
            ofile.newLine();
         }

         lcnt = 0;
         wbuf = new StringBuffer();
         // Set real format
         format = getHeaderString("REALHDRFORMAT");
         llength = Misc.atoi(format.substring(1));
         elength = Misc.atoi(format.substring(format.indexOf('F')+1));
         places = Misc.atoi(format.substring(format.indexOf('.')+1));
         DecimalFormat df = new DecimalFormat();
         df.setMinimumFractionDigits(places);
         df.setMaximumFractionDigits(places);
         df.setGroupingUsed(false);
         // Output real values
         for (ix=0; ix<VAL_NUMREALS; ix++)
         {
            wstr = df.format(_realValue[ix]);
            while (wstr.length() < elength)
            {
               wstr = " " + wstr;
            }
            wbuf.append(wstr);
            if (++lcnt >= llength)
            {
               ofile.write(wbuf.toString());
               ofile.newLine();
               lcnt = 0;
               wbuf.setLength(0);
            }
         }
         if (lcnt > 0)
         {
            ofile.write(wbuf.toString());
            ofile.newLine();
         }

         // Output comments
         for (hix=0; hix<_commentHeader.size(); hix++)
         {
            ofile.write((String)_commentHeader.get(hix));
            ofile.newLine();
         }
         // Output data header
         for (hix=0; hix<_dataHeader.size(); hix++)
         {
            ofile.write((String)_dataHeader.get(hix));
            ofile.newLine();
         }

         lcnt = 0;
         wbuf = new StringBuffer();
         // Set data format
         format = getHeaderString("DATAFORMAT");
         llength = Misc.atoi(format.substring(1));
         if (format.indexOf('I') > 0)
         {
            elength = Misc.atoi(format.substring(format.indexOf('I')+1));
            places = 0;
         }
         if (format.indexOf('F') > 0)
         {
            elength = Misc.atoi(format.substring(format.indexOf('F')+1));
            places = Misc.atoi(format.substring(format.indexOf('.')+1));
         }
         df = new DecimalFormat();
         df.setMinimumFractionDigits(places);
         df.setMaximumFractionDigits(places);
         df.setGroupingUsed(false);
         // Output data values
         for (ix=0; ix<_samplecount; ix++)
         {
            wstr = df.format((double)_data.get(ix) * _dataScale);
            while (wstr.length() < elength)
            {
               wstr = " " + wstr;
            }
            wbuf.append(wstr);
            if (++lcnt >= llength)
            {
               ofile.write(wbuf.toString());
               ofile.newLine();
               lcnt = 0;
               wbuf.setLength(0);
            }
         }
         if (lcnt > 0)
         {
            ofile.write(wbuf.toString());
            ofile.newLine();
         }

         // Output trailer
         for (hix=0; hix<_trailer.size(); hix++)
         {
            ofile.write((String)_trailer.get(hix));
            ofile.newLine();
         }
      }
      catch (IOException e)
      {
         errorVal = "IO Exception in write()";
         return false;
      }
      return true;
   }  //  public boolean write(BufferedWriter ofile)

   /**
   Loads the style of the COSMOS file. Reads the contents of the headers and some deployment
   specific variables from the file. Keywords are denoted by a leading "." and include ".textheader",
   ".integerheader", ".realheader", ".commentheader", ".dataheader", ".trailer", and ".set".<p>
   Column alignment helpers "12345..." are allowed and will be ignored, as are comment lines that begin
   with ";".
   @param stylefile - Style filename
   @return true if success, false if not.
   */
   public boolean loadStyle(String stylefile)
   {
      int lineNo = 0;
      String data;
      String rdata;
      // Which header am I working on right now?
      int headerState = HDR_UNKNOWN;
      // Place to save set commands
      ArrayList _sets = new ArrayList();

      defineVars();

      try
      {
         // Open the style file
         BufferedReader fil = new BufferedReader(new FileReader(stylefile));

         // Read the style data in
         while (true)
         {
            rdata = fil.readLine();
            // At end...
            if (rdata == null)
            {
               break;
            }
            lineNo++;
            // Ignore comments
            if (rdata.startsWith(";"))
            {
               continue;
            }
            // Ignore column helpers
            if (rdata.startsWith("12345"))
            {
               continue;
            }
            // Handle keywords
            if (rdata.startsWith("."))
            {
               data = rdata.toLowerCase();
               // Header commands just set header type
               if (data.startsWith(".textheader"))
               {
                  headerState = HDR_TEXT;
               }
               if (data.startsWith(".integerheader"))
               {
                  headerState = HDR_INTEGER;
               }
               if (data.startsWith(".realheader"))
               {
                  headerState = HDR_REAL;
               }
               if (data.startsWith(".commentheader"))
               {
                  headerState = HDR_COMMENT;
               }
               if (data.startsWith(".dataheader"))
               {
                  headerState = HDR_DATA;
               }
               if (data.startsWith(".trailer"))
               {
                  headerState = HDR_TRAILER;
               }
               // Sets commands are saved for now
               if (data.startsWith(".set"))
               {
                  _sets.add(rdata);
               }
            }
            else
            {
               // Process non-keyword data, which has to be header data
               // Store the header data in the correct header array
               switch (headerState)
               {
               case HDR_UNKNOWN:
                  // This is bad. Not expecting to get anything now.
                  errorVal = "Unexpected data at line "+lineNo;
                  return false;
               case HDR_TEXT:
                  data = padTo(rdata, 80);
                  _textHeader.add(data);
                  break;
               case HDR_INTEGER:
                  data = padTo(rdata, 80);
                  _integerHeader.add(data);
                  break;
               case HDR_REAL:
                  data = padTo(rdata, 80);
                  _realHeader.add(data);
                  break;
               case HDR_COMMENT:
                  data = padTo(rdata, 80);
                  _commentHeader.add(data);
                  break;
               case HDR_DATA:
                  data = padTo(rdata, 80);
                  _dataHeader.add(data);
                  break;
               case HDR_TRAILER:
                  data = padTo(rdata, 80);
                  _trailer.add(data);
                  break;
               }
            }
         }
      }
      catch (IOException e)
      {
         // Do nothing
         errorVal = "I/O Exception in loadStyle()";
         return false;
      }

      if (!presetVars())
      {
         // If problems presetting
         return false;
      }

      // Assign any header values done by user .set commands
      // This is done twice - before and after some intervention by the initialize() method
      int ix;
      int asize = _sets.size();
      String command;
      String varname;
      StringTokenizer stok;
      StringBuffer sbuf;
      // Go through the .set array
      for (ix=0; ix<asize; ix++)
      {
         command = (String)_sets.get(ix);
         // Tokenize the command
         stok = new StringTokenizer(command);
         if (stok.countTokens() >= 3)
         {
            // Toss ".set"
            stok.nextToken();
            // Get variable name
            varname = stok.nextToken().toUpperCase();
            // Toss integer and real sets for now
            if (varname.startsWith("INT"))
            {
               if (Misc.atoi(varname.substring(3)) > 0)
               {
                  continue;
               }
            }
            if (varname.startsWith("REAL"))
            {
               if (Misc.atoi(varname.substring(4)) > 0)
               {
                  continue;
               }
            }
            // Store the variable's specified value
            switch (getVarType(varname))
            {
            case TYPE_STRING:
               data = command.substring(command.indexOf(' ')+1);
               data = data.substring(data.indexOf(' ')+1);
               setHeaderString(varname, data);
               break;
            case TYPE_INTEGER:
               setHeaderInteger(varname, Misc.atoi(stok.nextToken()));
               break;
            case TYPE_REAL1:
            case TYPE_REAL2:
            case TYPE_REAL3:
            case TYPE_REAL4:
               setHeaderReal(varname, Misc.atod(stok.nextToken()));
               break;
            }
         }
      }   //  for( ix 0..<asize

      // Initialization by the program
      if (!initializeVars())
      {
         // If problems initializing
         return false;
      }

      // Assign any ints and reals done by .set commands
      for (ix=0; ix<asize; ix++)
      {
         command = (String)_sets.get(ix);
         // Tokenize it
         stok = new StringTokenizer(command);
         int aix;
         if (stok.countTokens() >= 3)
         {
            // Toss ".set"
            stok.nextToken();
            // Get variable name
            varname = stok.nextToken().toUpperCase();
            switch (getVarType(varname))
            {
               // Integer values
            case VAL_INTEGER:
               setHeaderInteger(varname, Misc.atoi(stok.nextToken()));
               break;
               // Real values
            case VAL_REAL:
               setHeaderReal(varname, Misc.atod(stok.nextToken()));
               break;
               // Values placed in the integer or real value lists
            default:
               if (varname.startsWith("INT"))
               {
                  aix = Misc.atoi(varname.substring(3));
                  if ((aix >= 1) && (aix <= VAL_NUMINTS))
                  {
                     _intValue[aix-1] = Misc.atoi(stok.nextToken());
                  }
               }
               if (varname.startsWith("REAL"))
               {
                  aix = Misc.atoi(varname.substring(4));
                  if ((aix >= 1) && (aix <= VAL_NUMREALS))
                  {
                     _realValue[aix-1] = Misc.atod(stok.nextToken());
                  }
               }
               break;
            }
         }
      }

      // Set final units representation
      setUnits();

      return true;
   }  //  public boolean loadStyle(String stylefile)

   /**
   Set scaling information. This is done very late in the game, just before writing the
   file out. This to make sure that user preferences are all honored.
   */
   private void setScaling()
   {
      int ix;
      // Get unit code value
      int unitcode = getHeaderInteger("UNITCODE");
      // Get full scale bits value
      double fsBits = (Math.pow(2.0, (double)(getHeaderInteger("RECORDERBITS")-1)) - 1.0);

      // Set scaling factor based on units
      switch (unitcode)
      {
      case 50:
         _dataScale = 1.0;
         break;
      case 51:
         _dataScale = getHeaderReal("RECORDERFULLSCALE") / fsBits;
         break;
      case 52:
         _dataScale = getHeaderReal("RECORDERFULLSCALE") * 1000.0 / fsBits;
         break;
      case 02:
         _dataScale = getHeaderReal("SENSORFSING") / fsBits;
         break;
      case 11:
         _dataScale = getHeaderReal("SENSORFSING") * 1000.0 / fsBits;
         break;
      case 12:
         _dataScale = getHeaderReal("SENSORFSING") * 1000000.0 / fsBits;
         break;
      case 04:
         _dataScale = getHeaderReal("SENSORFSING") * 980.665 / fsBits;
         break;
      case 07:
         _dataScale = getHeaderReal("SENSORFSING") * (980.665 / 2.54) / fsBits;
         break;
      case 10:
         _dataScale = getHeaderReal("SENSORFSING") * 981.0 / fsBits;
         break;
      default:
         // Anything else is not handled
         unitcode = 50;
         setHeaderInteger("UNITCODE", unitcode);
         setUnits();
      }
      // If not counts (V1), set V1 values
      if (unitcode != 50)
      {
         setHeaderReal("V1GFACTOR", (980.665 * getHeaderReal("SENSORFSING")) / (_dataScale * fsBits));

         double mean = 0.0;
         for (ix=0; ix<_samplecount; ix++)
         {
            mean += (double)_data.get(ix);
         }
         mean = mean / (double)_samplecount;
         setHeaderReal("V1MEAN", mean*_dataScale);

         for (ix=0; ix<_samplecount; ix++)
         {
            _data.set(_data.get(ix)-(int)mean, ix);
         }
      }
      int tix = 0;
      double average = 0.0;
      double maxval = Integer.MIN_VALUE;
      // Compute average for the data
      for (ix=0; ix<_samplecount; ix++)
      {
         double val = (double)_data.get(ix);
         average += val;
         if (val > maxval)
         {
            maxval = val;
            tix = ix;
         }
      }
      average = average / (double)_samplecount;

      // Set raw max value and time of it
      DecimalFormat df = new DecimalFormat();
      df.setMinimumFractionDigits(3);
      df.setMaximumFractionDigits(3);
      df.setGroupingUsed(false);
      String wstr = df.format(maxval / fsBits * getHeaderReal("SENSORFSING")) + " g";
      setHeaderString("RAWMAXVAL", wstr);
      setHeaderReal("RAWMAXTIME", ((double)tix)/((double)_sps));

      // If not counts (V1), set other V1 values
      if (unitcode != 50)
      {
         setHeaderReal("SAMPLEINTERVALMS", getHeaderReal("SAMPLEINTERVAL")*1000.0);
         setHeaderReal("SERIESLENGTH", getHeaderReal("RAWRECORDLENR"));
         setHeaderReal("SERIESMAXVAL", maxval * _dataScale);
         setHeaderReal("SERIESMAXTIME", ((double)tix)/((double)_sps));
         setHeaderReal("SERIESAVERAGE", average * _dataScale);
      }
   }  //  private void setScaling()

   /**
   Set units. Based on unit code, set processing stage, data type (in multiple fields), unit descriptor
   and data format.
   */
   private void setUnits()
   {
      int unitcode = getHeaderInteger("UNITCODE");

      int lsize = _unitModes.size();
      for (int lix=0; lix<lsize; lix++)
      {
         unitMode uval = (unitMode)_unitModes.get(lix);
         if (uval._unitcode == unitcode)
         {
            setHeaderInteger("PROCESSINGSTAGE", uval._procstage);
            setHeaderString("PHYSDATATYPE1", uval._physprop1);
            setHeaderString("PHYSDATATYPE2", uval._physprop2);
            setHeaderString("UNITDESCR", uval._unitdescr);
            setHeaderString("DATAFORMAT", "("+uval._dataformat+")");
         }
      }
   }  //  private void setUnits()

   /**
   Unit mode class stores unit properties and other information.
   */
   private class unitMode
   {
      int _unitcode;
      int _procstage;
      String _physprop1;
      String _physprop2;
      String _unitdescr;
      String _dataformat;

      /**
      Constructor
      @param unitcode - Unit code from table 2
      @param procstage - Processing stage (0 or 1)
      @param physprop1 - Physical property string 1
      @param physprop2 - Physical property string 2
      @param unitdescr - Unit description string
      @param dataformat - Data format
      */
      public unitMode(int unitcode, int procstage, String physprop1, String physprop2, String unitdescr, String dataformat)
            {
               _unitcode = unitcode;
               _procstage = procstage;
               _physprop1 = physprop1;
               _physprop2 = physprop2;
               _unitdescr = unitdescr;
               _dataformat = dataformat.toUpperCase();

               _unitModes.add(this);
            }
   }  //  private class unitMode

   /**
   Header variable class holds variable name, type, header (or not), and position in data tables or header.
   */
   private class headerVar
   {
      // Variables
      String _varname;
      int _varhdr;
      int _vartyp;
      int _hdrline;
      int _hdrix;
      int _varlen;

      /**
      Constructor
      @param varname - Variable name (can exist in multiple places if same type)
      @param varhdr - Header type
      @param vartyp - Variable type
      @param hdrline - Header line (starts at 1)
      @param hdrix - Header line index (starts at 1)
      @param varlen - Variable field length
      */
      public headerVar(String varname, int varhdr, int vartyp, int hdrline, int hdrix, int varlen)
      {
         _varname = varname.toUpperCase();
         _varhdr = varhdr;
         _vartyp = vartyp;
         _hdrline = hdrline;
         _hdrix = hdrix;
         _varlen = varlen;

         _hdrVars.add(this);
      }

      /**
      Get a variable type of a header value. Returns on first one found.
      @param varname - Variable name
      @return int value of variable type
      */
      public int getVarType(String varname)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               return hval._vartyp;
            }
         }
         return TYPE_UNKNOWN;
      }

      /**
      Get a string value from the headers. Returns on first one found.
      @param varname - Variable name
      @return String value from the header
      */
      public String getString(String varname)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               if (hval._vartyp == TYPE_STRING)
               {
                  ArrayList hdr = getHeaderByType(hval._varhdr);
                  String str = (String)hdr.get(hval._hdrline);
                  return str.substring(hval._hdrix, hval._hdrix+hval._varlen);
               }
            }
         }
         return null;
      }

      /**
      Get an int value from the headers. Returns on first one found.
      @param varname - Variable name
      @return int value from the header
      */
      public int getInteger(String varname)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               // Header value
               if (hval._vartyp == TYPE_INTEGER)
               {
                  ArrayList hdr = getHeaderByType(hval._varhdr);
                  String str = (String)hdr.get(hval._hdrline);
                  return Misc.atoi(str.substring(hval._hdrix, hval._hdrix+hval._varlen));
               }
               // Table value
               if (hval._vartyp == VAL_INTEGER)
               {
                  return _intValue[hval._hdrline];
               }
            }
         }
         return 0;
      }

      /**
      Get a real value from the headers. Returns on first one found.
      @param varname - Variable name
      @return real value from the header
      */
      public double getReal(String varname)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               switch (hval._vartyp)
               {
               case TYPE_REAL1:
               case TYPE_REAL2:
               case TYPE_REAL3:
               case TYPE_REAL4:
                  // Header value
                  ArrayList hdr = getHeaderByType(hval._varhdr);
                  String str = (String)hdr.get(hval._hdrline);
                  return Misc.atod(str.substring(hval._hdrix, hval._hdrix+hval._varlen));
               case VAL_REAL:
                  // Table value
                  return _realValue[hval._hdrline];
               }
            }
         }
         return 0;
      }

      /**
      Set an integer value into the header. Does every one that matches.
      @param varname - Variable name
      @param val - Integer value
      */
      public void setInteger(String varname, int val)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               // Header value
               if (hval._vartyp == TYPE_INTEGER)
               {
                  ArrayList hdr = getHeaderByType(hval._varhdr);
                  String str = (String)hdr.get(hval._hdrline);
                  String wstr = ""+val;
                  while (wstr.length() < hval._varlen)
                  {
                     wstr = " " + wstr;
                  }
                  // Insert to header line
                  str = str.substring(0, hval._hdrix) + wstr + str.substring(hval._hdrix+hval._varlen);
                  hdr.set(hval._hdrline, str);
               }
               // Table value
               if (hval._vartyp == VAL_INTEGER)
               {
                  _intValue[hval._hdrline] = val;
               }
            }
         }
      }  //  public void setInteger(String varname, int val)

      /**
      Set a real value into the header. Does every one that matches.
      @param varname - Variable name
      @param val - Real value
      */
      public void setReal(String varname, double val)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               // Table value
               if (hval._vartyp == VAL_REAL)
               {
                  _realValue[hval._hdrline] = val;
                  continue;
               }
               // Header value
               int rlen = 3;
               if (hval._vartyp == TYPE_REAL1)
               {
                  rlen = 1;
               }
               if (hval._vartyp == TYPE_REAL2)
               {
                  rlen = 2;
               }
               if (hval._vartyp == TYPE_REAL3)
               {
                  rlen = 3;
               }
               if (hval._vartyp == TYPE_REAL4)
               {
                  rlen = 4;
               }
               ArrayList hdr = getHeaderByType(hval._varhdr);
               String str = (String)hdr.get(hval._hdrline);
               DecimalFormat df = new DecimalFormat();
               df.setMinimumFractionDigits(rlen);
               df.setMaximumFractionDigits(rlen);
               df.setGroupingUsed(false);
               String wstr = df.format(val);
               while (wstr.length() < hval._varlen)
               {
                  wstr = " " + wstr;
               }
               // Insert to header line
               str = str.substring(0, hval._hdrix) + wstr + str.substring(hval._hdrix+hval._varlen);
               hdr.set(hval._hdrline, str);
            }
         }
      }  //  public void setReal(String varname, double val)

      /**
      Set a string value into the header. Does every one that matches.
      @param varname - Variable name
      @param val - String value
      */
      public void setString(String varname, String val)
      {
         int lsize = _hdrVars.size();
         for (int lix=0; lix<lsize; lix++)
         {
            headerVar hval = (headerVar)_hdrVars.get(lix);
            if (hval._varname.equals(varname))
            {
               ArrayList hdr = getHeaderByType(hval._varhdr);
               String str = (String)hdr.get(hval._hdrline);
               String wstr = val;
               while (wstr.length() < hval._varlen)
               {
                  wstr = wstr + " ";
               }
               if (wstr.length() > hval._varlen)
               {
                  wstr = wstr.substring(0, hval._varlen);
               }
               // Insert to header line
               str = str.substring(0, hval._hdrix) + wstr + str.substring(hval._hdrix+hval._varlen);
               hdr.set(hval._hdrline, str);
            }
         }
      }  //  public void setString(String varname, String val)

      /**
      Get the appropriate header by type
      @param hdrtyp - Header type index
      @return ArrayList for the matched header
      */
      public ArrayList getHeaderByType(int hdrtyp)
      {
         ArrayList hdr = null;
         switch (hdrtyp)
         {
         case HDR_TEXT:
            hdr = _textHeader;
            break;
         case HDR_INTEGER:
            hdr = _integerHeader;
            break;
         case HDR_REAL:
            hdr = _realHeader;
            break;
         case HDR_COMMENT:
            hdr = _commentHeader;
            break;
         case HDR_DATA:
            hdr = _dataHeader;
            break;
         case HDR_TRAILER:
            hdr = _trailer;
            break;
         }
         return hdr;
      }  //  public ArrayList getHeaderByType(int hdrtyp)
   }  //  private class headerVar
}   //  public class COSMOSDataHolder
