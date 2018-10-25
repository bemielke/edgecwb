/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;
import gov.usgs.anss.channeldisplay.ChannelDisplay;
import gov.usgs.anss.gui.Anss;
import gov.usgs.anss.db.DBSetupGUI;
import gov.usgs.anss.metadatagui.MetaGUI;
/** This class just contains a main() that is called when the EdgeConfig.jar is started and 
 * it parses any command line arguments and starts up the appropriate GUI (Anss, EdgeConfig,
 * ChannelsDisplay, or MetaGUI).  These GUI exist as very light jar files which can be used
 * to start the GUIs, but they largely are just the manifest starting the right main() for 
 * the GUI being used.
 * 
 * All arguments are passed to the started program.
 * 
 * <pre>
 * switch       Action description
 * -cd         Run ChannelDisplay
 * -chandisp   Run ChannelDisplay
 * -cdisp      Run ChannelDisplay
 * -chan       Run ChannelDisplay
 * -anss       Run Anss.jar
 * -a          Run Anss.jar
 * -md         Run MetaGUI
 * -meta       Run MetaGUI
 * -metadata   Run MetaGUI
 * -dbsetup    Run dbsetup
 * -neicfetchconfig Run NEICFetchConfig
 * </PRE>
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class GUIDispatcher {
  public static void main(String [] args) {
    if(args.length == 0) {EdgeConfig.main(args); }
    else {
      for(String arg: args) {
        if(arg.equalsIgnoreCase("-cd") || arg.equalsIgnoreCase("-chandisp") || 
                arg.equalsIgnoreCase("-cdisp") || arg.equalsIgnoreCase("-chan")) {
          ChannelDisplay.main(args); 
          System.exit(0);
        }
        else if(arg.equalsIgnoreCase("-anss") ||arg.equalsIgnoreCase("-a")) {
          Anss.main(args); 
        }
        else if(arg.equalsIgnoreCase("-md") || arg.equalsIgnoreCase("-meta") || arg.equalsIgnoreCase("-metadata")) {
          MetaGUI.main(args); 
        }
        else if(arg.equalsIgnoreCase("-dbsetup")) {
          DBSetupGUI.main(args); 
        }
        else if(arg.equalsIgnoreCase("-neicfetchconfig")) {
          gov.usgs.anss.fetcher.NEICFetchConfig.main(args);
          System.exit(0);
        }
        else {
          EdgeConfig.main(args); 
        }
      }
    }
    System.exit(0);
  }
}
