/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TwoWay {

  int id;
  int id1;
  int id2;
  String key1;
  String key2;

  public TwoWay(ResultSet rs) throws SQLException {
    id = rs.getInt(1);
    id1 = rs.getInt(2);
    id2 = rs.getInt(3);
    key1 = rs.getString(4);
    key2 = rs.getString(5);
  }

  @Override
  public String toString() {
    return key1 + "-" + key2;
  }

  public int getID() {
    return id;
  }

  public int getID1() {
    return id1;
  }

  public int getID2() {
    return id2;
  }

  public String getKey1() {
    return key1;
  }

  public String getKey2() {
    return key2;
  }
}
