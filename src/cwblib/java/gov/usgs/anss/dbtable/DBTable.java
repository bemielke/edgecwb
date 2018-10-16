/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.dbtable;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.alarm.Event;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.ArrayList;
import java.sql.Timestamp;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;

/**
 * This class reads/writes text files representing database tables. It is used by the real time code
 * to decouple the real time from the DB being up.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class DBTable {

  private final String filename;
  private final ArrayList<ArrayList<StringBuilder>> rows = new ArrayList<>(100);  // One for each row with a column in the arraylist
  private final ArrayList<Integer> ids = new ArrayList<>(100);
  private final ArrayList<StringBuilder> fields = new ArrayList<>(10);
  private final ArrayList<StringBuilder> work = new ArrayList<>(10);
  private final EdgeThread par;
  private RandomAccessFile rw;
  private byte[] buf;
  private byte[] lastBuf;
  private int charsLength;
  private int irow;
  private boolean insertOccurred = false;     // if the table has had an insert since the last made from the DB
  private boolean updateOccurred = false;
  private long lastLoad;
  private final StringBuilder sbtmp = new StringBuilder(100);
  private boolean dbg = false;

  public void invalidate() {
    charsLength = -1;
    insertOccurred = true;
  }
  public void setDebug(boolean t) {
    if (!dbg) {
      dbg = t;
    }
  }

  public String getFilename() {
    return filename;
  }

  public int getNRows() {
    return rows.size();
  }

  public boolean hasInsertOccurred() {
    return insertOccurred;
  }

  public boolean hasUpdateOccurred() {
    return updateOccurred;
  }

  public void setInsertOccurred(boolean t) {
    insertOccurred = t;
    if (dbg) {
      prta("DBT: " + filename + " setInsert ");
    }
  }

  public void setUpdateOccurred(boolean t) {
    updateOccurred = t;
    if (dbg) {
      prta("DBT: " + filename + " setUpdate ");
    }
  }

  public long getLastLoad() {
    return lastLoad;
  }

  @Override
  public String toString() {
    String s = filename + " #row=" + irow + "/" + rows.size() + "/" + ids.size() + " fields=";
    for (StringBuilder sb : fields) {
      s += sb + "|";
    }
    return s;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public DBTable(String filename, EdgeThread parent) {
    par = parent;
    this.filename = filename;
    if (Util.chkFilePath(filename)) {
      prta("DBT: **** File paths created! " + filename);
    }
  }

  public synchronized void writeTable() throws IOException {
    // figure the max widths of the fields
    if(ids.isEmpty() || fields.isEmpty()) {
      prta("writeTable(): "+filename+" **** has not been intitialized!");
      return ;
    }            // There is nothing to write out!
    int[] widths = new int[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      if (fields.get(i).length() > widths[i]) {
        widths[i] = fields.get(i).length();
      }
    }
    // Figure max width of the data portion of the data
    for (int j = 0; j < ids.size(); j++) {
      for (int i = 0; i < fields.size(); i++) {
        if (rows.get(j).get(i).length() > widths[i]) {
          widths[i] = rows.get(j).get(i).length();
        }
      }
    }
    // Pad by two spaces
    for (int i = 0; i < widths.length; i++) {
      widths[i] += 2;
    }

    Util.clear(sbtmp); // zap the buffer
    // Write out the header line
    sbtmp.append("|");
    for (int i = 1; i < fields.size(); i++) {
      Util.clear(tmpload).append(fields.get(i));
      sbtmp.append(Util.centerPad(tmpload, widths[i])).append("|");
    }

    // write out each row of data into the StringBuilder
    sbtmp.append("\n");
    for (int i = 0; i < ids.size(); i++) {       // for each row
      sbtmp.append("|");
      for (int col = 1; col < rows.get(i).size(); col++) {   //DEBUG: columns start with 1 with ID            
        //sqlfile.append(Util.getString(rs,column).replaceAll("\\|", "&!BS!&")).append("|"); // convert pipes in strings to marker
        Util.clear(tmpload).append(rows.get(i).get(col));
        Util.stringBuilderReplaceAll(tmpload, "|", "&!BS!&");
        sbtmp.append(Util.centerPad(tmpload, widths[col])).append("|");
      }
      sbtmp.append("\n");
    }
    //prta("DBTable.writeTable() len="+sbtmp.length()+"\n"+sbtmp);
    //new RuntimeException("Testing writeTable").printStackTrace(par.getPrintStream());
    // Write the string Builder into the file.
    try ( // Write out the file
            RandomAccessFile out = new RandomAccessFile(filename + "tmp", "rw")) {
      out.seek(0L);
      if (sbtmp.length() > buf.length) {
        buf = new byte[sbtmp.length() * 2];
        lastBuf = new byte[sbtmp.length() * 2];
      }
      charsLength = sbtmp.length();
      for (int i = 0; i < sbtmp.length(); i++) {
        buf[i] = (byte) sbtmp.charAt(i);
      }
      out.write(buf, 0, sbtmp.length());
    }
    // Now rename the just created file, this keeps from writing in the same place as well
    File out = new File(filename + "tmp");
    File file = new File(filename);
    if (out.exists()) {
      out.renameTo(file);
    }
    prta("DBT: * writeTable() " + filename + " size=" + sbtmp.length() + " ins=" + insertOccurred + " upd=" + updateOccurred + " reset them");
    insertOccurred = false;
    updateOccurred = false;
  }

  private ArrayList<StringBuilder> newRow = new ArrayList<>(10);

  public synchronized void newRow() {
    if (newRow.size() < fields.size()) {
      for (StringBuilder field : fields) {
        newRow.add(new StringBuilder(10));
      }
    } else {
      for (StringBuilder sb : newRow) {
        Util.clear(sb);
      }
    }
  }

  public synchronized int updateRow() {
    int max = -1;
    for (Integer id : ids) {
      max = Math.max(max, id);
    }
    ids.add(max + 1);
    Util.clear(newRow.get(1)).append((max + 1));    // ID always in 1 
    rows.add(newRow);
    Util.clear(sbtmp).append("Update row id=").append(max + 1).append(" ");
    for (int i = 1; i < newRow.size(); i++) {
      sbtmp.append(i).append(" ").append(fields.get(i)).append("=").append(newRow.get(i));
    }
    newRow = new ArrayList<>(1);          // since we just added this ArrayList to rows, we have to have a new one.
    prta("updateRow : " + sbtmp);
    insertOccurred = true;
    return max + 1;
  }

  public synchronized void updateString(String key, String s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateString newrow=" + irow + " " + key + "=" + s);
    }
    Util.clear(newRow.get(col)).append(s);
  }

  public synchronized void updateString(StringBuilder key, StringBuilder s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateString newrow=" + irow + " " + key + "=" + s);
    }
    Util.clear(newRow.get(col)).append(s);
  }

  public synchronized void updateInt(String key, int s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateInt newrow=" + irow + " " + key + "=" + s);
    }
    Util.clear(newRow.get(col)).append(s);
  }

  public synchronized void updateLong(String key, long s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateLong newrow=" + irow + " " + key + "=" + s);
    }
    Util.clear(newRow.get(col)).append(s);
  }

  public synchronized void updateDouble(String key, double s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateDouble newrow=" + irow + " " + key + "=" + s);
    }
    Util.clear(newRow.get(col)).append(s);
  }

  public synchronized void updateTimestamp(String key, Timestamp s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateTimestamp newrow=" + irow + " " + key + "=" + s);
    }
    Util.clear(newRow.get(col)).append(s.toString().substring(0, 19));
  }

  public synchronized void updateString(int row, String key, String s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateString irow=" + irow + " " + key + "=" + s + " was "+rows.get(row).get(col));
    }
    Util.clear(rows.get(row).get(col)).append(s);
    updateOccurred = true;

  }

  public synchronized void updateInt(int row, String key, int s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateInt irow=" + irow + " " + key + "=" + s+ " was "+rows.get(row).get(col));
    }
    Util.clear(rows.get(row).get(col)).append(s);
    updateOccurred = true;

  }

  public synchronized void updateLong(int row, String key, long s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateLong irow=" + irow + " " + key + "=" + s+ " was "+rows.get(row).get(col));
    }
    Util.clear(rows.get(row).get(col)).append(s);
    updateOccurred = true;

  }

  public synchronized void updateDouble(int row, String key, double s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateDouble irow=" + irow + " " + key + "=" + s + " was "+rows.get(row).get(col));
    }    
    Util.clear(rows.get(row).get(col)).append(s);
    updateOccurred = true;

  }

  /**
   * return the value given a row and column
   *
   * @param row given a row and column, return the string from the database
   * @param col the column
   * @return StringBuilder with contents
   */
  public synchronized StringBuilder getValue(int row, int col) {
    if (row < 0 || row > rows.size()) {
      return null;
    }
    if (col < 0 || col > rows.get(row).size()) {
      return null;
    }
    return rows.get(row).get(col);
  }

  /**
   *
   * @param col column to get
   * @return the header or field name of this column
   */
  public synchronized StringBuilder getField(int col) {
    return fields.get(col);
  }

  public synchronized int findRowWithID(int ID) {
    for (int i = 0; i < rows.size(); i++) {
      int col = findCol("id");
      if (Util.stringBuilderEqual(rows.get(i).get(col), "" + ID)) {
        return i;
      }
    }
    return -1;
  }

  public synchronized void updateTimestamp(int row, String key, Timestamp s) {
    int col = findCol(key);
    if (dbg) {
      prta("DBT: " + filename + " updateTimestamp irow=" + irow + " " + key + "=" + s+ " was "+rows.get(row).get(col));
    }    
    Util.clear(rows.get(row).get(col)).append(s.toString().substring(0, 19));
    updateOccurred = true;

  }

  public void init(String s) throws IOException {
    rw = new RandomAccessFile(filename, "rw");
    rw.seek(0L);
    rw.write(s.getBytes(), 0, s.length());
    rw.setLength(s.length());
    rw.close();
  }
  StringBuilder line = new StringBuilder(100);
  StringBuilder tmpload = new StringBuilder(20);

  /**
   *
   * @return if true, the tables has changed and the result set is ready to read back
   * @throws IOException
   */
  public synchronized boolean load() throws IOException {
    rw = new RandomAccessFile(filename, "r");
    rw.seek(0L);
    int rwlength = (int) rw.length();
    lastLoad = System.currentTimeMillis();
    boolean changed = false;
    if (buf == null) {
      buf = new byte[(int) rwlength * 2];
      lastBuf = new byte[(int) rwlength * 2];
      changed = true;
    }
    if (buf.length < rwlength) {
      buf = new byte[(int) rwlength * 2];
      lastBuf = new byte[(int) rwlength * 2];
      changed = true;
    }
    rw.read(buf, 0, (int) rwlength);
    rw.close();
    if (rwlength != charsLength) {
      changed = true;
      prta("DBT: " + filename + " has changed length=" + rwlength + " was " + charsLength);    // DEBUG
    } else {
      for (int i = 0; i < rwlength; i++) {
        if (buf[i] != lastBuf[i]) {
          changed = true;
          prta("DBT: " + filename + " has changed content");
          break;
        }
      }
      if (!changed) {
        prta("DBT: " + filename + " is the same!");
        updateOccurred = false;
        insertOccurred = false;
      }
    }
    if (!changed) {
      return changed;      // Nothing has 
    }
    charsLength = (int) rwlength;
    System.arraycopy(buf, 0, lastBuf, 0, rwlength); // Set the string builder
    boolean headers = false;
    ids.clear();
    //fields.clear();
    Util.clear(line);
    char lastChar = '{';
    int ifield = 0;
    int row = 0;         // Use this varible to trace rows read into the table, its used as a readback pointer later.
    int nlines = 0;
    boolean eolLast = false;
    for (int jj = 0; jj < rwlength; jj++) {
      char chr = (char) buf[jj];
      //if(ifield ==0 && work.size() == 0 && chr == '+') continue;    // skip any lines staring with pipe
      //if(ifield == 0 && work.size() > 0 && work.get(0).length() == 0 && chr == '+') continue;  // skip any lines starting with pipe
      if (chr == '\n' && lastChar == '|') {                   // End of a line
        nlines++;
        if (ifield == 0 && fields.get(0).length() == 0 && chr == '+') {     // is this a plus line meaning its nothting
          ifield = 0;
          for (StringBuilder sb : work) {
            Util.clear(sb);
          }
          continue;
        }

        if (!headers) {    // decode the headers as the first line without a plus sign, store in fields
          int col = 0;
          for (StringBuilder sb : work) {
            //prta("DBT: load field "+sb+" pos="+fields.size()+" col="+col);
            Util.sbToLowerCase(sb, tmpload);
            Util.trim(sb);
            if (col >= fields.size()) {
              fields.add(new StringBuilder(sb.length()).append(sb));
            } else {
              Util.clear(fields.get(col)).append(sb);
            }
            col++;
            //prta("DBT: lower field ="+sb+" field="+fields.get(fields.size()-1));
          }
          //prta("DBT: *** "+filename+" fields decoded="+fields.size()+" fields[1]="+fields.get(1));
          work.clear();
          ifield = 0;
          eolLast = true;
          headers = true;
        } else {                          // This is a data row, add or replace the data in the row.
          if (work.size() != fields.size()) {
            prta("DBT: **** " + filename + " #fields=" + fields.size() + " does not equal row columns=" + work.size()
                    + " id=" + work.get(1) + " row=" + row + "/" + nlines + " Probably a tab or pipe in some field");
            new IOException("DBA: **** " + filename + " #fields=" + fields.size() + " does not equal row columns=" + work.size()
                    + " id=" + work.get(1) + " row=" + row + "/" + nlines + " Probably a tab or pipe in some field").printStackTrace(par.getPrintStream());
            work.clear();
          } else {
            if (row < rows.size()) {      // does this row exist in table already
              ids.add(row, (int) Util.sbToLong(work.get(1)));       // save the ID in the ids array.
              for (int i = 0; i < work.size(); i++) {    // yes, for each column copy the data
                Util.clear(rows.get(row).get(i)).append(work.get(i));
                Util.clear(work.get(i));      // clear out the text for this column in working
              }
              row++;
            } else {
              // Add the work columns to a new row
              ArrayList<StringBuilder> r = new ArrayList<StringBuilder>(fields.size());
              rows.add(r);
              for (StringBuilder w : work) {
                r.add(new StringBuilder().append(w));
              }
              ids.add((int) Util.sbToLong(work.get(1)));       // save the ID in the ids array.
              work.clear();           // clear out the work array so new ones will be created
              row++;
            }
          }
          ifield = 0;                     // on exit this is a new line
          eolLast = true;
        }
      } else if (chr == '|') {               // This is the end of a field
        if (work.size() < ifield) {
          prta("DBT: ifield=" + ifield + " size=" + work.size() + " nlines=" + nlines + " row=" + row);
        }
        if (work.size() == ifield) {
          work.add(new StringBuilder(1));  // The first pipe and empty fields
        }
        Util.trim(work.get(ifield));
        eolLast = false;                // maybe a starting pipe, but reset anyway
        ifield++;
      } else {                              // This is just another character to add into the work group.
        if (work.size() == ifield) {
          work.add(new StringBuilder(10));  // work needs to be expanded
        }
        // if this is right after a EOL, then there is not a leading pipe, create empty first column in work
        if (eolLast) {
          if (work.size() == ifield + 1) {
            work.add(new StringBuilder(1));
          }
          eolLast = false;
          ifield++;                       // some files do not have leading pipes, if so, create the empty first field
        }
        work.get(ifield).append(chr);
      }
      lastChar = chr;
    }
    irow = -1;
    prta("DBT: " + filename + " load complete nlines=" + nlines + " ids.size=" + ids.size() + " fields.size=" + fields.size() + " rwlength=" + rwlength);
    return changed;
  }

  public boolean hasNext() {
    return (irow + 1 < ids.size());
  }

  public void next() {
    irow++;
  }

  private synchronized int findCol(String key) throws ArrayIndexOutOfBoundsException {
    int col;
    for (col = 0; col < fields.size(); col++) {
      if (Util.stringBuilderEqualIgnoreCase(fields.get(col), key)) {
        break;
      }
    }
    if (col >= fields.size()) {
      throw new ArrayIndexOutOfBoundsException("Cannot find field '" + key + "' in the fields in table " + filename + "! #fields=" + fields.size() + " [1]=" + fields.get(1) + toString());
    }
    return col;
  }

  private synchronized int findCol(StringBuilder key) throws ArrayIndexOutOfBoundsException {
    int col;
    for (col = 0; col < fields.size(); col++) {
      if (Util.stringBuilderEqualIgnoreCase(fields.get(col), key)) {
        break;
      }
    }
    if (col >= fields.size()) {
      throw new ArrayIndexOutOfBoundsException("Cannot find field '" + key + "' in the fields in table " + filename + "! #fields=" + fields.size() + " [1]=" + fields.get(1) + toString());
    }
    return col;
  }

  public synchronized short getShort(String key) throws ArrayIndexOutOfBoundsException {
    return (short) Util.sbToLong(rows.get(irow).get(findCol(key)));
  }

  public synchronized int getInt(String key) throws ArrayIndexOutOfBoundsException {
    return (int) Util.sbToLong(rows.get(irow).get(findCol(key)));
  }

  public synchronized double getDouble(String key) throws ArrayIndexOutOfBoundsException {
    String s = rows.get(irow).get(findCol(key)).toString();
    if (s.equals("")) {
      s = "0";
    }
    return Double.parseDouble(s);
  }

  public synchronized long getLong(String key) throws ArrayIndexOutOfBoundsException {
    return Util.sbToLong(rows.get(irow).get(findCol(key)));
  }

  public synchronized String getString(String key) throws ArrayIndexOutOfBoundsException {
    return Util.stringBuilderReplaceAll(rows.get(irow).get(findCol(key)), "&!BS!&", "|").toString();// convert back to pipe
  }

  public synchronized Timestamp getTimestamp(String key) throws ArrayIndexOutOfBoundsException {
    String s = rows.get(irow).get(findCol(key)).toString();
    if (s.equals("")) {
      return new Timestamp(System.currentTimeMillis());
    }
    s = s.replaceAll("/", "-");
    return Timestamp.valueOf(s);
  }

  public synchronized int getInt(int key) throws ArrayIndexOutOfBoundsException {
    return (int) Util.sbToLong(rows.get(irow).get(key));
  }

  public synchronized double getDouble(int key) throws ArrayIndexOutOfBoundsException {
    int id = ids.get(irow);
    String s = rows.get(irow).get(key).toString().trim();
    if (s.equals("")) {
      s = "0";
    }
    return Double.parseDouble(s);
  }

  public synchronized short getShort(int key) throws ArrayIndexOutOfBoundsException {
    return (short) Util.sbToLong(rows.get(irow).get(key));
  }

  public synchronized long getLong(int key) throws ArrayIndexOutOfBoundsException {
    return Util.sbToLong(rows.get(irow).get(key));
  }

  public synchronized String getString(int key) throws ArrayIndexOutOfBoundsException {
    return rows.get(irow).get(key).toString();
  }

  public synchronized Timestamp getTimestamp(int key) throws ArrayIndexOutOfBoundsException {
    return Timestamp.valueOf(rows.get(irow).get(key).toString().replaceAll("/", "-"));

  }

  public int getRow() {
    return irow;
  }

  public synchronized int getNCols() {
    return fields.size();
  }

  public synchronized int getNrows() {
    return ids.size();
  }
  StringBuilder sqlfile = new StringBuilder(10);
  StringBuilder sqlhdr = new StringBuilder(10);

  public synchronized void makeDBTableFromDB(ResultSet rs) throws SQLException, IOException {
    Util.clear(sqlfile);
    Util.clear(sqlhdr);
    insertOccurred = false;
    updateOccurred = false;
    ResultSetMetaData meta = rs.getMetaData();
    sqlhdr.append("|");
    int ncol = meta.getColumnCount();
    int[] type = new int[ncol + 1];
    for (int i = 1; i <= ncol; i++) {
      sqlhdr.append(meta.getColumnName(i)).append("|");
      type[i] = meta.getColumnType(i);

    }
    sqlhdr.append("\n");
    int id = 0;
    int row = 0;
    // Now do the columns
    while (rs.next()) {
      int column = 0;
      try {
        sqlfile.append("|");
        id = rs.getInt("ID");
        for (column = 1; column < ncol + 1; column++) {
          switch (type[column]) {
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
              //case Types.TEXT:
              sqlfile.append(Util.getString(rs, column).replaceAll("\\|", "&!BS!&")).append("|"); // convert pipes in strings to marker
              break;
            case Types.DATE:
              //Date d = Util.getDate(rs,name);
              Date d = Util.getDate(rs, column);
              String s = Util.dateToString(d);
              sqlfile.append(s).append("|");
              break;
            case Types.DECIMAL:
            case Types.DOUBLE:
              //double dbl = Util.getDouble(rs,name);
              sqlfile.append(Util.getDouble(rs, column)).append("|");
              break;
            case Types.REAL:
            case Types.FLOAT:
              //float flt = Util.getFloat(rs,name);
              sqlfile.append(Util.getFloat(rs, column)).append("|");
              break;
            case Types.INTEGER:
              //int i = Util.getInt(rs,name);
              sqlfile.append(Util.getInt(rs, column)).append("|");
              break;
            case Types.BIGINT:
              //long l = Util.getLong(rs,name);
              sqlfile.append(Util.getLong(rs, column)).append("|");
              break;
            case Types.SMALLINT:
              //short is = Util.getShort(rs,name);
              sqlfile.append(Util.getShort(rs, column)).append("|");
              break;
            case Types.TIME:
              //Time t = Util.getTime(rs,name);
              Time t = Util.getTime(rs, column);
              s = Util.timeToString(t);
              sqlfile.append(s);
              break;
            case Types.TIMESTAMP:
              sqlfile.append(Util.getTimestamp(rs, column).toString()).append("|");
              break;
            case Types.TINYINT:
              //byte b = Util.getByte(rs,name);
              sqlfile.append(Util.getByte(rs, column)).append("|");
              break;
            case Types.NUMERIC:
            case Types.NULL:
            case Types.VARBINARY: // certain functioned columns come this way in mysql
              //byte [] bb = rs.getBytes(column);
              sqlfile.append(rs.getString(column)).append("|");
              break;
            default:
              // See if this is a PostgreSQL enumc
              try {
                Object pgObject = rs.getObject(column);
                Method valueMethod = pgObject.getClass().getMethod("getValue");
                s = (String) valueMethod.invoke(pgObject);
                sqlfile.append(s).append("|");
              } catch (SQLException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                prt("DBT: Type is unknown and not PG Object e=" + e);
              }
              prt("DBT: Type not implemented id=" + id + " col=" + column + " colname=" + meta.getColumnName(column)
                      + " type=" + type[column] + " typeName=" + meta.getColumnTypeName(column));
              new RuntimeException(
                      "Type not implemented id=" + id + " col=" + column + " colname=" + meta.getColumnName(column)
                      + " type=" + type[column] + " typeName=" + meta.getColumnTypeName(column)).printStackTrace();
              sqlfile.append("** type unknown");
            //Util.exit(0);
          }
        }     // for on each colum
        sqlfile.append("\n");

      } catch (SQLException | RuntimeException e) {
        prt("DBT: ID=" + id + " column=" + column + " type=" + type[column] + " field=" + (fields.size() > column ? getField(column) : "null"));
        e.printStackTrace();
        throw e;
      }
      row++;
    }
    sqlhdr.append(sqlfile);
    if (buf == null) {
      buf = new byte[(int) sqlhdr.length() * 2];
      lastBuf = new byte[(int) sqlhdr.length() * 2];
    }
    if (buf.length < sqlhdr.length()) {
      buf = new byte[(int) sqlhdr.length() * 2];
      lastBuf = new byte[(int) sqlhdr.length() * 2];
    }
    for (int i = 0; i < sqlhdr.length(); i++) {
      buf[i] = (byte) sqlhdr.charAt(i);
    }
    try (RandomAccessFile rwout = new RandomAccessFile(filename + "tmp", "rw")) {
      rwout.write(buf, 0, sqlhdr.length());
      rwout.setLength(sqlhdr.length());
    }
    File out = new File(filename + "tmp");
    File file = new File(filename);

    prta("DBT: makeDBTableFromDB() move " + out + " to " + file);
    if (out.exists()) {
      out.renameTo(file);
    }

  }

  public static void main(String[] args) {
    try {
      Util.init("edge.prop");
      DBConnectionThread db = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss", false, false, "anss", Util.getOutput());
      if (!db.waitForConnection()) {
        if (!db.waitForConnection()) {
          if (!db.waitForConnection()) {
            Util.prt("DBT: main() Could not connect to database!");
            Util.exit(1);
          }
        }
      }

      if (args.length > 0) {
        for (String arg : args) {
          Util.prt(" " + arg);
          String[] parts = arg.split(":");
          try (ResultSet rs = db.executeQuery("SELECT * FROM " + parts[0])) {
            DBTable table = new DBTable(parts[1], null);
            table.makeDBTableFromDB(rs);
            table.load();
          }
        }
        System.exit(0);
      }
      DBTable t = new DBTable("DB/edge_channel.txt", null);
      t.load();
      while (t.hasNext()) {
        t.next();
        Channel c = new Channel(t);
        Util.prta("c=" + c + " created=" + c.getCreated());
      }
      ResultSet rs = db.executeQuery("SELECT * FROM alarm.event");
      DBTable table = new DBTable("DB/alarm_event.txt", null);
      table.makeDBTableFromDB(rs);
      table.load();
      ArrayList<Event> events = new ArrayList<Event>(10);
      while (table.hasNext()) {
        table.next();
        Event ev = new Event(table);
        events.add(ev);
        Util.prt("Event=" + ev.getID() + " " + ev.getPhrase() + " " + ev.toString3());
      }
      table.load();
      DBTable snwgroup = new DBTable("DB/edge_snwgroup.txt", null);
      if (snwgroup.load()) {
        Util.prt("SNWGRoup loaded " + snwgroup + " ev.size()=" + events.size());
      }
    } catch (SQLException | IOException | InstantiationException e) {
      e.printStackTrace();
    }
    DBTable sendto = new DBTable("DB/edge_sendto.txt", null);
    for (;;) {
      try {
        if (sendto.load()) {
          while (sendto.hasNext()) {
            sendto.next();
            Util.prt("id=" + sendto.getInt("id") + " " + sendto.getString("sendto") + " " + sendto.getString("description") + " " + sendto
                    + " fs=" + sendto.getInt("filesize") + " path=" + sendto.getString("filepath") + " updms=" + sendto.getInt("updatems")
                    + " class=" + sendto.getString("class") + " args=" + sendto.getString("args") + " allowr="
                    + sendto.getInt("allowrestricted") + " nodes=" + sendto.getString("nodes") + " auto=" + sendto.getString("autoset")
                    + " updated=" + sendto.getTimestamp("updated"));
          }
        }
      } catch (IOException | ArrayIndexOutOfBoundsException e) {
        e.printStackTrace();
        Util.prt("error");
      }
      Util.sleep(10000);
    }
  }
}
