/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

/*
 * TSPlotPanel.java
 *
 * Created on July 3, 2006, 4:40 PM
 * By Jeremy Powell
 *
 * TSPlotPanel examines the data of a miniseed the user wants to see.  A time series
 * graph is displayed.  The graph can have discontinuities or overlaps, so the
 * graph will change color to indicate this.  TSPlot also has a feature to 
 * 'cleanup' the plot (remove overlaps) to provide the user with a cleaner image.
 * TSPlotPanel needs to have a height of 500 pixels to display 3 graphs at a time.
 * Put the TSPlotPanel in a scrollPane for queries that return more than 3 graphs.
 */
public final class TSPlotPanel extends JPanel {

  private static DecimalFormat df;
  private final JTextField seedField = new JTextField(15);  // ("USACSO");   
  private final JTextField dateField = new JTextField(15);   //("2006,201-00:00");
  private final JTextField durationField = new JTextField(15); //("1000");  
  private final JTextField replotStartField = new JTextField(6);
  private final JTextField replotEndField = new JTextField(6);
  private final JTextField error = new JTextField(6);
  private GraphPanel graphPanel;
  private final JButton cleanupButton = new JButton("Cleanup");
  private final JButton graphButton = new JButton("Graph");
  private final JButton replotButton = new JButton("Replot");

  /**
   * Creates a new instance of TSPlot
   */
  public TSPlotPanel() {
    this(new Dimension(450, 500));  // has to be 500 tall to fit 3 graphs of 100 pixels tall
  }

  /**
   * Creates a new instance of TSPlot
   *
   * @param dim
   */
  public TSPlotPanel(Dimension dim) {
    UC.Look(this);   // ANSS system look and color

    this.setPreferredSize(dim);
    JPanel inputPanel = new JPanel(new GridLayout(3, 2));
    JPanel plotPanel = new JPanel();
    JPanel buttonPanel = new JPanel();
    JPanel replotPanel = new JPanel(new GridLayout(3, 2));
    UC.Look(inputPanel);   // ANSS system look and color
    UC.Look(plotPanel);   // ANSS system look and color
    UC.Look(buttonPanel);   // ANSS system look and color
    UC.Look(replotPanel);   // ANSS system look and color

    cleanupButton.addActionListener(new CleanupListener());
    graphButton.addActionListener(new GraphListener());
    replotButton.addActionListener(new ReplotListener());

    buttonPanel.add(graphButton);
    graphButton.setToolTipText("Click here to plot the time range and station entered.");
    buttonPanel.add(cleanupButton);
    buttonPanel.add(replotButton);
    Util.prt("Button panel dim=" + buttonPanel.getSize() + " #comps=" + buttonPanel.getComponents().length);
    JPanel midPanel = new JPanel(new BorderLayout());
    midPanel.add(buttonPanel, BorderLayout.NORTH);
    JLabel label = new JLabel("Drag a range with mouse to zoom");
    label.setVerticalTextPosition(SwingConstants.CENTER);
    midPanel.add(label, BorderLayout.CENTER);
    UC.Look(midPanel);

    cleanupButton.setEnabled(false);   // dont let them press these yet
    replotButton.setEnabled(false);

    inputPanel.add(new JLabel("SeedName(USISCO): "));
    inputPanel.add(seedField);
    seedField.setToolTipText("Seed names must be specifed NNSSSSSCCCLL.  Any portion of the right can be omitted.");
    inputPanel.add(new JLabel("Time(2005,340[ 00:00]): "));
    inputPanel.add(dateField);
    dateField.setToolTipText("NNNNN  it is the number of seconds before the current time to start the plot.\n"
            + "YYYY,DDD[ hh:mm:ss] where the Year and day-of-year are given with an optional time.\n"
            + "yyyy-mm-dd [hh:mm:ss] The date is specified in year, month day - the time is not optional");
    inputPanel.add(new JLabel("Duration(sec):"));
    inputPanel.add(durationField);
    durationField.setText("300");

    replotPanel.add(new JLabel("Replot start(sec): "));
    replotPanel.add(replotStartField);
    replotPanel.add(new JLabel("Replot end(sec): "));
    replotPanel.add(replotEndField);
    replotPanel.add(new JLabel("Error:"));
    replotPanel.add(error);
    error.setBackground(Color.gray);

    plotPanel.add(graphPanel = new GraphPanel(
            new Dimension((int) (dim.getWidth() - 50), (int) (dim.getHeight() - 20)), this));
    JScrollPane scrollPlot = new JScrollPane(plotPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    int width = this.getPreferredSize().width;
    int height = this.getPreferredSize().height;

    scrollPlot.setPreferredSize(new Dimension(width - 10, (int) (height * .66) + 30));
    scrollPlot.getVerticalScrollBar().setUnitIncrement(15);

    this.setLayout(new BorderLayout());
    this.setBorder(new BevelBorder(BevelBorder.RAISED));
    JPanel topPanel = new JPanel(new GridLayout(1, 3));

    topPanel.add(inputPanel);
    topPanel.add(midPanel);
    topPanel.add(replotPanel);
    UC.Look(topPanel);

    this.add(topPanel, BorderLayout.NORTH);
    this.add(scrollPlot, BorderLayout.CENTER);
    seedField.setToolTipText("12 character seed name with . for wild cards or start with /and give a file path with regexp in file name portion (// for absolute path)");
  }

  // when graph button is pushed, get the query values and grapher does the query.
  // Then the graphPanel draws the data for display
  class GraphListener implements ActionListener {

    public void actionPerformed(ActionEvent e) {
      ErrorTrack err = new ErrorTrack();
      // get the values from the input fields
      String seed = seedField.getText();
      String date = dateField.getText();

      if (seed.equals("")) {
        seed = "USISCO";
      }
      if (date.equals("")) {
        date = "600";
        dateField.setText("600");
      }
      try {
        double sec = Double.parseDouble(date);    // is it not a date?
        GregorianCalendar now = new GregorianCalendar();
        now.add(Calendar.SECOND, (int) -sec);
        if (df == null) {
          df = new DecimalFormat("00");
        }
        date = now.get(Calendar.YEAR) + "," + now.get(Calendar.DAY_OF_YEAR) + " "
                + df.format(now.get(Calendar.HOUR_OF_DAY)) + ":" + df.format(now.get(Calendar.MINUTE)) + ":"
                + df.format(now.get(Calendar.SECOND));
      } catch (NumberFormatException e2) {
        java.sql.Timestamp d = FUtil.chkTimestamp(dateField, err);
        dateField.setText(d.toString().substring(0, 16));
        Util.prt("d=" + d.toString().substring(0, 16));
        date = d.toString().substring(0, 16);
      }
      String dur = durationField.getText();
      if (dur.equals("")) {
        dur = "600";
      }
      try {
        double dd = Double.parseDouble(dur);
        durationField.setBackground(Color.white);
      } catch (NumberFormatException e2) {
        err.set(true);
        err.appendText("Duration is invalid");
        durationField.setBackground(Color.red);
      }
      if (err.isSet()) {
        error.setText(err.getText());
        error.setBackground(Color.red);

      } else {
        error.setBackground(Color.gray);
      }
      replotStartField.setText("0");
      replotEndField.setText(dur);

      // takes a long time to query
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

      // inside offsetSpan, tries to parse empty string to double, fails
      if (dur.equals("")) {
        dur = "300";
      }
      graphPanel.doQuery(seed, date, dur, (Util.getProperty("CWBIP") == null ? "cwbrs" : Util.getProperty("CWBIP")));

      setCursor(Cursor.getDefaultCursor());

      if (graphPanel.gotData()) {
        cleanupButton.setEnabled(true);
        replotButton.setEnabled(true);
      } else {
        cleanupButton.setEnabled(false);
        replotButton.setEnabled(false);
      }
    }
  }

  // when cleanup button is pushed, the panel cleans (fixes offset) the data
  // and displays the data graphically again
  class CleanupListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      graphPanel.cleanupGraph();
    }
  }

  class ReplotListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      graphPanel.replotGraph(Integer.parseInt(replotStartField.getText()),
              Integer.parseInt(replotEndField.getText()));
    }
  }

  public void setReplotVals(int start, int end) {
    replotStartField.setText(Integer.toString(start));
    replotEndField.setText(Integer.toString(end));
    graphPanel.replotGraph(Integer.parseInt(replotStartField.getText()),
            Integer.parseInt(replotEndField.getText()));
  }
}
