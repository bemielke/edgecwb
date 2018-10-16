/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

/**
 * This sets up a faked up edgethread so that logging can be used from test routines that do not
 * need to have a real EdgeThread. Typically create one of these with "-empty","FAKE", and then call
 * setUseConsole(true) to have logging go to std out. With setUseConsole(false) it would go to
 * log/edgemom.log or the file set with EdgeThread.setMainLogName?
 *
 *
 * @author davidketchum
 */
public final class FakeThread extends EdgeThread {

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
  }

  @Override
  public void terminate() {
    prt("Fake terminate called!");
  }

  public FakeThread(String argline, String tag) {
    super(argline, tag);
    statussb.append("No STATUS string");
    monitorsb.append("No Monitor string");
    consolesb.append("No console output");
    if(argline.contains(">")) {
      this.setPrintStream(staticout);
    }
  }
}
