/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.*;

/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class ChangeCopyright {

  private static boolean dbg;
  private static final String newNotice = "/*\n * This software is in the public domain because it contains materials \n"
          + " * that originally came from the United States Geological Survey, \n"
          + " * an agency of the United States Department of Interior. For more \n"
          + " * information, see the official USGS copyright policy at \n"
          + " * http://www.usgs.gov/visual-id/credit_usgs.html#copyright\n */\n";

  public ChangeCopyright(File path) {
    File[] files = path.listFiles();
    StringBuilder sb = new StringBuilder(100000);
    byte[] b = new byte[10000000];
    for (File file : files) {
      if (file.isDirectory()) {
        if (dbg) {
          System.out.println("Directory : " + file);
        }
        new ChangeCopyright(file);
      } else {
        if (file.getName().endsWith(".java")) {
          if (file.getAbsolutePath().indexOf("gov/usgs/") > 0) {
            System.out.println("Process: " + file.getAbsolutePath());
            try {
              if (sb.length() > 0) {
                sb.delete(0, sb.length());
              }
              try (RandomAccessFile rw = new RandomAccessFile(file.getAbsoluteFile(), "rw")) {
                rw.read(b, 0, (int) rw.length());
                for (int j = 0; j < rw.length(); j++) {
                  sb.append((char) b[j]);
                }
                int iscomm = sb.indexOf("/*");
                int copy = sb.indexOf("opyright");
                int iecomm = sb.indexOf("*/");
                int template = sb.indexOf("Tools | Templates");
                int iclass = sb.indexOf("class");
                if (iclass < 0) {
                  iclass = sb.indexOf("interface");
                }
                if (iclass < 0) {
                  System.out.print("**** no class or interface " + file.getAbsolutePath());
                }
                if (iscomm == -1 || iecomm == -1 || (copy == -1 && template == -1) || iclass < iscomm) {
                  System.out.println(sb.substring(0, iclass) + "\n *** Bad copyright form! " + file.getAbsolutePath() + " add at beginning");
                  System.in.read(b, 0, 1);
                  if (b[0] == 'y') {
                    sb.insert(0, newNotice);
                  } else {
                    rw.close();
                    continue;
                  }
                } else {
                  System.out.println("***** " + file.getAbsolutePath() + " ******\n" + sb.substring(iscomm, iecomm + 2));
                  sb.delete(iscomm, iecomm + 2);
                  sb.insert(iscomm, newNotice);
                  System.out.println(sb.substring(iscomm, iscomm + newNotice.length()));
                  System.out.println("*********");
                }
                rw.seek(0L);
                rw.write(sb.toString().getBytes());
                rw.setLength(sb.length());
              }
            } catch (IOException e) {
              System.out.println("IOError e=" + e);
              e.printStackTrace();
            }
          } else {
            System.out.println("NOT a gov.usgs. file - skip it " + file.getAbsolutePath());
          }
        } else if (dbg) {
          System.out.println("Not a directory or java file =" + file.getAbsolutePath());
        }
      }
    }
  }

  public static void main(String args[]) {
    // Look for public GPL copyrights in all source and change to the USGS notice
    String newNotice = "This software is in the public domain because it contains materials \n"
            + "that originally came from the United States Geological Survey, \n"
            + "an agency of the United States Department of Interior. For more \n"
            + "information, see the official USGS copyright policy at \n"
            + "http://www.usgs.gov/visual-id/credit_usgs.html#copyright\n";
    if (args.length == 0) {
      Util.prt("Usage : java -cp Util.jar gov.usgs.anss.util.ChangeCopyright PATH_TO_SOURCE");
      System.exit(0);
    }
    for (String arg : args) {
      if (arg.equals("-dbg")) {
        dbg = true;
      } else {
        File path = new File(arg);
        new ChangeCopyright(path);
      }
    }

  }
}
