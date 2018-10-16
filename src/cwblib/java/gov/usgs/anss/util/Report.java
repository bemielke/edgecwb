/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.sql.SQLException;
import java.sql.ResultSet;

/**
 * Report.java - Data base access to the reports which basically is a title and the SQL query to
 * generate a report.
 *
 * Created on July 8, 2002, 1:23 PM
 *
 * @author ketchum
 */
public class Report //implements Comparator
{

  /**
   * Creates a new instance of Report
   */
  int ID;
  String Report;

  // All fields of file go here
  //  double longitude
  String sql;

  /**
   * Construct this record from the positioned ResultSet
   *
   * @exception SQLException Thrown if result set is not positioned, network or database down
   * @param rs a positioned result set
   */
  public Report(ResultSet rs) throws SQLException {
    makeReport(rs.getInt("ID"), rs.getString("Report") // ,rs.getDouble(longitude)
            ,
             rs.getString("sql")
    );
  }

  /**
   * create a Report from only data variables (usually for debugging). Do not associated it with a
   * databaserecord
   *
   * @param inID Database ID (normally zero)
   * @param loc The name of the report
   * @param sqlin The SQL query that generates this report
   */
  public Report(int inID, String loc, //, double lon
          String sqlin
  ) {
    makeReport(inID, loc, sqlin //, lon
    );
  }

  private void makeReport(int inID, String loc, //, double lon
          String sqlin
  ) {
    ID = inID;
    Report = loc;     // longitude = lon
    sql = sqlin;

    // Put asssignments to all fields from arguments here
  }

  /**
   * Return the title for toString2(normally used in JComboBoxes).
   *
   * @return The title of the report
   */
  public String toString2() {
    return Report;
  }

  /**
   * return the report title as the string representation
   *
   * @return The title of the report
   *
   */
  @Override
  public String toString() {
    return Report;
  }
  // getter

  /**
   * Get MySQL unique ID for this report
   *
   * @return The MySQL unique ID
   */
  public int getID() {
    return ID;
  }

  /**
   * return the title of the report
   *
   * @return String with title of report
   */
  public String getReport() {
    return Report;
  }

}
