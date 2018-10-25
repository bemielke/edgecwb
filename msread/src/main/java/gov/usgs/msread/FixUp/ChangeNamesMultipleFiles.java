/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.msread.FixUp;
import gov.usgs.anss.edge.EdgeProperties;
import java.io.File;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.*;
/**
 *
 * @author davidketchum
 */
public class ChangeNamesMultipleFiles {
  private static int [] NDAYS_PATH;
  private static int NDAYS;
  static int DEFAULT_LENGTH=20000;        // in blocks
  static int DEFAULT_EXTEND_BLOCKS=4000;  // in blocks
  public static int MAX_MASTER_BLOCKS=250;
  private static int NPATHS;
  private static final int fileRecordCount=0;
  private static String [] DATA_PATH;
  private static final boolean dbg=true;
  public static void main(String [] args) {
    if(NDAYS <= 0) {
    //filesLock = new Integer(0);     // This is more stable to lock on than files.
    EdgeProperties.init();          // read the properties in (if not already in!)
    DEFAULT_LENGTH = Integer.parseInt(Util.getProperty("daysize").trim());
    DEFAULT_EXTEND_BLOCKS = Integer.parseInt(Util.getProperty("extendsize").trim());

    // The number of data paths
    NPATHS = Integer.parseInt(Util.getProperty("ndatapath").trim());
    if(dbg) Util.prt("Ndatapaths="+Util.getProperty("ndatapath"));

    // Now read in the number of days on each path and the paths, total #of days in NDAYS
    NDAYS = Integer.parseInt(Util.getProperty("nday").trim()); // First # of days (maybe only)
    NDAYS_PATH = new int[NPATHS];
    NDAYS_PATH[0]=NDAYS;
    DATA_PATH = new String[NPATHS];
    DATA_PATH[0] = Util.getProperty("datapath").trim();
    if(NPATHS > 0)
      for(int i=1; i<NPATHS; i++) {
        NDAYS_PATH[i] = Integer.parseInt(Util.getProperty("nday"+i).trim());
        NDAYS += NDAYS_PATH[i];
        DATA_PATH[i] = Util.getProperty("datapath"+i);
      }
    }
    Util.prt("NPATHS="+NPATHS);
    for(int i=0; i<NPATHS; i++) Util.prt(i+" "+DATA_PATH[i]);
    int year = 2009;
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-yr")) year = Integer.parseInt(args[i+1]);
    }
    // We are looking for files from the same data name yyyy_ddd_4.ms, change the name of it and yyyy_ddd_4.idx to add #1, #2 etc
        // look through the paths for this file or an empty.
    for(int idoy=1; idoy<366; idoy++) {
      String doy = ""+idoy;
      if(doy.length() < 3) doy ="0"+doy;
      if(doy.length() < 3) doy = "0"+doy;
      String filename = ""+year+"_"+doy+"_4";
      int nfound=0;
      Util.prt("Check "+doy+" "+filename);
      for(int i=0; i<NPATHS; i++) {
        String path = DATA_PATH[i];
        File dir = new File(path.substring(0,path.length()-1));
        //if(dbg) Util.prt("IndexFileRep: path="+path+" dir="+dir.toString()+" isDir="+dir.isDirectory());
        String [] filess = dir.list();
        if(filess == null) {
          SendEvent.debugEvent("IFRFileOpen","Failed to open files on path="+path, "IndexFileReplicator");
          Util.prta("Attempt to open files on path="+path+" dir="+dir.toString()+
              " failed.  Is VDL denied access to path? Too many Files open?");
        }
        else {
          for (String files : filess) {
            if (files.indexOf(".ms") > 0) {
              //if(dbg) Util.prt(filess[j] +" to "+filename);
              if (files.contains(filename+".ms")) {
                File f= new File(path+"/"+filename+".ms");
                File idx = new File(path+"/"+filename+".idx");
                File f2 = new File(path+"/"+filename+"#"+nfound+".ms");
                File idx2 = new File(path+"/"+filename+"#"+nfound+".idx");
                if(f.exists() && idx.exists() && !f2.exists() && ! idx2.exists()) {
                  if(nfound == 0) Util.prt("Skipon "+f.getAbsolutePath());
                  else {  Util.prt("rename "+f.getAbsolutePath()+" to "+f2.getAbsolutePath()+
                          " "+idx.getAbsolutePath()+" to "+idx2.getAbsolutePath());
                  f.renameTo(f2);
                  idx.renameTo(idx2);
                  }
                }
                nfound++;
              }
            }
          }
        }
      }
    }
  }
}
