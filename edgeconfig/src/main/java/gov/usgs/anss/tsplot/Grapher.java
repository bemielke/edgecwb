/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import gov.usgs.anss.edge.*;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.awt.geom.Line2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/*
 * Grapher.java
 *
 * Created on July 5, 2006, 10:20 AM
 * By Jeremy Powell
 *
 * Collects the points of data and builds lines connecting the points to be displayed.
 */
public final class Grapher {

  private MSGroup[] MSGroups;
  private VisibleGraph[] vizGraphs;

  private Date origStartTime;   // the start/end time of the queired data
  private Date origEndTime;
  private Date vizStartTime;    // the start/end of the visible data/graph
  private Date vizEndTime;

  private int largestNumData;   // track the largest amount of data we get
  private int numGraphs;   // the number of blocks/graphs the grapher examined
  private int numVizGraphs;   // number of graphs that have visible data

  public int GRAPH_WIDTH = 1000;   // the number of pixels across the screen

  public void setGraphWidth(int pixels) {
    GRAPH_WIDTH = pixels;
  }

  /**
   * Creates a new instance of Grapher
   */
  public Grapher() {
  }

  /**
   * Builds the graphs to display and puts them into the lines ArrayList, accessed with getLines(i),
   * i being the index of the graph you want
   *
   * @param clean - true if you want to create graphs that are clean, false if you want precise
   * values that can show overlaps
   */
  public void buildGraphs(boolean clean) {
    // figure out the start/end time of the whole timeseries
    //buildGraphs(clean, 0, (int)(origEndTime.getTime()-origStartTime.getTime())/1000);
    int start = (int) (vizStartTime.getTime() - origStartTime.getTime()) / 1000;
    int end = (int) (vizEndTime.getTime() - origStartTime.getTime()) / 1000;
    buildGraphs(clean, start, end);
  }

  /**
   * Builds the graphs to display and puts them into the lines ArrayList.
   *
   * @param clean - true if you want to create graphs that are clean, false if you want precise
   * values that can show gaps or overlaps
   * @param startSec - the seconds value to start graphing after the query start time
   * @param endSec - the seconds value to end graphing after the query start time
   */
  public void buildGraphs(boolean clean, int startSec, int endSec) {
    vizStartTime = new Date();
    vizEndTime = new Date();
    vizStartTime.setTime(origStartTime.getTime() + startSec * 1000);
    vizEndTime.setTime(origStartTime.getTime() + endSec * 1000);

    double secPerPixel = (double) (endSec - startSec) / (double) GRAPH_WIDTH;
//        if(secPerPixel < 1) secPerPixel = 1;

    vizGraphs = new VisibleGraph[MSGroups.length];
    numVizGraphs = 0;
    for (int i = 0; i < numGraphs; ++i) {
      MSGroup group = MSGroups[i];            // one group is an entire graph
      long groupStartInMilli = group.getStartTime().getTimeInMillis();
      double rate = group.getRate();

      Date groupStart = group.getStartTime().getTime();
      Date groupEnd = group.getEndTime().getTime();

      int[] graphBreakValues = group.getBreakValues(); // get break values and times
      long[] graphBreakTimes = group.getBreakTimes(); // times in milliseconds
      long[] breakEnds = new long[group.getBreakValues().length];

      for (int brk = 0; brk < graphBreakValues.length; ++brk) {
        int numData;
        if (brk == graphBreakValues.length - 1) {
          numData = group.getNumData() - graphBreakValues[brk];
        } else {
          numData = graphBreakValues[brk + 1] - graphBreakValues[brk];
        }
        long dataElapse = (long) ((double) numData / rate * 1000.);   // in milliseconds
        breakEnds[brk] = graphBreakTimes[brk] + dataElapse;
      }

      boolean noVizDataAvailable = false;
      for (int brk = 0; brk < breakEnds.length - 1; ++brk) {
        if (breakEnds[brk] < vizStartTime.getTime() && graphBreakTimes[brk + 1] > vizEndTime.getTime()) {
          noVizDataAvailable = true;
          break;
        }
      }

      // does the MSGroup have values that are within the visible graphing region?
      if (noVizDataAvailable) {
        vizGraphs[i] = new VisibleGraph(0, 0, new ArrayList(), new int[0]);
      } else if (((groupStart.after(vizStartTime) && groupStart.before(vizEndTime))
              || (groupEnd.before(vizEndTime) && groupEnd.after(vizStartTime)))
              || (groupStart.before(vizStartTime) && groupStart.before(vizEndTime)
              && groupEnd.after(vizStartTime) && groupEnd.after(vizEndTime))) {
        ++numVizGraphs;

        int offsetVal = 25;    // the value to offset the graph on a break
        int offset = 0;

        // get the number of possible viewable data
        double numData = (endSec - startSec) * rate;

        // see if we need to scale the x axis
        double xSpacing = 1;
        double scale = numData / GRAPH_WIDTH;   // data per pixel
        if (scale < 1) {
          scale = 1;
          xSpacing = GRAPH_WIDTH / numData;
        }
// JP:  round the scale up or down?  wont matter if they search for a days worth of data

        // start/end time of the vizible graph in milliseconds
        long vizStartInMilli = vizStartTime.getTime();
        long vizEndInMilli = vizEndTime.getTime();

        long diff = vizStartInMilli - groupStartInMilli;
        int dataStartIndex = 0;     // where to start getting the data
        double xPos = 0;            // where to start drawing the graphs

        if (diff > 0) {   // starts before the visual area
          // figure out how far into the data it should start to build the lines
          long timeToUse = groupStart.getTime();

          for (int brk = 0; brk < graphBreakTimes.length; ++brk) {
            if (graphBreakTimes[brk] >= timeToUse
                    && ((graphBreakTimes[brk] > vizStartInMilli && graphBreakTimes[brk] < vizEndInMilli)
                    || (graphBreakTimes[brk] < vizStartInMilli && breakEnds[brk] > vizStartInMilli))) {
              timeToUse = graphBreakTimes[brk];
              dataStartIndex = (int) graphBreakValues[brk];
              break;
            }
          }

          Date useDate = new Date();
          useDate.setTime(timeToUse);

          long secondDiff = vizStartInMilli - timeToUse;

          if (secondDiff > 0) {
            dataStartIndex += (int) (secondDiff / 1000 * rate);
          } else {
            double dur = endSec - startSec;
            double movePixels = ((double) Math.abs(secondDiff) / 1000.) / dur;
            xPos = movePixels * GRAPH_WIDTH;
            xPos = Math.floor(xPos + .5);
          }
        } else {   // starts after or at the begining of visual graph
          // figure out the xposition to begin drawing
          double dur = endSec - startSec;
          double movePixels = ((double) Math.abs(diff) / 1000.) / dur;
          xPos = movePixels * GRAPH_WIDTH;
          // round the xPos to the nearest int value (pixel)
          xPos = Math.floor(xPos + .5);
        }

        // go through the data, create points and lines to display later
        ArrayList<Line2D.Double> tmpLines = new ArrayList<Line2D.Double>();

        int vizMax = Integer.MIN_VALUE;  // values to hold the max/min that is being displayed
        int vizMin = Integer.MAX_VALUE;

        int[] vizBreakVals = new int[graphBreakValues.length];   // will have extra 0's at end
        int vizBreakCount = 0;

        // numData/scale should always be GRAPH_WIDTH
        int arrayIndex = dataStartIndex;

        // get the initial point
        int max1 = Integer.MIN_VALUE;         // max and min of this data
        int min1 = Integer.MAX_VALUE;
        // get the max/min  of the first set of data that will make a single point
        for (int counter = 0; counter < scale && arrayIndex + counter < group.getNumData(); ++counter) {
          int val = group.getData(arrayIndex + counter);
          if (val > max1) {
            max1 = val;
          }
          if (val < min1) {
            min1 = val;
          }
        }
        for (int j = 0; j < GRAPH_WIDTH - 1; ++j) {
          // check if the graph is noncontinuous (change offset)
          for (int brk = 0; brk < graphBreakValues.length; ++brk) {
            // we average a number of data (scale) into one point, so we need to see if
            // any of those values were a breakValue.  check btwn 
            // arrayIndex and arrayIndex + scale to see if breakValue is there
            if (graphBreakValues[brk] > arrayIndex && graphBreakValues[brk] <= arrayIndex + scale) {
              // dont offset if its a clean graph
              if (!clean) {
                offset += offsetVal;      // set Offset
                offsetVal = -offsetVal;   // flip offsetValue
              }

              vizBreakVals[vizBreakCount] = j;
              ++vizBreakCount;

              // move the xPosition
              long elapsed = graphBreakTimes[brk] - vizStartInMilli;
              // sec * pix/sec gives num of pixels to skip
              double dur = endSec - startSec;
              double movePixels = ((double) elapsed / 1000.) / dur;
              xPos = movePixels * GRAPH_WIDTH;
              // round the xPos to the nearest int value (pixel)
              xPos = Math.floor(xPos + .5);

              break;
            }
          }

          int max2 = Integer.MIN_VALUE;
          int min2 = Integer.MAX_VALUE;
          // get the max/min  of the second set of data that will make a single point
          for (int counter = (int) scale; counter < 2. * scale
                  && arrayIndex + counter < group.getNumData(); ++counter) {
            int val = group.getData(arrayIndex + counter);
            if (val > max2) {
              max2 = val;
            }
            if (val < min2) {
              min2 = val;
            }

          }
          if (max2 != Integer.MIN_VALUE && min2 != Integer.MAX_VALUE
                  && max1 != Integer.MIN_VALUE && min1 != Integer.MAX_VALUE) {
            double ave1 = (max1 + min1) / 2.;   // this will round down every time, switch back and forth?
            double ave2 = (max2 + min2) / 2.;

            // see if these values are the max or mins
            if (ave1 > vizMax) {
              vizMax = (int) (ave1 + .5);
            }
            if (ave1 < vizMin) {
              vizMin = (int) (ave1 + .5);
            }
            if (ave2 > vizMax) {
              vizMax = (int) (ave2 + .5);
            }
            if (ave2 < vizMin) {
              vizMin = (int) (ave2 + .5);
            }

            Line2D.Double line = new Line2D.Double(
                    xPos + .5, ave1 + offset, xPos + xSpacing + .5, ave2 + offset);
            tmpLines.add(line);

            max1 = max2;  // save this data for the next calculation
            min1 = min2;

            xPos += xSpacing;
          }// else data wasnt set, so do nothing -- end of data? break?
//                    if(scale < 1)  ++arrayIndex;
//                    else arrayIndex += scale;
          arrayIndex += scale;
        }
        int[] tmp = new int[vizBreakCount];
        System.arraycopy(vizBreakVals, 0, tmp, 0, vizBreakCount);
        vizGraphs[i] = new VisibleGraph(vizMax, vizMin, tmpLines, tmp);
      } else {
        vizGraphs[i] = new VisibleGraph(0, 0, new ArrayList(), new int[0]);
      }
    }
  }

  /**
   * gets the lines of the specified graph
   *
   * @param graphNum - the index of the graph whose lines are being requested
   * @return - an array list containing the lines of the graph/time series
   */
  public ArrayList getLines(int graphNum) {
    return vizGraphs[graphNum].getLines();
  }

  /**
   * Sets the values the grapher needs to query
   *
   * @param seed - the name of the seed being queried
   * @param date - the start date of the query
   * @param dur - the duration of the query
   * @param cwbip the IP address of the CWB to use
   * @return true if something was found and plotted
   */
  public boolean doQuery(String seed, String date, String dur, String cwbip) {
    // my query variables
    ArrayList<ArrayList<MiniSeed>> blkResults;
    if (seed.charAt(0) == '/') {
      origStartTime = null;
      origEndTime = null;
      // This is a file expression of some sort.  Open any files that match the expresssion
      String fileString = seed.substring(1);
      int last = fileString.lastIndexOf("/");
      String[] files;
      if (last >= 0) {
        String dir = fileString.substring(0, last);
        String fileMask = fileString.substring(last + 1);
        File directory = new File(dir);
        files = directory.list();
        for (int i = 0; i < files.length; i++) {
          if (!files[i].matches(fileMask)) {
            files[i] = null;
          } else {
            files[i] = dir + "/" + files[i];
          }
        }
      } else {
        files = new String[1];
        files[0] = fileString;
      }
      // for each non-null string in files, open the file and create the array lists
      blkResults = new ArrayList<ArrayList<MiniSeed>>(100);
      for (int i = 0; i < files.length; i++) {
        if (files[i] != null) {
          try {
            RawDisk rw = new RawDisk(files[i], "r");
            long length = rw.length();
            int iblk = 0;
            ArrayList<MiniSeed> mss = new ArrayList<MiniSeed>((int) (length / 512));
            blkResults.add(mss);
            int lastBlocksize = 512;
            byte[] buf = new byte[512];
            Util.prta(i + " Open file : " + files[i] + " start block=" + iblk + " end=" + (length / 512L));
            for (;;) {
              rw.readBlock(buf, iblk, 512);

              //mslist.clear();
              // The request data from the Q680 is really odd, it contains no network code in Log
              // records (both are zero).  We need to detect this before trying to decode it!
              if (buf[15] == 'L' && buf[16] == 'O' && buf[17] == 'G') {
                iblk += lastBlocksize / 512;
                continue;
              }

              // Bust the first 512 bytes to get all the header and data blockettes - data
              // may not be complete!  If the block length is longer, read it in complete and
              // bust it appart again.
              try {
                MiniSeed ms = new MiniSeed(buf);
                if (origStartTime == null) {
                  origStartTime = new Date();
                  origStartTime.setTime(ms.getTimeInMillis());
                }
                if (ms.hasBlk1000()) {
                  lastBlocksize = ms.getBlockSize();
                  if (ms.getBlockSize() > 512) {
                    buf = rw.readBlock(iblk, ms.getBlockSize());
                    ms = new MiniSeed(buf);
                  }
                  mss.add(ms);
                }
              } catch (IllegalSeednameException e) {
                Util.prta("Illegal seedname e=" + e);
              }
              iblk = iblk + lastBlocksize / 512;
            }
          } catch (EOFException e) {
            Util.prta("File done " + files[i]);
            if (origEndTime == null) {
              origEndTime = new Date();
              origEndTime.setTime(blkResults.get(0).get(blkResults.get(0).size() - 1).getLastTimeInMillis());
            }
          } catch (FileNotFoundException e) {
            Util.IOErrorPrint(e, "Unexpected file not found! file=" + files[i]);
          } catch (IOException e) {
            Util.IOErrorPrint(e, "Reading in miniseed file");
          }
        }     // if file is not null
      }       // for each file
      dur = "" + (origEndTime.getTime() - origStartTime.getTime()) / 1000.;
    } else {    // do a query
      String[] args = new String[13];
      args[0] = "-s";
      args[1] = seed;
      args[2] = "-b";
      args[3] = "\"" + date + "\"";
      args[4] = "-t";
      args[5] = "null";
      args[6] = "-d";
      args[7] = dur;
      args[8] = "-q";
      args[9] = "-h";
      String[] parts = cwbip.split(":");
      args[10] = parts[0];
      args[11] = "-p";
      args[12] = "2061";
      if (parts.length >= 2) {
        args[12] = parts[1];
      }

      Date beg
              = origStartTime = Util.stringToDate2(date);
      origEndTime = (Date) beg.clone();
      origEndTime.setTime(origStartTime.getTime() + (long) (Double.parseDouble(dur) * 1000));

      // do the query
      blkResults = query(args);
    }
    if (blkResults == null) {
      return false;
    }

    // check if data was returned
    numGraphs = blkResults.size();
    if (numGraphs == 0) {
      return false;   // no data
    }

//        // TESTING FOR 2 GRAPHS
//        numGraphs = 2;
//        MSGroups = new MSGroup[2];
//        MSGroups[0] = new testMSGroup(0);
//        MSGroups[1] = new testMSGroup(90);
    // create MSGroups 
    MSGroups = new MSGroup[numGraphs];
    int count = 0;
    for (int i = 0; i < numGraphs; ++i) {
      if (blkResults.get(i) == null) {
        Util.prt("Got null in block results in Grapher.doQuery");
        continue;
      }
      if (blkResults.get(i).get(0).getRate() <= 0. || blkResults.get(i).get(0).getNsamp() == 0) {
        Util.prt("Cannot plot non-timeseries channel " + blkResults.get(i).get(0).toString());
        continue;
      }
      if (blkResults.get(i).get(0).getEncoding() != 10 && blkResults.get(i).get(0).getEncoding() != 11) {
        Util.prt("Cannot decode channel for " + blkResults.get(i).get(0).toString());
        continue;
      }
      Collections.sort(blkResults.get(i));

      MSGroups[count++] = new MSGroup((ArrayList<MiniSeed>) blkResults.get(i));
    }
    if (count != numGraphs) {
      Util.prt("reduce numGraphs from " + numGraphs + " to " + count);
      numGraphs = count;
    }
    return true;
  }

  public ArrayList<ArrayList<MiniSeed>> query(String[] args) {
    return EdgeQueryClient.query(args);
  }

  /**
   * Helper function for query fcn
   */
  private static boolean read(InputStream in, byte[] b, int off, int l)
          throws IOException {
    int len;
    while ((len = in.read(b, off, l)) > 0) {
      off += len;
      l -= len;
      if (l == 0) {
        return true;
      }
    }
    return false;
  }

//    /**
//     * Returns the number of data points in the largest miniseed set
//     */
//    public int getLargestNumData(){ return largestNumData; }
  /**
   * Returns the number of blocks/graphs the grapher examined
   *
   * @return the number of MSgroups
   */
  public int getNumGraphs() {
    return numGraphs;
  }

  /**
   * Returns the number of visible graphs to display
   *
   * @return the number of visible graphs
   */
  public int getNumVizGraphs() {
    return numVizGraphs;
  }

  /**
   * Returns the mid point of the data (max+min)/2
   *
   * @param index The graph index to return the midpoint for
   * @return The mid pont of the data (max+min)/2
   */
  public int getMid(int index) {
    return vizGraphs[index].getMid();
  }

  /**
   * Returns the visible range of the data
   *
   * @param index The graph index of the MSGroup
   * @return The visible range of the data
   */
  public int getDelta(int index) {
    return vizGraphs[index].getDelta();
  }

  /**
   * Returns the visible max value of the data
   *
   * @param index The graph index of the MSGroup
   * @return The max value of the data
   */
  public int getMax(int index) {
    return vizGraphs[index].getMax();
  }

  /**
   * Returns the min value of the data
   *
   * @param index The graph index of the MSGroup
   * @return the min value
   */
  public int getMin(int index) {
    return vizGraphs[index].getMin();
  }

  /**
   * Returns the name of the seed the data was retrieved from
   *
   * @param index The graph index
   * @return the seedname
   */
  public String getSeedName(int index) {
    return MSGroups[index].getSeedName();
  }

  /**
   * Returns an array of the break values of the MSGroup specified at index
   *
   * @param index - the index value of the MSGroup whose values are being found
   * @return the Break values for the MSGroup
   */
  public int[] getBreakValues(int index) {
    return vizGraphs[index].getBreakValues();
  }

  /**
   * Returns an array of the break times of the MSGroup specified at index. The times are the
   * starting times of the non continuous data
   *
   * @param index - the index value of the MSGroup whose values are being found
   * @return the array of break times
   */
  public long[] getBreakTimes(int index) {
    return MSGroups[index].getBreakTimes();
  }

  /**
   * returns the rate of the MSGroup specified at index
   *
   * @param index - the index value of the MSGroup whose value is being found
   * @return The digit rate in Hz
   */
  public double getRate(int index) {
    return MSGroups[index].getRate();
  }

  /**
   * Returns the scale value of the graph at index
   *
   * @param index = the index value of the MSGroup whose value is being found
   * @return The scale value of the graph
   */
  public double getScale(int index) {
    double scale = MSGroups[index].getNumData() / GRAPH_WIDTH;
    if (scale <= 1) {
      scale = 1;
    }
    return scale;
  }

  /**
   * Returns the starting time of the graph/MSGroup with the given index
   *
   * @param index - the index of the MSGroup whose starting time is being retrieved
   * @return the starting time of the group
   */
  public long getStartTime(int index) {
    return MSGroups[index].getStartTime().getTimeInMillis();
  }

  /**
   * Returns the ending time of the graph/MSGroup with the given index
   *
   * @param index - the index of the MSGroup whose ending time is being retrieved
   * @return the Endtime for the MSGroup
   */
  public long getEndTime(int index) {
    return MSGroups[index].getEndTime().getTimeInMillis();
  }

  /**
   * Returns the starting time/date of the vizible graph area
   *
   * @return The starting time/date of the visible graph
   */
  public Date getVizStartTime() {
    return vizStartTime;
  }

  /**
   * Returns the ending time of the vizible graph area
   *
   * @return The Visible endtime
   */
  public Date getVizEndTime() {
    return vizEndTime;
  }

  /**
   * Returns the original starting time of the data
   *
   * @return The original start time
   */
  public Date getOrigStartTime() {
    return origStartTime;
  }

  /**
   * Returns the original ending time of the data
   *
   * @return the original ending time
   */
  public Date getOrigEndTime() {
    return origEndTime;
  }

  /**
   * returns the duration of the vizible graph in seconds
   *
   * @return the graph duration in seconds.
   */
  public long getGraphDuration() {
    long dur = origEndTime.getTime() - origStartTime.getTime();  // millisec
    return dur / 1000;
  }
}
