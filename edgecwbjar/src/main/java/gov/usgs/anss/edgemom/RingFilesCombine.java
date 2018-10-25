/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;
import gov.usgs.anss.edgeoutput.RingFileInputer;
import gov.usgs.anss.edgeoutput.RingFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
/** This class combines two or more RingFiles into a combined ring files.
 * It is useful to make a single output ring file from many instances on a node
 * so only one RRPClient can be run to send the data to some foreign source.  Otherwise
 * a RRPClient would have to be run for every one.
 * 
 * Note all command line switch are passed to the creator of the RingFile so additional
 * switch documentation for the output file is available there.
 * 
 * <PRE>
 * switch    args             Description
 * -f      outfileStem     The combined output file without any .ring
 * -in ring1;ring2;...;ringn The full path and ring file names of the rings to be combined.
 * Arguments that go to the output RingFile setup 
 * -s   nnnnn              Number of megabytes of the output combined ring file
 * -u   updateMS           Number of milliseconds between updates to state of ring (Def=500)
 * -p   path               Path to put the ring file on, default = "/data2/"
 * -allowrestricted        This switch does not work when combining!!!!!
 * </PRE>
 * 
 * 
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class RingFilesCombine extends EdgeThread  {
  private ArrayList<RingFileInputer> inputs = new ArrayList<>(10);
  private ArrayList<RawDisk> states = new ArrayList<>(10);
  private final RingFile out;
  private int [] lastAcks = new int[10];
  private String [] inputFiles;
  private byte [] buf = new byte[512];
  private ByteBuffer bb;
  private StringBuilder runsb = new StringBuilder(100);
  
  private long nblks;
  public RingFilesCombine(String argline, String tag) throws IOException, FileNotFoundException {
    super(argline,tag);
    String [] args = argline.split("\\s");
    prta("argline="+argline);
    bb = ByteBuffer.wrap(buf);
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-f")) {
        outfile = args[i+1];
        i++;
      }
      else if(args[i].equals("-in")) {
        prta("Input files " + args[i+1]);
        inputFiles = args[i+1].split("[:;]");
        for(String filename: inputFiles) {
          int lastAck = -1;
          RawDisk state;
          try {
            state = new RawDisk(filename+".combine", "r");
            state.readBlock(buf, 0, 4);
            bb.position(0);
            state.close();
            lastAck = bb.getInt();
          } catch (FileNotFoundException e) {
            lastAck = -1;        // It is unknown
          }
          catch(IOException e) {
            e.printStackTrace(getPrintStream());
          }
          try {
            state = new RawDisk(filename+".combine","rw");
            states.add(state);
            bb.position(0);
            if(lastAck == -1) {
              lastAck = 1;
            }
            bb.putInt(lastAck);
            state.writeBlock(0, buf, 0, 4);
            state.setLength(4);
            RingFileInputer in = new RingFileInputer(filename, lastAck);
            prta(Util.clear(runsb).append(i).append(" InputRing: ").append(in.toString()));
            inputs.add(in);
            lastAcks[states.size() -1] = lastAck;
          }
          catch(IOException e) {
            e.printStackTrace(getPrintStream());
          }
        }
        i++;
      }
    }
    out = new RingFile(argline, (EdgeThread) this);
    Util.clear(runsb);
    out.toStringBuilder(runsb);
    prta(runsb);
    running = true;
    start();
  }
  @Override
  public void run() {
    long lastStatusUpdate = System.currentTimeMillis();
    while(!terminate) {
      long lastNblks = nblks;
      long now = System.currentTimeMillis();
      for(int i=0; i<inputs.size(); i++) {
        if(terminate) break;
        RingFileInputer in = inputs.get(i);
        int nwrite =0;
        while(in.getNextout() != in.getLastSeq() && nwrite < 100) { // data is available
          if(terminate) break;
          try {
            int len = in.getNextData(buf, true);    // No block read failed
            if(len == -1) break;
            lastAcks[i] = in.getNextout();
            out.writeNext(buf);
            nblks++;
            nwrite++;
          }
          catch(IOException e) {
            e.printStackTrace(getPrintStream());
          }
        }   //while(data available)
      }   // Loop on inputs files
      // Should lastAck be updated
      if(now - lastStatusUpdate > 30000) {
        // for each file put the lastAck in the buffer, and write it to the state file
        writeLastAcks();
        lastStatusUpdate = now;
        Util.clear(runsb).append(tag).append(" #blks=").append(nblks).
                append(" out=").append(out.getFilename()).append(" out.next=").append(out.getNextout()).append(" From:");
        for(int i=0; i<inputs.size(); i++) {
          runsb.append(" ").append(inputFiles[i]).append(" nxt=").append(inputs.get(i).getNextout());
        }
        prta(runsb);
      }
      if(terminate) break;
      if(lastNblks == nblks) {    // If nothing was written, wait a bit
        try {
          sleep(200);
        }
        catch(InterruptedException expected) {
        }
      }
      else {        // Copying fast, slow thing down a bit
        try {
          sleep(10);    // 10000 blocks per second at 10 millis per 100 blocks
        }
        catch(InterruptedException expected) {
          
        }       
      }
    }
    // Must be terminated, write out the lastAcks
    writeLastAcks();
    out.close();
    for(int i=0; i<inputs.size(); i++) {
      inputs.get(i).close();
      try {
        states.get(i).close();
      }
      catch(IOException expected) {
      }
    }
    running = false;
  }
  private void writeLastAcks() {
    for(int i=0; i<states.size(); i++) {   
      bb.position(0);
      bb.putInt(lastAcks[i]);
      try {
        states.get(i).writeBlock(0, buf, 0, 4);
      }
      catch(IOException e) {
        e.printStackTrace(getPrintStream());
      }
    }
  }
  @Override
  public void terminate() {
    // Set terminate do interrupt.  If IO might be blocking, it should be closed here.
    terminate = true;
    interrupt();
    //in.close();
  }
  @Override
  public String toString() {
    return tag + " RRPC: nblks=" + nblks + " nfiles" + inputs.size();
  }
  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly
  public static void main(String [] args) {
    EdgeProperties.init();
    String argline="";
    for(String arg: args) {
      argline = arg +" ";
    }
    argline = argline.trim();
    if(argline.equals("")) {
      argline = "-in /data2/PTWC8#0.ring:/data2/PTWC8#1.ring -f PTWCCOMB -s 4000 >> rfc";
    }
    try {
      RingFilesCombine combine = new RingFilesCombine(argline, "COMB");
      while( combine.isAlive()) {
        Util.sleep(1000);
      }
    }
    catch(IOException e) {
      e.printStackTrace();
    }
    Util.prta("End of execution");
  }
}
