/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.sql.*;
import java.util.ArrayList;
import java.lang.reflect.*;

/**
 * This can be used to manage a result set from a SQL query. The typical class that does the query
 * must have a constructor of the for object(java.sql.ResultSet). This constructor must be declared
 * public or it will not show up on the reflection list. This constructor is called from this class
 * to create a array of objects of that class via getObjects(Class).
 *
 * @author ketchum
 * @version 1.00
 */
public final class QueryOld extends Object {

  boolean failed;
  private final StringBuilder text = new StringBuilder(10000);
  ;
  private String query;           // Text string for query
  private ArrayList<RSFieldsOld> fields;      // This will be the RSFieldsOld list
  private ResultSet rs;         // Result set for query
  private Statement stmt;
  private ResultSetMetaData md; // Place to store the meta-data (field names etc)
  private ArrayList<Object> recs;         // Contains objects of result set
  private int rows;
  private String error;

  Connection C;

  /**
   * return the ResultSet for this QueryOld
   *
   * @return a ResultSet for this QueryOld
   */
  public ResultSet getResultSet() {
    return rs;
  }

  /**
   * return an ArrayList with the fields of the QueryOld
   *
   * @return An Array list with the fields from the QueryOld
   */
  public ArrayList getFields() {
    return fields;
  }

  /**
   * This is so Cool! If you have a this object which controls a ResultSet in rs, this routine will
   * return a ArrayList of objects of type cl(the argument). So if a query is run on Table Games
   * with class Game the equivalent object, you call this to get n ArrayList of Games satisfying the
   * query.
   *
   * @param cl The class on which objects should be built
   * @return an Array list of objects of Class cl one for each row in ResultSet for this QueryOld
   */
  public ArrayList getObjects(Class cl) {
    // This gets the constructor of the type that takes a RS
    if (failed) {
      return (new ArrayList());
    }
    try {

      // Find the constructor with just a ResultSet argurment
      Constructor construct
              = cl.getConstructor(new Class[]{ResultSet.class});

      //  position before 1st record in result set
      rs.beforeFirst();
      rows = 0;
      recs = new ArrayList<>();

      // Loop through all rows calling constructor for new obj
      while (rs.next()) {
        recs.add(rows, construct.newInstance(new Object[]{rs}));
//       Util.prt(recs.get(rows).toString());
        rows++;
      }
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "SQL error in getObjects create obj");
    } catch (NoSuchMethodException E) {
      Util.prt("No such constructor found " + cl.getName()
              + "(Result set)");
    } catch (SecurityException E) {
      Util.prt("GetObjects : Security exception:"
              + E.getMessage());
    } catch (InstantiationException | InvocationTargetException | IllegalAccessException E) {
      Util.prt("GetObjects " + E.getMessage());
      E.printStackTrace();
    }
    return recs;
  }

  /**
   * Update all of the objects created in this class via MySQL
   *
   * @return Number of rows in resultset (whether changed or not by this method)
   */
  public int updateObjects() {
    int irow = 0;
    try {
      if (rs.first()) {
        while (rs.next()) {
          irow++;
          if (rs.rowUpdated()) {
            Util.prt("Update row=" + irow);
            rs.updateRow();
          }
        }
      } else {
        Util.prt("cannot first in update Objects!");
        Util.exit("connot first in update objects!");
      }
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "updateObjecs SQL error");
    }
    return irow;
  }

  public String getText(String delimiter) {
    return getStringBuilder(delimiter).toString();
  }

  /**
   * This builds a text from all of the columns of the current query. Reflection is used to get all
   * of the columns titles. The result set is "rewound" and the entry set positioned creating a line
   * of text for each row in the resultset
   *
   * @param delimiter This string is output between each field
   * @return A string with title row followed by one text line for each row in resultSet
   */
  public StringBuilder getStringBuilder(String delimiter) {
    try {
      if (failed) {
        text.append(error);
        close();
        return text;
      }
      if (md == null) {
        close();
        return text;
      }
    } catch (SQLException e) {
      return text;
    }
    Util.clear(text);
    text.append(delimiter);
    Util.prt("Query.getText delimiter=" + delimiter + "&");
    try {
      for (int i = 1; i <= md.getColumnCount(); i++) {
        RSFieldsOld rsf = (RSFieldsOld) fields.get(i - 1);
        String tmp = rsf.getLabel();
        text.append(Util.centerPad(tmp, rsf.getWidth())).append(delimiter);
      }
      text.append("\n");
      // Read through each row in Resultset snd create a line
      rs.beforeFirst();

      rows = 0;
      //StringBuilder s = new StringBuilder(10000);
      while (rs.next()) {
        text.append(delimiter);
        for (int i = 1; i <= md.getColumnCount(); i++) {
          RSFieldsOld rsf = (RSFieldsOld) fields.get(i - 1);
          text.append(rsf.toString(rs)).append(delimiter);
        }
        text.append("\n");
        rows++;
        //if(rows % 1000 == 0) Util.prt(rows+" processed");
      }
      close();
      text.append("Total Rows returned = ").append(rows).append("\n");
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "Query get text SQL error");
    }
    return text;
  }

  public void close() throws SQLException {
    if (rs != null) {
      rs.close();
    }
    if (stmt != null) {
      stmt.close();
    }
    if (fields != null) {
      fields.clear();
    }
  }

  /**
   * return number of rows in results. Only valid after a call to getText() or getObjects()
   *
   * @return Number of returned rows
   */
  public int getNRows() {
    return rows;
  }

  /**
   * Creates new Query using a existing connection and specified statement
   *
   * @param Cin A MySQL JDBC connection
   * @param in String with the SQL command to execute (normally a SELECT).
   * @exception SQLException Can be thrown if connection is bad, network down, or MySQL not running
   */
  public QueryOld(Connection Cin, String in)
          throws SQLException {
    C = Cin;
    executeSQL(in);

  }

  public final void executeSQL(String in) throws SQLException {
    query = in;
    if (in.length() < 1) {
      return; // Empty call
    }
    failed = false;
    if (in.indexOf("UPDATE") == 0 || in.indexOf("update") == 0
            || in.indexOf("INSERT") == 0 || in.indexOf("TRUNCATE") == 0
            || in.indexOf("insert") == 0 || in.indexOf("truncate") == 0
            || in.indexOf("DELETE") == 0 || in.indexOf("delete") == 0) {
      try {
        stmt = C.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);  // Use statement for query
        String[] updates = in.split(";");
        for (int i = 0; i < updates.length; i++) {
          if (updates[i].trim().equals("")) {
            continue;
          }
          Util.prt("updates[" + i + "]=" + updates[i]);
          try {
            int j = stmt.executeUpdate(updates[i]);
            text.append(updates[i]).append("\n").append(j).append(" rows updated.\n");
            Util.prt(j + " rows updated.");
          } catch (SQLException E) {
            Util.SQLErrorPrint(E, "ResultSetTo update failed=" + updates[i]);
            failed = true;
            error = "Update via Query : " + in + "\n"
                    + "SQLException : " + E.getMessage() + "\n"
                    + "SQLState     : " + E.getSQLState() + "\n"
                    + "SQLVendorErr : " + E.getErrorCode() + "\n";
          }
        }
        stmt.close();
      } catch (SQLException E) {
        Util.SQLErrorPrint(E, "ResultSetTo query failed=" + in);
        failed = true;
        error = "Query : " + in + "\n"
                + "SQLException : " + E.getMessage() + "\n"
                + "SQLState     : " + E.getSQLState() + "\n"
                + "SQLVendorErr : " + E.getErrorCode() + "\n";
        throw E;
      }
    } else {
      try {
        if (rs != null) {
          rs.close();
        }
        if (stmt != null) {
          stmt.close();
        }
        stmt = C.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);  // Use statement for query
        rs = stmt.executeQuery(in);
        rs.next();                // Position the one and only
        if (rs.isFirst()) {     // is this an empty set

          // Now create a ArrayLlist of fields with the meta data
          md = rs.getMetaData();
          Util.prt("Query o.k. " + in + ";\nColumns="
                  + md.getColumnCount());
          fields = new ArrayList<>();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            fields.add(new RSFieldsOld(i, md));
          }
        } else {
          Util.prt("Query is empty " + in);
          error = "Query : " + in + "\n\n Is empty";
          failed = true;
        }
      } catch (SQLException E) {
        Util.SQLErrorPrint(E, "ResultSetTo query failed=" + in);
        failed = true;
        error = "Query : " + in + "\n"
                + "SQLException : " + E.getMessage() + "\n"
                + "SQLState     : " + E.getSQLState() + "\n"
                + "SQLVendorErr : " + E.getErrorCode() + "\n";
        throw E;
      }
    }
  }

  /**
   * This is a helper routine to format a phone number from aaapppdddd to 'aaa ppp-ddd'.
   *
   * @param in Phone number as aaapppddd
   * @return Phone number as aaa ppp-ddd
   */
  static String format_phone(String in) {
    String s;
    if (in.length() == 10) {
      s = in.substring(0, 3) + " " + in.substring(3, 6) + "-"
              + in.substring(6, 10);
    } else {
      s = in;
    }
//    Util.prt("format phone in="+in+ " Out="+s);
    return s;
  }

  /**
   * This main converted fields in the umpire database when initially loaded. It is a good example
   * of how to use the QueryOld to get an ArrayList of objects from the database, change them and
   * update them.
   *
   * @param args Unused
   */
  public static void main(String args[]) {
    JDBConnectionOld jcjbl;
    Connection C;
    User user = new User("dk");
    try {
      jcjbl = new JDBConnectionOld(UC.JDBCDriver(),
              UC.JDBCDatabase());
      C = JDBConnectionOld.getConnection();
      QueryOld ss = new QueryOld(C, "Select * from category");
      Util.prt(ss.getText("|"));
      /*       // Convert phone numbers
        // Do preferred fields
        String s = ump.getPreferredFields();
//        Util.prt("Fwas="+s);
        StringTokenizer tk = new StringTokenizer(s,", ");
        s = "";
        while (tk.hasMoreTokens()) {
          String token = tk.nextToken();
          if(token.equalsIgnoreCase("GO")) token="GD";
          if(token.equalsIgnoreCase("LK")) token="LW";
          s += token+" ";
        }
//        Util.prt("Fnow="+s);
        ump.setPreferredFields(s);
        
        // Do qualifieds
        long quals = UC.stringToQualified(((Umpire)objs.get(i)).getQualText());
        s = UC.qualifiedToString(quals);
        Util.prt("qual in="+ump.getQualText()+" out="+s);
        ump.setQualified(quals);
        
        // Do birth date
        Date d = ump.getBirthDate();
        s = d.toString();
        tk = new StringTokenizer(s,"-");
        String token = tk.nextToken();
        int iy = Integer.parseInt(token);
        if(iy < 1000) iy = 0;
//        Util.prt("birth year="+iy);
 //       ump.setBirthYear(iy);
        
        Util.prt("i="+i+"\n"+ump.toString());
        ump.updateRecord();
      } */

    } catch (SQLException E) {
      Util.prt("SQL MAIN exception");
    }
  }
  /*  
  public static void main(String args[]) {
    JDBConnectionOld jcjbl;
    Connection C;
    User user=new User("dkt");
    try {
      jcjbl = new JDBConnectionOld(UC.JDBCDriver(),
        UC.JDBCDatabase());
      C = jcjbl.getConnection();
       QueryOld ss = new QueryOld(C,
       "Select * from games where LOCATE('14301',game) != 0");
       Util.prt(ss.getText("|"));
       Game tmp = new Game("101","343","home","visit");

       ArrayList objs = ss.getObjects(Game.class);
      for(int i=0; i<objs.size(); i++) 
        Util.prt(objs.get(i).toString());
    } catch (SQLException E) {
      Util.prt("SQL MAIN exception");
    }
  }*/
}
