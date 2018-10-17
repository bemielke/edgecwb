/*
 * Copyright 2010, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package gov.usgs.anss.net;
/*
 * TCPQ330.java
 *
 * Created on July 18, 2005, 3:31 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
import java.net.*;
import java.io.*;
import gov.usgs.anss.util.*;

/**
 *
 * @author davidketchum
 */
public class TCPQ330 extends Thread{
  String tag;
  String ipadr;
  int delay;
  boolean terminate;
  /** Creates a new instance of TCPQ330 */
  public TCPQ330(String tg, String ip, int del) {
    tag=tg;
    ipadr=ip;
    delay=del;
    terminate=false;
    start();
  }
  public void  run() {
    Socket s=null;
    while(!terminate) {
      try {
        Util.prta("Open socket to "+ipadr+"/80");
        s = new Socket(ipadr,80);
      }
      catch(UnknownHostException e) {
        Util.prt("could not open socket to "+ipadr+"/80");
        Util.exit(0);
      }
      catch(IOException e) {
        Util.prt("IOException while opening socket to "+ipadr+"/80");
      }
      try {sleep(delay*1000L);} catch(InterruptedException e) {}
      try{
        Util.prta("closing socket");
        s.close();
      }
      catch(IOException e) {
        Util.prt("IOException closing socket");
        
      }
      try {sleep(2000L);} catch(InterruptedException e) {}
    }
  }
}
