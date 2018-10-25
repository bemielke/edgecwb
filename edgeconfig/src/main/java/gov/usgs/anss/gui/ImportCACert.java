/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import java.io.*;

public class ImportCACert {

  private static final String CACERT
          = "-----BEGIN CERTIFICATE-----\n"
          + "MIIDUjCCArugAwIBAgIJAMuNyvXrQvTuMA0GCSqGSIb3DQEBBAUAMHoxCzAJBgNV\n"
          + "BAYTAlVTMREwDwYDVQQIEwhDb2xvcmFkbzEPMA0GA1UEBxMGR29sZGVuMS8wLQYD\n"
          + "VQQKEyZOYXRpb25hbCBFYXJ0aHF1YWtlIEluZm9ybWF0aW9uIENlbnRlcjEWMBQG\n"
          + "A1UEAxMNRGF2aWQgRmlmaWVsZDAeFw0wNTA1MjMyMjA5MTdaFw0wNTA2MjIyMjA5\n"
          + "MTdaMHoxCzAJBgNVBAYTAlVTMREwDwYDVQQIEwhDb2xvcmFkbzEPMA0GA1UEBxMG\n"
          + "R29sZGVuMS8wLQYDVQQKEyZOYXRpb25hbCBFYXJ0aHF1YWtlIEluZm9ybWF0aW9u\n"
          + "IENlbnRlcjEWMBQGA1UEAxMNRGF2aWQgRmlmaWVsZDCBnzANBgkqhkiG9w0BAQEF\n"
          + "AAOBjQAwgYkCgYEAvxkc4WSEVgVoPHCONGGq5Z83Qd6tskUuyQZB1tv4RNAhy+S8\n"
          + "yshGkQBOw9a766hYdjCngvFn0hECyrTbpzYuUMA8ewOOasb168t/itos0x2D8ww0\n"
          + "sIkiSCSdxLvyHJsSr2lEUr2SmBl8uCHHEEB7kzVnC/kD3vHHOCLps0trsVUCAwEA\n"
          + "AaOB3zCB3DAdBgNVHQ4EFgQUKXwAU9yjW5ZKivdcOMuiS9ngrDgwgawGA1UdIwSB\n"
          + "pDCBoYAUKXwAU9yjW5ZKivdcOMuiS9ngrDihfqR8MHoxCzAJBgNVBAYTAlVTMREw\n"
          + "DwYDVQQIEwhDb2xvcmFkbzEPMA0GA1UEBxMGR29sZGVuMS8wLQYDVQQKEyZOYXRp\n"
          + "b25hbCBFYXJ0aHF1YWtlIEluZm9ybWF0aW9uIENlbnRlcjEWMBQGA1UEAxMNRGF2\n"
          + "aWQgRmlmaWVsZIIJAMuNyvXrQvTuMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEE\n"
          + "BQADgYEAjBRS7HnCiSEb4v64RwZUMzrpraE+hzDVAv8cuVxSrAVO9a9BNS2/5wjv\n"
          + "lGmagZAgxgerskPvWvdSThK1miuNTdAPM2WanP+YlM4JcW4ZYXqM3uqLf42Ajlyg\n"
          + "T8LDcyi51qwD0KN8fdo0qwdzYsQAbAcor7Ttq1EPs6BDmrV9J14=\n"
          + "-----END CERTIFICATE-----\n";
  private static final String KEYTOOL_NAME = "keytool";
  private static final String KEYTOOL_PATH
          = new File(new File(System.getProperty("java.home"), "bin"), KEYTOOL_NAME).getPath();
  private static final String TRUSTSTORE_PATH
          = new File(System.getProperty("user.dir"), ".keystore").getPath();
  private static final String DEFAULT_ALIAS = "mykey";
  private static final String DEFAULT_PASSWORD = "becky123";

  private static String password = DEFAULT_PASSWORD;

  /**
   * Try to import CACERT into the default truststore. First check if a certificate with the default
   * alias exists in the truststore. If it does not, attempt to import it and report on the result.
   *
   * @param args
   * @throws java.io.IOException
   */
  public static void main(String[] args) throws IOException {
    Process keytool;
    OutputStreamWriter stdin;
    BufferedReader stdout, stderr;
    int status;

    handleArgs(args);

    String[] LIST_COMMAND = {KEYTOOL_PATH, "-list", "-alias", DEFAULT_ALIAS,
      "-storepass", password};
    String[] IMPORT_COMMAND = {KEYTOOL_PATH, "-import", "-noprompt",
      "-storepass", password};

    System.out.println("Checking if alias \"" + DEFAULT_ALIAS + "\" exists in the truststore...");
    System.out.println(join(LIST_COMMAND));
    keytool = Runtime.getRuntime().exec(LIST_COMMAND);
    stdout = new BufferedReader(new InputStreamReader(keytool.getInputStream()));
    stderr = new BufferedReader(new InputStreamReader(keytool.getErrorStream()));
    while (true) {
      try {
        status = keytool.waitFor();
        break;
      } catch (InterruptedException e) {
      }
    }
    flushOut(stdout);
    flushOut(stderr);
    if (status == 0) {
      System.out.println();
      System.out.println("The alias \"" + DEFAULT_ALIAS + "\" already exists in the truststore. This is probably what");
      System.out.println("you want. If you need to remove it, use the following command:");
      System.out.println(join(new String[]{KEYTOOL_PATH, "-delete", "-alias", DEFAULT_ALIAS, "-storepass", password}));
      System.exit(0);
    }

    System.out.println();
    System.out.println("Importing certificate...");
    System.out.println(join(IMPORT_COMMAND));
    keytool = Runtime.getRuntime().exec(IMPORT_COMMAND);
    stdin = new OutputStreamWriter(keytool.getOutputStream());
    stdin.write(CACERT);
    stdin.close();
    stdout = new BufferedReader(new InputStreamReader(keytool.getInputStream()));
    stderr = new BufferedReader(new InputStreamReader(keytool.getErrorStream()));
    while (true) {
      try {
        status = keytool.waitFor();
        break;
      } catch (InterruptedException e) {
      }
    }
    flushOut(stdout);
    flushOut(stderr);
    System.out.println("Command finished (exit status " + status + ").");
    System.out.println();

    if (status == 0) {
      System.out.println("The certificate was imported successfully.");
    } else {
      System.out.println("The certificate was not imported. See the error message above.");
    }

    System.exit(status);
  }

  private static void handleArgs(String[] args) {
    int i;

    for (i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-c":
        case "--cacert":
          System.out.print(CACERT);
          System.exit(0);
        case "-h":
        case "--help":
          printHelp();
          System.exit(0);
        case "-p":
        case "--password":
          if (i + 1 < args.length) {
            password = args[i + 1];
            i++;
          } else {
            System.err.println("Option " + args[i] + " requires an argument.");
            System.exit(1);
          } break;
        case "-t":
        case "--truststore":
          System.out.println(TRUSTSTORE_PATH);
          System.exit(0);
        default:
          System.err.println("Invalid option: " + args[i]);
          System.exit(1);
      }
    }
  }

  private static void printHelp() {
    System.out.print(
            "ImportCACert imports the CA certificate needed to make SSL connections to\n"
            + "a MySQL database.\n"
            + "\n"
            + "Options:\n"
            + "  -c, --cacert              Write the CA certificate to standard output\n"
            + "  -h, --help                Display this help message\n"
            + "  -p, --password PASSWORD   Use PASSWORD as the truststore password\n"
            + "                            (default \"" + DEFAULT_PASSWORD + "\")\n"
            + "  -t, --truststore          Display the path to the truststore\n");
  }

  private static void flushOut(BufferedReader reader) throws IOException {
    while (reader.ready()) {
      System.out.println(KEYTOOL_NAME + ": " + reader.readLine());
    }
    System.out.flush();
  }

  private static String join(String[] sa) {
    String s;
    int i;

    s = "";
    if (sa.length > 0) {
      s = sa[0];
      for (i = 1; i < sa.length; i++) {
        s += " " + sa[i];
      }
    }

    return s;
  }
}
