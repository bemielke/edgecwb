/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;

import gov.usgs.anss.guidb.SpansList;
import gov.usgs.anss.guidb.HoldingsList;
import java.io.*;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.*;

/**
 * A list of gaps between holdings. Each gap is represented by a {@link
 * SpansList.Span} object.
 */
public class GapsList extends SpansList
{
  public String hostName;
  public String seedName;
  public String type;
  public Date start;
  public Date end;
  @Override
  public String toString() {return seedName;}
  /**
   * Create a new, empty GapsList.
   */
  public GapsList()
  {
    super();
  }
  
  /**
   * Create a new GapsList by complementing a HoldingsList. The gaps in the
   * returned list are guaranteed to be sorted by start date and not to overlap.
   *
   * @param holdings The HoldingsList from which to derive the GapsList.
   */
  public GapsList(HoldingsList holdings)
  {
    super();

    Span holding;
    Date prev;
    int i;
    
    this.hostName = holdings.hostName;
    this.seedName = holdings.seedName;
    this.type = holdings.type;
    this.start = holdings.start;
    this.end = holdings.end;

    holdings.sort();

    /* prev is the end of the previous holding, which becomes the start of the
       current gap. */
    prev = start;
    for (i = 0; i < holdings.size(); i++) {
      holding = holdings.get(i);
      if (start != null && holding.end.before(start))
        continue;
      if (end != null && holding.start.after(end))
        break;
      if (prev != null && prev.before(holding.start))
        add(new Span(prev, holding.start));
      if (prev == null || prev.before(holding.end))
        prev = holding.end;
    }
    if (prev != null && end != null && prev.before(end))
      add(new Span(prev, end));
  }

  /**
   * Create a new GapsList from a gap report as produced by {@link
   * #write(Writer)}.
   *
   * @param reader The Reader from which to read the gap report
   */
  public GapsList(Reader reader) throws IOException, ParseException
  {
    this(reader, null, null);
  }

  /**
   * Get the proportion of time used by the holdings between gaps in this list
   * as a number between 0.0 and 1.0.
   *
   * @return the proportion of time not used by gaps
   */
  public float getAvailability()
  {
    return 1.0f - (float) getSpansLength() / (end.getTime() - start.getTime());
  }

  /**
   * Create a new GapsList from a gap report as produced by {@link
   * #write(Writer)}. Optionally restrict the time range for which holdings will
   * be included. If certain special comments are present, they are interpreted
   * and their values stored. If a start date or end date is present in the
   * report, gaps will be constrained to be in that range.
   *
   * @param reader The Reader from which to read the gap report
   * @param start If not null, do not include gaps that end before this date
   * @param end If not null, do not include gaps that start after this date
   */
  public GapsList(Reader reader,Date start, Date end) throws IOException,
    ParseException
  {
    super();

    BufferedReader in;
    String line;
    Matcher matcher;
    String[] dates;

    /* this.start and this.end may be modified below, but start and end will
       remain the same to remember whether they were given as null or not. */
    this.start = start;
    this.end = end;

    in = new BufferedReader(reader);
    line = in.readLine();
    if (line == null || !line.equalsIgnoreCase("# Gap report"))
      throw new ParseException("This does not appear to be a gap report.", 0);

    /* First build a list of gaps. It will be transformed into a list of
       holdings below. */
    while ((line = in.readLine()) != null) {
      if (line.equals(""))
        continue;
      matcher = Pattern.compile("# Host name: (.*)", Pattern.CASE_INSENSITIVE).matcher(line);
      if (matcher.matches()) {
        hostName = matcher.group(1);
        continue;
      }
      matcher = Pattern.compile("# SEED name: \"(.*)\"", Pattern.CASE_INSENSITIVE).matcher(line);
      if (matcher.matches()) {
        seedName = matcher.group(1);
        continue;
      }
      matcher = Pattern.compile("# Type: (.*)", Pattern.CASE_INSENSITIVE).matcher(line);
      if (matcher.matches()) {
        type = matcher.group(1);
        continue;
      }
      matcher = Pattern.compile("# Start date: (.*)", Pattern.CASE_INSENSITIVE).matcher(line);
      if (matcher.matches() && this.start == null) {
        this.start = DATE_FORMAT.parse(matcher.group(1));
        continue;
      }
      matcher = Pattern.compile("# End date: (.*)", Pattern.CASE_INSENSITIVE).matcher(line);
      if (matcher.matches() && this.end == null) {
        this.end = DATE_FORMAT.parse(matcher.group(1));
        continue;
      }
      matcher = Pattern.compile("#.*").matcher(line);
      if (matcher.matches())
        continue;

      Span gap = new Span();
      dates = line.split("\t");
      gap.start = DATE_FORMAT.parse(dates[0]);
      gap.end = DATE_FORMAT.parse(dates[1]);

      /* If a start date was specified, truncate the gap to fit within it. If
         not, update the start date to completely contain the gap if
         necessary. */
      if (start != null && gap.start.before(start))
        gap.start = start;
      if (start == null
              && (this.start == null || gap.start.before(this.start)))
        this.start = gap.start;

      /* Likewise with the end. */
      if (end != null && gap.end.after(end))
        gap.end = end; 
      if (end == null
              && (this.end == null || gap.end.after(this.end)))
        this.end = gap.end;

      add(gap);
    }

    sort();
  }

  /**
   * Write a textual description of this GapsList to writer.
   *
   * @param writer Where to write the report
   */
  @Override
  public void write(Writer writer)
  {
    PrintWriter out;
    
    out = new PrintWriter(new BufferedWriter(writer));
    out.println("# Gap report");
    if (hostName != null)
      out.println("# Host name: " + hostName);
    if (seedName != null)
      out.println("# SEED name: \"" + seedName + "\"");
    if (type != null)
      out.println("# Type: " + type);
    if (start != null)
      out.println("# Start date: " + DATE_FORMAT.format(start));
    if (end != null)
      out.println("# End date: " + DATE_FORMAT.format(end));
    out.println();
    out.flush();
    super.write(writer);
  }
  
  /**
   * Returns the size of the largest gap found in the GapsList, in milliseconds
   */
  public long getLongestGapLen(){
   long bigLen = Long.MIN_VALUE;
   for(int i = 0; i < size(); ++i){
      Span s = (Span) get(i);
      long len = s.end.getTime() - s.start.getTime();
      if(len > bigLen){
         bigLen = len;
      }
   }
   if(bigLen == Long.MIN_VALUE) bigLen=0;
   return bigLen;
  }
  
  /**
   * Returns the average gap length in the GapsList, in milliseconds
   */
  public long getAveGapLen(){
   return (size() > 0 ? getSpansLength()/size(): 0);
  }
}
