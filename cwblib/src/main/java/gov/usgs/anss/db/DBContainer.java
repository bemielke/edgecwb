/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import org.jasypt.util.text.BasicTextEncryptor;

/**
 * This class reads in a file and gets a user name and inKey for a given "user"
 *
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class DBContainer {

  private static final String edgeMask = "dcCVQT7FraEABZ2R2e3HbcoCm4SWK4m9";
  private String name;
  private String inKey;
  private StringBuilder sb = new StringBuilder(1000);

  public static String getMask() {
    return decrypt(edgeMask, "ansis42");
  }

  public DBContainer(String name, String key) {
    this.name = name;
    this.inKey = key;
  }

  public String getKey(String key) throws IOException {
    String ans;
    try (BufferedReader in = new BufferedReader(new StringReader(read()))) {
      ans = null;
      String line;
      while ((line = in.readLine()) != null) {
        String[] parts = line.split(":");
        if (parts[0].trim().equals(key.trim())) {
          ans = parts[1];
          break;
        }
      }
      if (ans == null && key.equalsIgnoreCase("readonly")) {
        ans = getKey("user");
      }
      if (ans == null && key.equalsIgnoreCase("update")) {
        ans = getKey("user");
      }
    }
    return ans;
  }

  public String read() throws IOException {
    byte[] b;
    try (RandomAccessFile rw = new RandomAccessFile(name, "r")) {
      b = new byte[(int) rw.length()];
      rw.read(b);
    }
    String in = new String(b);
    BasicTextEncryptor bte = new BasicTextEncryptor();
    bte.setPassword(inKey);
    String out = bte.decrypt(in);
    return out;
  }

  public void add(String key, String text) throws IOException {
    BufferedReader in = null;
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }
    try {
      in = new BufferedReader(new StringReader(read()));
    } catch (FileNotFoundException e) {
      System.out.println("Creating " + name);
    }
    String line;
    boolean found = false;
    if (in != null) {
      while ((line = in.readLine()) != null) {
        String[] parts = line.split(":");
        if (parts[0].trim().equals(key.trim())) {
          sb.append(key).append(":").append(text).append("\n");
          found = true;
        } else {
          sb.append(line).append("\n");
        }
      }
      in.close();
    }
    if (!found) {
      sb.append(key).append(":").append(text).append("\n");
    }
    BasicTextEncryptor bte = new BasicTextEncryptor();
    bte.setPassword(inKey);
    String out = bte.encrypt(sb.toString());
    try (RandomAccessFile rw = new RandomAccessFile(name, "rw")) {
      rw.write(out.getBytes());
      rw.setLength(out.length());
    }

  }

  public static String encrypt(String value, String encrypt) {
    BasicTextEncryptor bte = new BasicTextEncryptor();
    bte.setPassword(encrypt);
    return bte.encrypt(value);
  }

  public static String decrypt(String value, String encrypt) {
    BasicTextEncryptor bte = new BasicTextEncryptor();
    bte.setPassword(encrypt);
    return bte.decrypt(value);
  }

  public void close() {
    name = null;
    inKey = null;
    sb = null;
  }

}
