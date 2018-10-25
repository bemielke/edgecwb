/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Date;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;

/*
 * GraphPanel.java
 *
 * Created on July 5, 2006, 9:32 AM
 * By Jeremy Powell
 *
 * GraphPanel extends the javax.swing.JPanel
 * Queried data is displayed on the panel in graph form.  Has a Grapher obj
 * that builds the data so the GraphPanel can display it easily.
 */
public final class GraphPanel extends JPanel {

  private final Grapher grapher;
  private final Dimension dim;
  private final Color[] colors = {Color.ORANGE, Color.BLUE, Color.MAGENTA,
    Color.BLACK, Color.RED, Color.GREEN.darker()};
  private boolean gotData;       // did the query return data?
  private boolean clean;         // are we displaying clean graphs?
  private final int LEFT_WHITE_SPACE = 80;    // the left hand buffer to display words on the graph
  private final int BTWN_GRAPH_SPACE = 20;     // the vertical space btwn the graphs
  private int totalGraphHeight;
  private final TSPlotPanel parentPanel;     // handle to the parent panel

  MyListener myListener; // detect where user drags mouse
  Rectangle highlightRect = null;
  Rectangle rectToDraw = null;
  Rectangle previousRectDrawn = new Rectangle();
  double secPerPixel;

  /**
   * Creates a new instance of GraphPanel
   * @param d
   * @param parent
   */
  public GraphPanel(Dimension d, JPanel parent) {
    dim = new Dimension(d.width - 50, 1500); //(int) (d.height*.66) + 15);
    this.setPreferredSize(dim);
    this.setMinimumSize(dim);
    grapher = new Grapher();
    grapher.setGraphWidth(d.width - 150);
    this.setBackground(Color.white);
    gotData = false;
    clean = false;
    parentPanel = (TSPlotPanel) parent;

    myListener = new MyListener();
    addMouseListener(myListener);
    addMouseMotionListener(myListener);
  }

  /**
   * Draws the graphs
   */
  @Override
  public void paintComponent(Graphics g) {
    Graphics2D g2D = (Graphics2D) g;
    super.paintComponent(g2D);

    // paint highlighted box on background if user is dragging mouse
    if (highlightRect != null) {
      //Draw a rectangle behind the graphs.
      g2D.setColor(new Color(192, 249, 254));
      g.fillRect(rectToDraw.x, rectToDraw.y,
              rectToDraw.width - 1, rectToDraw.height - 1);
    }

    g2D.setFont(new Font("Dialog", Font.PLAIN, 10));

    int width = this.getWidth();
    int height = this.getHeight();
    int singleGraphHeight = 100;

    int numGraphs = grapher.getNumGraphs();

    // extend the canvas to fit the number of graphs that we have
    if (numGraphs > 0) {
      // save 100 pixels per graph, 5 for the whitespace
      totalGraphHeight = singleGraphHeight * numGraphs + BTWN_GRAPH_SPACE * (grapher.getNumVizGraphs() + 1);
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              totalGraphHeight));
      // update the scrolling pane since the size could be different now
      this.revalidate();
    } else {
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              singleGraphHeight));
      totalGraphHeight = singleGraphHeight;
      this.revalidate();
      return;  // no graphs to draw, get out of paint
    }

    // set coord system to normal: bottom left corner is origin
    // now we have to put a negative infront of every Y point
    g2D.translate(0, this.getPreferredSize().height);

    // the Y where each graph will be centered on.  The first zero represents
    // the bottom graph, the last zero will be the top graph
    int zero = singleGraphHeight / 2 + BTWN_GRAPH_SPACE;

    // get the start time of the graph
    Date startDate = (Date) grapher.getVizStartTime().clone();
    Date endDate = (Date) grapher.getVizEndTime().clone();

    this.setPreferredSize(new Dimension(grapher.GRAPH_WIDTH + 50 + LEFT_WHITE_SPACE,
            this.getPreferredSize().height));
    this.revalidate();

    // draw 4 lines and print their times
    int numTimePeriods = 4;
    // get pixels per 1/4 of image
    int dataPerTimePeriod = (int) (endDate.getTime() - startDate.getTime()) / 1000 * (int) grapher.getRate(0) / numTimePeriods;
    double secPerTimePeriod = (double) (endDate.getTime() - startDate.getTime()) / 1000. / numTimePeriods;
    int pixPerTimePeriod = (int) grapher.GRAPH_WIDTH / numTimePeriods;

    secPerPixel = secPerTimePeriod / (double) pixPerTimePeriod;

    for (int i = 0; i <= numTimePeriods + 1; ++i) {
      // remember to negate y axis
      g2D.setColor(Color.DARK_GRAY);
      g2D.drawLine(LEFT_WHITE_SPACE + pixPerTimePeriod * i, -(this.getHeight()), LEFT_WHITE_SPACE + pixPerTimePeriod * i, 0);
      String[] time = startDate.toString().split(" ");   // turn time into a string array
      g2D.setColor(Color.BLACK);
      g2D.drawString(time[1] + " " + time[2] + " " + time[3], LEFT_WHITE_SPACE + pixPerTimePeriod * i - 30, -(this.getHeight() - 10));  // top
      g2D.drawString(time[1] + " " + time[2] + " " + time[3], LEFT_WHITE_SPACE + pixPerTimePeriod * i - 30, -2);  // bottom
      startDate.setTime(startDate.getTime() + (long) secPerTimePeriod * 1000);
    }

    int colorInc = 0;   // value to switch colors on breaks
    for (int graph = 0; graph < numGraphs; ++graph) {
      // get the lines for the graph we're currently drawing
      ArrayList lines = grapher.getLines(graph);
      if (lines.isEmpty()) {
        continue;   // no lines
      }
      // get the x values where the graph needs to be broken
      int[] graphBreakValues = grapher.getBreakValues(graph);

      // find out how large the delta is for this graph.  if its larger than 100,
      // scale it down to fit better.
      int delta = grapher.getDelta(graph);
      double yScale = 1;       // set to 1 so points stay the same if we dont need to scale
      if (delta / (double) singleGraphHeight > 1) {
        yScale = delta / (double) singleGraphHeight;
      }

      int viewVal = (int) (grapher.getMid(graph) / yScale) - zero;

      // draw graph, get the lines and draw them
      g2D.setColor(colors[colorInc % colors.length]);   // change graph color
      ++colorInc;

      // draw seed name
      g2D.drawString(grapher.getSeedName(graph), 1, -zero);
      // draw max num string at correct height
      g2D.drawString(Integer.toString(grapher.getMax(graph)), 1,
              -(int) (grapher.getMax(graph) / yScale - viewVal - 10));
      g2D.drawLine(0, -(int) (grapher.getMax(graph) / yScale - viewVal),
              grapher.GRAPH_WIDTH + 50 + LEFT_WHITE_SPACE,
              -(int) (grapher.getMax(graph) / yScale - viewVal));
      // draw min num string at correct height
      g2D.drawString(Integer.toString(grapher.getMin(graph)), 1,
              -(int) (grapher.getMin(graph) / yScale - viewVal + 1));
      g2D.drawLine(0, -(int) (grapher.getMin(graph) / yScale - viewVal),
              grapher.GRAPH_WIDTH + 50 + LEFT_WHITE_SPACE,
              -(int) (grapher.getMin(graph) / yScale - viewVal));

      for (int i = 0; i < lines.size(); ++i) {
        // check if the graph is noncontinuous (change colors)
        if (!clean) {
          for (int j = 0; j < graphBreakValues.length; ++j) {
            if (i == graphBreakValues[j]) {
              g2D.setColor(colors[colorInc % colors.length]);  // change color
              ++colorInc;
            }
          }
        }
        Line2D.Double line = (Line2D.Double) lines.get(i);
        g2D.drawLine((int) (line.x1 + LEFT_WHITE_SPACE), -(int) (line.y1 / yScale - viewVal),
                (int) (line.x2 + LEFT_WHITE_SPACE), -(int) (line.y2 / yScale - viewVal));
      }
      zero = zero + singleGraphHeight + BTWN_GRAPH_SPACE;  // calc new zero value
    }
  }

  public void doQuery(String seed, String date, String dur, String cwbip) {
    clean = false;
    gotData = grapher.doQuery(seed, date, dur, cwbip);
    if (!gotData) {
      JOptionPane.showMessageDialog(this, "No data was returned.  Either there "
              + "is no data or, if this message took a long time to display, the server is down.",
              "No data returned.", JOptionPane.ERROR_MESSAGE);
    } else {
      // build unclean graphs if we got data
      grapher.buildGraphs(clean, 0, Integer.parseInt(dur));
    }

    paintComponent(this.getGraphics());
  }

  /**
   * Must only be called after a successful query has been made
   */
  public void cleanupGraph() {
    clean = true;
    // assuming the query has already been made
    if (gotData) {
      grapher.buildGraphs(clean);

      paintComponent(this.getGraphics());
    } // can get rid of this now, right?
    else {
      JOptionPane.showMessageDialog(this, "There is no data to cleanup.  "
              + "You must press the GRAPH button first.",
              "Connection Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Make the grapher replot its data and prepare new graphs to display
   *
   * @param start - the starting time to replot the data, elapsed seconds since the begining of the
   * query
   * @param end - the ending time to replot the data, elapsed seconds since the begining of the
   * query
   */
  public void replotGraph(int start, int end) {
    int dur = (int) grapher.getGraphDuration();

    if (start < 0 || end < start || start > dur || end > dur) {
      JOptionPane.showMessageDialog(this, "The replot values are not valid, please try again.",
              "Invalid values", JOptionPane.ERROR_MESSAGE);
    } else {
      clean = false;     // reset the clean value
      grapher.buildGraphs(clean, start, end);
      highlightRect = null;   // clear the highlight rectangle
    }
    paintComponent(this.getGraphics());
  }

  /**
   * Returns true if the grapher was able to collect data, false otherwise
   */
  public boolean gotData() {
    return gotData;
  }

  class MyListener extends MouseInputAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
      highlightRect = null;   // reset the values on each new mousepress
      rectToDraw = null;
      previousRectDrawn = new Rectangle();

      int x = e.getX();
      highlightRect = new Rectangle(x, 0, 0, 0);
      updateHighlightRect(grapher.GRAPH_WIDTH, getHeight()); //getWidth(), getHeight());
      repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      updateSize(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      updateSize(e);

      // calculate the times of the start and end of the highlight box
      double startX = Math.min(highlightRect.getX(), highlightRect.getX() + highlightRect.getWidth());
      double endX = Math.max(highlightRect.getX(), highlightRect.getX() + highlightRect.getWidth());

      int secSinceOrigTime = (int) (grapher.getVizStartTime().getTime() - grapher.getOrigStartTime().getTime()) / 1000;

      int startSec = (int) ((startX - LEFT_WHITE_SPACE) * secPerPixel) + secSinceOrigTime;
      int endSec = (int) ((endX - LEFT_WHITE_SPACE) * secPerPixel) + secSinceOrigTime;

      parentPanel.setReplotVals(startSec, endSec);
    }

    void updateSize(MouseEvent e) {
      int x = e.getX();
      if (x < LEFT_WHITE_SPACE) {
        x = LEFT_WHITE_SPACE;
      }

      highlightRect.setSize(x - highlightRect.x, totalGraphHeight);
      updateHighlightRect(getWidth(), getHeight()); //getWidth(), getHeight());
      Rectangle totalRepaint = rectToDraw.union(previousRectDrawn);
      repaint(totalRepaint.x, totalRepaint.y,
              totalRepaint.width, totalRepaint.height);
    }
  }

  private void updateHighlightRect(int compWidth, int compHeight) {
    int x = highlightRect.x;
    int y = 0;
    int width = highlightRect.width;
    int height = highlightRect.height;

    //Make the width positive, if necessary.
    if (width < 0) {
      width = 0 - width;
      x = x - width + 1;
      if (x < 0) {
        width += x;
        x = 0;
      }
    }
    //The rectangle shouldn't extend past the drawing area.
    if ((x + width) > compWidth) {
      width = compWidth - x;
    }

    //Update rectToDraw after saving old value.
    if (rectToDraw != null) {
      previousRectDrawn.setBounds(rectToDraw.x, rectToDraw.y,
              rectToDraw.width, rectToDraw.height);
      rectToDraw.setBounds(x, y, width, height);
    } else {
      rectToDraw = new Rectangle(x, y, width, height);
    }
  }
}
