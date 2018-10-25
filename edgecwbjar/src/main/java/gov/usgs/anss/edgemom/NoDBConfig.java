/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * This class computes files based on a enumKeys-value value files and the
 * templates in the NoDB directory. All files have substitutions done for all of
 * the enumKeys value pairs and the files are moved to the same positions they
 * have in NoDB relative to the current directory - that is if there is a file
 * NoDB/EW/import.setup the substituted file will be in ./EW/import.setup.
 * Normally this would either be run manually during a normal setup, or it can
 * be run as a Thread under EdgeMom so that changes in the enumKeys value file
 * are propagated every 2 minutes.
 * <br>
 * The routine checks the modified dates of the property and enumKeys files and
 * if they have been changed then the files are check to see if they are the
 * same as the cached copy of those files from the last full run. If either of
 * the files is different, then the full substitution is performed. The check
 * for modified dates can be skipped with the -nodate option which is used on
 * systems that do not support the modified date on files.
 * <br>
 *
 * <PRE>
 * switch    value        description
 * -config  keyfile   Set the enumKeys-value file name to this instead of the default "config/edgemom.config"
 * -prop    propfile  If another property file is needed to complete the conversion (def=edge.prop)
 * -once              If present only run the conversion once and then exit
 * -initdb            If present, the channel and snwstation database are set to being empty.
 * -wait    secs      The number of seconds to wait between passes (def=120)
 * -nodate            If present, the enumKeys and property files check for modified dates and the files are always read.
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class NoDBConfig extends EdgeThread {

  private final Map<String, String> keyvalue = Collections.synchronizedMap(new TreeMap<String, String>());
  private String keyfile = "config/geomag.config";
  private StringBuilder keyfilesb = new StringBuilder(100);
  private String propfile = "edge.prop";
  private StringBuilder propfilesb = new StringBuilder(100);
  private Properties keys = new Properties();
  private byte[] buf = new byte[10000];    // This buffer is shared between all RandomAccessFiles read and written
  private final StringBuilder sb = new StringBuilder(10000);
  // If tags files are used, they make the config file with the template file
  private String tagsFile;
  private Properties tags = new Properties();
  private StringBuilder tagsfilesb = new StringBuilder(100);
  private String templateFile = "config/slate2tag9.template";
  private final StringBuilder template = new StringBuilder(1000);
  private long waitms;
  private boolean onceonly;
  private boolean noModifiedCheck;
  private boolean zapDBFiles;
  private boolean dbg;

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor enumKeys value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      Util.clear(monitorsb);
    }
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      Util.clear(statussb);
    }
    return statussb.append(tag);
  }

  @Override
  public void terminate() {
    prta(tag + " terminate called");
    terminate = true;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    if (consolesb.length() > 0) {
      Util.clear(consolesb);
    }
    return consolesb;
  }

  public NoDBConfig(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    waitms = 120000;
    if (args.length == 0) {
      Util.prt("Usage : nodbconfig : -config config/filename -template file.template [-initdb][-once][-prop propfile][-nodate]");
      Util.prta("NoDB: makeConfigFromTags ran new keyfile written " + keyfile);
      Util.prt("switch    value        description");
      Util.prt("-config  keyfile   Set the enumKeys-value file name to this instead of the default 'config/edgemom.config'");
      Util.prt("-prop    propfile  If another property file is needed to complete the conversion (def=edge.prop)");
      Util.prt("-once              If present only run the conversion once and then exit");
      Util.prt("-initdb            If present, the channel and snwstation database are set to being empty.");
      Util.prt("-nodate            If present, the enumKeys and property files check for modified dates and the files are always read.");
      System.exit(0);
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].trim().equals("")) {
        continue;
      }
      if (args[i].equalsIgnoreCase("-config")) {
        keyfile = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-prop")) {
        propfile = args[i + 1];
        Util.init(propfile);
        i++;
      } else if (args[i].equalsIgnoreCase("-once")) {
        onceonly = true;
      } else if (args[i].equalsIgnoreCase("-wait")) {
        waitms = Integer.parseInt(args[i + 1]) * 1000;
        i++;
      } else if (args[i].equalsIgnoreCase("-nodate")) {
        noModifiedCheck = true;
      } else if (args[i].equalsIgnoreCase("-tags")) {
        tagsFile = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-template")) {
        templateFile = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-initdb")) {
        zapDBFiles = true;
      } else if (args[i].equalsIgnoreCase("-dbg")) {
        dbg = true;
      } else if (args[i].startsWith(">")) {
        break;
      } else {
        Util.prta("NoDB: ** Unknown switch =" + args[i]);
      }
    }

    if (tagsFile != null) {    // check on tags file and template file and see if they are changed
      makeConfigFromTags(tagsFile, templateFile, keyfile);  // write out new enumKeys file

      System.exit(0);
    }
    this.setDaemon(true);
    running = true;
    start();
  }

  public final void makeConfigFromTags(String tagsFile, String templateFile, String config) {
    tags.clear();
    try (FileInputStream i = new FileInputStream(tagsFile)) {
      tags.load(i);
      Enumeration tagsenum = tags.keys();
      while (tagsenum.hasMoreElements()) {
        String s = (String) tagsenum.nextElement();
        String value = tags.getProperty(s).trim();
        if (value.contains("!")) {
          value = value.substring(0, value.indexOf("!")).trim();
        }
        tags.setProperty(s, value);
      }
      String station = tags.getProperty("STATION");
      station = station.trim();
      if (!tagsFile.contains(station)) {
        System.err.println("****** Tags file variable STATION does not match the .tabs filename " + station + " file=" + tagsFile);
        prta("***** Tags file var STATION variable does not match the tags Filename STATION=" + station + " file=" + tagsFile);
        System.exit(1);
      }
      tagsenum = tags.keys();
      while (tagsenum.hasMoreElements()) {
        String s = (String) tagsenum.nextElement();
        String value = tags.getProperty(s).trim();
        if (value.contains("!")) {
          value = value.substring(0, value.indexOf("!")).trim();  // Set the value to the tag value.
        }
        tags.setProperty(s, value);
        System.err.println("Tags " + s + "=" + value);
        prta("Tags " + s + "=" + value);
      }
      tags.list(getPrintStream());
    } catch (FileNotFoundException e) {
      System.err.println("NoDB: Tags file not found=" + tagsFile);
      prta("NoDB: Tags file not found=" + tagsFile);
    } catch (IOException e) {
      prta("NoDB: Error loading properties file");
    }
    try {
      Util.readFileToSB(templateFile, template);
      int nsub = substitute("TagsToTemplate", tags, template);
      prta("NoDB: write tagsToTemplate file " + tagsFile.replaceAll("tags", "config") + " after " + nsub + " substitutions");
      System.err.println("NoDB: write tagsToTemplate file " + tagsFile.replaceAll("tags", "config"));
      Util.writeFileFromSB(tagsFile.replaceAll("tags", "config"), template);
      if (template.indexOf("${") >= 0) {
        Util.prta(" ** Tags out files contains unsubstituded variables " + tagsFile.replaceAll("tags", "config"));
      }

    } catch (IOException e) {
      System.err.println("NoDB: error reading template file=" + templateFile + " e=" + e);
      prta("NoDB: error reading template file=" + templateFile + " e=" + e);
    }

  }

  private int substitute(String description, Properties keys, StringBuilder sb) {
    int nsubs = 0;
    // Do all of the properties in the config file
    for (int i = 0; i < 2; i++) {
      // Do all of the properties in the selected properies files (probably edge.prop)
      Enumeration enumKeys = keys.propertyNames();
      while (enumKeys.hasMoreElements()) {
        String key = (String) enumKeys.nextElement();
        if (key == null) {
          prta("Got a null element !!!");
        } else if (keys.getProperty(key) == null) {
          prta("keys.getProperty for s=" + key + " is null");
        } else {
          int nsub = Util.stringBuilderReplaceAll2(sb, "${" + key.toLowerCase().trim() + "}", keys.getProperty(key));
          if (dbg && nsub > 0) {
            Util.prta("NoDB: #sub=" + nsub + " in " + description + " ${" + key.toLowerCase().trim() + "} for " + keys.getProperty(key));
          }
          nsubs += nsub;
        }
      }
    }
    return nsubs;
  }

  @Override
  public void run() {
    boolean skip;
    long lastModified = 0;
    String value;
    while (!terminate) {
      skip = true;

      File prop = new File(propfile);
      if(prop.exists()) {
        prta("*** NoDB: Prop file does not exist "+propfile);
      }
      File keyf = new File(keyfile);
      if (prop.lastModified() > lastModified || keyf.lastModified() > lastModified || noModifiedCheck) {
        prta("NoDB: Files modified : prop=" + Util.ascdatetime(prop.lastModified())
                + " keyfile=" + Util.ascdatetime(keyf.lastModified())
                + " lastMod=" + Util.ascdatetime(lastModified) + " dbg=" + dbg + " waitms=" + waitms);
        lastModified = Math.max(prop.lastModified(), keyf.lastModified()) + 10000;  // set for latest update + 10 secs
        if (terminate) {
          break;
        }
        try {
          if (!getStringBuilder(keyfile, sb, keyfilesb)) {
            skip = false;
          }
          if (!getStringBuilder(propfile, sb, propfilesb)) {
            skip = false;
          }

        } catch (IOException e) {
          prta("NoDB: IOError geting " + propfile + " or " + keyfile + " e=" + e);
        }
        // If the files are different, read them in and process them.
        if (skip) {
          prta("NoDB: Files are the same!");
        } else {
          // Read in the prop file
          Util.init(propfile);
          keys.clear();
          // read in the enumKeys file
          try {
            try (FileInputStream i = new FileInputStream(keyfile)) {
              keys.load(i);
              //prtProperties();
            }

            Enumeration key = keys.keys();
            while (key.hasMoreElements()) {
              String s = (String) key.nextElement();
              value = keys.getProperty(s);
              if (value.contains("!")) {
                value = value.substring(0, value.indexOf("!")).trim();  // Set the value to the tag value.
              }
              keys.setProperty(s, value);
              keyvalue.put(s, keys.getProperty(value));
            }
            keys.list(getPrintStream());
          } catch (FileNotFoundException e) {
            prta("NoDB: Properties file not found=" + propfile);
          } catch (IOException e) {
            prta("NoDB: Error loading properties file");
          }
          // Add the propfile to the keys
          Enumeration key = Util.getProperties().keys();
          while (key.hasMoreElements()) {
            String s = (String) key.nextElement();
            value = Util.getProperty(s);
            if (value.contains("!")) {
              value = value.substring(0, value.indexOf("!")).trim();  // Set the value to the tag value.
            }
            Util.setProperty(s, value);
            keys.setProperty(s, value);
            keyvalue.put(s, keys.getProperty(value));
          }
          keys.list(getPrintStream());

          // Starting in the NoDB directory, process all files in that tree
          File nodbDir = new File("NoDB");
          String basepath = nodbDir.getAbsolutePath();
          if (nodbDir.isDirectory()) {
            doFiles(nodbDir, basepath);
          } else {
            prta("NoDB: *** ~vdl/NoDB is not a directory!");
          }
        }
      } else {
        prta("NoDB: No modified files : prop=" + Util.ascdatetime(prop.lastModified())
                + " keyfile=" + Util.ascdatetime(keyf.lastModified())
                + " lastMod=" + Util.ascdatetime(lastModified) + " waitms=" + waitms);
      }
      if (onceonly) {
        break;
      }
      for (int i = 0; i < waitms / 200; i++) {
        try {
          sleep(200);
        } catch (InterruptedException expected) {
        }
        if (terminate) {
          break;
        }
      }
    }
    prta("NoDB: exiting terminate=" + terminate);
    running = false;
  }

  /**
   * Given a file or directory and a basepath, do substitutions on the files and
   * put the results in the current directory after stripping the basepath. If
   * the File is a directory then call doFiles() recursively to process all
   * files down a directory tree.
   *
   * @param dir A directory or file to process, directories are recursively
   * processed
   * @param basePath This portion of the file name to the template is stripped
   * off when the file is written to ./
   */
  private void doFiles(File dir, String basePath) {
    File[] files = dir.listFiles();
    String s;
    int nsubs;
    for (File file : files) {
      if (terminate) {
        break;
      }
      if (file.isDirectory()) {
        doFiles(file, basePath);   // recursion is cool
      } else {
        try {
          if (file.getName().startsWith(".")) {
            continue;
          }
          nsubs = 0;
          getStringBuilder(file, sb, null);
          String filename = file.getAbsolutePath().replaceAll(basePath, "./").replaceAll("//", "/");
          if (dbg) {
            Util.prta("NoDB: Start " + filename);
          }
          // if -zap is not present, then do not update files like channel or snwstation
          if(filename.contains("edge_snwstation.txt")) {
            File f = new File("./DB/edge_snwstation.txt");
            if(!zapDBFiles && f.exists()) {
              Util.prta("NoDB: skipping existing DB/edge_snwstation.txt");
              continue;
            }
          }
          if(filename.contains("edge_channel.txt")) {
            File f = new File("./DB/edge_channel.txt");
            if(!zapDBFiles && f.exists()) {
              Util.prta("NoDB: skipping existing DB/edge_channel.txt");
              continue;
            }
          }

          // Do all of the properties in the config file
          for (int i = 0; i < 2; i++) {
            Enumeration key = keys.keys();
            while (key.hasMoreElements()) {
              s = (String) key.nextElement();
              if (dbg) {
                Util.prta("Try key element=" + s + " " + (s != null ? keys.getProperty(s) : "null"));
              }
              if (s == null) {
                Util.prta("Got a null element !!!");
              } else if (keys.getProperty(s) != null) {
                int nsub = Util.stringBuilderReplaceAll2(sb, "${" + s.toLowerCase().trim() + "}", keys.getProperty(s));
                if (dbg && nsub > 0) {
                  Util.prta("NoDB: #sub=" + nsub + " in " + filename + " ${" + s.toLowerCase().trim() + "} for " + keys.getProperty(s));
                }
                nsubs += nsub;
                if (filename.indexOf("${") > 0) {
                  filename = filename.replaceAll("\\$\\{" + s.toLowerCase().trim() + "\\}", keys.getProperty(s));
                }
              } else {
                if (dbg) {
                  prta("keys.getProperty for s=" + s + " is null");
                }
              }
            }

            // Do all of the properties in the selected properies files (probably edge.prop)
            key = Util.getProperties().keys();
            while (key.hasMoreElements()) {
              s = (String) key.nextElement();
              if (dbg) {
                Util.prta("Try prop element=" + s + " " + (s != null ? keys.getProperty(s) : "null"));
              }
              if (s == null) {
                prta("Got a null element !!!");
              } else if (keys.getProperty(s) == null) {
                prta("keys.getProperty for s=" + s + " is null");
              } else {
                int nsub = Util.stringBuilderReplaceAll2(sb, "${" + s.toLowerCase().trim() + "}", keys.getProperty(s));
                if (dbg && nsub > 0) {
                  Util.prta("NoDB: #sub=" + nsub + " in " + filename + " ${" + s.toLowerCase().trim() + "} for " + keys.getProperty(s));
                }
                if (nsub >= 1000) {
                  Util.prta("NoDB: replaceAll2 in infinite loop - fix the key file " + s.toLowerCase().trim() + " " + keys.getProperty(s));
                }
                nsubs += nsub;
                if (filename.indexOf("${") > 0) {
                  filename = filename.replaceAll("\\$\\{" + s.toLowerCase().trim() + "\\}", keys.getProperty(s));
                }
              }
            }
          }
          if (sb.indexOf("${") >= 0) {
            Util.prta("NoDB: ** file contains unmatched keys " + filename);
          }
          for (int i = 0; i < sb.length(); i++) {
            buf[i] = (byte) sb.charAt(i);
          }
          Util.chkFilePath(filename);
          if (file.getName().charAt(0) == '_') {
            filename = filename.replaceAll(file.getName(), file.getName().substring(1));
          }
          try (RandomAccessFile rw = new RandomAccessFile(filename, "rw")) {
            rw.seek(0L);
            rw.write(buf, 0, sb.length());
            rw.setLength(sb.length());
            Util.prta("NoDB: write file=" + filename + " " + nsubs + " substitutions");
          }
        } catch (IOException e) {
          prta("NoDB: ** IOErr reading " + file + " e=" + e);

        }
      }
    }

  }

  /**
   * read in a file, if its the same as the perm version, return true, if not
   * return false and make the perm version equal the file
   *
   * @param file The file to check
   * @param in A StringBuilder to use as scratch space for reading this in
   * @param perm The current version of contents of the file as a StringBuilder,
   * if null, do not do any comparison
   * @return True if the file has not changed, false if not
   * @throws IOException
   */
  private synchronized boolean getStringBuilder(String file, StringBuilder in, StringBuilder perm) throws IOException {
    try (RandomAccessFile rw = new RandomAccessFile(file, "r")) {
      if (buf.length < rw.length()) {
        buf = new byte[(int) rw.length() * 2];
      }
      rw.seek(0L);
      rw.read(buf, 0, (int) rw.length());
      if (in.length() > 0) {
        Util.clear(in);
      }
      for (int i = 0; i < rw.length(); i++) {
        in.append((char) buf[i]);
      }
    }
    if (perm == null) {
      return false;
    }
    // Is this new version the same length and content
    boolean ret = true;
    if (perm.length() != in.length()) {
      ret = false;
    } else {
      for (int i = 0; i < in.length(); i++) {
        if (in.charAt(i) != perm.charAt(i)) {
          ret = false;
          break;
        }
      }
    }
    // If the file has changed, copy it into the perm
    if (ret == false) {
      if (perm.length() > 0) {
        Util.clear(perm);
      }
      perm.append(sb);
    }
    return ret;
  }

  /**
   * read in a file, if its the same as the perm version, return true, if not
   * return false and make the perm version equal the file
   *
   * @param file The file to check
   * @param in A StringBuilder to use as scratch space for reading this in
   * @param perm The current version of contents of the file as a StringBuilder,
   * if null, do not do any comparison
   * @return True if the file has not changed, false if not
   * @throws IOException
   */
  private synchronized boolean getStringBuilder(File file, StringBuilder in, StringBuilder perm) throws IOException {
    return getStringBuilder(file.getAbsolutePath(), in, perm);
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Util.init("edge.prop");
    String argline = "";
    if (args.length == 0) {
      argline = "-tags config/TEST.tags -template config/slate2.template -config config/TEST.config -once ";
    } else {
      for (String arg : args) {
        
        argline += arg + " ";
      }
    }
    String logfile = ">> nodbconfig";
    if(argline.contains("-once")) logfile="";
    NoDBConfig config = new NoDBConfig(argline + logfile, "NoDBC");
    while (config.isRunning()) {
      Util.sleep(200);
    }
    System.exit(0);

  }
}
