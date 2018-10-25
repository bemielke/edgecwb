/*
 * This software is in the public domain because it contains materials 
  * that originally came from the United States Geological Survey, 
  * an agency of the United States Department of Interior. For more 
  * information, see the official USGS copyright policy at 
  * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 *  GetSeedLinkAutoConfig uses ssh to load contents of SeedLinkClient setup files that
 *  have been auto generated using SeedLinkConfigBuilder.  
 *  Files will be automatically generated when the "-cfg" parameter is used with
 *  SeedLinkClient.  
 *  The "vdl" user on the server this process is run on must have direct ssh access
 *  to the other servers.  When ssh is called no input should be required or the 
 *  process will fail.
 * 
 *  Input Parameters:
 *  -v  Verbose output
 *  -r  Role name of the server to retrieve config files for
 *  -u  User to perform ssh login as
 * 
 * @author bmielke
 */
public class GetSeedLinkAutoConfig {

  private String sshUser;
  private List<ThreadData> allThreadData;
  private static final StringBuilder sb = new StringBuilder();
  private boolean verbose = false;
  private String singleRoleName = null;

  public GetSeedLinkAutoConfig(String sshUser) {
    this.sshUser = sshUser;
  }
  
  public GetSeedLinkAutoConfig(boolean verbose, String sshUser) {
    this.sshUser = sshUser;
    this.verbose = verbose;
  }
  
  public GetSeedLinkAutoConfig(boolean verbose,String sshUser,String roleName) {
    this.sshUser = sshUser;
    singleRoleName = roleName;
    this.verbose = verbose;
  }

  public void loadThreadData() {
    allThreadData = ThreadData.getAllThreadData(singleRoleName);
  }

  private String getConfigFileNameFromArgs(String argString) {
    String fileName = null;
    String[] args = argString.split("\\s");
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-l")) {
        fileName = args[++i];
        break;
      }
    }

    return fileName;
  }

  public void getConfigFilesForThreadData() {
    String fileName;
    String fileInfo;
    if (verbose) Util.prt("Retrieving Config File information:");
    for (ThreadData td : allThreadData) {
      if (shouldGetConfigFile(td)) {
        if (verbose) Util.prt("Getting Config file Info for " + td.getConfigFileName() + " on " + 
                td.getRoleName() + " @ " + td.getIpAddress());
        fileName = getConfigFileNameFromArgs(td.getArgs());
        if (fileName != null) {
          fileInfo = getConfigFileInfo(fileName, td.getIpAddress());
          if (fileInfo != null) {
            td.setConfigFileContents(fileInfo);
            td.setConfigFileName(fileName.substring(0, fileName.lastIndexOf("/")) + "/Automatic"
                    + fileName.substring(fileName.lastIndexOf("/")));
          } else {
            td.setConfigFileContents(null);
          }
        } else {
          td.setConfigFileContents(null); 
        }
      } else {
        td.setConfigFileContents(null);
      }
    }
  }

  public void setConfigFilesForThreadData() {
    if (verbose) Util.prt("\nUpdating database:");
    for (ThreadData td : allThreadData) {
      if (td.getConfigFileContents() != null) {
        if (td.storeConfigFileContents() && verbose) {
          Util.prt(td.getRoleName() + " file " + td.getConfigFileName() + " successfully updated in DB.");
        } else {
          if (verbose) Util.prt("!!!!! " +td.getRoleName() + " file " +  td.getConfigFileName() + " failed to update in DB.");
        };
      }
    }
  }

  private boolean shouldGetConfigFile(ThreadData td) {
    if (td.getArgs().contains("-cfg")) {
      return true;
    }
    return false;
  }

  public String getConfigFileInfo(String fileName, String ipAddress) {
    String fileInfo = null;
    try {      
      gov.usgs.anss.util.Subprocess sp = new gov.usgs.anss.util.Subprocess("ssh -oBatchMode=yes " + sshUser + "@" + ipAddress
              + " cat " + fileName);
      try { // Wait a maximum of 3 minutes for a response
        if (sp.waitFor(120000) == -99) {
          Util.prta("GetSeedLinkAutoConfig:getConfigFileInfo:: ssh command timed out to remote=" + ipAddress);
        } else {
          fileInfo = sp.getOutput();
          if (fileInfo.length() < 10) {
            Util.prta("GetSeedLinkAutoConfig::getConfigFileInfo:: !! File = " + fileName + " on remote="
                    + ipAddress + " may not exist or ssh does not connect without user input.  (i.e. not a known host.)");
            fileInfo = null;
          }          
        }
      } catch (InterruptedException e) {
        Util.prta("GetSeedLinkAutoConfig:getConfigFileInfo:: ssh command interupted to remote=" + ipAddress);
      }
    } catch (IOException e) {
      Util.prta(e + " GetSeedLinkAutoConfigJava:getConfigFileInfo:: Failed to run ssh command on remote=" + ipAddress);
    }
    return fileInfo;
  }

  public static void main(String[] args) {
    try {
      if (args.length <= 1) {
        Util.prt("Usage:\n-u ssh user name [-r single server role to check] [-v verbose output]");
        System.exit(0);
      }
      boolean verbose = false;
      String roleName = null;
      String sshUser = null;
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-v")) {
          verbose = true;
        }
        if (args[i].equals("-r")) {
          roleName = args[++i];
          if ("ALL".equals(roleName)) roleName = null;
        }
        if (args[i].equals("-u")) {
          sshUser = args[++i];
        }
      }

      Util.setProcess("EdgeConfig");
      Util.init(gov.usgs.edge.config.UC.getPropertyFilename());
      DBConnectionThread.init(null);
      Util.prt("DBServer=" + Util.getProperty("DBServer"));
      try {
        DBConnectionThread dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "user", "edge",
                true, false, "edge", Util.getOutput());
        if (!dbconnedge.waitForConnection()) {
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              if (!dbconnedge.waitForConnection()) {
                Util.prta(" ****** Could not connect to edge database at " + Util.getProperty("DBServer"));
                dbconnedge.close();
                System.exit(1);
              }
            }
          }
        }
      } catch (InstantiationException e) {
        Util.prta(e + "SQLException making connection to db " + Util.getProperty("DBServer"));
        e.printStackTrace();
        System.exit(1);
      }
      if (verbose) {
        Util.prt("\n------------------");
      }
      GetSeedLinkAutoConfig gslac = new GetSeedLinkAutoConfig(verbose, sshUser,roleName);
      gslac.loadThreadData();
      gslac.getConfigFilesForThreadData();
      gslac.setConfigFilesForThreadData();
      if (verbose) {
        Util.prt("\n------------------\n");
      }
      DBConnectionThread.getThread("edge").close();
    } catch (Exception e) {
      Util.prta(e + " Unhandled exception...exiting.");
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static class ThreadData {

    private int instanceSetupId;
    private String args;
    private String configFileName;
    private String ipAddress;
    private String configFileContents = null;
    private String roleName;

    public String getRoleName() {
      return roleName;
    }

    public void setRoleName(String roleName) {
      this.roleName = roleName;
    }

    public int getInstanceSetupId() {
      return instanceSetupId;
    }

    public void setInstanceSetupId(int instanceSetupId) {
      this.instanceSetupId = instanceSetupId;
    }

    public String getArgs() {
      return args;
    }

    public void setArgs(String args) {
      this.args = args;
    }

    public String getConfigFileName() {
      return configFileName;
    }

    public void setConfigFileName(String configFileName) {
      this.configFileName = configFileName;
    }

    public String getIpAddress() {
      return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
      this.ipAddress = ipAddress;
    }

    public String getConfigFileContents() {
      return configFileContents;
    }

    public void setConfigFileContents(String configFileContents) {
      this.configFileContents = configFileContents;
    }

    public ThreadData(int instanceSetupId, String args, String configFileName, String ipAddress,
                      String roleName) {
      this.instanceSetupId = instanceSetupId;
      this.args = args;
      this.configFileName = configFileName;
      this.ipAddress = ipAddress;
      this.roleName = roleName;
    }
    
    @Override
    public String toString () {
      sb.setLength(0);
      sb.append("InstanceId: ").append(instanceSetupId).append("\n").append("args: ").
              append(args).append("\n").append("configFileName: ").append(configFileName).
              append("\n").append("RoleName: ").append(roleName).append("ipAddress: ").append(ipAddress).append("\n");
      
      return sb.toString();
    }

    public boolean storeConfigFileContents() {
      DBConnectionThread dbconn = DBConnectionThread.getThread("edge");
      if (dbconn == null) {
        return false;
      }
//      Util.prta("Updating config file for:" + toString());
      String sql = "UPDATE edge.instancesetup SET configfile = ? , config = ? WHERE ID = ?";
      try (PreparedStatement stmt = dbconn.prepareStatement(sql, true)) {
        stmt.setString(1, configFileName);
        stmt.setString(2, Util.chkTrailingNewLine(configFileContents));
        stmt.setInt(3, instanceSetupId);
        stmt.executeUpdate();
      } catch (SQLException e) {
        Util.prta(e + " ThreadData:storeConfigFileContents:: SQL error persisting thread data.");
        return false;
      }
      return true;
    }

    public static List<ThreadData> getAllThreadData(String roleName) {
      ArrayList<ThreadData> tdList = new ArrayList<>();
      DBConnectionThread dbconn = DBConnectionThread.getThread("edge");
      if (dbconn == null) {
        return null;
      }
      String sql;
      if (roleName == null) {
        sql = "SELECT inset.id instancesetupid, inset.args args"
                        + ", inset.configfile configfile, rl.ipadr ipaddress, rl.role rolename FROM edge.instancesetup "
                        + "inset LEFT JOIN edge.edgethread AS et ON et.id = inset.edgethreadID "
                        + " LEFT JOIN edge.instance AS ins ON ins.id = inset.instanceID LEFT JOIN "
                        + " role AS rl ON rl.id = ins.roleID WHERE et.classname = 'SeedLinkClient';";
      } else {
        sql = "SELECT inset.id instancesetupid, inset.args args"
                        + ", inset.configfile configfile, rl.ipadr ipaddress, rl.role rolename FROM edge.instancesetup "
                        + "inset LEFT JOIN edge.edgethread AS et ON et.id = inset.edgethreadID "
                        + " LEFT JOIN edge.instance AS ins ON ins.id = inset.instanceID LEFT JOIN "
                        + " role AS rl ON rl.id = ins.roleID WHERE et.classname = 'SeedLinkClient' AND rl.role = "
                        + Util.sqlEscape(roleName) + ";";
      }
      try {
        try (Statement stmt = dbconn.getNewStatement(false);                  
             ResultSet rs = stmt.executeQuery(sql)) {
          while (rs.next()) {
            ThreadData td = new ThreadData(rs.getInt("instancesetupid"), rs.getString("args"),
                    rs.getString("configFile"), rs.getString("ipaddress"),rs.getString("rolename"));
            tdList.add(td);
          }
        }

      } catch (SQLException e) {
        Util.prta(e + " ThreadData:getAllThreadData:: SQL error getting all thread data.");
        return null;
      }
      return tdList;
    }
  }
}