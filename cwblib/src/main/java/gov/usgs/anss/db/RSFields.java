/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.anss.util.Util;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;

/**
 * This contains the information about the fields in a resultSet. Basically the metadata for the
 * dictionary. This includes the information on column width, precision of floating values, scale,
 * column #, field name, field type.
 *
 * @author ketchum
 * @version 1.00
 */
public final class RSFields extends Object {

  String label;
  String name;
  int type;
  String typeName;
  int precision;
  int scale;
  int column;
  int width;
  String table;
  String schema;

  /**
   * Set the width
   *
   * @param i Width to set
   */
  public void setWidth(int i) {
    width = i;
  }

  /**
   * set the Precision
   *
   * @param i The precision to set
   */
  public void setPrecision(int i) {
    precision = i;
  }

  /**
   * set the Scale
   *
   * @param i The scale to set
   */
  public void setScale(int i) {
    scale = i;
  }

  /**
   * set the Label of the column
   *
   * @param lab The label to set
   */
  public void setLabel(String lab) {
    label = lab;
  }

  /**
   * Return the label
   *
   * @return String with this label
   */
  public String getLabel() {
    return label;
  }

  /**
   * return the name of the field
   *
   * @return String with name of the field
   */
  public String getName() {
    return name;
  }

  /**
   * return the table on which this query was done.
   *
   * @return string with Table name
   */
  public String getTable() {
    return table;
  }

  /**
   * Return the MySQL type of the field (see static data in java.sql.Types)
   *
   * @return int with data type
   */
  public int getType() {
    return type;
  }

  /**
   * return the MySQL type as a string (from static data in java.sql.Types)
   *
   * @return The type as a string
   */
  public String getTypeName() {
    return typeName;
  }

  /**
   * return the precision
   *
   * @return the precision
   */
  public int getPrecision() {
    return precision;
  }

  /**
   * return the scale from the Mysql dictionary
   *
   * @return Scale per the MySQL dictionary
   */
  public int getScale() {
    return scale;
  }

  /**
   * return the column number assigned to this column
   *
   * @return the column number assigned at construction
   */
  public int getColumn() {
    return column;
  }

  /**
   * return width from MySQL dictionary
   *
   * @return Width from MySQL dictionary
   */
  public int getWidth() {
    return width;
  }

  /**
   * Creates new RSFields with information about one column of a ResultSetMetaData
   *
   * @param columnin integer column number of the metadata
   * @param md The ResultSetMetaData to decode and store
   */
  public RSFields(int columnin, ResultSetMetaData md) {
    try {
      column = columnin;
      label = md.getColumnLabel(column);
      name = md.getColumnName(column);
      type = md.getColumnType(column);
      typeName = md.getColumnTypeName(column);
      precision = md.getPrecision(column);
      scale = md.getScale(column);
      width = md.getColumnDisplaySize(column);
      if (width < label.length()) {
        width = label.length();
      }
      if (width > 100000) {
        width = label.length() + 1;
      }
      if (width > 80) {
        width = 80;
      }
      table = md.getTableName(column);
      schema = md.getSchemaName(column);
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "Getting RSField meta data");
    }
  }

  public String toStringDump() {
    return "col=" + column + " label=" + label + " name=" + name
            + " type=" + type + "/" + typeName + " prec.scale=" + precision + "/" + scale + " width=" 
            + width + " table=" + table + " schema=" + schema;
  }

  /**
   * Convert the column represented by this RSFields to a string given a result set where this
   * column resides. It will output the data based on the width and precision information in the
   * dictionary
   *
   * @param rs The result set we which to decode this column from
   * @return A string representation of this column in the input ResultSet
   */
  public String toString(ResultSet rs) {
    String s = "";
    if (typeName.equalsIgnoreCase("Text")) {
      type = Types.VARCHAR;
    }
    if (typeName.equalsIgnoreCase("Varchar")) {
      type = Types.VARCHAR;
    }
    try {
      switch (type) {
        case Types.VARCHAR:
        case Types.CHAR:
          //case Types.TEXT:
          //s = Util.getString(rs,name);
          s = Util.getString(rs, column);
          s = Util.rightPad(s, width).toString();
          break;
        case Types.DATE:
          //Date d = Util.getDate(rs,name);
          Date d = Util.getDate(rs, column);
          s = Util.dateToString(d);
          s = Util.leftPad(s, width).toString();
          break;
        case Types.DECIMAL:
        case Types.DOUBLE:
          //double dbl = Util.getDouble(rs,name);
          double dbl = Util.getDouble(rs, column);
          s = "" + dbl;
          s = Util.leftPad(s, width).toString();
          break;
        case Types.REAL:
        case Types.FLOAT:
          //float flt = Util.getFloat(rs,name);
          float flt = Util.getFloat(rs, column);
          s = "" + flt;
          s = Util.leftPad(s, width).toString();
          break;
        case Types.INTEGER:
          //int i = Util.getInt(rs,name);
          int i = Util.getInt(rs, column);
          s = "" + i;
          s = Util.leftPad(s, width).toString();
          break;
        case Types.BIGINT:
          //long l = Util.getLong(rs,name);
          long l = Util.getLong(rs, column);
          s = "" + l;
          s = Util.leftPad(s, width).toString();
          break;
        case Types.SMALLINT:
          //short is = Util.getShort(rs,name);
          short is = Util.getShort(rs, column);
          s = "" + is;
          s = Util.leftPad(s, width).toString();
          break;
        case Types.TIME:
          //Time t = Util.getTime(rs,name);
          Time t = Util.getTime(rs, column);
          s = Util.timeToString(t);
          s = Util.leftPad(s, width).toString();
          break;
        case Types.TIMESTAMP:
          //Timestamp ts = Util.getTimestamp(rs,name);
          Timestamp ts = Util.getTimestamp(rs, column);
          s = ts.toString();
          s = Util.leftPad(s, width).toString();
          break;
        case Types.TINYINT:
          //byte b = Util.getByte(rs,name);
          byte b = Util.getByte(rs, column);
          s = "" + b;
          s = Util.leftPad(s, width).toString();
          break;
        case Types.NUMERIC:
        case Types.NULL:
        case Types.VARBINARY: // certain functioned columns come this way in mysql
          //byte [] bb = rs.getBytes(column);
          s = Util.getString(rs, column);
          break;
        default:
          // See if this is a PostgreSQL enumc
          try {
            Object pgObject = rs.getObject(column);
            Method valueMethod = pgObject.getClass().getMethod("getValue");
            s = (String) valueMethod.invoke(pgObject);
            return Util.leftPad(s, width).toString();
          } catch (IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException |
                  InvocationTargetException | SQLException e) {
            Util.prt("Type is unknown and not PG Object e=" + e);
          }
          Util.prt("Type not implementd type=" + type + " typeName=" + typeName);
          new RuntimeException("Type not implemented type=" + type + " typeName=" + typeName).printStackTrace();
          s = Util.leftPad("*Type Unknonw", width).toString();
        //Util.exit(0);
      }
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "SQL error getting strings in RSField");
      E.printStackTrace();
    }
    return s;
  }

}
