/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.msread;
import java.net.Socket;
import java.io.IOException;
/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class Poke {
  public static void main(String [] args) {
    System.out.println("Use ^] to exit");
    if( args.length == 0) {
      args = new String[2];
      args[0] = "136.177.24.147";
      args[1] = "80";
    }
    else  {
      for(int i=0; i<args.length; i++) {
        System.out.println(i+" = "+args[i]);
      }
    }
    try {
      Socket sock = new Socket(args[0], Integer.parseInt(args[1]));
      int in = 0;
      if(args.length >= 3) {
        //args[2] = args[2].replaceAll("\\n","\n");
        System.out.println(args[2]);
        sock.getOutputStream().write(args[2].getBytes());
        sock.getOutputStream().write("\n".getBytes());
      }
      sock.getInputStream();
      for(;;) {
        while( sock.getInputStream().available() > 0) {
          in = sock.getInputStream().read();
          System.out.print((char) in);
        }
        while ( System.in.available() > 0) {
          in = System.in.read();
          if( in == 29) break;
          if( in > 0) {
            sock.getOutputStream().write(in);
          }
        }
        if( in == 29) break;
      }
      System.out.println("EOF found");
    }
    catch(IOException e) {
      if(e.toString().contains("timed out")) {
        System.out.println("Connection timed out");
      }
      else if(e.toString().contains("refused")) {
        System.out.println("Connection refused");
        
      }
      e.printStackTrace();
    }
    
  }
}
