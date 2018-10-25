/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * ImportCACert.java
 *
 * Created on September 24, 2007, 4:26 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.edge.config;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.Subprocess;
import java.io.*;

public class ImportCACert
{
  private static final String CACERT = "-----BEGIN CERTIFICATE-----\n" +
"MIIJ+jCCB+KgAwIBAgIQSeV7u0WVHrFBqkjcKiEnEzANBgkqhkiG9w0BAQsFADAV\n" +
"MRMwEQYDVQQDEwpET0lSb290Q0EyMB4XDTE2MDQyNjE4MjE1MFoXDTM2MDQyNjE4\n" +
"MjE1MFowFTETMBEGA1UEAxMKRE9JUm9vdENBMjCCAiIwDQYJKoZIhvcNAQEBBQAD\n" +
"ggIPADCCAgoCggIBANTHy0AvCFT0CZOsktj3kpFfFJgrhiS5haBK7DvYYpbZoaWa\n" +
"jOpldnvoqnd1bOJUJ9jUwxZERo27FJHZ8HSdU8ac63hdK1N6t1OrlQOjgs/Kn0LY\n" +
"b7xOf6iDBfhdhXcjpq2KZBzVi0tWuyBPhDmZyTLqZ5UREy1mV3/p1pJ3Hx5lPziH\n" +
"Fid+wILxe2fk+N1ExY/GA+cgdsSCP4kp4aBxXLNTq+oMu/NahyF+NmcbqUk3xh+v\n" +
"U1UA7h35b8kjd/3Kx2Bv1EDveWbzaS7sn8T3OVnU6n9UObUcqaoJOXF3PDdQqPIh\n" +
"YVLT8/s15YaUznTe7jc46YjnqZPaJJGbDir/m03QR8qWi0qMhLjTTfPYe4DFYa27\n" +
"4e8sTeK0DTSpUMBhQqagRQEFcYRd6QaZ4wfj+8zwX7EUha9jOKrMALvxRurkEqsa\n" +
"m9NZntaHlSkRjQAZ562TUYowBpb841O0v0c9+i8SM6D9kRVV+NIj3StPNkQG7qlc\n" +
"+PhF5YA4jYAifZ7AtWraLeopTPonfX0avWbIt5ryy4Y+sISwsg4HZ+rdNrJq9MUu\n" +
"YWDbdO/lRclnFJ64VmD0rH7Fuef7CDiQvwn0NPJHQoU6h/zHnfOEIlh44h+0uy+R\n" +
"lEp41vrb9mA/a7ZZEohcJroQ6JL1Z8b+KLY47ryuqneLklCVTGbMNGZxusOtAgMB\n" +
"AAGjggVEMIIFQDALBgNVHQ8EBAMCAYYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4E\n" +
"FgQUv4YryvNsbT5fHDtOTtiN52rHak8wEAYJKwYBBAGCNxUBBAMCAQAwggTtBgNV\n" +
"HSAEggTkMIIE4DCCAg8GCWCGSAFlAwIBEzCCAgAwMAYIKwYBBQUHAgEWJGh0dHA6\n" +
"Ly9wa2kyLmRvaS5uZXQvbGVnYWxwb2xpY3kuYXNwADCCAcoGCCsGAQUFBwICMIIB\n" +
"vB6CAbgAQwBlAHIAdABpAGYAaQBjAGEAdABlACAAaQBzAHMAdQBlAGQAIABiAHkA\n" +
"IAB0AGgAZQAgAEQAZQBwAGEAcgB0AG0AZQBuAHQAIABvAGYAIAB0AGgAZQAgAEkA\n" +
"bgB0AGUAcgBpAG8AcgAgAGEAcgBlACAAbwBuAGwAeQAgAGYAbwByACAAaQBuAHQA\n" +
"ZQByAG4AYQBsACAAdQBuAGMAbABhAHMAcwBpAGYAaQBlAGQAIABVAFMAIABHAG8A\n" +
"dgBlAHIAbgBtAGUAbgB0ACAAdQBzAGUAIABhAGwAbAAgAG8AdABoAGUAcgAgAHUA\n" +
"cwBlACAAaQBzACAAcAByAG8AaABpAGIAaQB0AGUAZAAuACAAVQBuAGEAdQB0AGgA\n" +
"bwByAGkAegBlAGQAIAB1AHMAZQAgAG0AYQB5ACAAcwB1AGIAagBlAGMAdAAgAHYA\n" +
"aQBvAGwAYQB0AG8AcgBzACAAdABvACAAYwByAGkAbQBpAG4AYQBsACwAIABjAGkA\n" +
"dgBpAGwAIABhAG4AZAAvAG8AcgAgAGQAaQBzAGMAaQBwAGwAaQBuAGEAcgB5ACAA\n" +
"YQBjAHQAaQBvAG4ALjCCAskGCmCGSAFlAwIBEwEwggK5MDUGCCsGAQUFBwIBFilo\n" +
"dHRwOi8vcGtpMi5kb2kubmV0L2xpbWl0ZWR1c2Vwb2xpY3kuYXNwADCCAn4GCCsG\n" +
"AQUFBwICMIICcB6CAmwAVQBzAGUAIABvAGYAIAB0AGgAaQBzACAAQwBlAHIAdABp\n" +
"AGYAaQBjAGEAdABlACAAaQBzACAAbABpAG0AaQB0AGUAZAAgAHQAbwAgAEkAbgB0\n" +
"AGUAcgBuAGEAbAAgAEcAbwB2AGUAcgBuAG0AZQBuAHQAIAB1AHMAZQAgAGIAeQAg\n" +
"AC8AIABmAG8AcgAgAHQAaABlACAARABlAHAAYQByAHQAbQBlAG4AdAAgAG8AZgAg\n" +
"AHQAaABlACAASQBuAHQAZQByAGkAbwByACAAbwBuAGwAeQAuACAARQB4AHQAZQBy\n" +
"AG4AYQBsACAAdQBzAGUAIABvAHIAIAByAGUAYwBlAGkAcAB0ACAAbwBmACAAdABo\n" +
"AGkAcwAgAEMAZQByAHQAaQBmAGkAYwBhAHQAZQAgAHMAaABvAHUAbABkACAAbgBv\n" +
"AHQAIABiAGUAIAB0AHIAdQBzAHQAZQBkAC4AIABBAGwAbAAgAHMAdQBzAHAAZQBj\n" +
"AHQAZQBkACAAbQBpAHMAdQBzAGUAIABvAHIAIABjAG8AbQBwAHIAbwBtAGkAcwBl\n" +
"ACAAbwBmACAAdABoAGkAcwAgAGMAZQByAHQAaQBmAGkAYwBhAHQAZQAgAHMAaABv\n" +
"AHUAbABkACAAYgBlACAAcgBlAHAAbwByAHQAZQBkACAAaQBtAG0AZQBkAGkAYQB0\n" +
"AGUAbAB5ACAAdABvACAAYQAgAEQAZQBwAGEAcgB0AG0AZQBuAHQAIABvAGYAIAB0\n" +
"AGgAZQAgAEkAbgB0AGUAcgBpAG8AcgAgAFMAZQBjAHUAcgBpAHQAeQAgAE8AZgBm\n" +
"AGkAYwBlAHIALjANBgkqhkiG9w0BAQsFAAOCAgEAF/q4Z2mRTIYJMu5mzlWsbV4o\n" +
"gGQJ9YcSdUZRq2vzINJCpGDXstAIE81Pfz/Fna98KOkjEB8XGXVUGQf07c9ylGJS\n" +
"XFoBwcN8GgOuys5iiP9/yd2yLHB8rBb8pu9RForl9RoTsYY8nFuOOtl9o2EfB/1O\n" +
"PbRYkfHhhqrfvvHdvDKWPmT+ZhaliWJrg2my432yqBqPePjqMZSl4sxiPYi9WicU\n" +
"UWYdJpxQlys3igICD4GXOcSh316jfaqfN8+9jps+lgO7rqOA41B8fU9Gwi4B8jjx\n" +
"Tw0pgvbuebwwL5IQwrsGcA8rFfRPR6CaSY5v3XXqTMbCXyYjNK1/44I9MoFFaFPc\n" +
"e3cqZ5cQ+lCoW3UE0SLNZb3YKh28ES/Gi5CO0Bq5P8QVLRJQL5xOaSzV9blszHv5\n" +
"okR+lkSsVo2QzR/mzFD7lXtwznkd/uak0hripTB7MtZenBzoQ8zAgjgw5TXjRSAZ\n" +
"goWiJTAg+YTKclhJ7Cfg/m4XeCxzNgz/pU1XEdBF2Ngvp3C9M5CSBcqzb234uiFF\n" +
"SyvJl/6erDTkQ5dLrnSnsJIw1ZS/XG/Fi41u8il0piLc5depTLn9qiWf29BRBEtG\n" +
"xwFKSmqlRWsClj/zADirBTjcctw7ajPMkRpebgn+Bzv1eWDx4+OolQuR/a45644Q\n" +
"GHVtIa/kVEl2DE0WcUw=\n" +
"-----END CERTIFICATE-----\n";
/*  private static final String CACERT =
"-----BEGIN CERTIFICATE-----\n" +
"MIIDUjCCArugAwIBAgIJAMuNyvXrQvTuMA0GCSqGSIb3DQEBBAUAMHoxCzAJBgNV\n" +
"BAYTAlVTMREwDwYDVQQIEwhDb2xvcmFkbzEPMA0GA1UEBxMGR29sZGVuMS8wLQYD\n" +
"VQQKEyZOYXRpb25hbCBFYXJ0aHF1YWtlIEluZm9ybWF0aW9uIENlbnRlcjEWMBQG\n" +
"A1UEAxMNRGF2aWQgRmlmaWVsZDAeFw0wNTA1MjMyMjA5MTdaFw0wNTA2MjIyMjA5\n" +
"MTdaMHoxCzAJBgNVBAYTAlVTMREwDwYDVQQIEwhDb2xvcmFkbzEPMA0GA1UEBxMG\n" +
"R29sZGVuMS8wLQYDVQQKEyZOYXRpb25hbCBFYXJ0aHF1YWtlIEluZm9ybWF0aW9u\n" +
"IENlbnRlcjEWMBQGA1UEAxMNRGF2aWQgRmlmaWVsZDCBnzANBgkqhkiG9w0BAQEF\n" +
"AAOBjQAwgYkCgYEAvxkc4WSEVgVoPHCONGGq5Z83Qd6tskUuyQZB1tv4RNAhy+S8\n" +
"yshGkQBOw9a766hYdjCngvFn0hECyrTbpzYuUMA8ewOOasb168t/itos0x2D8ww0\n" +
"sIkiSCSdxLvyHJsSr2lEUr2SmBl8uCHHEEB7kzVnC/kD3vHHOCLps0trsVUCAwEA\n" +
"AaOB3zCB3DAdBgNVHQ4EFgQUKXwAU9yjW5ZKivdcOMuiS9ngrDgwgawGA1UdIwSB\n" +
"pDCBoYAUKXwAU9yjW5ZKivdcOMuiS9ngrDihfqR8MHoxCzAJBgNVBAYTAlVTMREw\n" +
"DwYDVQQIEwhDb2xvcmFkbzEPMA0GA1UEBxMGR29sZGVuMS8wLQYDVQQKEyZOYXRp\n" +
"b25hbCBFYXJ0aHF1YWtlIEluZm9ybWF0aW9uIENlbnRlcjEWMBQGA1UEAxMNRGF2\n" +
"aWQgRmlmaWVsZIIJAMuNyvXrQvTuMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEE\n" +
"BQADgYEAjBRS7HnCiSEb4v64RwZUMzrpraE+hzDVAv8cuVxSrAVO9a9BNS2/5wjv\n" +
"lGmagZAgxgerskPvWvdSThK1miuNTdAPM2WanP+YlM4JcW4ZYXqM3uqLf42Ajlyg\n" +
"T8LDcyi51qwD0KN8fdo0qwdzYsQAbAcor7Ttq1EPs6BDmrV9J14=\n" +
"-----END CERTIFICATE-----\n";*/
  private static final String KEYTOOL_NAME = "keytool";
  private static final String KEYTOOL_PATH =
          new File(new File(System.getProperty("java.home"), "bin"), KEYTOOL_NAME).getPath();
  private static final String TRUSTSTORE_PATH =
          new File(System.getProperty("user.dir"), ".keystore").getPath();
  private static final String DEFAULT_ALIAS = "DOIRoot2";
  private static final String DEFAULT_PASSWORD = "changeit";

  private static String password = DEFAULT_PASSWORD;

  /**
   * Try to import CACERT into the default truststore. First check if a
   * certificate with the default alias exists in the truststore. If it does
   * not, attempt to import it and report on the result.
   */
  public static void main(String[] args) throws IOException
  {
    Process keytool;
    OutputStreamWriter stdin;
    BufferedReader stdout, stderr;
    int status;

    handleArgs(args);
    File f = new File(System.getProperty("java.home")+Util.FS+"lib"+Util.FS+"security"+Util.FS+"cacerts");
    if(f.exists()) {
      try {
        Subprocess sp = new Subprocess("cp "+f.getAbsolutePath()+" "+System.getProperty("user.home")+Util.FS+".keystore");
        sp.waitFor();
      }
      catch(InterruptedException e) {
        
      }
    }
    String[] LIST_COMMAND = { KEYTOOL_PATH, "-list", "-alias", DEFAULT_ALIAS,
            "-storepass", password };
    String[] IMPORT_COMMAND = { KEYTOOL_PATH, "-import", "-noprompt",
            "-storepass", password };

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
      System.out.println(join(new String[] { KEYTOOL_PATH, "-delete", "-alias", DEFAULT_ALIAS, "-storepass", password }));
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
    System.out.println("Command finished (exit status " + status +  ").");
    System.out.println();

    if (status == 0) {
      System.out.println("The certificate was imported successfully.");
    } else {
      System.out.println("The certificate was not imported. See the error message above.");
    }

    System.exit(status);
  }

  private static void handleArgs(String[] args)
  {
    int i;

    for (i = 0; i < args.length; i++) {
      if (args[i].equals("-c") || args[i].equals("--cacert")) {
        System.out.print(CACERT);
        System.exit(0);
      } else if (args[i].equals("-h") || args[i].equals("--help")) {
        printHelp();
        System.exit(0);
      } else if (args[i].equals("-p") || args[i].equals("--password")) {
        if (i + 1 < args.length) {
          password = args[i + 1];
          i++;
        } else {
          System.err.println("Option " + args[i] + " requires an argument.");
          System.exit(1);
        }
      } else if (args[i].equals("-t") || args[i].equals("--truststore")) {
        System.out.println(TRUSTSTORE_PATH);
        System.exit(0);
      } else {
        System.err.println("Invalid option: " + args[i]);
        System.exit(1);
      }
    }
  }

  private static void printHelp()
  {
    System.out.print(
"ImportCACert imports the CA certificate needed to make SSL connections to\n" +
"a MySQL database.\n" +
"\n" +
"Options:\n" +
"  -c, --cacert              Write the CA certificate to standard output\n" +
"  -h, --help                Display this help message\n" +
"  -p, --password PASSWORD   Use PASSWORD as the truststore password\n" +
"                            (default \"" + DEFAULT_PASSWORD + "\")\n" +
"  -t, --truststore          Display the path to the truststore\n");
  }

  private static void flushOut(BufferedReader reader) throws IOException
  {
    while (reader.ready())
      System.out.println(KEYTOOL_NAME + ": " + reader.readLine());
    System.out.flush();
  }

  private static String join(String[] sa)
  {
    String s;
    int i;

    s = "";
    if (sa.length > 0) {
      s = sa[0];
      for (i = 1; i < sa.length; i++)
        s += " " + sa[i];
    }

    return s;
  }
}
