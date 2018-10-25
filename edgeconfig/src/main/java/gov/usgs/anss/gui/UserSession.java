/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * This class handle I/O for a user session to an RTS using the ConsoleServer system. It has two
 * modes : 1) It is used from its own Main as a command in a shell with a command like 'java -cp
 * ~/ANSS/Anss.jar gov.usgs.anss.gui.UserSession -h nnn.nnn.nnn.nn -p nnnn [Station]' 2) As the
 * communications handler for the link to ConsoleServer for a UserSessionPanel in one of the GUIs.
 * The basic differences is whether input comes from System.in. or is handled by the Panel, and
 * whether characters are displayed using System.out.print?? or is sent via call backs to the
 * UserSessionPanel. If the Panel is not used, a simple KeyHandler gets input from System.in.
 *
 * There is support for a simple menu of commands handled by this interface (basically send a file,
 * or exit). There is another menu available from the Server which is used to upload, download,
 * restart, etc.
 *
 * @author davidketchum
 */
public final class UserSession extends Thread {

  private final String host;
  private final int port;
  private String name;
  private Socket s;
  private boolean terminate;
  private KeyHandler key;
  private final byte[] last20 = new byte[20];
  private int last20p = 0;
  private final UserSessionPanel parent;
  private boolean onetime;

  /**
   * Creates a new instance of UserSession
   *
   * @param h The host for the user session
   * @param pt the port for this session
   * @param nm The name bay which this session is known
   * @param noreconnect if true, this link will not try to reconnect automatically
   * @param par If this is being used from a UserSession panel, set this to the panel.
   */
  public UserSession(String h, int pt, String nm, boolean noreconnect, UserSessionPanel par) {
    host = h;
    port = pt;
    parent = par;
    onetime = noreconnect;
    name = nm;
    if (parent == null) {
      key = new KeyHandler();
    }
    start();
  }

  public void close() {
    terminate = true;
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
  }

  public String getLast20() {
    String p = "";
    for (int i = 0; i < 20; i++) {
      p += (char) last20[(last20p + i) % 20];
    }
    return p;
  }

  @Override
  public void run() {
    while (!terminate) {
      // This loop establishes a socket
      while (!terminate) {
        try {
          s = new Socket(host, port);
          println(Util.asctime() + " " + name + " CC: Created new socket to SocketAgent at " + host + "/" + port + " terminate=" + terminate);
          if (terminate) {
            break;
          }
          break;
        } catch (UnknownHostException e) {
          println(name + " CC: Unknown host for socket=" + host + "/" + port);
          try {
            sleep(100000);
          } catch (InterruptedException e2) {
          }
        } catch (IOException e) {
          if (e.getMessage().contains("Connection refused")) {
            println(Util.asctime() + " " + name + " CC: ** Connection refused to " + host + "/" + port + " try again in 10");
          } else {
            println(name + " CC: *** IOError opening client " + host + "/" + port + " try again in 10 " + e);
          }
        }
        try {
          sleep(10000);
        } catch (InterruptedException e2) {
        }
      }
      try {
        s.setTcpNoDelay(true);
        if (port == 7900 || port == 7219 || port == 2009) {
          println(Util.asctime() + " " + name + " Send username=" + System.getProperty("user.name") + " to server");
          s.getOutputStream().write((System.getProperty("user.name") + "\n" + name + "\n").getBytes());
        }
        long loop = 0;
        InputStream in = s.getInputStream();
        while (!terminate) {
          if (s.isClosed()) {
            break;
          }
          int chr = Util.socketRead(in);
          if (chr == -1) {
            if (getLast20().contains("Good bye.")) {
              println("\n" + Util.asctime() + " " + name + " Connection broken into!\n");
              onetime = true;     // We need to leave
            } else if (!onetime) {
              println("\n" + Util.asctime() + " " + name + " Connection to remote server lost.  Reconnect." + getLast20());
            }
            break;
          }
          putChar(chr);
          //println("char rcv="+chr);
          last20[last20p++] = (byte) chr;
          last20p = last20p % 20;
          if ((loop++ % 20) == 0) {
            try {
              sleep(1);
            } catch (InterruptedException e) {
            }// DCK: DEBUG - run() gets in infinite loop is it here?
          }
        }
      } catch (IOException e) {
        if (e.toString().contains("Socket closed") || e.toString().contains("socket closed")
                || e.toString().contains("Connection reset") || e.toString().contains("Socket is closed")) {
          println(Util.asctime() + " " + name + " Session Ended - Socket closed.");
        } else {
          println(Util.asctime() + " " + name + " IO reading from socket or writing to System.out" + e);
          e.printStackTrace();
        }
      }
      if (onetime) {
        if (parent != null) {
          parent.doText();
        }
        break;
      }
    }
    println("USH: handler is exiting.  terminate=" + terminate);
  }

  private void println(String s) {
    if (parent != null) {
      parent.newString(s + "\n");
    } else {
      System.out.println(s);
    }
  }

  private void putChar(int chr) {
    //Util.prt("Putchar="+chr+" "+(char) chr);
    if (parent != null) {
      if (onetime) {
        parent.newCharNoUpdate(chr);
      } else {
        parent.newChar(chr);
      }
    } else {
      System.out.write(chr);
      System.out.flush();
    }
  }
  boolean waitForInputChar = false;

  public void sendChar(byte chr) throws IOException {
    if (waitForInputChar) {
      //println("Got command char="+chr);
      if (chr == '1') {
        println("Disconnection");
        name = "NONE";
        try {
          s.close();
        } catch (IOException e) {
        }
        return;
      }
      if (chr == '2') {
        terminate = true;
        try {
          s.close();
        } catch (IOException e) {
        }
        System.exit(1);
      }
      if (chr == '8') {
        sendTextFile();
      }
      waitForInputChar = false;
      if (chr == 16) {
        s.getOutputStream().write(chr);
        s.getOutputStream().flush();    // send send ctrl P
      }
      return;
    }
    if (chr == 10) {
      chr = 13;
    }
    if (chr == 16 && !waitForInputChar) {
      waitForInputChar = true;
      usage();
      return;
    }
    //println("char rcv="+chr);
    if (s != null) {
      s.getOutputStream().write(chr);
      s.getOutputStream().flush();
    }
  }

  String lastTextFile = "qsub680.c";

  private boolean sendTextFile() throws IOException {
    byte[] b = new byte[10];
    System.out.print("Enter filename to send : (def=" + lastTextFile + ") : ");
    String file = "";
    for (;;) {
      int chr = System.in.read();
      //println("File chr="+chr);
      if (chr != 10 && chr != 13) {
        if (chr == 0177) {
          file = file.substring(0, file.length() - 1);
          System.out.print((char) 8);
          System.out.print((char) ' ');
          System.out.print((char) 8);
        } else {
          file += (char) chr;
          System.out.print((char) chr);
        }
      } else {
        break;
      }
    }
    println("File =" + file);
    if (file.equals("")) {
      file = lastTextFile;
    }
    lastTextFile = file;
    String line;
    try {
      BufferedReader in = new BufferedReader(new FileReader(file));
      String shortfile = file;
      if (shortfile.lastIndexOf("/") >= 0) {
        shortfile = shortfile.substring(shortfile.lastIndexOf("/") + 1);
      }
      line = "del " + shortfile + "\rxmode /term noecho\rcopy /term " + shortfile + "\r";
      long start = System.currentTimeMillis();
      println(Util.asctime() + " Starting transfer....");
      s.getOutputStream().write(line.getBytes(), 0, line.length());
      int nchar = 0;
      try {
        sleep(1000);
      } catch (InterruptedException e) {
      }
      while ((line = in.readLine()) != null) {
        s.getOutputStream().write((line + "\r").getBytes(), 0, line.length() + 1);
        nchar += line.length() + 1;
        //try {sleep(line.length()*3);} catch(InterruptedException e) {}  // Limit to 9600 baud!
        /*int nchar = s.getInputStream().read(b);
          if(b[0] == '\n') b[0] = '\r';
          if(nchar > 0) conn.write(this, b, 0, nchar);*/
        if (getLast20().contains("read error") || getLast20().contains("000:244")
                || getLast20().contains("Read I/O")) {
          System.out.print("   ******* READ ERROR found during transfer - abort! ******");
          break;
        }
      }

      b[0] = 27;    // ctrl x
      s.getOutputStream().write(b, 0, 1);
      line = "xmode /term echo\r";
      s.getOutputStream().write(line.getBytes(), 0, line.length());
      for (int j = 0; j < nchar * 4 / 1000; j++) {
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        //println("Last20="+getLast20()+" pnt="+last20p);
        if (getLast20().contains("term echo")) {
          break;
        }
      }
      System.out.print(Util.asctime() + " " + file + " " + nchar + " chars has been sent at " + (nchar * 10000 / (System.currentTimeMillis() - start)) + " b/s\n");
      s.getOutputStream().write("\r".getBytes(), 0, 1);
    } catch (FileNotFoundException e) {
      System.out.print("File not found file=" + file + "\n");
    } catch (IOException e) {
      System.out.print("Error reading file=" + file + "Aborting....\n");
    }
    return true;
  }

  public final class KeyHandler extends Thread {

    public KeyHandler() {
      start();
    }

    @Override
    public void run() {
      try {
        while (!terminate) {
          if (s == null) {
            try {
              sleep(100);
            } catch (InterruptedException e) {
            }
          } else if (s.isClosed()) {
            try {
              sleep(100);
            } catch (InterruptedException e) {
            }

          } else {
            int chr = System.in.read();
            if (chr == 10) {
              chr = 13;
            }
            if (chr == 16) {
              usage();
              chr = System.in.read();
              //println("Got command char="+chr);
              if (chr == '1') {
                println("disconnection2");
                name = "NONE";
                try {
                  s.close();
                } catch (IOException e) {
                }
                continue;
              }
              if (chr == '2') {
                terminate = true;
                try {
                  s.close();
                } catch (IOException e) {
                }
                System.exit(1);
              }
              if (chr == '8') {
                sendTextFile();
              }
              if (chr != 16) {
                continue;
              }
            }
            //println("char rcv="+chr);
            s.getOutputStream().write(chr);
            s.getOutputStream().flush();
          }
        }
      } catch (IOException e) {
        System.out.println("KeyHandler: IOError writting to server. e=" + e);
        if (e.getMessage().contains("Broken pipe")) {
          println("\nConnection lost to server.");
          System.exit(0);
        }

        println("\nIOException reading from system.in or writing to socket=" + e);
        e.printStackTrace();

      }
    }
    String lastTextFile = "qsub680.c";

    private boolean sendTextFile() throws IOException {
      byte[] b = new byte[10];
      System.out.print("Enter filename to send : (def=" + lastTextFile + ") : ");
      String file = "";
      for (;;) {
        int chr = System.in.read();
        //println("File chr="+chr);
        if (chr != 10 && chr != 13) {
          if (chr == 0177) {
            file = file.substring(0, file.length() - 1);
            System.out.print((char) 8);
            System.out.print((char) ' ');
            System.out.print((char) 8);
          } else {
            file += (char) chr;
            System.out.print((char) chr);
          }
        } else {
          break;
        }
      }
      println("File =" + file);
      if (file.equals("")) {
        file = lastTextFile;
      }
      lastTextFile = file;
      String line;
      try {
        BufferedReader in = new BufferedReader(new FileReader(file));
        String shortfile = file;
        if (shortfile.lastIndexOf("/") >= 0) {
          shortfile = shortfile.substring(shortfile.lastIndexOf("/") + 1);
        }
        line = "del " + shortfile + "\rxmode /term noecho\rcopy /term " + shortfile + "\r";
        long start = System.currentTimeMillis();
        println(Util.asctime() + " Starting transfer....");
        s.getOutputStream().write(line.getBytes(), 0, line.length());
        int nchar = 0;
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        while ((line = in.readLine()) != null) {
          s.getOutputStream().write((line + "\r").getBytes(), 0, line.length() + 1);
          nchar += line.length() + 1;
          //try {sleep(line.length()*3);} catch(InterruptedException e) {}  // Limit to 9600 baud!
          /*int nchar = s.getInputStream().read(b);
          if(b[0] == '\n') b[0] = '\r';
          if(nchar > 0) conn.write(this, b, 0, nchar);*/
          if (getLast20().contains("read error") || getLast20().contains("000:244")
                  || getLast20().contains("Read I/O")) {
            System.out.print("   ******* READ ERROR found during transfer - abort! ******");
            break;
          }
        }

        b[0] = 27;    // ctrl x
        s.getOutputStream().write(b, 0, 1);
        line = "xmode /term echo\r";
        s.getOutputStream().write(line.getBytes(), 0, line.length());
        for (int j = 0; j < nchar * 4 / 1000; j++) {
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          }
          //println("Last20="+getLast20()+" pnt="+last20p);
          if (getLast20().contains("term echo")) {
            break;
          }
        }
        System.out.print(Util.asctime() + " " + file + " " + nchar + " chars has been sent at " + (nchar * 10000 / (System.currentTimeMillis() - start)) + " b/s\n");
        s.getOutputStream().write("\r".getBytes(), 0, 1);
      } catch (FileNotFoundException e) {
        System.out.print("File not found file=" + file + "\n");
      } catch (IOException e) {
        System.out.print("Error reading file=" + file + "Aborting....\n");
      }
      return true;
    }
  }

  public void usage() {
    println("\n\n1       Disconnect only");
    println("2       Disconnect and exit");
    println("8       Send ASCII file");
    println("^P      Go to server menu");

  }

  public static void main(String[] args) {
    String host = "localhost";
    int port = 8002;
    String station = "DCK";
    int lasti = 0;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
        lasti = i + 1;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
        lasti = i + 1;
      }
    }
    if (lasti < args.length) {
      station = args[lasti];
    }
    System.out.println("connecting to " + host + "/" + port + " for " + station);
    UserSession session = new UserSession(host, port, station, true, null);
  }
}
