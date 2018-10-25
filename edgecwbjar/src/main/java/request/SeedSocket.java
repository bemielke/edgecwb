/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package request;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;

/**
 * This class creates a background thread which connects to a server and
 * downloads a seed data, appending it to the supplied file.  There are also
 * calls which allow the calling program to keep track of the status of the
 * download.
 * @author fshelly
 *
 */

public class SeedSocket extends Thread
{
	// Maximum number of records that we allow to be transfered at one time
	public static final int MAX_RECORDS = 20000;

	private Socket socket = null;
	private PrintWriter out = null;
	private final String hostname;
	private final int port;
	private final String id;
	private final String date;
	private final String time;
	private final int duration;
	private final OutputStream outfile;
	private boolean bDone=false;
	private int recordCount=0;
	private boolean bCancel=false;

  private int throttle;    // DCK probably should add this to the constructor

	public SeedSocket(String hostname, int port, String id, String date,
			String time, int duration, OutputStream outfile, int throt)
	{
		// Run Thread constructor
		super();
    this.throttle = throt;
    if(throttle <= 0) throttle = 6000;
		this.hostname = hostname;
		this.port = port;
		this.id = id;
		this.date = date;
		this.time = time;
		this.duration = duration;
		this.outfile = outfile;
		this.bCancel = false;
	} // SeedSocket() constructor

	public boolean Done()
	{
		return bDone;
	}

	public int GetRecordCount()
	{
		return recordCount;
	}

  @Override
	public void run()
	{
		byte seedrecord[] = new byte[512];
		char charfield[] = new char[5];
		String field;
		int  byteCount;
		int  readCount;
    long lastThrottleCheck = System.currentTimeMillis();    // DCK time of last throttle
		try
		{
			//socket = new Socket(hostname, port);
      /*DCK open socket with small receive buffer and set nolinger */
          socket = new Socket();
          socket.setReceiveBufferSize(4096);
          InetSocketAddress adr = new InetSocketAddress(hostname,port);
          socket.setSoLinger(false, 0);
          socket.connect(adr);
          //if(dbg)

			out = new PrintWriter(socket.getOutputStream(), true);
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + hostname + ":" + port);
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to: " + hostname + ":" + port+" e="+e);
			System.exit(1);
		} // End try/catch block

		// Send DATREQ to server
		String datreq = "DATREQ " + id + " " + date + " " + time + " "
		+ duration + '\u0000';
		out.write(datreq);
		out.flush();

		// Get return data
		recordCount = 0;
		byteCount = 0;
		try
		{ int len=1;
      while(len > 0) {
        len = this.readFully(socket, seedrecord, 0, 512);
			/*while ((readCount = socket.getInputStream().read(seedrecord, byteCount, 512-byteCount)) > 0)
			{
				if (bCancel)
				{
					socket.close();
					bDone = true;
					return;
				}
				byteCount += readCount;
				if (byteCount == 512)
				{*/
					// See if this is a trailer record
					for (int i=13; i < 18; i++)
					{
						charfield[i-13] = (char) seedrecord[i];
					}
					field = String.copyValueOf(charfield);
					if (field.toString().compareTo("RQLOG") == 0)
					{
						// We see the terminating record, so terminate
						break;
					}

				/*	// We have a full record so save it
				}
       * */
					byteCount = 0;
					recordCount++;
					outfile.write(seedrecord);

			} // while we are successfully reading data
			outfile.flush();
		} catch (IOException e1)
		{
			System.err.println("Unexpected IOException reading data from input stream, ABORT!");
			e1.printStackTrace();
			System.exit(1);
		}
		try
		{
			socket.close();
		} catch (IOException e)
		{
			; // Don't care if I get an exception on a close
		}
		socket = null;

		bDone = true;
	} // run()
  boolean inAvail;
  boolean inReader;
  boolean inThrottle;
  long totalBytes;
  private void prt(String s) {System.out.println(s);}
  private void prta(String s) {System.out.println(s);}
  private int readFully(Socket s, byte [] buf, int off, int len) throws IOException {
    int nchar=0;
    int l=off;
    try {
      InputStream in = s.getInputStream();
      inReader=true;
      while(len > 0) {            //
        int navailLoop=0;
        inAvail=true;
        /*while(in.available() <=0 && navailLoop++ <500) {
          Util.sleep(10);
          if(s.isInputShutdown() || s.isClosed()) {
            Util.prta("readFully s.shutdown="+s.isInputShutdown()+" closed="+s.isClosed());
            break;
          }
        }*/
        inAvail=false;
        nchar= in.read(buf, l, Math.min(len,512));// get nchar
        if(nchar <= 0) {
          prta(len+" read nchar="+nchar+" len="+len+" in.avail="+in.available());
          inReader=false;
          return 0;
        }     // EOF - close up
        l += nchar;               // update the offset
        totalBytes += nchar;
        len -= nchar;             // reduce the number left to read
        inThrottle=true;
        doDynamicThrottle(nchar);
        inThrottle=false;
      }
      inReader=false;
      return l;
    }
    catch(IOException e) {      // We expect sockets to be closed by timeout.
      if(s.isClosed()) {
        prta("ReadFully leaving due to timeout detected.  Socket is closed");
      }
      prt("IOE found len="+len+" nchar="+nchar+" l="+l);
      for(int i=0; i<l; i++) prt("i="+i+" buf="+buf[i]);
      inReader=false;
      throw e;
    }

  }
  long lastThrottleCheck;
  private void doDynamicThrottle(int nb) {
      // Throttle the input
    long minms =  nb*8000/throttle - (System.currentTimeMillis() - lastThrottleCheck);
    if(minms > 0)
      try{
        sleep(minms);
      } catch(InterruptedException e) {} // Wait for minimum time to go by
    lastThrottleCheck = System.currentTimeMillis();
  }
	public void SetCancel(boolean bCancel)
	{
		this.bCancel = bCancel;
	}

} // class SeedSocket