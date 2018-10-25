/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * This class reads the configuration file for a Volcano subnetogram so the
 * configuration is known to CWBWaveServer so additional groups can be made
 * available to SWARM. This is only used on the Volcano Program servers at the
 * NEIC.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class SubnetConfig {

  private TreeMap<String, ArrayList<String>> chan = new TreeMap<>();

  /**
   * given a NSCL (location code trimmed if blank)
   *
   * @param ch A NSCL
   * @return An ArrayList with one subnet group name in each entry
   */
  public ArrayList<String> getGroups(String ch) {
    return chan.get(ch.concat("       ").substring(0, 9));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(1000);
    Set keys = chan.keySet();
    Iterator itr = keys.iterator();
    while (itr.hasNext()) {
      String key = (String) itr.next();
      ArrayList<String> groups = chan.get(key);
      sb.append(key).append(":");
      for (String group : groups) {
        sb.append(group).append(",");
      }
      sb.append("\n");
    }
    return sb.toString();

  }

  public SubnetConfig(String path) {
    File top = new File(path);
    if (top.isDirectory()) {
      File[] vos = top.listFiles();
      for (File vo : vos) {
        if (vo.isDirectory()) { // Its a config directory
          File[] configs = vo.listFiles();
          for (File config : configs) {
            if (config.getAbsolutePath().endsWith(".config")) {
              try {
                try (BufferedReader in = new BufferedReader(new FileReader(config))) {
                  String line;
                  String subnetname = config.getName();
                  while ((line = in.readLine()) != null) {
                    if (line.startsWith("#")) {
                      continue;
                    }
                    if (!line.contains("=")) {
                      continue;
                    }
                    if (line.contains("#")) {
                      line = line.substring(0, line.indexOf("#")).trim();
                    }
                    String[] parts = line.split("=");
                    if (parts[0].equalsIgnoreCase("subnetname")) {
                      subnetname = parts[1];
                    }
                    if (parts[0].equalsIgnoreCase("channel")) {
                      String[] ch = parts[1].split("\\s");
                      String channel = ch[2].concat("  ").substring(0, 2) + ch[0].concat("     ").substring(0, 5) + ch[1].concat("   ").substring(0, 2);// no directrion
                      //if(ch.length >=4) channel += ch[3].concat("  ").substring(0,2);
                      channel = channel.trim();
                      ArrayList<String> groups = chan.get(channel);
                      if (groups == null) {
                        groups = new ArrayList<>(2);
                        chan.put(channel, groups);
                      }
                      groups.add(subnetname.replaceAll("\n",""));
                    }   // iif its a channel
                  }     // while more lines
                }
              } catch (FileNotFoundException expected) { // not really, but what could we do!

              } catch (IOException expected) { // not really, but what could we do!

              }
            }   // Its a config file
          }
        }
      }
    }
  }

  public static void main(String[] args) {
    SubnetConfig sn = new SubnetConfig("/data/subnet/config");
    System.out.println(sn.toString());
  }
}
