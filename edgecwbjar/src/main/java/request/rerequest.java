/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package request;
/**
 *
 */

/**
 * @author fshelly
 *
 */

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class rerequest
{

	public static void main(String[] args)
	{

		if (args.length == 8 || args.length == 9)
		{
			// Correct number of arguments, see if they parse
			if (args[0].compareTo("get") != 0)
			{
				ShowHelp();
				System.exit(1);
			}
			String hostname = args[1];
			int port = Integer.parseInt(args[2]);
			String id = args[3];
			String date = args[4];
			String time = args[5];
			int duration = Integer.parseInt(args[6]);

			String filename = args[7];
      int throttle=6000;
      if(args.length >= 9) throttle = Integer.parseInt(args[8]);
			OutputStream outfile=null;

			System.out.println(id + ": append to " + filename);
			System.out.flush();
			try
			{
				outfile = new FileOutputStream(filename, true);
			} catch (FileNotFoundException e)
			{
				System.err.println("Failed to open file " + filename);
				e.printStackTrace();
				System.exit(1);
			}
      long start=System.currentTimeMillis();
			// Get data for this channel and time period
			SeedSocket getSeedThread = new SeedSocket(hostname, port, id, date,
					time, duration, outfile, throttle);
			getSeedThread.start();

			while (!getSeedThread.Done() && getSeedThread.isAlive())
			{
				try
				{
					Thread.sleep(1000);
				} catch (InterruptedException e)
				{
					System.err.println("Sleep failed waiting for SeedThread");
					e.printStackTrace();
				}
			} // loop until data collection thread says it is done

				System.out.println("Collected " + getSeedThread.GetRecordCount()
						+ " records "+(System.currentTimeMillis() - start)/1000.+" s");
				System.out.flush();

			try
			{
				outfile.close();
			} catch (IOException e)
			{
				System.out.println("Error closing seed data file.");
				e.printStackTrace();
			}
			if (getSeedThread.GetRecordCount() < 1)
			{
				// No data so delete output file
				System.out.println("Warning, no data added to file " + filename);
			}
		} // possible data request command
		else // argument count is wrong, show help
		{
			ShowHelp();
		}

	} // main()

	private static void ShowHelp()
	{
		System.out.print(
				"USAGE: ASPseed get <host> <port> <station>.[<location>-]<channel>\n" +
				"               <start yyyy/mm/dd> <start hh:mm:ss>\n" +
				"               <seconds to save> <savefile>\n" +
				"	<host>          Host name or IP address\n" +
				"	<port>          4003 or other port number\n" +
				"	<station>       Station name\n" +
				"	<location>      Location code, missing means blank location\n" +
				"	<channel>       3 Character channel name\n" +
				"	<yyyy/mm/dd>    Year/ month/day of month\n" +
				"   <hh:mm:ss>      Hour:Minute:Second start/end time\n" +
				" <savefile>      Append all data to this file\n" +
        " throttle        Throttle the link to this number bps\n"+
				"\n" +
				"Downloads seed records matching given pattern to <savefile>\n" +
				"\n" +
        "  Example:\n" +
        "     rerequest get bbsr 4003 BBSR.00-BHZ 2010/01/12 14:00:00 1200 bhz.seed\n"
			);

	} // ShowHelp()

} // class rerequest

