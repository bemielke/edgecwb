/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.util;
import  java.sql.*;
import gov.usgs.anss.mysql.MySQLConnectionThreadOld;

/**
*
 * This routine basically forms an object wrapping a ResultSet for one record from
 * a JDBC table.  
 * When a new one is created, it is read back using the created and created_by
 * fields (unlikely anyone could create more than one record in one table at
 * precisely the right time).  It then behaves just like one that was read.
 * The getter and setter methods by data type are use to manipulate the data.
  * This routine requires the following in the creation of the table :
 * CREATE TABLE name? (
 * ID            INT(6) UNSIGNED NOT NULL AUTO_INCREMENT, PRIMARY KEY (ID),

 * updated       TIMESTAMP,
 * created_by    INT(6),
 * created       TIMESTAMP);
 *
 * The created and created_by will be set when this record is first written.
 * updated will change automatically when changes are made (at least in MySQL)
 * ID is the unique primary key.
  * Created on July 1, 2002
*  @author  David  Ketchum 
*  @version  1.00  (base  version)
 */
public  class  DBObjectOld  extends  Object  {
/*

//	Put  the  data  base  mapped  definitions  here
  
*/
    static boolean dbg;
    static boolean dbgdet;
    int  ID;			//  Unique  record  name


//  Non-persistent  data
    Connection  C;
    MySQLConnectionThreadOld mysql;
    String table;               // The table this is a member of
    String field;
    String value;
    ResultSet  rs;
    long resultSetAge;          // The time this result set was mode
    Statement stmt;
    int  row;
    boolean  modified;          //  if  Set,  some  field  has  been  modified
    boolean  newRecord;        //  If  set,  then  the  constructed  record  is
                                                //  Not  already  in  the  data  base
    int autoInKeyFromRS;
    
  /** create a DBObjectOld for ID in file tab. Tis  is  the  normal  way  of  getting  a
   *single  $DBObject  for  use
   *@param Cin A MySQL connection
   *@param tab The table to look up ID in
   *@param IDin The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public  DBObjectOld(Connection  Cin,  String tab, int IDin)  throws  SQLException  {
    if(dbgdet) Util.prt("DBO: cons1:"+tab+" id="+IDin);
    makeDBObject(Cin, tab, "id", ""+IDin);
    dbg=false;
    dbgdet=false;
    return;
  }

  /** This is just a convenient (or stupid) place to put the a generally QUERY in a table. This
   * returns a result set based on a query of the form "SELECT * FROM tab WHERE ID=IDin;".  The
   *returned result set is already positioned to the ID (no need to use rs.next())
   * @param Cin Connection to MySQL
   * @param tab The table for the select statement
   * @param IDin The ID in the table for this single row
   * @return A result set already positioned for the row
   * @exception SQLException Usually the network or Database is down
   */
  public static ResultSet Query(Connection  Cin,  String tab, int IDin) throws SQLException {
    if(dbgdet) Util.prt("DBO:query():"+tab+" id="+IDin);
      Statement  stmt  = Cin.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);    //  Use  statement  for  query
      ResultSet rs  =  stmt.executeQuery("SELECT * FROM " + tab
              + " WHERE ID=" + Util.sqlEscape(IDin));
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      rs.next();                        //  Position  the  one  and  only
      return rs;
  }
  /** This is just a convenient (or stupid) place to put the a generally QUERY in a table. This
   * returns a result set based on a query of the form "SELECT * FROM tab WHERE field=IDin;".  The
   *returned result set is already positioned to the first row returned.  Note : this routine
   * could return more than one row which could be access by ResultSet.next();
   * @param Cin Connection to MySQL
   * @param tab The table for the select statement
   * @param field The field on which the WHERE clause is to run
   * @param IDin The ID in the table for this single row
   * @return A result set already positioned for the row
   * @exception SQLException Usually the network or Database is down
   */
  public static ResultSet Query(Connection  Cin,  String tab, String field, int IDin) throws SQLException {
    if(dbgdet) Util.prt("DBO:query():"+tab+" fld="+field+" id="+IDin);
      Statement  stmt  = Cin.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);    //  Use  statement  for  query
      ResultSet rs  =  stmt.executeQuery("SELECT * FROM " + tab
              + " WHERE " + field + "=" + Util.sqlEscape(IDin));
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      rs.next();                        //  Position  the  one  and  only
      return rs;
  }



  /**   used  for  creating DBObjectOld objects which are new rows in the table.
    * when a new one is created,  it is read back using the created and created_by
    * fields (unlikely anyone could create more than one record in one table at
    * precisely the right time).  It then behaves just like one that was read.
   * Normally, all of the fields in the table are set by calls to the "update????()"
   *routines and then the record made permanent with a call to "updateRecord()".
   * @param Cin The Mysql connection
   * @param tab The table in which to create a new record
   * @exception SQLException Usually the network or Database is down
    */
  public DBObjectOld(Connection Cin, String tab) throws SQLException {
    dbg=false;
    dbgdet=false;
    if(dbgdet) Util.prt("DBO:cons:"+tab);
    makeDBObject (Cin, tab, "id","0");
//    Util.prt(table + ": DBObjectOld creat NEW"+ID);
  }

  //
  /** create a DBObjectOld "where field=id".  Note : The query might return more than one
   * row, but this object will only act on the first row returned.  There is no checking
   * to see if this is true.  It might also create a new row if the "Where field=id" does
   * not match any rows in the table. Use "isNew()" to check this out.
   *@param Cin A MySQL connection
   *@param tab The table to look up ID in
   *@param field The field name in which to find the ID
   *@param ID The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
   public DBObjectOld(Connection Cin, String tab, String field, String ID) throws SQLException {
    if(dbgdet) Util.prt("DBO:cons:"+tab+" fld="+field+" id="+ID);
    makeDBObject(Cin, tab, field, ID);
  }

  private void makeDBObject(Connection Cin, String tab, String fieldin, String IDin) throws SQLException{
    C  =  Cin;						//  Save  connection  for  later
    table = tab;
    modified=false;
    field = fieldin;
    value = IDin;
    if(dbgdet) Util.prt("DBO:makeDBO:"+tab+"fld="+field+" id="+IDin);
    try  {
      if(rs != null) rs.close();
      if(stmt != null) stmt.close();
      stmt  =  C.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
              ResultSet.CONCUR_UPDATABLE);    //  Use  statement  for  query
      rs = stmt.executeQuery("SELECT * FROM " + tab
              + " WHERE upper(" + field + ")=upper(" + Util.sqlEscape(IDin)+")");
      resultSetAge=0;

//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      row = rs.getRow();
      boolean ok = rs.next();                        //  Position  the  one  and  only
      if(  rs.isFirst() && ok)  {
        DBObjectDone(rs);
        if(dbg) Util.prt(table+": DBObject:makeDBO: cons found "  +  ID +": "+field+"="+IDin);
        newRecord=false;
        return;
      }
      else  {
        if(dbg) Util.prt(table+ ": DBObject makeDBO:Cons: new record : "+field+"="+  IDin);
        rs.moveToInsertRow();		//  Changes  in  insert  row  only
        //  ***  remember  to  update  odd  fields  like  "password"  which  might  not  be
        //  updated  by  the  user.  the  clearAllCoumns  will  make  sure  everything  gets
        //  something,  it  just  may  not  be  what  you  want!
        Util.clearAllColumns(rs);     //  Insure  all  columns  are  clear
//        DBObjectDone(rs);                //  insures  object  fields  are  empty
        row=0;

        newRecord=true;
        return;
      }

    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,table + ": ResultSetTo DBObject  failed");
      newRecord=true;
      rs.moveToInsertRow();//  All  changes  in  insert  row
      throw  E;
    }
  }
    /** create a DBObjectOld for ID in file tab. This  is  the  normal  way  of  getting  a
   *single  $DBObject  for  use
   *@param Cin A MySQL connection
   *@param tab The table to look up ID in
   *@param IDin The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
  public  DBObjectOld(MySQLConnectionThreadOld  Cin,  String tab, int IDin)  throws  SQLException  {
    if(dbgdet) Util.prt("DBO: cons1:"+tab+" id="+IDin);
    makeDBObject(Cin, tab, "id", ""+IDin);
    dbg=false;
    dbgdet=false;
    return;
  }

  /** This is just a convenient (or stupid) place to put the a generally QUERY in a table. This
   * returns a result set based on a query of the form "SELECT * FROM tab WHERE ID=IDin;".  The
   *returned result set is already positioned to the ID (no need to use rs.next())
   * @param Cin Connection to MySQL
   * @param tab The table for the select statement
   * @param IDin The ID in the table for this single row
   * @return A result set alread positioned for the row
   * @exception SQLException Usually the network or Database is down
   */
  public static ResultSet Query(MySQLConnectionThreadOld  Cin,  String tab, int IDin) throws SQLException {
    if(dbgdet) Util.prt("DBO:query():"+tab+" id="+IDin);
      Statement  stmt  = Cin.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);    //  Use  statement  for  query
      ResultSet rs  =  stmt.executeQuery("SELECT * FROM " + tab
              + " WHERE ID=" + Util.sqlEscape(IDin));
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      rs.next();                        //  Position  the  one  and  only
      return rs;
  }
  /** This is just a convenient (or stupid) place to put the a generally QUERY in a table. This
   * returns a result set based on a query of the form "SELECT * FROM tab WHERE field=IDin;".  The
   *returned result set is already positioned to the first row returned.  Note : this routine
   * could return more than one row which could be access by ResultSet.next();
   * @param Cin Connection to MySQL
   * @param tab The table for the select statement
   * @param field The field on which the WHERE clause is to run
   * @param IDin The ID in the table for this single row
   * @return A result set already positioned for the row
   * @exception SQLException Usually the network or Database is down
   */
  public static ResultSet Query(MySQLConnectionThreadOld  Cin,  String tab, String field, int IDin) throws SQLException {
    if(dbgdet) Util.prt("DBO:query():"+tab+" fld="+field+" id="+IDin);
      Statement  stmt  = Cin.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);    //  Use  statement  for  query
      ResultSet rs  =  stmt.executeQuery("SELECT * FROM " + tab
              + " WHERE " + field + "=" + Util.sqlEscape(IDin));
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      rs.next();                        //  Position  the  one  and  only
      return rs;
  }



  /**   used  for  creating DBObjectOld objects which are new rows in the table.
    * when a new one is created,  it is read back using the created and created_by
    * fields (unlikely anyone could create more than one record in one table at
    * precisely the right time).  It then behaves just like one that was read.
   * Normally, all of the fields in the table are set by calls to the "update????()"
   *routines and then the record made permanent with a call to "updateRecord()".
   * @param Cin The Mysql connection
   * @param tab The table in which to create a new record
   * @exception SQLException Usually the network or Database is down
    */
  public DBObjectOld(MySQLConnectionThreadOld Cin, String tab) throws SQLException {
    dbg=false;
    dbgdet=false;
    if(dbgdet) Util.prt("DBO:cons:"+tab);
    makeDBObject (Cin, tab, "id","0");
//    Util.prt(table + ": DBObjectOld creat NEW"+ID);
  }

  //
  /** create a DBObjectOld "where field=id".  Note : The query might return more than one
   * row, but this object will only act on the first row returned.  There is no checking
   * to see if this is true.  It might also create a new row if the "Where field=id" does
   * not match any rows in the table. Use "isNew()" to check this out.
   *@param Cin A MySQL connection
   *@param tab The table to look up ID in
   *@param field The field name in which to find the ID
   *@param ID The ID to get (the row selection unique ID)
   * @exception SQLException Usually the network or Database is down
   */
   public DBObjectOld(MySQLConnectionThreadOld Cin, String tab, String field, String ID) throws SQLException {
     dbgdet=true;
     dbg=true;
    if(dbgdet) Util.prt("DBO:cons:"+tab+" fld="+field+" id="+ID);
    makeDBObject(Cin, tab, field, ID);
  }
  
  private void makeDBObject(MySQLConnectionThreadOld Cin, String tab, String fieldIn, String IDin) throws SQLException{
    mysql  =  Cin;						//  Save  connection  for  later
    table = tab;
    field=fieldIn;
    value = IDin;
    modified=false;
    if(dbgdet) Util.prta("DBO:makeDBOMy:"+tab+" fld="+field+" id="+IDin);
    try  {
      if(rs != null) rs.close();
      if(stmt != null) stmt.close();
      //mysql.closeConnection();
      //mysql.waitForConnection();
      //ResultSet rs2= mysql.executeQuery("SHOW TABLES");
      //if(dbgdet) Util.prta("DBO:makeDBOMy: do aft SHOW Tables");
      stmt  =  mysql.getNewStatement(true);
      if(dbgdet) Util.prta("DBO:makeDOBMy: after statement created");
      rs = stmt.executeQuery("SELECT * FROM " + (mysql != null?(mysql.getDatabase().equals("isc")?"irsan.":""):"") + tab
              + " WHERE upper(" + field + ")=upper(" + Util.sqlEscape(IDin)+")");
      if(dbgdet) Util.prta("DBO:makeDOBMy: after ResultSet built");
      resultSetAge = System.currentTimeMillis();
//      Util.prt(tab+" rs row="  +  rs.getRow()  );
      row = rs.getRow();
      boolean ok = rs.next();                        //  Position  the  one  and  only
      if(  rs.isFirst() && ok)  {
        DBObjectDone(rs);
        if(dbg) Util.prt(table+": DBObjectMy:makeDBO: cons found "  +  ID +": "+field+"="+IDin);
        newRecord=false;
        return;
      }
      else  {
        if(dbg) Util.prt(table+ ": DBObjectMy makeDBO:Cons: new record : "+field+"="+  IDin);
        rs.moveToInsertRow();		//  Changes  in  insert  row  only
        //  ***  remember  to  update  odd  fields  like  "password"  which  might  not  be
        //  updated  by  the  user.  the  clearAllCoumns  will  make  sure  everything  gets
        //  something,  it  just  may  not  be  what  you  want!
        Util.clearAllColumns(rs);     //  Insure  all  columns  are  clear
//        DBObjectDone(rs);                //  insures  object  fields  are  empty
        row=0;

        newRecord=true;
        return;
      }

    }  catch  (SQLException  E)
    { E.printStackTrace();
      Util.SQLErrorPrint(E,table + ": ResultSetTo DBObject  failed");
      newRecord=true;
      if(rs != null) rs.moveToInsertRow();//  All  changes  in  insert  row
      throw  E;
    }
  }


  private  void  DBObjectDone(ResultSet  inrs)  {
    if(dbgdet) Util.prt("DBO:objDone():"+table);
    try  {
      rs  =  inrs;
      row  =  rs.getRow();
      modified  =  false;
      ID=Util.getInt(rs, "id");
      if(dbgdet) Util.prt("DBO:ObjDone():"+table+" id="+ID+" rw="+row);
      
      return;
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,table+": DBObjectDone()  SQL  error");
      E.printStackTrace();
      return;
    }
  }
    
  public void refreshRecord() throws SQLException {
    if(dbgdet) Util.prt("DBO:refreshRec():"+table+" id="+ID);
    if( C == null) makeDBObject(mysql, table, "id", ""+ID);
    else makeDBObject(C,table,"id",""+ID);

  }
  /**  Call this to on DBObjectOld object that is ready for DB update. All of the
   *created, created_by, and updated fields are taken care of here
   * @exception SQLException Usually the network or Database is down
  */
  public void updateRecord() throws SQLException {
    updateRecord(false);
  }
  /**  Call this to on DBObjectOld object that is ready for DB update. All of the
   *created, created_by, and updated fields are taken care of here.  If skipMakeValid
   *is true, then the record is not repositioned and its ID is not made known.  This is
   *useful if the user is only making new records but does not need them back one made.
   *@param skipMakeValid If true, the new record is not repositioned and its ID will not be known
   * @exception SQLException Usually the network or Database is down
  */
  public  void  updateRecord  (boolean skipMakeValid)  throws  SQLException  {
    if(dbgdet) Util.prt("DBO:updRec():"+table+" skip="+skipMakeValid+" new="+newRecord+" mod="+modified);
    int i=0;
    try  {
      if(modified)  {
        if(newRecord)  {                // New record, set creation/or and reread record
          Timestamp ts = Util.now();
          rs.updateTimestamp("created",ts);   // Set create date when created
          i++;
          rs.updateInt("created_by",User.getUserID());
          i++;
          if(mysql.getVendor().toLowerCase().indexOf("postgres") >= 0) {
            Statement stmt2  =  mysql.getNewStatement(true);
            ResultSet rs2 = stmt2.executeQuery("SELECT nextval('"+table+"_id_seq'),max(id) from "+table);
            if(rs2.next()) {
              int idnew = rs2.getInt(1);
              int idmax = rs2.getInt(2);
              rs2.close();
              stmt2.close();
              Util.prt("Postgres set ID to "+idnew+" max(id)="+idmax);
              rs.updateInt("id",idnew);

            }
            else {
              Util.prta("Failed to update POSTGRES ID - abort operation!"+table);
              throw new SQLException("Failted to update POSGTRESS ID for table="+table);
            }
          }
          rs.insertRow();
          //rs.moveToCurrentRow();
          rs.last();
          autoInKeyFromRS = rs.getInt("id");
          DBObjectDone(rs);
          i++;
 //         rs.close();
          /*if(!skipMakeValid) {
            if(stmt == null) stmt  =  C.createStatement();    //  Use  statement  for  query
            String s = "SELECT * FROM " + table
                    + " WHERE created=" + Util.sqlEscape(ts)
                    + " AND created_by="+Util.sqlEscape(User.getUserID())
                    + " ORDER BY ID";
            rs = stmt.executeQuery(s);
            i++;
            rs.next();
            i++;
            while(!rs.isLast() ) {
              //Util.prt("DBOBJECT skipping record to last one="+rs.getInt("id"));
              rs.next();
            }
            i=100;
            DBObjectDone(rs);
            refreshRecord();
            i++;
            if(dbg) Util.prt(table + "updRec: new DBObjectOld entry id ="+ID);
          }*/
          if(dbg) Util.prt(table + ": UpdRec: new DBObject record done ID="+ID);
          newRecord=false;
        }
        else  {
          //if(dbg)
            Util.prt("DBO:updRec: old obj update "+table+"/"+field+" id="+ID);
          rs.updateTimestamp("updated",Util.TimestampNow());
          rs.updateRow();
          Util.prt("DBO:updRec: old obj update done");
        }
        modified=false;
        newRecord=false;
      }
    }  
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject UpdateRecord failed. i="+i+
              " ID="+ID+"="+" concur="+rs.CONCUR_UPDATABLE);
      E.printStackTrace();
      throw  E;
    }
  }
  /** delete the existing record 
   * @throws SQLException
   */
  public void deleteRecord() throws SQLException {
    if(dbgdet) Util.prt("DBO:deleteRec():"+table+" new="+newRecord+" mod="+modified);
    if(newRecord) return;

    try  {
      if(C == null) {
        mysql.executeUpdate( "DELETE FROM " + table + " WHERE ID=" + ID);
      }
      else {
        if(stmt == null) stmt  =  C.createStatement();    //  Use  statement  for  query
        String s = "DELETE FROM " + table + " WHERE ID=" + ID+";";
        stmt.executeUpdate(s);
      }
    }  
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Delete failed. ID="+ID+
          " concur="+rs.CONCUR_UPDATABLE);
      throw  E;
    }
  }

 /**  Convert the record to a string 
  * @return it is "table="+table+" ID="+ID */
  @Override
 public  String  toString()  {
   return  "table="+table+" ID="+ID;
 }
    
  /** Is this record new
   * @return boolean indicating newness*/
  public  boolean  isNew()  {  return  newRecord;}
  /** Checks to see if any of the set****() have been called which modified the record
   * since it was read in
   * @return boolean with whether a change has been made. 
   */
  public boolean isModified() { return modified;}
  /** return the unique ID.
   * @return the Unique ID*/  
  public  int  getID()  {  return ID;}    //  Never  a  setter  for  this!!!
  /** get a field using the ResultSet get*(). 
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public int getInt(String column) throws SQLException {return rs.getInt(column);}
  /** get a field using the ResultSet get*(). 
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public long getLong(String column) throws SQLException {return rs.getLong(column);}
  /** get a field using the ResultSet get*(). 
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public String getString(String column) throws SQLException {return rs.getString(column);}
  /** get a field using the ResultSet get*(). 
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public byte getByte(String column) throws SQLException {return rs.getByte(column);}
  /** get a field using the ResultSet get*() 
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public short getShort(String column) throws SQLException {return rs.getShort(column);}
  /** get a field using the ResultSet get*().
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public Timestamp getTimestamp(String column) throws SQLException {return rs.getTimestamp(column);}
  /** get a field using the ResultSet get*() .
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public double getDouble(String column) throws SQLException {return rs.getDouble(column);}
  /** get a field using the ResultSet get*() .
   *@param column The name of the field to get
   *@return The value of the field
   * @exception SQLException Usually the network or Database is down
   */
  public Date getDate(String column) throws SQLException {return rs.getDate(column);}
  /** if the resultset is stale (older than a few minutes), rebuild it before starting a modification
   *
   */
  private void preModifyCheck() throws SQLException {
    if(!modified && resultSetAge > 0 && System.currentTimeMillis() - resultSetAge > 300000) {
      Util.prt("preModifyCHeck() making fresh result set "+table+"/"+field+"="+value);
      if(mysql != null) makeDBObject(mysql, table,field,value);
      else if(C != null) makeDBObject(C, table, field, value);
      resultSetAge=System.currentTimeMillis();
    }
  }
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setInt(String column, int val) throws SQLException {
    preModifyCheck();
    try {
      rs.updateInt(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setInt column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
 
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setLong(String column, long val) throws SQLException {
    preModifyCheck();
    try {
      rs.updateLong(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setLong column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
 
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   *@exception SQLException Usually the network or Database is down
  */
  public void setDouble(String column, double val) throws SQLException {
    preModifyCheck();
    if(dbgdet) Util.prt("DBO:SetDouble "+column+"="+val);
    try {
      rs.updateDouble(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setDouble column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setString(String column, String val) throws SQLException {
    preModifyCheck();
    if(dbgdet) Util.prt("DBO:SetString "+column+"="+val);
    try {
      rs.updateString(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setString column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
 
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setTimestamp(String column, Timestamp val) throws SQLException {
    preModifyCheck();
    if(dbgdet) Util.prt("DBO:SetTimestamp "+column+"="+val);
    try {
      rs.updateTimestamp(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setTimestamp column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setDate(String column, Date val) throws SQLException {
    preModifyCheck();
    if(dbgdet) Util.prt("DBO:Setdate "+column+"="+val);
    if(val.getTime() == 0) {      // If none, set NULL for date
      rs.updateNull(column);      // If null, make no change
      return;
    }
    try {
      rs.updateDate(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setDate column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setByte(String column, byte val) throws SQLException {
    preModifyCheck();
    if(dbgdet) Util.prt("DBO:SetByte "+column+"="+val);
    try {
      rs.updateByte(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setByte column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
  /** Set the column to the value given and mark the object as modified.
   *@param column The name of the field to update
   *@param val The value to set in the field
   * @exception SQLException Usually the network or Database is down
  */
  public void setShort(String column, short val) throws SQLException {
    preModifyCheck();
    if(dbgdet) Util.prt("DBO:SetShort "+column+"="+val);
    try {
      rs.updateShort(column,val);
      modified=true;
    } 
    catch  (SQLException  E)  
    { Util.SQLErrorPrint(E,table+": DBObject Update failed setShort column="+column+" "+rs.CONCUR_UPDATABLE);
      throw  E;
    }    return;
  }
  
  public void close() throws SQLException {
    if(rs != null) rs.close();
    rs=null;
    if(stmt != null) stmt.close();
    stmt = null;
    C = null;
  }
  
  /** Unit test main
   @param args command line parameters*/
  public  static  void  main  (String  []  args)  {
    JDBConnectionOld  jcjbl;
    Connection  C;
    User user=new User("dkt");
    try  {
      jcjbl  =  new  JDBConnectionOld(UC.JDBCDriver(),  UC.JDBCDatabase());
      C  =  JDBConnectionOld.getConnection();
      UC.setConnection(C);
      

     
      //user=new User(C,"dkt","karen");

      //  Test  for  an  existing  record
      DBObjectOld  obj  =  new  DBObjectOld(C,  "holdings");
      Util.prt("MAIN : Read  record  :  "  +  obj.toString()+
        " new="+obj.isNew()+" modified="+obj.isModified());
      obj.setString("seedname","USAAA  BHZ00");
      obj.setString("type", "bb");
      obj.setTimestamp("start",Util.TimestampNow());
      obj.setTimestamp("end",Util.TimestampNow());
      obj.updateRecord();
      //obj.refreshRecord();
      Util.prt("Update done");
      obj.setString("type","cc");
      obj.updateRecord();
      obj.setTimestamp("start",Util.TimestampNow());
      obj.updateRecord();

    } //catch (JCJBLBadPassword e) {
    //  System.err.println("Password must be wrong on User construction");
    //}
    catch  (SQLException  e)  {
      Util.SQLErrorPrint(e," Main SQL unhandled=");
      System.err.println("SQLException  on  getting test $DBObject");
    }
  }
}
