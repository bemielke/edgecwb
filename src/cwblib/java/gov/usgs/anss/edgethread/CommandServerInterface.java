/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

/** This is the interface standard for a class which uses the CommandServer class to implement the 
 * server and this definition to implement the commands.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public interface CommandServerInterface {
  public static final String helpBase = "Usage : exit, quit - terminate connection to CD11CommandServer\nhelp or ? - print this message\n";
  /**
   * 
   * @param command Take and command line and return StringBuilder with the results.
   * @return  the results
   */
  abstract StringBuilder doCommand(String command) ;
  /** A string to help the use.  EXIT, QUIT, HELP, and ? are implemented in the CommandServer so please mention them.
   * @return 
   */
  abstract String getHelp();
}
