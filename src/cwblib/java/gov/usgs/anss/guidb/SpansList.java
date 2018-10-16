/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;

import java.io.*;
import java.text.SimpleDateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import gov.usgs.anss.util.Util;

/**
 * This is a list of abstract time intervals called Spans. The type of elements
 * of this list is SpansList.Span.
 *
 * This is the common superclass of {@link HoldingsList} and {@link GapsList}.
 */
public class SpansList
{ protected static final DecimalFormat df2 = new DecimalFormat("0.00");
  protected static final SimpleDateFormat DATE_FORMAT
          = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  @Override
  public String toString() {return "sz="+list.size()+(list.size()>0?" 1st="+
          list.get(0).toString()+" lst="+list.get(list.size()-1).toString():"");
  }
  /**
   * This is the backing list that holds the Spans.
   */
  private ArrayList<Span> list;
  public int getSpansListSize() {return list.size();}
  //public Span getSpan(int i) {return list.get(i);}
  /**
   * A Span represents an interval of time with millisecond precision. Spans are
   * used by HoldingsList and GapsList to represent both holdings and the gaps
   * between them.
   *
   * @see HoldingsList
   * @see GapsList
   */
  public static class Span implements Comparable<Span>
  {
    public Date start;
    public Date end;
    @Override
    public String toString() {return start.toString()+"-"+end.toString();}
    /**
     * Create a new Span without setting its start and end dates.
     */
    public Span()
    {
    }
    
    /**
     * Create a new Span with the specified start and end dates.
     *
     * @param start The start date
     * @param end The end date
     */
    public Span(Date start, Date end)
    {
      this.start = start;
      this.end = end;
    }

    /**
     * Compare the start date of this Span to that of the one contained in o
     * (which must also be a Span object).
     *
     * @throws NullPointerException if either Span's start field is null
     * @throws ClassCastException if o is not a Span object
     */
    public int compareTo(Span o)
    {
      return start.compareTo( o.start);
    }
  }

  /**
   * Create a new, empty SpansList.
   */
  public SpansList()
  {
    list = new ArrayList<Span>();
  }
  /** Add a span to the end of the list.  Do not do anything if span length is zero or negative
   * @param start A milliseconds time since 1970
   * @param end A Milliseconds time since 1970
   * 
   */
  public void add(long start, long end) {
    add(new Span(new Date(start), new Date(end)));
  }
  /**
   * Add a Span to the end of the list. Don't do anything if the length of the
   * span is zero or negative.
   *
   * @param span The Span to add
   */
  public void add(Span span)
  {
    if (!span.start.before(span.end))
      return;

    list.add(span);
  }

  /**
   * Retrieve a Span from the list by index.
   *
   * @param index the index of the Span to retrieve
   * @return The span at index i
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  public Span get(int index)
  {
    return (Span) list.get(index);
  }

  /**
   * Return the number of Spans in the list.
   *
   * @return the number of Spans in the list.
   */
  public int size()
  {
    return list.size();
  }

  /**
   * Empty the list.
   */
  public void clear()
  {
    list.clear();
  }

  /**
   * Return the total length of the spans in this SpansList.
   *
   * @return the sum of the lengths of the spans, in milliseconds
   */
  public long getSpansLength()
  {
    Span span;
    long length;
    int i;

    length = 0;
    for (i = 0; i < size(); i++) {
      span = get(i);
      length += span.end.getTime() - span.start.getTime();
    }

    return length;
  }

  /**
   * Sort the list into ascending order by the start dates of the spans.
   *
   * @throws NullPointerException if any Span's start field is null.
   */
  public void sort()
  {
    Collections.sort(list);
  }

  /**
   * Consolidate this SpansList in place. Overlapping Spans are merged together
   * and the list is trimmed to the new size if necessary.
   */
  public void consolidate()
  {
    Span prev, cur;
    int prevIndex, curIndex;

    if (size() == 0)
      return;

    /* The algorithm below requires that the list be sorted in order by start
       date. */
    sort();

    /* Think of prev and cur as two pointers into the list. cur is advanced
       every iteration, and prev is advanced only when there is no overlap
       between prev and cur. If there is overlap, cur is merged onto prev. When
       cur has passed the last element, the list is trimmed right after prev. */
    prevIndex = 0;
    for (curIndex = 1; curIndex < size(); curIndex++) {
      prev = (Span) get(prevIndex);
      cur = (Span) get(curIndex);
      if (!cur.start.after(prev.end)) {
        /* Overlap. */
        if (cur.end.after(prev.end))
          prev.end = cur.end;
      } else {
        /* No overlap. Advance prev. */
        prevIndex++;
        prev = (Span) get(prevIndex);
        prev.start = cur.start;
        prev.end = cur.end;
      }
    }

    trimAfter(prevIndex);
  }

  /**
   * Return an Iterator over the endpoints of the Spans in the list. One Span's
   * start and end are returned in order before moving to the next.
   * @return An iterator
   */
  public Iterator endpointsIterator()
  {
    return new Iterator()
    {
      private int index = 0;
      private boolean state = false;

      public boolean hasNext()
      {
        return index < size();
      }

      public Object next()
      {
        Date d;

        if (!hasNext())
          throw new NoSuchElementException();
        if (state == false) {
          d = ((Span) list.get(index)).start;
        } else {
          d = ((Span) list.get(index)).end;
          index++;
        }
        state = !state;

        return d;
      }

      public void remove()
      {
      }
    };
  }

  /**
   * Write this list to writer. One Span is written per line, in the form
   * "start\tend", where '\t' represents a tab character.
   *
   * @param writer where to write the report
   */
  public void write(Writer writer)
  {
    PrintWriter out;
    Span span;
    int i;
    
    out = new PrintWriter(new BufferedWriter(writer));
    for (i = 0; i < size(); i++) {
      span = get(i);
      out.println(DATE_FORMAT.format(span.start) + "\t"
              + DATE_FORMAT.format(span.end)+"\t"+Util.leftPad(df2.format((span.end.getTime() - span.start.getTime()) / 86400000.0),12));
    }
    out.flush();
  }

  /**
   * Remove all of the Spans whose index is greater than index. Do nothing if
   * index is past the end of the list.
   *
   * @param the index position beyond which to remove all Spans
   */
  private void trimAfter(int index)
  {
    index++;

    while (list.size() > index)
      list.remove(index);
  }
}
