/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.sql.*;
import java.util.ArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * This can be used to manage a result set from a SQL query. The typical class that does the query
 * must have a constructor of the for object(java.sql.ResultSet). This constructor must be declared
 * public or it will not show up on the reflection list. This constructor is called from this class
 * to create a array of objects of that class via getObjects(Class).
 *
 * @author ketchum
 * @version 1.00
 */
public final class Query extends Object {

  boolean failed;
  private final StringBuilder text = new StringBuilder(10000);
  ;
  private String query;           // Text string for query
  private ArrayList<RSFields> fields;      // This will be the RSFields list
  private ResultSet rs;         // Result set for query
  private Statement stmt;
  private ResultSetMetaData md; // Place to store the meta-data (field names etc)
  private ArrayList<Object> recs;         // Contains objects of result set
  private int rows;
  private String error;
  private int state;
  private final String tag;
  private EdgeThread par;
  private boolean dbg;

  public void setDebug(boolean t) {
    dbg = t;
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  DBConnectionThread dbconn;

  @Override
  public String toString() {
    return tag + "Query: DBOk=" + (dbconn == null ? "null" : dbconn.isOK()) + " st=" + state + " failed=" + failed + " err=" + error + " rows=" + rows;
  }

  /**
   * return the ResultSet for this Query
   *
   * @return a ResultSet for this Query
   */
  public ResultSet getResultSet() {
    return rs;
  }

  /**
   * return an ArrayList with the fields of the Query
   *
   * @return An Array list with the fields from the Query
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
   * @return an Array list of objects of Class cl one for each row in ResultSet for this Query
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
//      prta(recs.get(rows).toString());
        rows++;
      }
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "SQL error in getObjects create obj");
    } catch (NoSuchMethodException E) {
      prta(tag + "No such constructor found " + cl.getName()
              + "(Result set)");
    } catch (SecurityException E) {
      prta(tag + "GetObjects : Security exception:"
              + E.getMessage());
    } catch (InstantiationException | InvocationTargetException | IllegalAccessException E) {
      prta(tag + "GetObjects " + E.getMessage());
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
            prta(tag + "Query: Update row=" + irow);
            rs.updateRow();
          }
        }
      } else {
        prta(tag + "Query: cannot first in update Objects!");
        Util.exit("cannot first in update objects!");
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
    state = 1;
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
    prta(tag + "Query.getText delimiter=" + delimiter + "&");
    try {
      for (int i = 1; i <= md.getColumnCount(); i++) {
        RSFields rsf = (RSFields) fields.get(i - 1);
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
          RSFields rsf = (RSFields) fields.get(i - 1);
          text.append(rsf.toString(rs)).append(delimiter);
        }
        text.append("\n");
        rows++;
        //if(rows % 1000 == 0) prta(rows+" processed");
      }
      close();
      text.append(tag).append("Total Rows returned = ").append(rows).append("\n");
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, tag + "Query: get text SQL error");
    }
    return text;
  }

  public void close() throws SQLException {
    state = 2;
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
  public Query(DBConnectionThread Cin, String in)
          throws SQLException {
    dbconn = Cin;
    tag = "UNKN";
    prta("Query: created dbconn=" + dbconn + " sql=in");
    executeSQL(in);

  }

  /**
   * Creates new Query using a existing connection and specified statement
   *
   * @param Cin A MySQL JDBC connection
   * @param in String with the SQL command to execute (normally a SELECT).
   * @param tg Logging tag
   * @param parent An edgethread for logging
   * @exception SQLException Can be thrown if connection is bad, network down, or MySQL not running
   */
  public Query(DBConnectionThread Cin, String in, String tg, EdgeThread parent)
          throws SQLException {
    par = parent;
    tag = tg;
    dbconn = Cin;
    prta(tag + "Query: created dbconn=" + dbconn + " sql=in");
    executeSQL(in);

  }

  public final void executeSQL(String in) throws SQLException {
    query = in;
    state = 3;
    error = "";
    if (in.length() < 1) {
      return; // Empty call
    }
    failed = false;
    if (in.indexOf("UPDATE") == 0 || in.indexOf("update") == 0
            || in.indexOf("INSERT") == 0 || in.indexOf("TRUNCATE") == 0
            || in.indexOf("insert") == 0 || in.indexOf("truncate") == 0
            || in.indexOf("DELETE") == 0 || in.indexOf("delete") == 0) {
      for (int itry = 0; itry < 3; itry++) {
        state = 4;
        error = "";
        try {
          if (!dbconn.isOK()) {
            dbconn.reopen();
          }
          state = 5;
          stmt = dbconn.getNewStatement(true);
          state = 6;
          //C.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);  // Use statement for query
          String[] updates = in.split(";");
          for (int i = 0; i < updates.length; i++) {
            if (updates[i].trim().equals("")) {
              continue;
            }
            prta(tag + "Query: updates[" + i + "]=" + updates[i]);
            try {
              int j = stmt.executeUpdate(updates[i]);
              text.append(updates[i]).append("\n").append(j).append(" rows updated.\n");
              prta(tag + " " + j + " rows updated.");
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
          if (!failed) {
            break;    // everything is o.k.
          }
        } catch (SQLException E) {
          Util.SQLErrorPrint(E, "ResultSetTo query failed=" + in);
          failed = true;
          error = "Query: " + in + "\n"
                  + "SQLException : " + E.getMessage() + "\n"
                  + "SQLState     : " + E.getSQLState() + "\n"
                  + "SQLVendorErr : " + E.getErrorCode() + "\n";
          if (itry >= 2) {
            throw E;
          } else {
            dbconn.reopen();
          }
          Util.sleep(15000);
        }
      }
    } else {
      for (int itry = 0; itry < 3; itry++) {
        prta(tag + "Query: try=" + itry);
        state = 7;
        try {
          if (rs != null) {
            rs.close();
          }
          if (stmt != null) {
            stmt.close();
          }
          state = 8;
          if (!dbconn.isOK()) {
            prta(tag + "Query: dbconn reopen started");
            boolean t = dbconn.reopen();
            prta(tag + "Query: dbconn reopen returned " + t);
          }
          state = 9;
          prta(tag + "Query: getNew Statment");
          stmt = dbconn.getNewStatement(false);
          state = 10;
          prta(tag + "Query: executeQuery in=" + in);
          rs = stmt.executeQuery(in);
          state = 11;
          rs.next();                // Position the one and only
          if (rs.isFirst()) {     // is this not an empty set
            state = 12;
            // Now create a ArrayLlist of fields with the meta data
            md = rs.getMetaData();
            state = 13;
            prta(tag + "Query: o.k. columns=" + md.getColumnCount() + " in=" + in);
            fields = new ArrayList<>();
            state = 14;
            for (int i = 1; i <= md.getColumnCount(); i++) {
              fields.add(new RSFields(i, md));
            }
            state = 15;
            prta(tag + "Query: o.k.  break out of loop and return");
            break;      // everything was ok.
          } else {
            prta(tag + "Query: is empty " + in);
            error = "Query : " + in + " Is empty";
            failed = true;
          }
        } catch (SQLException E) {
          state = 16;
          Util.SQLErrorPrint(E, "ResultSetTo query failed=" + in);
          failed = true;
          error = "Query: " + E.toString() + " " + in;
          prta(tag + error);
          if (itry >= 2) {
            throw E;
          }
          dbconn.reopen();
          state = 17;
          Util.sleep(15000);
        }
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
//    prta("format phone in="+in+ " Out="+s);
    return s;
  }

  /**
   * This main converted fiekds in the umpire database when initially loaded. It is a good example
   * of how to use the Query to get an ArrayList of objects from the database, change them and
   * update them.
   *
   * @param args Unused
   */
  public static void main(String args[]) {

  }
}
