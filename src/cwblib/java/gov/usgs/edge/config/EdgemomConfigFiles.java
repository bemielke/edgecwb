/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * This class makes the roles_NODE, groups_NODE and edgemom.setup* for all of the current setting of
 * roles to CPUs. The makeAllFiles() method is normally used by the configuration code (Mainly
 * EdgemomConfigThread) to create all of the files in EDGEMOM subdirectory. The various build
 * routines make StringBuilders in case the results of a single config is needed by a GUI or some
 * such.
 *
 * @author rjackson & dcketchum
 */
public final class EdgemomConfigFiles {

  //private String hostname;
  //private String fqhostname;
  //private int momindex;
  private final String edgemomName;
  private static TreeMap<String, String> edgemom;
  private static EdgeThread par;

  public static void setParent(EdgeThread parent) {
    par = parent;
  }

  private static void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private static void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * constructor
   *
   * @param ts The edgemom name to use
   */
  public EdgemomConfigFiles(Edgemomsetup ts) {

    edgemomName = ts.toString2();
    if (edgemom == null) {
      edgemom = new TreeMap<>();
    }
    if (edgemom.containsKey(edgemomName)) {
      prta("We are creating a duplicate EdgeMom object !!!!!!!");
      System.exit(1);   // panic
    }
  }

  /**
   * this will add the threads for the given roll and account and return an StringBuilder with the
   * results. Duplicates tags and one only threads will have been eliminated if there are are any in
   * the given threads plus the added threads from roll and account.
   *
   * @param threads List of threads, if not empty the roll/account will be added to this list
   * @param roles The roll list of roles
   * @param account The account to add
   * @return The string builder with the edgemom.setup file, and the amended set of threads
   */
  public static StringBuilder GenEdgeStringBuilder(ArrayList<Edgemomsetup> threads, ArrayList<Role> roles, String account) {
    for (Role role : roles) {
      GenEdgeString(threads, role, account);
    }
    StringBuilder sb = new StringBuilder();
    sb.append("#\n# Edgemomsetup file for - ");
    for (Role role : roles) {
      sb.append(role.getRole()).append(" ");
    }
    sb.append("\n#\n# Form is \"unique_key:Class_of_Thread:Argument line [>][>>filename no extension]\"\n#\n");

    for (Edgemomsetup thread : threads) {
      sb.append(thread.getComment()).append("\n").append(thread.getCommandLine()).append("\n");
    }
    return sb;
  }

  /**
   * this adds Edgemomsetup for the given roleID and account to the ArrayList of Edgemomsetups
   *
   * @param threads An arrayList of threads in EdgemomSetup form where the list of threads is
   * returned to caller
   * @param role Role of the desired edgemom
   * @param account of the desired edgemom (vdl, vdl1, etc)
   */
  public static void GenEdgeString(ArrayList<Edgemomsetup> threads, Role role, String account) {

    Edgethread edge;
    Edgemomsetup loc;
    try {
      try (Statement stmt = DBConnectionThread.getConnection("edge").createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.edgemomsetup WHERE roleID=" + role.getID() + " AND account='" + account.trim() + "' ORDER BY tag;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            loc = new Edgemomsetup(rs);
            boolean duplicate = false;
            edge = Edgethread.getEdgethreadWithID(loc.getEdgethreadID());
            if (edge == null) {
              prt("**** got an unknown edgetread =" + loc);
              SendEvent.edgeSMEEvent("EdgeUnknownThr", "Unknown thread configured =" + loc, "EdgemomConfigFiles");
            }
            // Check for duplicates on only one threads
            if (edge != null) {
              if (edge.getOnlyOne() != 0) {
                for (Edgemomsetup thread : threads) {
                  if (thread.getEdgethreadID() == loc.getEdgethreadID()) {
                    prt("Eliminate duplicate on only thread  =" + thread.getRole() + "-"
                            + thread.getAccount() + " to " + role.getRole() + "-" + account + "/" + loc);
                    duplicate = true;
                  }
                }
              }
            }

            for (Edgemomsetup thread : threads) {
              if (thread.getTag().equalsIgnoreCase(loc.getTag())) {
                duplicate = true;
                prt("Eliminate Duplicate tag =" + thread.getRole() + "-" + thread.getAccount()
                        + " to " + role.getRole() + "-" + account + "/" + loc.getTag());
              }
            }
            if (!duplicate) {
              threads.add(loc);
            }
          }
        }
      }

    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEdgemomsetups() on table SQL failed");
    }
  }

  /**
   * generate the groups_NODE file
   *
   * @param threads Threads for this edgemom to be scanned for group implications
   * @param roles The roles assigned for this node (included in the comments)
   * @return A StringBuffer with the groups_NODE file content
   */
  public static StringBuilder GenGroupsBuffer(ArrayList<Edgemomsetup> threads, ArrayList<Role> roles) {

    StringBuilder sb = new StringBuilder(1000);
    sb.append("#!").append(Util.fs).append("bin").append(Util.fs).append("bash -f\n# for roles ");
    for (Role role : roles) {
      sb.append(role.getRole()).append(" ");
    }
    sb.append("\nVDL_GROUPS=\"");
    boolean hasone = false;
    for (Edgemomsetup thread : threads) {
      if (thread.getEdgethreadName().equalsIgnoreCase("Q330Manager")) {
        hasone = true;
        sb.append("q330 ");
      }
      if (thread.getTag().contains("DADP") && sb.indexOf("dadp") < 0) {
        hasone = true;
        sb.append("dadp ");
      }
      if (thread.getTag().contains("RIAR") && sb.indexOf("gtsn") < 0) {
        hasone = true;
        sb.append("gtsn test ");
      }
    }
    if (hasone) {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append("\"\nexport VDL_GROUPS\n");
    return sb;
  }

  /**
   * given an array list of roles, make up a roles file
   *
   * @param roles ArrayList of roles
   * @param cpu The CPU which is having edgemom.setups built for (one or more roles)
   * @return The text of the roles_nnnn file as a StringBuilder
   */
  public static StringBuilder GenRoleBuffer(ArrayList<Role> roles, Cpu cpu) {

    StringBuilder sb = new StringBuilder(1000);
    sb.append("#!").append(Util.fs).append("bash -f\n# for node ").append(cpu.getCpu());

    sb.append("\nVDL_ROLES=\"");

    for (Role role : roles) {
      sb.append(role.getRole().trim()).append(" ");
    }
    if (roles.size() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append("\"\nexport VDL_ROLES\n");
    return sb;
  }

  /**
   * write out a file with the given contents
   *
   * @param filename name of the file
   * @param wfsb File contents in a StringBuffer.
   */
  public static void WriteFile(String filename, StringBuilder wfsb) {
    //StringBuilder sb = new StringBuilder(100);
    RandomAccessFile fileout;
    Util.chkFilePath(filename);
    try {

      fileout = new RandomAccessFile(filename, "rw");
    } catch (FileNotFoundException e) {
      prta("Error writing to file " + e.getMessage());
      return;
    }
    try {
      String r = wfsb.toString();
      fileout.writeBytes(r);
      fileout.setLength((long) r.length());
      fileout.close();
    } catch (IOException e) {
      prta("Error writing to file" + e.getMessage());
    }
  }

  /**
   * This method creates all group_NODE, roles_NODE and edgemom.setup files from the database for
   * all roles and for the current roles-to-cpu mappings and puts them in the EDGEMOM directory.
   *
   * It also writes all edgefile side files into the EDGEMOM directory.
   *
   * This is called by the EdgemomConfigThread.run() to create all the files when a reconfiguration
   * event is detected.
   *
   * @param configDir Path to the configuration directory
   */
  public static void makeAllFiles(String configDir) {
    if (configDir == null) {
      configDir = "EDGEMOM" + Util.fs;
    }
    ArrayList<String> accounts = new ArrayList<>(5);
    ArrayList<String> hosts = new ArrayList<>(10);
    ArrayList<Role> roles = new ArrayList<>(1);   // Accumulate the roles associated with this host
    String host;
    String account;
    //Statement s=null;
    for (int i = 0; i < 10; i++) {
      prta("makeAllFiles(): get connection for edge DBG");
      try {
        DBConnectionThread.getThread("edge").reopen();
        if (!DBConnectionThread.waitForConnection("edge")) {
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              prta("MakeAllFiles: database connection did not reopen promptly");
            }
          }
        }
        try (ResultSet rs = DBConnectionThread.getThread("edge").executeQuery("select cpu from edge.cpu order by cpu")) {
          while (rs.next()) {
            hosts.add(rs.getString("cpu"));
          }
        }
        break;
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "Trying to get hosts!");
        if (i >= 9) {
          System.exit(1);
        }
        Util.sleep(1000);
      }
    }
    prta("ECF.makeAllFiles() #hosts=" + hosts.size());

    // Look through the EDGEMOM directory and delete all candidate files.
    File edir = new File(configDir.substring(0, configDir.length() - 1));
    if (edir.isDirectory()) {
      File[] files = edir.listFiles();
      for (File file : files) {
        if (file.isDirectory()) {
          continue;
        }
        if (file.getName().contains(".setup")) {
          file.delete();
        } else if (file.getName().contains("groups_")) {
          file.delete();
        } else if (file.getName().contains("roles_")) {
          file.delete();
        }
      }
    } else {
      prt("There is no EDGEMOM directory!  Exit()");
      System.exit(2);
    }

    for (String host1 : hosts) {
      host = host1;
      prta("Start ECF.makeAllFiles() for host=" + host);
      Cpu cpu = null;
      try {
        // For the CPU create groups, roles and get the cpuid for this cpu
        ResultSet rs1 = DBConnectionThread.getThread("edge").executeQuery("select * from edge.cpu where cpu = '" + host + "'");// and edgemomsetup='LABV%';");
        if (rs1.next()) {
          cpu = new Cpu(rs1);
        }
        rs1.close();

        // Make list of accounts for all roles assigned to this cpu
        accounts.clear();
        roles.clear();
        if (cpu != null) {
          rs1 = DBConnectionThread.getThread("edge").executeQuery("select * from edge.role where cpuid=" + cpu.getID() + " ORDER BY role");
          while (rs1.next()) {
            roles.add(new Role(rs1));
            String[] acct = rs1.getString("accounts").split("\\s");
            for (String acct1 : acct) {
              boolean found = false;
              for (String account1 : accounts) {
                if (account1.equals(acct1)) {
                  found = true;
                }
              }
              if (!found) {
                accounts.add(acct1);
              }
            }
          }
          rs1.close();
          WriteFile(configDir + "roles_" + host, GenRoleBuffer(roles, cpu));  // roles_$NODE
        } else {
          prta("**** did not get a edge.cpu for host=" + host);
        }
      } catch (SQLException e) {
        prta("SQLException on getting cpu" + e.getMessage());
        Util.SQLErrorPrint(e, "SQLExcepion on getting cpu");
        e.printStackTrace();
        System.exit(0);
      } catch (RuntimeException e) {
        e.printStackTrace(par.getPrintStream());
      }
      for (String account1 : accounts) {
        account = account1;
        prt("Start " + cpu + " account=" + account);
        ArrayList<Edgemomsetup> threads = new ArrayList<>(20);
        StringBuilder sb = GenEdgeStringBuilder(threads, roles, account);
        WriteFile(configDir + "groups_" + host, GenGroupsBuffer(threads, roles));
        WriteFile(configDir + "edgemom.setup-" + host + "-" + account, sb);
      } // End for each account
    } // end for each host/node
    // Now write out all of the side files
    try {
      try (ResultSet rs = DBConnectionThread.getThread("edge").getNewStatement(false).executeQuery("SELECT * FROM edge.edgefile ORDER BY edgefile")) {
        while (rs.next()) {
          String filename = rs.getString("edgefile");
          if ((filename.charAt(0) == '_' || filename.contains("/_")) && !filename.contains("NoDB")) {
            prt("Skip " + filename);
            continue;
          }
          prt("Write " + filename);
          String content = rs.getString("content");
          StringBuilder sb = new StringBuilder(content.length());
          sb.append(content);
          if (filename.charAt(0) == '~') {
            WriteFile(Util.fs + "home" + Util.fs + filename.substring(1).trim(), sb);
          } else {
            WriteFile(configDir + filename.trim(), sb);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to write files");
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.init("edge.prop");
    prt("");
    Util.setNoInteractive(true);
    try {
      if (DBConnectionThread.getThread("anss") == null) {
        DBConnectionThread tmp
                = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss", true, (Util.getProperty("SSLEnabled") != null),
                        "anss", Util.getOutput());
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              prt("Did not promptly connect to anss from EdgemomConfigThread");
            }
          }
        }
      }
    } catch (InstantiationException e) {
      prt("Instantiation getting (impossible) edgero e=" + e.getMessage());
    }

    try {
      prt("MakeEtcHost: connect to " + Util.getProperty("DBServer"));
      DBConnectionThread tmp
              = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", true, (Util.getProperty("SSLEnabled") != null),
                      "edge", Util.getOutput());

      if (!DBConnectionThread.waitForConnection("edge")) {
        if (!DBConnectionThread.waitForConnection("edge")) {
          if (!DBConnectionThread.waitForConnection("edge")) {
            prt("Did not promptly connect to edgeConfig from EdgemomConfigThread");
          }
        }
      }
      prt("EdgemomConfigThread: connect to " + Util.getProperty("DBServer"));
    } catch (InstantiationException e) {
      prta("Instantiation getting (impossible) anssro e=" + e.getMessage());
    }

    makeAllFiles(null);
    System.exit(1);

  }       // end of main()
}
