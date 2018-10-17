/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

import gov.usgs.anss.util.Util;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.io.*;

/**
 * CommandServer.java - This server looks for a telnet type connection, accepts a message of the
 * form command,arg1,arg2\n and execute this command on the creator of this thread through the
 * CommandServerInterface.  It can be used to wrap the IP portion and allow its co-class to handle
 * the commands.
 *
 * @author davidketchum
 */
public final class CommandServer extends Thread {

  private int port;
  private final int portorg;
  private ServerSocket d;
  private final String tag;
  private int totmsgs;
  private final CommandServerInterface commandObject;
  private final EdgeThread par;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public int getNumberOfMessages() {
    return totmsgs;
  }

  public int getPort() {
    return port;
  }

  private void prta(StringBuilder sb) {
    if (par == null) {
      Util.prta(sb);
    } else {
      par.prta(sb);
    }
  }

  /**
   * Creates a new instance of a CommandServer
   *
   * @param tg An identifying tag of the creator
   * @param porti The starting port that will be used for this service .
   * @param parent A parent EdgeThread for logging
   * @param obj An object implementing the CommandServerInterface to call to execute commands and
   * get help
   */
  public CommandServer(String tg, int porti, EdgeThread parent, CommandServerInterface obj) {
    port = porti;
    portorg = porti;
    par = parent;
    tag = tg;
    commandObject = obj;
    setDaemon(true);
    if (Util.isShuttingDown) {
      return;   // do not start
    }
    start();
  }

  @Override
  public String toString() {
    return "Cmd: " + tag + " p=" + port + " d=" + d + " totmsgs=" + totmsgs;
  }

  @Override
  public void run() {

    // Open up a port to listen for new connections.
    int loop = 0;
    while (loop++ >= 0) {
      try {
        if (d != null) {
          if (!d.isClosed()) {
            try {
              d.close();
              sleep(1000);
            } catch (IOException | InterruptedException e) {
            }
          }
        }
        prta(Util.clear(tmpsb).append(tag).append(" Cmd: ").append(port).append(" Open Port=").append(port));
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().contains("Address already in use")) {
          prta(Util.clear(tmpsb).append(tag).append(" Cmd: ").append(port).append(" Address in use - go to next "));
          port++;
        } else {
          prta(Util.clear(tmpsb).append(tag).append(" Cmd: ").append(port).
                  append(" Error opening TCP listen port =").append(port).append("-").
                  append(e.getMessage()));
        }
        if (loop == 1) {
          try {
            sleep(60000);
          } catch (InterruptedException e2) {
          }  // allow ports to expire
        }
        try {
          sleep(2000);
        } catch (InterruptedException e2) {
        }
      } catch (IOException e) {
        prta(Util.clear(tmpsb).append(tag).append(" Cmd: ").append(port).append("Error opening socket server=").append(e.getMessage()));
        try {
          sleep(2000);
        } catch (InterruptedException e2) {
        }
      }
    }

    while (!Util.isShuttingDown) {
      try {
        prta(Util.clear(tmpsb).append(tag).append(" Cmd: ").append(port).append(" at accept"));
        Socket s = d.accept();
        prta(Util.clear(tmpsb).append(tag).append(" Cmd: ").append(port).append(" from ").append(s));
        try {
          OutputStream out = s.getOutputStream();
          out.write((commandObject.getHelp()).getBytes());

          BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
          String line;
          while ((line = in.readLine()) != null) {
            prta(Util.clear(tmpsb).append(tag).append(" Cmd: execute :").append(line).append("|"));
            // The line should be yyyy,doy,node\n
            String[] parts = line.split("[,\\s]");

            //for(int i=0; i<parts.length; i++) prta(Util.clear(tmpsb).append(tag).append("CMD: ").append(i).append(" ").append(parts[i]));
            if (parts.length == 1) {
              if (parts[0].equalsIgnoreCase("HELP") || parts[0].equals("?")) {
                out.write(commandObject.getHelp().getBytes());
              } else if (parts[0].equalsIgnoreCase("EXIT") || parts[0].equalsIgnoreCase("QUIT")) {
                out.write("Exiting\n".getBytes());
                break;
              } else {
                StringBuilder ans = commandObject.doCommand(line);
                for (int i = 0; i < ans.length(); i++) {
                  out.write((byte) ans.charAt(i));
                }
              }
            } else {
              StringBuilder ans = commandObject.doCommand(line);
              for (int i = 0; i < ans.length(); i++) {
                out.write((byte) ans.charAt(i));
              }
            }
          }
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e, "Cmd:" + port + " IOError on socket");
        } catch (RuntimeException e) {
          prta(Util.clear(tmpsb).append(tag).append(" Cmd:").append(port).
                  append(" RuntimeException in EBC Cmd e=").append(e).append(" ").
                  append(e == null ? "" : e.getMessage()));
          if (e != null) {
            e.printStackTrace(par.getPrintStream());
          }
        }
        prta(Util.clear(tmpsb).append(tag).append(" Cmd:").append(port).
                append(" Cmd: has exit on s=").append(s));
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException e) {
              prta(Util.clear(tmpsb).append(tag).append(" Cmd:").append(port).
                      append(" IOError closing socket"));
            }
          }
        }
      } catch (IOException e) {
        if (e.getMessage().contains("operation interrupt")) {
          prta(Util.clear(tmpsb).append(tag).append(" Cmd:").append(port).
                  append(" interrupted.  continue "));
        }
      }
    }       // end of infinite loop (while(true))
    prta(Util.clear(tmpsb).append(tag).append("is exiting!"));
  }
}
