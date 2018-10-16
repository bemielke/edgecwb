/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;


import gov.usgs.anss.guidb.GapsList;
import gov.usgs.anss.guidb.SpansList;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;

import java.util.Date;

import gov.usgs.anss.util.Util;

/**
 * A sorted list of holdings. Each holding is represented by a {@link
 * SpansList.Span} object.
 */
public class HoldingsList extends SpansList
{
  public String hostName;
  public String seedName;
  public String type;
  public Date start;
  public Date end;

  /**
   * Create a new, empty HoldingsList.
   */
  public HoldingsList()
  {
    super();
  }

  /**
   * Create a new HoldingsList by complementing a GapsList. The holdings in the
   * returned list are guaranteed to be sorted by start date and not to overlap.
   *
   * @param gaps The GapsList from which to derive the HoldingsList.
   */
  public HoldingsList(GapsList gaps)
  {
    super();

    Span gap;
    Date prev;
    int i;

    hostName = gaps.hostName;
    seedName = gaps.seedName;
    type = gaps.type;
    start = gaps.start;
    end = gaps.end;

    gaps.sort();

    /* prev is the end of the previous gap, which becomes the start of the
       current holding. */
    prev = start;
    for (i = 0; i < gaps.size(); i++) {
      gap = gaps.get(i);
      if (start != null && gap.end.before(start))
        continue;
      if (end != null && gap.start.after(end))
        break;
      if (prev != null && prev.before(gap.start))
        add(new Span(prev, gap.start));
      if (prev == null || prev.before(gap.end))
        prev = gap.end;
    }
    if (prev != null && end != null && prev.before(end))
      add(new Span(prev, end));
  }

  /**
   * Create a new HoldingsList from a Connection.
   *
   * @param connection The Connection from which to read the holdings
   * @param seedName Get holdings for the given SEED name
   * @param type Get holdings for the given type
   */
  public HoldingsList(Connection connection, String seedName, String type)
          throws SQLException
  {
    this(connection, seedName, type, null, null);
  }

  /**
   * Create a new HoldingsList from a Connection. Optionally restrict the time
   * range for which holdings will be included. seedName, type, start, and end
   * are set, but hostName is not.
   *
   * If start is null, it will be set to the earliest start date retrieved, and
   * if end is null, it will be set to the latest end date retrieved. If start
   * or end is not null, holdings that overlap them will be truncated to fit.
   *
   * @param connection The Connection from which to read the holdings
   * @param seedName Get holdings for the given SEED name
   * @param type Get holdings for the given type
   * @param start If not null, do not include holdings that end before this
   *              date
   * @param end If not null, do not include holdings that start after this
   *            date
   */
  public HoldingsList(Connection connection, String seedName, String type,
          Date start, Date end) throws SQLException
  {
    /* The start and end dates for each holding are each broken into two fields:
       a timestamp truncated to the second (start, end) and an integral number
       of milliseconds (start_ms, end_ms). Our queries must reflect this split.
       These date formats are for the timestamp part and the millisecond part,
       respectively. */
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final SimpleDateFormat msFormat = new SimpleDateFormat("S");
    Span holding;
    Statement statement;
    ResultSet rs;
    String query;
    
    this.seedName = seedName;
    this.type = type;
    /* this.start and this.end may be modified below, but start and end will
       remain the same to remember whether they were given as null or not. */
    this.start = start;
    this.end = end;

    statement = connection.createStatement();
    for(int i=0; i<3; i++) {
      String table="holdingshist";
      if(i == 1) table = "holdings";
      if(i == 2) table = "holdingshist2";
      query = "SELECT start, start_ms, ended, end_ms from status."+table
              + " WHERE seedname=" + Util.sqlEscape(seedName)
              + " AND type=" + Util.sqlEscape(type);
      /* Select all the holdings that overlap the interval at all. If start or end
         is null, then there is no starting or ending limit, respectively. */
      /*if (start != null) {
        query += " AND (end > {ts '" + dateFormat.format(start) + "'}"
                + " OR (end = {ts '" + dateFormat.format(start) + "'}"
                + " AND end_ms > " + msFormat.format(start) + "))";
      }
      if (end != null) {
        query += " AND (start < {ts '" + dateFormat.format(end) + "'}"
                + " OR (start = {ts '" + dateFormat.format(end) + "'}"
                + " AND start_ms < " + msFormat.format(end) + "))";
      }*/
      if(start != null || end != null) query +=" AND NOT (";
      if(start != null) query += "ended <='"+dateFormat.format(start)+"'";
      if(start != null && end != null) query += " OR ";
      if(end != null) query += "start >='"+dateFormat.format(end)+"'";
      if(start != null || end != null) query +=")";
      query += " ORDER BY start, start_ms";
      Util.prt("query="+query);

      rs = statement.executeQuery(query);
      while (rs.next()) {
        holding = new Span();
        holding.start = new Date(rs.getTimestamp("start").getTime() + rs.getShort("start_ms"));
        holding.end = new Date(rs.getTimestamp("ended").getTime() + rs.getShort("end_ms"));

        /* If a start date was specified, truncate the holding to fit within it.
           If not, update the start date to completely contain the holding if
           necessary. */
        if (start != null && holding.start.before(start))
          holding.start = start;
        if (start == null
                && (this.start == null || holding.start.before(this.start)))
          this.start = holding.start;

        /* Likewise with the end. */
        if (end != null && holding.end.after(end))
          holding.end = end; 
        if (end == null
                && (this.end == null || holding.end.after(this.end)))
          this.end = holding.end;

        add(holding);
      }
    }
    statement.close();

    consolidate();
  }

  /**
   * Get the proportion of time used by the holdings in this list as a number
   * between 0.0 and 1.0.
   *
   * @return the proportion of time used by holdings
   */
  public float getAvailability()
  {
    return (float) getSpansLength() / (end.getTime() - start.getTime());
  }
}
