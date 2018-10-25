/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.test;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.cwbqueryclient.ZeroFilledSpan;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.seed.*;
/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class GeomagTest1 {
  public static void main(String [] args) {
    ArrayList<MiniSeed> cblks = new ArrayList<MiniSeed>(10);
    ArrayList<MiniSeed> blks = new ArrayList<MiniSeed>(10);
    for(int i=0; i<args.length; i++) {
      try {
        blks.clear();
        RandomAccessFile rw = new RandomAccessFile(args[i],"r");
        byte [] b = new byte[(int) rw.length()];
        rw.seek(0L);
        rw.read(b, 0, (int) rw.length());
        rw.close();
        for(int off=0; off<b.length; off=off+512) {
          blks.add(new MiniSeed(b, off, 512));
        }
        Collections.sort(blks);
        Util.prt(blks.size()+" blocks found in "+args[i]);
        for(int j=0; j<blks.size(); j++) Util.prt(j+" "+blks.get(j).toString());
        String lastSeed=blks.get(0).getSeedNameString();
        for(int iblk=0; iblk<blks.size(); iblk++) {
          if(blks.get(iblk).getSeedNameString().equals(lastSeed) && iblk != blks.size()-1) cblks.add(blks.get(iblk));
          else {
            if(iblk == blks.size()-1) cblks.add(blks.get(iblk));
            Util.prt(cblks.size()+" blocks found for channel "+lastSeed);
            for(int j=0; j<cblks.size(); j++) Util.prt(j+" "+cblks.get(j).toString());
            ZeroFilledSpan span = new ZeroFilledSpan(cblks, cblks.get(0).getGregorianCalendar(), 86400., 99999000);
            Util.prt("ZF: "+span);
            for(int j=0; j<11; j++) Util.prt("ZFS["+j+"] "+span.getData()[j]);
            lastSeed=blks.get(iblk).getSeedNameString();
            cblks.clear();
            cblks.add(blks.get(iblk));    // Add the block from the new channel
          }
        }
      }
      catch(IllegalSeednameException e) {
        Util.prt("Bad seedname="+e);
      }
      catch(IOException e) {
        Util.prt("IOError="+e);
        e.printStackTrace();
      }
    }
    Util.prt("End of execution");
  }
}
