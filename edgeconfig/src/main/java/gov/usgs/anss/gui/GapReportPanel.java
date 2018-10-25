/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import gov.usgs.anss.guidb.GapsList;
import gov.usgs.anss.guidb.SpansList;
import gov.usgs.anss.guidb.HoldingsList;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JFileChooser;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.Box;
import javax.swing.SpinnerDateModel;
import javax.swing.BoxLayout;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.table.AbstractTableModel;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.SEEDNameTypeBox;

/**
 * GapReportPanel is a user interface for visualizing the gaps in holding data for a particular SEED
 * name in a certain date range. The user is also given the option of saving the gap data to a file.
 *
 * The database is never modified.
 *
 * @author David Fifield
 */
public final class GapReportPanel extends JPanel {

  public static final String TITLE = "Single gap report";

  private final DecimalFormat percentFormat = new DecimalFormat("#0.00%");

  private final JPanel topPanel;
  private SEEDNameTypeBox seedNameTypeBox;
  private final JSpinner startSpinner, endSpinner;
  private final JLabel statusLabel, availabilityLabel, ioLabel;
  private final GapView gapView;
  private final JTable table;
  private final JFileChooser fileChooser;

  /* The list of gaps used to build the table. It is updated in search. */
  private GapsList gaps;
  private static DBConnectionThread connection;

  public static DBConnectionThread getDBConnection() {
    return connection;
  }

  /**
   * This is the model used to build the table of gaps. About the only special thing it does is
   * format dates.
   */
  private class GapTableModel extends AbstractTableModel {

    private final String[] columnNames = {"Start date", "End date", "Length"};
    private final DecimalFormat floatFormat = new DecimalFormat("0.000");
    private final SimpleDateFormat dateFormat
            = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public int getRowCount() {
      return gaps.size();
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public Object getValueAt(int row, int column) {
      SpansList.Span span;

      if (row < gaps.size()) {
        span = (SpansList.Span) gaps.get(row);
        switch (column) {
          case 0:
            return dateFormat.format(span.start);
          case 1:
            return dateFormat.format(span.end);
          case 2:
            return floatFormat.format(
                    (span.end.getTime() - span.start.getTime()) / 1000.0);
          default:
            return null;
        }
      } else {
        return null;
      }
    }

    @Override
    public String getColumnName(int column) {
      if (column >= 0 && column < getColumnCount()) {
        return columnNames[column];
      } else {
        return "";
      }
    }
  }

  /**
   * Create a new GapReportPanel.
   *
   * @param connection The connection to the database to use
   */
  public GapReportPanel(DBConnectionThread connection) {
    GapReportPanel.connection = connection;
    JPanel bottomPanel;
    JButton searchButton, saveButton;
    JScrollPane scrollPane;
    Box box;
    JLabel label;
    //GregorianCalendar calendar;
    Calendar calendar;

    gaps = new GapsList();

    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

    topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));

    box = Box.createVerticalBox();
    label = new JLabel("SEED name and type: ");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(label);
    seedNameTypeBox = new SEEDNameTypeBox();
    seedNameTypeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    seedNameTypeBox.setEnabled(false);
    box.add(seedNameTypeBox);
    topPanel.add(box);

    topPanel.add(Box.createHorizontalStrut(10));

    /* Make the initial start date one month in the past, at midnight. */
    //calendar = new GregorianCalendar();
    calendar = Calendar.getInstance();
//    calendar.add(GregorianCalendar.MONTH, -1);
//    calendar.clear(GregorianCalendar.HOUR);
//    calendar.clear(GregorianCalendar.HOUR_OF_DAY);
//    calendar.clear(GregorianCalendar.MINUTE);
//    calendar.clear(GregorianCalendar.SECOND);
//    calendar.clear(GregorianCalendar.MILLISECOND); 

    box = Box.createVerticalBox();
    label = new JLabel("Start date: ");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(label);
    startSpinner = new JSpinner(new SpinnerDateModel());
//    startSpinner.setValue(calendar.getTime());
    startSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(startSpinner);
    box.setMaximumSize(box.getPreferredSize());
    topPanel.add(box);

    topPanel.add(Box.createHorizontalStrut(10));

    box = Box.createVerticalBox();
    label = new JLabel("End date: ");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(label);
    endSpinner = new JSpinner(new SpinnerDateModel());
    endSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
//    calendar.add(Calendar.MONTH, 1);
//    endSpinner.setValue(calendar.getTime());
    box.add(endSpinner);
    topPanel.add(box);

    topPanel.add(Box.createHorizontalStrut(10));

    box = Box.createVerticalBox();
    // add combobox to select time to change
    JComboBox timeBox = new JComboBox(MultiGapReportPanel.TIMES);
    timeBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) {
        MultiGapReportPanel.Time time;

        time = (MultiGapReportPanel.Time) event.getItem();
        setDates(time.field, time.amount);
      }
    });
    timeBox.setSelectedIndex(1);
    box.add(timeBox);
    box.setMaximumSize(box.getPreferredSize());
    topPanel.add(box);

    topPanel.add(Box.createHorizontalStrut(20));

    searchButton = new JButton("Search");
    searchButton.setMaximumSize(searchButton.getPreferredSize());
    searchButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    searchButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        search();
      }
    });
    topPanel.add(searchButton);

    topPanel.add(Box.createHorizontalStrut(20));

    box = Box.createVerticalBox();
    statusLabel = new JLabel();
    box.add(statusLabel);
    availabilityLabel = new JLabel();
    box.add(availabilityLabel);
    topPanel.add(box);

    topPanel.add(Box.createHorizontalGlue());

    topPanel.setMaximumSize(new Dimension(topPanel.getMaximumSize().width,
            topPanel.getPreferredSize().height));
    add(topPanel);

    add(Box.createVerticalStrut(2));

    gapView = new GapView();
    gapView.setMinimumSize(new Dimension(100, 20));
    gapView.setPreferredSize(new Dimension(100, 20));
    add(gapView);

    add(Box.createVerticalStrut(2));

    table = new JTable(new GapTableModel());
    /* Set the table widths to 40% for the start and end dates and 20% for the
       length of the gap. */
    table.getColumnModel().getColumn(0).setPreferredWidth(400);
    table.getColumnModel().getColumn(1).setPreferredWidth(400);
    table.getColumnModel().getColumn(2).setPreferredWidth(200);
    scrollPane = new JScrollPane(table);
    add(scrollPane);

    add(Box.createVerticalStrut(10));

    bottomPanel = new JPanel();
    bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.LINE_AXIS));

    saveButton = new JButton("Save to file");
    saveButton.setMaximumSize(saveButton.getPreferredSize());
    saveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        /* Search first, to make sure the user's selection and the gap data are
           consistent. */
        search();
        popupSaveDialog();
      }
    });
    bottomPanel.add(saveButton);

    bottomPanel.add(Box.createHorizontalStrut(20));
    ioLabel = new JLabel();
    bottomPanel.add(ioLabel);

    bottomPanel.add(Box.createHorizontalGlue());

    bottomPanel.setMaximumSize(new Dimension(bottomPanel.getMaximumSize().width,
            bottomPanel.getPreferredSize().height));
    add(bottomPanel);

    add(Box.createVerticalStrut(20));

    fileChooser = new JFileChooser();

    /* Run seedNameTypeBox.initialize in a separate thread because it makes a
       potentially lengthy database access. */
    new Thread() {
      @Override
      public void run() {
        /*if(!DBConnectionThread.waitForConnection("gap")) 
          if(!DBConnectionThread.waitForConnection("gap"))
            if(!DBConnectionThread.waitForConnection("gap")) System.out.println("**** DBConnection to 'gap' did not open");*/
        seedNameTypeBox.initialize(GapReportPanel.connection.getConnection());
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            seedNameTypeBox.setEnabled(true);
          }
        });
      }
    }.start();

    UC.Look((GapReportPanel) this);
  }

  /**
   * Perform a search with the given SEED name and start and end dates and update the table with the
   * results.
   */
  private void search() {
    Date start, end;
    HoldingsList holdings;
    SEEDNameTypeBox.SEEDNameType nameType;
    String seedName, type;

    statusLabel.setText("Searching...");
    availabilityLabel.setText("");
    ioLabel.setText("");

    gaps.clear();

    start = (Date) startSpinner.getValue();
    end = (Date) endSpinner.getValue();
    nameType = (SEEDNameTypeBox.SEEDNameType) seedNameTypeBox.getSelectedItem();
    seedName = nameType.seedName;
    type = nameType.type;
    try {
      holdings = new HoldingsList(DBConnectionThread.getConnection("gap"), seedName, type, start, end);
      gaps = new GapsList(holdings);
      gapView.setGaps(gaps);
    } catch (SQLException e) {
      statusLabel.setText(e.getMessage());
    }

    if (gaps.size() == 1) {
      statusLabel.setText(gaps.size() + " gap");
    } else {
      statusLabel.setText(gaps.size() + " gaps");
    }

    // if the percent says 100, but we have gaps, set it to 99.99% to show gaps.
    float percent = gaps.getAvailability();
    //if(percentFormat.format(percent).equals("100.00%") && gaps.size() > 0){
    if (percent > .9999f) {
      percent = .9999f;
    }
    availabilityLabel.setText(percentFormat.format(percent)
            + " available");

    ((GapTableModel) table.getModel()).fireTableDataChanged();
  }

  /**
   * Display a save dialog and save the current gap data to the selected file if the user desires.
   */
  private void popupSaveDialog() {
    File file;
    int status;

    status = fileChooser.showSaveDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      file = fileChooser.getSelectedFile();
      try {
        save(file);
        ioLabel.setText("Gaps saved to " + file.getName() + ".");
      } catch (IOException e) {
        ioLabel.setText(e.getMessage());
      }
    } else if (status == JFileChooser.CANCEL_OPTION) {
      ioLabel.setText("Save canceled.");
    }
  }

  /**
   * Save the current gap data to file.
   *
   * @param file The file to which to save the data.
   */
  private void save(File file) throws IOException {
    FileWriter writer;

    writer = new FileWriter(file);
    gaps.write(writer);
    writer.close();
  }

  /**
   * Set the end date spinner to the present and set the start date spinner relative to the end.
   *
   * @param field one of {@link Calendar}'s field values, like Calendar.DATE or Calendar.MONTH
   * @param amount how much to add to the given field relative to the present
   */
  private void setDates(int field, int amount) {
    Calendar calendar = Calendar.getInstance();
    // set calendar to midnight this morning/last night
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.HOUR, 0);
    calendar.set(Calendar.AM_PM, Calendar.AM);

    endSpinner.setValue(calendar.getTime());
    calendar.add(field, amount);
    startSpinner.setValue(calendar.getTime());
  }
}
