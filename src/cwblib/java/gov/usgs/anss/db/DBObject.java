/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.anss.util.Util;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.Date;

/**
 * This routine basically forms an object wrapping a ResultSet for one record from a JDBC table.
 * When a new one is created, it is read back using the created and created_by fields (unlikely
 * anyone could create more than one record in one table at precisely the right time). It then
 * behaves just like one that was read. The getter and setter methods by data type are use to
 * manipulate the data. This routine requires the following in the creation of the table : CREATE
 * TABLE name? ( ID INT(6) UNSIGNED NOT NULL AUTO_INCREMENT, PRIMARY KEY (ID),
 *
 * updated TIMESTAMP, created_by INT(6), created TIMESTAMP);
 *
 * The created and created_by will be set when this record is first written. updated will change
 * automatically when changes are made (at least in MySQL) ID is the unique primary key. Created on
 * July 1, 2002
 *
 * @author David Ketchum
 * @version 1.00 (base version)
 */
public final class DBObject extends Object {

  /*

//	Put  the  data  base  mapped  definitions  here
  
   */
  private static boolean dbg;
  private static boolean dbgdet;
  private int ID;			//  Unique  record  name

//  Non-persistent  data
  private Connection C;
  private DBConnectionThread connThread;
  private String table;               // The table this is a member of
  private String schema;
  private String field;
  private String value;
  private String addField = null;
  private int addId;
  private ResultSet rs;
  private long resultSetAge;          // The time this result set was mode
  private Statement stmt;
  private int row;
  private boolean modified;          //  if  Set,  some  field  has  been  modified
  private boolean newRecord;        //  If  set,  then  the  constructed  record  is
  //  Not  already  in  the  data  base
  private String selectClause = "SELECT * FROM ";
  private int autoInKeyFromRS;

  /**
   * create a DBObject for ID in file tab. This is the normal way of getting a single $DBObject for
   * use
   *
   * @param Cin A MySQL connection
   * @param tab The table to look up ID in
   * @param IDin The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String tab, int IDin) throws SQLException {
    if (dbgdet) {
      Util.prt("DBO: cons1:" + tab + " id=" + IDin);
    }
    makeDBObject(Cin, tab, "id", "" + IDin);
    dbg = false;
    dbgdet = false;

  }

  /**
   * create a DBObject for ID in file tab. This is the normal way of getting a single $DBObject for
   * use
   *
   * @param Cin A MySQL connection
   * @param sch A schema if schemas are being used
   * @param tab The table to look up ID in
   * @param IDin The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String sch, String tab, int IDin) throws SQLException {
    if (dbgdet) {
      Util.prt("DBO: cons1:" + tab + " id=" + IDin);
    }
    schema = sch;
    makeDBObject(Cin, tab, "id", "" + IDin);
    dbg = false;
    dbgdet = false;

  }

  /**
   * create a DBObject for ID in file tab. This is the normal way of getting a single $DBObject for
   * use
   *
   * @param Cin A MySQL connection
   * @param select The SQL statement to execute to build this object
   * @param sch A schema if schemas are being used
   * @param tab The table to look up ID in
   * @param IDin The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String select, String sch, String tab, int IDin) throws SQLException {
    if (dbgdet) {
      Util.prt("DBO: cons1:" + tab + " id=" + IDin);
    }
    selectClause = select;
    schema = sch;
    makeDBObject(Cin, tab, "id", "" + IDin);
    dbg = false;
    dbgdet = false;

  }

  /**
   * used for creating DBObject objects which are new rows in the table. when a new one is created,
   * it is read back using the created and created_by fields (unlikely anyone could create more than
   * one record in one table at precisely the right time). It then behaves just like one that was
   * read. Normally, all of the fields in the table are set by calls to the "update????()" routines
   * and then the record made permanent with a call to "updateRecord()".
   *
   * @param Cin The Mysql connection
   * @param tab The table in which to create a new record
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String tab) throws SQLException {
    dbg = false;
    dbgdet = false;
    if (dbgdet) {
      Util.prt("DBO:cons:" + tab);
    }
    makeDBObject(Cin, tab, "id", "0");
//    Util.prt(table + ": DBObject creat NEW"+ID);
  }

  /**
   * used for creating DBObject objects which are new rows in the table. when a new one is created,
   * it is read back using the created and created_by fields (unlikely anyone could create more than
   * one record in one table at precisely the right time). It then behaves just like one that was
   * read. Normally, all of the fields in the table are set by calls to the "update????()" routines
   * and then the record made permanent with a call to "updateRecord()".
   *
   * @param Cin The Mysql connection
   * @param sch The schema if schemas are being used
   * @param tab The table in which to create a new record
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String sch, String tab) throws SQLException {
    dbg = false;
    dbgdet = false;
    schema = sch;
    if (dbgdet) {
      Util.prt("DBO:cons:" + tab);
    }
    makeDBObject(Cin, tab, "id", "0");
//    Util.prt(table + ": DBObject creat NEW"+ID);
  }

  /**
   * This is just a convenient (or stupid) place to put the a generally QUERY in a table. This
   * returns a result set based on a query of the form "SELECT * FROM tab WHERE ID=IDin;". The
   * returned result set is already positioned to the ID (no need to use rs.next())
   *
   * @param Cin Connection to MySQL
   * @param tab The table for the select statement
   * @param IDin The ID in the table for this single row
   * @return A result set already positioned for the row
   * @exception SQLException Usually the network or Database is down
   */
  public static ResultSet Query(DBConnectionThread Cin, String tab, int IDin) throws SQLException {
    if (dbgdet) {
      Util.prt("DBO:query():" + tab + " id=" + IDin);
    }
    ResultSet rs = Cin.getNewStatement(true).executeQuery("SELECT * FROM " + tab
            + " WHERE ID=" + Util.sqlEscape(IDin));
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
    rs.next();                        //  Position  the  one  and  only
    return rs;
  }

  /**
   * This is just a convenient (or stupid) place to put the a generally QUERY in a table. This
   * returns a result set based on a query of the form "SELECT * FROM tab WHERE field=IDin;". The
   * returned result set is already positioned to the first row returned. Note : this routine could
   * return more than one row which could be access by ResultSet.next();
   *
   * @param Cin Connection to MySQL
   * @param tab The table for the select statement
   * @param field The field on which the WHERE clause is to run
   * @param IDin The ID in the table for this single row
   * @return A result set already positioned for the row
   * @exception SQLException Usually the network or Database is down
   */
  public static ResultSet Query(DBConnectionThread Cin, String tab, String field, int IDin) throws SQLException {
    if (dbgdet) {
      Util.prt("DBO:query():" + tab + " fld=" + field + " id=" + IDin);
    }
    ResultSet rs = Cin.getNewStatement(true).executeQuery("SELECT * FROM " + tab
            + " WHERE " + field + "=" + Util.sqlEscape(IDin));
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
    rs.next();                        //  Position  the  one  and  only
    return rs;
  }

  public int getMaxID() {
    try {
      ResultSet rstmp = connThread.getNewStatement(true).executeQuery("SELECT max(id) FROM " + table);
      if (rstmp.next()) {
        int max = rstmp.getInt(1);
        rstmp.close();
        return max;
      }
    } catch (SQLException expected) {
    }   // Nothing we can do!
    return 101;
  }

  //
  /**
   * create a DBObject "where field=id". Note : The query might return more than one row, but this
   * object will only act on the first row returned. There is no checking to see if this is true. It
   * might also create a new row if the "Where field=id" does not match any rows in the table. Use
   * "isNew()" to check this out.
   *
   * @param Cin A MySQL connection
   * @param sch Schema name
   * @param tab The table to look up ID in
   * @param field The field name in which to find the ID
   * @param ID The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String sch, String tab, String field, String ID) throws SQLException {
    dbgdet = false;
    dbg = false;
    schema = sch;
    if (dbgdet) {
      Util.prt("DBO:cons:" + tab + " fld=" + field + " id=" + ID);
    }
    resultSetAge = System.currentTimeMillis();
    makeDBObject(Cin, tab, field, ID);
  }
  
  /**
   * 
   * create a DBObject "where field=id and addCol = addColId". Note : The query might return more than one row, but this
   * object will only act on the first row returned. There is no checking to see if this is true. It
   * might also create a new row if the "Where field=id" does not match any rows in the table. Use
   * "isNew()" to check this out.
   * 
   * @param Cin A MySQL connection
   * @param sch Schema name
   * @param tab The table to look up ID in
   * @param field The field name in which to find the ID
   * @param ID The ID to get (the row selection unique ID)
   * @param addCol An column name that is also used to identify a specific object
   * @param addColId The ID value that addCol must be to match
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String sch, String tab, String field, String ID,
          String addCol, int addColId) throws SQLException {
    dbgdet = false;
    dbg = false;
    schema = sch;
    addField = addCol;
    addId = addColId;
    if (dbgdet) {
      Util.prt("DBO:cons:" + tab + " fld=" + field + " id=" + ID);
    }
    resultSetAge = System.currentTimeMillis();
    makeDBObject(Cin, tab, field, ID);
  }
  /**
   * create a DBObject "where field=id". Note : The query might return more than one row, but this
   * object will only act on the first row returned. There is no checking to see if this is true. It
   * might also create a new row if the "Where field=id" does not match any rows in the table. Use
   * "isNew()" to check this out.
   *
   * @param Cin A MySQL connection
   * @param select select SQL for this object
   * @param sch schmea name
   * @param tab The table to look up ID in
   * @param field The field name in which to find the ID
   * @param ID The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public DBObject(DBConnectionThread Cin, String select, String sch, String tab, String field, String ID) throws SQLException {
    dbgdet = false;
    dbg = false;
    selectClause = select;
    schema = sch;
    if (dbgdet) {
      Util.prt("DBO:cons:" + tab + " fld=" + field + " id=" + ID);
    }
    resultSetAge = System.currentTimeMillis();
    makeDBObject(Cin, tab, field, ID);
  }

  private void makeDBObject(DBConnectionThread Cin, String tab, String fieldIn, String IDin) throws SQLException {
    connThread = Cin;						//  Save  connection  for  later
    if (!connThread.isOK()) {
      Util.prt("DBO: makeDBObject dbconn is not OK - do a reopen");
      connThread.reopen();
    }
    table = tab;
    field = fieldIn;
    value = IDin;
    modified = false;
    if (dbgdet) {
      Util.prta("DBO:makeDBOMy:" + tab + " fld=" + field + " id=" + IDin);
    }
    String s = "";
    try {
      if (rs != null) {
        rs.close();
      }
      if (stmt != null) {
        stmt.close();
      }
      //connThread.closeConnection();
      //connThread.waitForConnection();
      //ResultSet rs2= connThread.  executeQuery("SHOW TABLES");
      //if(dbgdet) Util.prta("DBO:makeDBOMy: do aft SHOW Tables");
      if (System.currentTimeMillis() - resultSetAge > 300000) {
        Util.prta("DBO: Stale DBConnection : reopen");
        connThread.reopen();
      }
      stmt = connThread.getNewStatement(true);
      if (dbgdet) {
        Util.prta("DBO:makeDOBMy: after statement created");
      }
      if (fieldIn.equalsIgnoreCase("id")) {
        s = selectClause + (schema != null ? (!schema.equals("") ? schema + "." : "") : "") + tab
                + " WHERE  " + field + "='" + IDin + "'";
      } else {
        s = selectClause + (schema != null ? (!schema.equals("") ? schema + "." : "") : "") + tab
                + " WHERE upper(" + field + ")=upper(" + Util.sqlEscape(IDin) + ")";
        if (addField != null) {
          s += " and upper(" + addField + ") = " + addId;
        }
      }

      rs = stmt.executeQuery(s);
      if (dbgdet) {
        Util.prta("DBO:makeDOBMy: after ResultSet built");
      }
      resultSetAge = System.currentTimeMillis();
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      row = rs.getRow();
      boolean ok = rs.next();                        //  Position  the  one  and  only
      if (rs.isFirst() && ok) {
        DBObjectDone(rs);
        if (dbg) {
          Util.prt(table + ": DBObjectMy:makeDBO: cons found " + ID + ": " + field + "=" + IDin);
        }
        newRecord = false;
      } else {
        if (dbg) {
          Util.prt(table + ": DBObjectMy makeDBO:Cons: new record : " + field + "=" + IDin);
        }
        rs.moveToInsertRow();		//  Changes  in  insert  row  only
        //  ***  remember  to  update  odd  fields  like  "password"  which  might  not  be
        //  updated  by  the  user.  the  clearAllCoumns  will  make  sure  everything  gets
        //  something,  it  just  may  not  be  what  you  want!
        Util.clearAllColumns(rs);     //  Insure  all  columns  are  clear
//        DBObjectDone(rs);                //  insures  object  fields  are  empty
        row = 0;

        newRecord = true;
      }

    } catch (SQLException E) {
      E.printStackTrace();
      Util.SQLErrorPrint(E, table + ": ResultSetTo DBObject  failed s=" + s);
      newRecord = true;
      if (rs != null) {
        rs.moveToInsertRow();//  All  changes  in  insert  row
      }
      throw E;
    }
  }

  private void DBObjectDone(ResultSet inrs) {
    if (dbgdet) {
      Util.prt("DBO:objDone():" + table);
    }
    try {
      rs = inrs;
      row = rs.getRow();
      modified = false;
      ID = Util.getInt(rs, "id");
      if (dbgdet) {
        Util.prt("DBO:ObjDone():" + table + " id=" + ID + " rw=" + row);
      }

    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObjectDone()  SQL  error");
      E.printStackTrace();
    }
  }

  /**
   * refreshRecord remakes the result set for this object. This should be called when the
   * possibility exists that the ResultSet or connections have gone stale. Basically it makes the
   * results set fresh, then the user can use "updateFields" then updateRecord().
   *
   * @throws SQLException If an unsolvable exception occurs (like database is down!)
   */
  public void refreshRecord() throws SQLException {
    //if(dbgdet) 
    Util.prta("DBO:refreshRec():" + table + " id=" + ID);
    makeDBObject(connThread, table, "id", "" + ID);
    Util.prta("DBO:refresh done.");
  }

  /**
   * Call this to on DBObject object that is ready for DB update. All of the created, created_by,
   * and updated fields are taken care of here.
   *
   * @exception SQLException Usually the network or Database is down
   */
  public void updateRecord() throws SQLException {
    if (dbgdet) {
      Util.prt("DBO:updRec():" + table + " new=" + newRecord + " mod=" + modified);
    }
    int i = 0;
    try {
      if (modified) {
        if (newRecord) {                // New record, set creation/or and reread record
          Timestamp ts = Util.now();
          rs.updateTimestamp("created", ts);   // Set create date when created
          i++;
          if (connThread.getVendor().toLowerCase().contains("postgres")) {
            Statement stmt2 = connThread.getNewStatement(true);
            ResultSet rs2 = stmt2.executeQuery("SELECT nextval('" + table + "_id_seq'),max(id) from "
                    + (schema != null ? (!schema.equals("") ? schema + "." : "") : "") + table);
            if (rs2.next()) {
              int idnew = rs2.getInt(1);
              int idmax = rs2.getInt(2);
              rs2.close();
              stmt2.close();
              //Util.prt("Postgres set ID to "+idnew+" max(id)="+idmax);
              rs.updateInt("id", idnew);

            } else {
              Util.prta("Failed to update POSTGRES ID - abort operation!" + table);
              throw new SQLException("Failted to update POSGTRESS ID for table=" + table);
            }
          }
          rs.insertRow();
          //rs.moveToCurrentRow();
          rs.last();
          autoInKeyFromRS = rs.getInt("id");
          DBObjectDone(rs);
          i++;

          if (dbg) {
            Util.prt(table + ": UpdRec: new DBObject record done ID=" + ID);
          }
          newRecord = false;
        } else {
          if (dbg) {
            Util.prt("DBO:updRec: old obj update " + table + "/" + field + " id=" + ID);
          }
          rs.updateTimestamp("updated", Util.TimestampNow());
          rs.updateRow();
          if (dbg) {
            Util.prt("DBO:updRec: old obj update done");
          }
        }
        modified = false;
        newRecord = false;
      }
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject UpdateRecord failed. i=" + i
              + " ID=" + ID + "=" + " concur=" + ResultSet.CONCUR_UPDATABLE);
      E.printStackTrace();
      throw E;
    }
  }

  /**
   * delete the existing record
   *
   * @throws SQLException
   */
  public void deleteRecord() throws SQLException {
    if (dbgdet) {
      Util.prt("DBO:deleteRec():" + table + " new=" + newRecord + " mod=" + modified);
    }
    if (newRecord) {
      return;
    }

    try {
      if (C == null) {
        connThread.executeUpdate("DELETE FROM " + (schema != null ? (!schema.equals("") ? schema + "." : "") : "") + table + " WHERE ID=" + ID);
      } else {
        if (stmt == null) {
          stmt = C.createStatement();    //  Use  statement  for  query
        }
        String s = "DELETE FROM " + (schema != null ? (!schema.equals("") ? schema + "." : "") : "") + table + " WHERE ID=" + ID + ";";
        stmt.executeUpdate(s);
      }
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Delete failed. ID=" + ID
              + " concur=" + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Convert the record to a string
   *
   * @return it is "table="+table+" ID="+ID
   */
  @Override
  public String toString() {
    return "table=" + table + " ID=" + ID;
  }

  /**
   * Is this record new
   *
   * @return boolean indicating newness
   */
  public boolean isNew() {
    return newRecord;
  }

  /**
   * Checks to see if any of the set****() have been called which modified the record since it was
   * read in
   *
   * @return boolean with whether a change has been made.
   */
  public boolean isModified() {
    return modified;
  }

  /**
   * return the unique ID.
   *
   * @return the Unique ID
   */
  public int getID() {
    return ID;
  }    //  Never  a  setter  for  this!!!

  /**
   * get a field using the ResultSet get*().
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public int getInt(String column) throws SQLException {
    return rs.getInt(column);
  }

  /**
   * get a field using the ResultSet get*().
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public long getLong(String column) throws SQLException {
    return rs.getLong(column);
  }

  /**
   * get a field using the ResultSet get*().
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public String getString(String column) throws SQLException {
    return rs.getString(column);
  }

  /**
   * get a field using the ResultSet get*().
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public byte getByte(String column) throws SQLException {
    return rs.getByte(column);
  }

  /**
   * get a field using the ResultSet get*()
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public short getShort(String column) throws SQLException {
    return rs.getShort(column);
  }

  /**
   * get a field using the ResultSet get*().
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public Timestamp getTimestamp(String column) throws SQLException {
    return rs.getTimestamp(column);
  }

  /**
   * get a field using the ResultSet get*() .
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public double getDouble(String column) throws SQLException {
    return rs.getDouble(column);
  }

  /**
   * get a field using the ResultSet get*() .
   *
   * @param column The name of the field to get
   * @return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public Date getDate(String column) throws SQLException {
    return rs.getDate(column);
  }

  /**
   * if the resultset is stale (older than a few minutes), rebuild it before starting a modification
   *
   */
  private void preModifyCheck() throws SQLException {
    if (!modified && resultSetAge > 0 && System.currentTimeMillis() - resultSetAge > 300000) {
      makeDBObject(connThread, table, field, value);
      resultSetAge = System.currentTimeMillis();
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setInt(String column, int val) throws SQLException {
    preModifyCheck();
    try {
      rs.updateInt(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setInt column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setLong(String column, long val) throws SQLException {
    preModifyCheck();
    try {
      rs.updateLong(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setLong column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setDouble(String column, double val) throws SQLException {
    preModifyCheck();
    if (dbgdet) {
      Util.prt("DBO:SetDouble " + column + "=" + val);
    }
    try {
      rs.updateDouble(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setDouble column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setString(String column, String val) throws SQLException {
    preModifyCheck();
    if (dbgdet) {
      Util.prt("DBO:SetString " + column + "=" + val);
    }
    try {
      rs.updateString(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setString column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setTimestamp(String column, Timestamp val) throws SQLException {
    preModifyCheck();
    if (dbgdet) {
      Util.prt("DBO:SetTimestamp " + column + "=" + val);
    }
    try {
      rs.updateTimestamp(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setTimestamp column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setDate(String column, Date val) throws SQLException {
    preModifyCheck();
    if (dbgdet) {
      Util.prt("DBO:Setdate " + column + "=" + val);
    }
    if (val.getTime() == 0) {      // If none, set NULL for date
      rs.updateNull(column);      // If null, make no change
      return;
    }
    try {
      rs.updateDate(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setDate column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setByte(String column, byte val) throws SQLException {
    preModifyCheck();
    if (dbgdet) {
      Util.prt("DBO:SetByte " + column + "=" + val);
    }
    try {
      rs.updateByte(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setByte column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  /**
   * Set the column to the value given and mark the object as modified.
   *
   * @param column The name of the field to update
   * @param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
   */
  public void setShort(String column, short val) throws SQLException {
    preModifyCheck();
    if (dbgdet) {
      Util.prt("DBO:SetShort " + column + "=" + val);
    }
    try {
      rs.updateShort(column, val);
      modified = true;
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, table + ": DBObject Update failed setShort column=" + column + " " + ResultSet.CONCUR_UPDATABLE);
      throw E;
    }
  }

  public void close() throws SQLException {
    if (rs != null) {
      rs.close();
    }
    rs = null;
    if (stmt != null) {
      stmt.close();
    }
    stmt = null;
    C = null;
  }

  /**
   * Unit test main
   *
   * @param args command line parameters
   */
  public static void main(String[] args) {
    DBConnectionThread jcjbl;
    Connection C;
    try {
      jcjbl = new DBConnectionThread("localhost", "mcr", "vdl", "nop240", true, false, "TEST", "postgres");

      //user=new User(C,"dkt","karen");
      //  Test  for  an  existing  record
      DBObject obj = new DBObject(jcjbl, "holdings");
      Util.prt("MAIN : Read  record  :  " + obj.toString()
              + " new=" + obj.isNew() + " modified=" + obj.isModified());
      obj.setString("seedname", "USAAA  BHZ00");
      obj.setString("type", "bb");
      obj.setTimestamp("start", Util.TimestampNow());
      obj.setTimestamp("end", Util.TimestampNow());
      obj.updateRecord();
      //obj.refreshRecord();
      Util.prt("Update done");
      obj.setString("type", "cc");
      obj.updateRecord();
      obj.setTimestamp("start", Util.TimestampNow());
      obj.updateRecord();

    } catch (InstantiationException e) {
      Util.prt("Instantiation error! in DBObject.main()");
      System.exit(0);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, " Main SQL unhandled=");
      System.err.println("SQLException  on  getting test $DBObject");
    }
  }
}
