package kmi.utilities.dave;

import java.io.*;
import java.util.*;
import kmi.tools.*;
//import kmi.seismic.*;
import kmi.altus.*;
import kmi.smarts.lib.*;

/**
 This class is an application to convert Kinemetrics EVT files into COSMOS format.
 It uses two primary classes to perform this conversion:<p>
 kmi.altus.GenericEvent reads EVT files into an object<p>
 kmi.seismic.COSMOSDataHolder holds, formats and outputs data in COSMOS format<p>
 This program takes the EVT filename on the command line, and formats it according to
 the contents of the COSMOS.CFG style file which allows for customization of data and
 header fields that can't be loaded from the EVT source data. COSMOS.CFG is created
 if non-existant by copying from a master CFG file so that a customized style file does
 not get overwritten when a new version is installed.<p>
 The program also supports limited command line switches such as some options for
 file naming.<p>
 The program is intended to operate as a command line program, so that it may be run
 from batch files or from other higher level automated processing programs.<p>
 Under normal circumstances, the program will be installed by an installer that will
 create all of the class paths and will create a native launcher program.
  Note: In order to execute the program from the command line, the location of the .exe
  filename must be manually added to the system PATH. <p>
 Copyright:    Copyright (c) 2002, Kinemetrics, Inc.
 @author DM Pumphrey
 @version 1.0
 */

public class K2COSMOS
{
	// Variables
   final int FILE_MODE1    = 1;        // File mode 1 - YYYYMMDDHHMMSS.site.chan#.chname.v0
   final int FILE_MODE2    = 2;        // File mode 2 - YYYYJJJHHMMSS.site.chan#.chname.v0
   final int FILE_MODE3    = 3;        // File mode 3 - basename.chname.v0
   final int MAX_ARRAY     = 100000;   // Maximum in RAM array size
   final int RAMSAMPLES    = 40000;    // Maximum number of in-RAM samples

    /**
    Construct and run the application as an object
	 */ 
    public K2COSMOS(String[] args)
    {
      int ix;
      String fileName = null;
      int fileMode = FILE_MODE1;

      // Process comamnd line arguments
      for (ix=0; ix<args.length; ix++)
      {
         // Switches
         if (args[ix].startsWith("-"))
         {
            // Switches
            if (args[ix].startsWith("-n"))
            {
               fileMode = Misc.atoi(args[ix].substring(2));
            }
         }
         else
         {
            // Else, filename
            fileName = args[ix];
         }
      }

      if (fileName == null)
      {
         UserMsg.log("* Filename not specified on command line");
         return;
      }

      // Check arguments
      switch (fileMode)
      {
      case FILE_MODE1:
      case FILE_MODE2:
      case FILE_MODE3:
         break;
      default:
         UserMsg.log("* File Naming mode unrecognized");
         return;
      }

      // Try to open the style file
      // Working style file is cosmos.cfg. This file will be created by copying default.cfg
      // if it does not exist so that user's preferences are not overwritten on an update.
      int attempt = 0;
      BufferedReader styleFile = null;
      while (true)
      {
         try
         {
            switch (attempt)
            {
            case 1:
               // Didn't work first time. Copy and try again.
               FileUtil.copy("default.cfg", "cosmos.cfg");
            case 0:
               // Try to open first time, or after file copy
               styleFile = new BufferedReader(new FileReader("cosmos.cfg"));
               break;
            case 2:
               // Not gonna work
               UserMsg.log("* Unable to open cosmos.cfg style file");
               break;
            }
            // Opened OK
            break;
         }
         catch (IOException x)
         {
            // Exception - file not there.
            // Try again
            attempt++;
         }
      }

      // If style file not found, give up
      if (styleFile == null)
      {
         return;
      }
      // Style file will be used by COSMOSDataHolder - Not used here
      styleFile = null;

		try
		{
			// Create a generic event object
			GenericEvent evt = new GenericEvent();
			// Reader the header in from the file
			if (evt.loadFromFile(fileName))
			{
				StringBuffer emsg = new StringBuffer();

				if (evt.readTimeSeriesFromFile(fileName, emsg))
				{
               int scnt = evt.getScanCount();
               int ccnt = evt.getUsedChannels();
               for (int cix=0; cix<ccnt; cix++)
               {
                  // Get mapped channel index
                  int mcix = evt.channelIndex(cix)-1;
                  // Create an array to store the data
  		            FileBasedTempDataArray sampleData = new FileBasedTempDataArray("samples", RAMSAMPLES, MAX_ARRAY);
                  // Scaling factor (+/- 20v full scale)
                  double factor = 8388.6080 / evt.getFullScale(mcix);
                  // Read in the samples
                  for (int six=0; six<scnt; six++)
                  {
                        sampleData.set((int)Math.round(evt.getEvtData(cix, six)*factor), six);
                  }
		            /*
		            Create the object to hold the COSMOS data.
		            */
		            kmi.utilities.dave.COSMOSDataHolder cData = new kmi.utilities.dave.COSMOSDataHolder(evt.getScanCount(), evt.getSampleRate());
                  /*
                  Read the style in.
                  */
                  if (!cData.loadStyle("cosmos.cfg"))
                  {
                     UserMsg.error("*** " + cData.error());
                  }

                  /*
                  Set values that will realistically come from the input data
                  */

                  // Determine trigger type
                  int ttype = 1;                                                    // Assume seismic trigger
                  if (evt.isFT())
                  {
                     ttype = 5;                                                     // Functional test
                  }
                  else if (evt.getStreamFlags() != 0)
                  {
                     ttype = 10;                                                    // Sensor calibration
                  }
                  else if (evt.getTriggerBitmap() == 0)
                  {
                     ttype = 4;                                                     // Manual trigger
                  }
                  cData.setHeaderInteger("TRIGGERTYPE", ttype);                     // Seismic trigger?

                  cData.setHeaderInteger("RECORDERTYPE", 108 + evt.getUnitType());  // Unit type
                  String rname = "";
                  switch (evt.getUnitType())
                  {
                  case 0:
                     rname = "K2";
                     break;
                  case 1:
                     rname = "Etna";
                     break;
                  case 2:
                     rname = "MW";
                     break;
                  case 3:
                     rname = "Makalu";
                     break;
                  case 4:
                     rname = ParamsList.PRODUCTID;
                     break;
                  }
                  cData.setHeaderString("RECORDERNAME", rname);                     // Recorder name
                  cData.setHeaderInteger("RECORDERSN", evt.getSerialNumber());      // Recorder SN
                  cData.setHeaderInteger("RECORDERCHANS", evt.getUsedChannels());   // Recorder channels
                  cData.setHeaderReal("RECORDERLSB",
                     evt.getFullScale(mcix)/8388608.0*1000000.0);                   // LSB in uV
                  cData.setHeaderReal("RECORDERFULLSCALE", evt.getFullScale(mcix)); // Full scale in volts
                  cData.setHeaderInteger("RECORDERBITS", 24);                       // Number of bits as recorded
                  cData.setHeaderInteger("EFFECTIVEBITS", 24);
                  cData.setHeaderReal("PREEVENTSECS", evt.getPreEvent());           // Pre-event
                  cData.setHeaderReal("POSTEVENTSECS", evt.getPostEvent());         // Post-event
				      Calendar st_tm = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
                  // Set current time (add UTC offset)
                  long utcStartTime = (long)(evt.getStartTime()-(evt.getGpsLocalOffset()*3600000));
				      st_tm.setTime(new Date(utcStartTime));
				      String sttime = Misc.itoa(st_tm.get(Calendar.MONTH)+1, 2)
                     + "/" + Misc.itoa(st_tm.get(Calendar.DAY_OF_MONTH), 2)
                     + "/" + Misc.itoa(st_tm.get(Calendar.YEAR), 4)
					      + " " + Misc.itoa(st_tm.get(Calendar.HOUR_OF_DAY), 2)
					      + ":" + Misc.itoa(st_tm.get(Calendar.MINUTE), 2)
					      + ":" + Misc.itoa(st_tm.get(Calendar.SECOND), 2)
					      + "." + Misc.itoa(st_tm.get(Calendar.MILLISECOND), 3)
                     + " UTC";
                  cData.setHeaderString("RECORDSTART", sttime);                     // Record start time
                  cData.setHeaderInteger("STARTYEAR", st_tm.get(Calendar.YEAR));    // Start time - Year
                  cData.setHeaderInteger("STARTJULIANDAY",
                     st_tm.get(Calendar.DAY_OF_YEAR));                              // Julian day
                  cData.setHeaderInteger("STARTMONTH", st_tm.get(Calendar.MONTH)+1);// Month
                  cData.setHeaderInteger("STARTDAYOFMONTH",
                     st_tm.get(Calendar.DAY_OF_MONTH));                             // Day of month
                  cData.setHeaderInteger("STARTHOUR",
                     st_tm.get(Calendar.HOUR_OF_DAY));                              // Hour
                  cData.setHeaderInteger("STARTMINUTE",
                     st_tm.get(Calendar.MINUTE));                                   // Minute
                  double dsec = ((double)(evt.getStartTime() % 60000))/1000.0;
                  cData.setHeaderReal("STARTSECOND", dsec);                         // Second
                  // Determine clock quality
                  int umin = (int)(evt.getStartTime() - evt.getGpsLastLock(0)) / 60000;
                  int cqual = 5 - (umin / 200);
                  int csource = 1;
                  if (evt.getClockSource() == 3)
                  {
                     csource = 5;
                  }
                  if ((cqual < 0) || (csource != 5))
                  {
                     cqual = 0;
                  }
                  cData.setHeaderInteger("TIMEQUALITY", cqual);                     // Time quality 0-5 (degrade by one each 200 minutes since lock)
                  cData.setHeaderInteger("TIMESOURCE", csource);                    // Time source (1=Recorder, 5=GPS (evt.getClockSource() == 3)
                  cData.setHeaderReal("UTCOFFSET", evt.getGpsLocalOffset());        // UTC offset (hours)
                  if (evt.getLatitude() != 0)
                  {
                     cData.setHeaderReal("STALATITUDE", evt.getLatitude());         // Latitude
                  }
                  if (evt.getLongitude() != 0)
                  {
                     cData.setHeaderReal("STALONGITUDE", evt.getLongitude());       // Longitude
                  }
                  if (evt.getElevation() != 0)
                  {
                     cData.setHeaderReal("STAELEVATION", evt.getElevation());       // Elevation
                  }
                  cData.setHeaderReal("SAMPLEINTERVAL",
                     1.0/(double)evt.getSampleRate());                              // Sample interval
                  cData.setHeaderInteger("RECORDERCHNUM", (mcix+1));                // Recorder channel number
                  // Determine sensor type
                  int itype = cData.getHeaderInteger("UNKNOWNI");
                  String stype = "";
                  switch (evt.getSensorType(mcix))
                  {
                  case 10:
                  case 11:
                  case 12:
                  case 13:
                  case 14:
                  case 15:
                  case 16:
                     itype = 4;
                     stype = "FBA-11";
                     break;
                  case 20:
                     itype = 7;
                     stype = "FBA-23";
                     break;
                  case 32:
                     itype = 20;
                     stype = "EpiSnsr";
                     break;
                  case 34:
                     itype = 1301;
                     stype = "MarkL22";
                     break;
                  case 35:
                     itype = 1300;
                     stype = "MarkL4";
                     break;
                  case 37:
                     itype = 1202;
                     stype = "CMG3T";
                     break;
                  case 38:
                     itype = 1204;
                     stype = "CMG40T";
                     break;
                  case 39:
                     itype = 200;
                     stype = "CMG5";
                     break;
                  }
                  cData.setHeaderString("SENSORTYPE", stype);                       // Sensor type string
                  cData.setHeaderInteger("SENSORTYPEI", itype);                     // Sensor type code translated from evt.getSensorType()
                  cData.setHeaderInteger("SENSORSN", evt.getSensorSN(mcix));        // Sensor serial number
                  double nf = evt.getNaturalFrequency(mcix);
                  if (nf <= 0)
                  {
                     nf = cData.getHeaderReal("UNKNOWNR");
                  }
                  cData.setHeaderReal("SENSORNATFREQ", nf);                         // Sensor natural frequancy
                  double dmp = evt.getDamping(mcix);
                  if (dmp <= 0)
                  {
                     dmp = cData.getHeaderReal("UNKNOWNR");
                  }
                  cData.setHeaderReal("SENSORDAMPING", dmp);                        // Sensor damping
                  cData.setHeaderReal("SENSORSENS", evt.getSensitivity(mcix));      // Sensor sensitivity (v/g)
                  cData.setHeaderReal("SENSORFSINV", evt.getFullScale(mcix));       // Sensor full scale output
                  cData.setHeaderReal("SENSORFSING",
                     evt.getFullScale(mcix)/evt.getSensitivity(mcix));              // Full scale in g
                  cData.setHeaderReal("SENSORGAIN", evt.getSensorGain(mcix));       // Gain from header
                  cData.setHeaderInteger("SENSORAZIMUTH",
                     evt.getSensorAzimuth(mcix));                                   // Azimuth of sensor
                  cData.setHeaderReal("SENSOROFFSETN",
                     evt.getSensorOffsetN(mcix));                                   // Sensor offset north
                  cData.setHeaderReal("SENSOROFFSETE",
                     evt.getSensorOffsetE(mcix));                                   // Sensor offset east
                  cData.setHeaderReal("SENSOROFFSETV",
                     evt.getSensorOffsetUp(mcix));                                  // Sensor offset vertical

                  // Set the data values
                  cData.setData(sampleData);

		            /*
		            Create a filename to store this under.
		            Format: YYYYMMDDHHMMSS.site.chan#.chname.v0
		            */
                  String cname = evt.getChannelID(mcix);
                  if (cname.length() == 0)
                  {
                     cname = "C"+(mcix+1);
                  }
                  String sname = evt.getStationID();
                  if (sname.length() == 0)
                  {
                     sname = "SN"+evt.getSerialNumber();
                  }
                  int stage = cData.getHeaderInteger("PROCESSINGSTAGE");
                  int lastix = -1;
                  String ofname = "";
                  switch (fileMode)
                  {
                  case FILE_MODE1:
		               ofname = cData.makeFileName((int)(utcStartTime/1000), sname, (mcix+1), cname, false);
                     break;
                  case FILE_MODE2:
		               ofname = cData.makeFileName((int)(utcStartTime/1000), sname, (mcix+1), cname, true);
                     break;
                  case FILE_MODE3:
                     lastix = fileName.lastIndexOf('.');
                     if (lastix > 0)
                     {
                        ofname = fileName.substring(0, lastix) + "." + cname + ".v" + stage;
                     }
                     break;
                  }
                  // There must be a filename by now
                  if (ofname.length() <= 0)
                  {
                     UserMsg.log("* Cannot create output filename");
                     break;
                  }

                  // Create the output file
                  BufferedWriter ofile = new BufferedWriter(new FileWriter(ofname));

                  cData.write(ofile);

                  ofile.flush();
		            ofile.close();

                  // Clean up working arrays
                  sampleData.cleanup();
                  cData.cleanup();
               }
				}
				else
				{
					UserMsg.log("* "+emsg.toString());
				}
			}
         // Clean up EVT file working arrays
         evt.cleanupBeforeExit();
		}
		catch(IOException e)
		{
			UserMsg.log("* I/O error on reading EVT file");
		}
    }

    /**
    Main method
	 */
    public static void main(String[] args)
    {
      if (args.length == 0)
      {
         UserMsg.log("EVT to COSMOS converter p/n 302437 v1.0");
         UserMsg.log("Usage: k2cosmos filename -switches");
         UserMsg.log("Switches:");
         UserMsg.log("-n1 - Names files as yyyymmddhhmmss.station.chnum.chname.v0 (default)");
         UserMsg.log("-n2 - Names files as yyyyjjjhhmmss.station.chnum.chname.v0 (julian day)");
         UserMsg.log("-n3 - Names files as rootname.chname.v0");
         return;
      }
      new K2COSMOS(args);
    }
}