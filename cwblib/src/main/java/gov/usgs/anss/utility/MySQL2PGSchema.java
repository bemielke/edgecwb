  /*
   * This software is in the public domain because it contains materials 
   * that originally came from the United States Geological Survey, 
   * an agency of the United States Department of Interior. For more 
   * information, see the official USGS copyright policy at 
   * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
   */
  package gov.usgs.anss.utility;

  import gov.usgs.anss.edge.RawDisk;
  import java.util.ArrayList;
  import gov.usgs.anss.util.Util;
  import java.io.IOException;
  import java.io.BufferedReader;
  import java.io.StringReader;

  /** This class takes a MySQL schema and converts it to an equivalent in Postgres.  This was part of the
 * Postgres conversion that never happened, we sent to MariaDB instead.
   *
   * @author U.S. Geological Survey <ketchum at usgs.gov>
   */
  public final class MySQL2PGSchema {

    public static void main(String[] args) {
      String line;
      int ipos;
      int iend;
      String table = "";
      String schema = "";
      String owner = "";
      int istart = 0;
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-s")) {
          schema = args[i + 1];
          istart = i + 2;
        }
        if (args[i].equals("-o")) {
          owner = args[i + 1];
          istart = i + 2;
        }
      }
      if (owner.equals("")) {
        owner = schema;
      }
      if (schema.equals("")) {
        Util.prta("You must set a schema!");
        System.exit(1);
      }
      ArrayList<String> keys = new ArrayList<>(10);
      StringBuilder enums = new StringBuilder(10);
      ArrayList<String> foreign = new ArrayList<>(10);
      for (int i = istart; i < args.length; i++) {
        int nline = 0;
        try {
          RawDisk in = new RawDisk(args[i], "rw");
          int len = (int) in.length();
          StringBuilder sb = new StringBuilder(len);

          sb.append("SET client_encoding = 'UTF8';\nSET standard_conforming_strings = off;\n"
                  + "SET check_function_bodies = false;\nSET client_min_messages = warning;\n"
                  + "SET escape_string_warning = off;\n");

          sb.append("DROP SCHEMA ").append(schema).append(" CASCADE;\n");
          sb.append("CREATE SCHEMA ").append(schema).append(";\n");
          sb.append("ALTER SCHEMA ").append(schema).append(" OWNER TO ").append(owner).append(";\n");
          sb.append("SET search_path = ").append(schema).append(", pg_catalog;\n");
          sb.append("ENUMS HERE\n");

          byte[] b = new byte[len];
          in.readBlock(b, 0, len);
          in.close();
          String s = new String(b);
          BufferedReader r = new BufferedReader(new StringReader(s));
          boolean addUpdated = true;
          boolean createdPresent = false;
          while ((line = r.readLine()) != null) {
            nline++;
            line = line.trim();
            if (line.length() == 0) {
              sb.append("\n");
              continue;
            }
            if (line.charAt(0) == '-') {
              sb.append(line).append("\n");
              continue;
            }
            if (line.contains("@saved_cs_client")) {
              continue;
            }
            if (line.contains("character_set_client")) {
              continue;
            }
            if (line.length() < 2) {
              continue;
            }
            if (line.substring(0, 2).equals("/*")) {
              continue;
            }
            line = line.replaceAll("`", "");
            line = line.replaceAll("ID int\\(.*\\) unsigned NOT NULL auto_increment", "id serial PRIMARY KEY");
            line = line.replaceAll("0000-00-00", "1970-01-02");
            if (line.contains("double")) {
              ipos = line.indexOf("(");
              iend = line.indexOf(")");
              if (ipos > 0) {
                line = line.replaceAll("\\" + line.substring(ipos, iend) + "\\)", "");
              }

              line = line.replaceAll("double ", "double precision ");
            }
            if (line.contains("tinyint")) {
              line = line.replaceAll("tinyint ", "int2 ");
            }
            if (line.contains("mediumtext ")) {
              line = line.replaceAll("mediumtext ", "text ");
            }
            if (line.contains("longtext ")) {
              line = line.replaceAll("longtext ", "text ");
            }
            if (line.contains(" datetime ")) {
              line = line.replaceAll(" datetime ", " timestamp ");
            }
            if (line.contains("binary")) {
              line = line.replaceAll("binary", "char");
              line = line.replaceAll("\\\\0", "");
              //Util.prt("Binary="+line);
            }

            if (line.contains("int(") || line.contains("tinying(") || line.contains("int2(")) {
              line = line.replaceAll("tinyint", "int2");
              ipos = line.indexOf("(");
              iend = line.indexOf(")");
              line = line.replaceAll("\\" + line.substring(ipos, iend) + "\\)", "");
              line = line.replaceAll("unsigned ", "");
            }
            if (line.contains("CREATE TABLE")) {
              ipos = line.indexOf("CREATE TABLE");
              iend = line.indexOf("(");
              table = line.substring(ipos + 13, iend).trim();
            }
            if (line.contains("PRIMARY KEY  (ID),")) {
              continue;
            }
            if (line.contains("PRIMARY KEY  (ID)")) {
              continue;
            }
            if (line.substring(0, 8).equals("updated ")) {
              addUpdated = true;
              continue;
            }
            if (line.contains("KEY")
                    && !line.contains("id serial PRIMARY")) {
              //Util.prt("Key Line="+line);
              if (!line.contains("id")
                      || line.contains("")
                      || line.contains("")
                      || line.contains("")
                      || line.contains("")
                      || line.contains("userid")
                      || line.contains("exportid")
                      || line.contains("snweventid")) {
                if (line.endsWith(",")) {
                  line = line.substring(0, line.length() - 1);
                }
                if (line.contains("PRIMARY")) {
                  Util.prt("Band primary line=" + line);
                }
                //Util.prt("Add Key="+line);
                keys.add(line);
                continue;
              }
            }
            if (line.contains("timestamp")) {
              line = line.replaceAll(" on update CURRENT_TIMESTAMP", "");
            }
            if (line.contains("enum")) {
              String[] parts = line.split("\\s");
              ipos = line.indexOf("(");
              iend = line.indexOf(")");
              String types = line.substring(ipos, iend + 1);

              String e = "CREATE TYPE " + table + "_" + parts[0] + " AS ENUM " + types + ";\n";
              line = line.replaceAll("\\" + types.substring(0, types.length() - 1) + "\\)", "");
              line = line.replaceAll("enum", table + "_" + parts[0] + " ");
              enums.append(e);
            }
            if (line.contains("created")) {
              createdPresent = true;
            }
            if (line.substring(0, 4).equals("end ")) {
              line = "ending" + line.substring(3);
            }
            if (line.contains("bin blob")) {
              line = line.replaceAll("bin blob", "bin text");
            }

            if (line.charAt(0) == ')') { // Handle end of table
              if (table.equals("baler")) {
                Util.prt("Pgm");
              }
              while (sb.charAt(sb.length() - 1) == ',' || sb.charAt(sb.length() - 1) == '\n') {
                sb.deleteCharAt(sb.length() - 1);
              }
              sb.append("\n);\n");
              if (table.equals("")) {
                Util.prt("No table found at line=" + nline);
                System.exit(1);
              }
              if (!owner.equals("")) {
                sb.append("ALTER TABLE ").append(schema).append(".").
                        append(table).append(" OWNER TO ").append(owner).append(";\n");
              }
              if (addUpdated) {
                if (createdPresent) {
                  sb.append("ALTER TABLE ").append(schema).append(".").append(table).
                          append(" ALTER COLUMN created SET DEFAULT CURRENT_TIMESTAMP;\n");
                }
                sb.append("ALTER TABLE ").append(schema).append(".").append(table).append(" ADD updated TIMESTAMP;\n");

                sb.append("ALTER TABLE ").append(schema).append(".").append(table).
                        append(" ALTER COLUMN updated SET DEFAULT CURRENT_TIMESTAMP;\n");
                sb.append("CREATE OR REPLACE FUNCTION update_updated_column()\n"
                        + "RETURNS TRIGGER AS '\n"
                        + "   BEGIN\n"
                        + "     NEW.updated = NOW();\n"
                        + "     RETURN NEW;\n"
                        + "   END;\n"
                        + "' LANGUAGE 'plpgsql';\n");

                sb.append("CREATE TRIGGER update_updated_").append(table).append(" BEFORE UPDATE ON ").
                        append(schema).append(".").append(table).append("\n FOR EACH ROW EXECUTE PROCEDURE update_updated_column();\n");
              }
              for (int j = 0; j < keys.size(); j++) {
                line = keys.get(j);
                boolean unique = false;
                if (line.contains("UNIQUE")) {
                  unique = true;
                  line = line.replaceAll("UNIQUE", "").trim();

                }
                line = line.replaceAll("  ", " ").replaceAll("  ", " ").replaceAll("  ", " ");
                String[] parts = line.split("\\s");  // Should be "KEY label (field,...);
                if (parts.length != 3) {
                  Util.prt("KEY is not splitting up right " + line + " " + parts.length + " on " + schema + "." + table + " at " + nline);
                  System.exit(1);
                }
                sb.append("CREATE ").append(unique ? "UNIQUE " : "").append("INDEX ").append(table).
                        append("_").append(parts[1].replaceAll("-", "_")).append(" ON ").append(schema).
                        append(".").append(table).append(" ").append(parts[2].replaceAll(",end\\)", ",ending)")).append(";\n");
                table = "";
                createdPresent = false;
                keys.clear();
                foreign.clear();
              }
            } else {
              String[] p = line.split("\\s");
              if (p[0].equals(p[0].toLowerCase())) {
                line = line.replaceAll(p[0], p[0].toLowerCase());
              }
              if (p[0].endsWith("id") && !p[0].equals("id")) {
                // Potential foreign key
                String t = p[0].replaceAll("id", "");
                Util.prt("create foreign key for " + table + "." + p[0] + " to " + schema + "." + t + ".id");
                foreign.add(p[0]);
              }
              if (Character.isLowerCase(line.charAt(0))) {
                sb.append("   ");
              }
              sb.append(line).append("\n");
            }

          }
          sb.append("CREATE OR REPLACE FUNCTION \"reset_sequence\" (tablename text) RETURNS \"pg_catalog\".\"void\" AS \n"
                  + "$body$\n"
                  + "  DECLARE \n"
                  + "  BEGIN \n"
                  + "  EXECUTE 'SELECT setval( ''' \n"
                  + "  || tablename  \n"
                  + "  || '_id_seq'', ' \n"
                  + "  || '(SELECT id + 1 FROM \"' \n"
                  + "  || tablename  \n"
                  + "  || '\" ORDER BY id DESC LIMIT 1), false)';  \n"
                  + "  END;  \n"
                  + "$body$  LANGUAGE 'plpgsql'\n");
          ipos = sb.indexOf("ENUMS HERE");
          sb.replace(ipos, ipos + 10, enums.toString());

          // Write out the resulting file
          RawDisk out = new RawDisk(args[i] + "pg", "rw");
          out.writeBlock(0, sb.toString().getBytes(), 0, sb.length());
          out.setLength(sb.length());
          out.close();
          in.close();
        } catch (IOException e) {
          Util.prta("IOError=" + e);
          e.printStackTrace();
        }
      }
    }
  }
