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
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.PostScript;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;
import javax.swing.*;

/**
 * MultiGapReportPanel is an interface for visualizing the gaps between holdings for many different
 * SEED names in a certain date range.
 *
 * The database is never modified.
 *
 * @author David Fifield
 */
public final class MultiGapReportPanel extends JPanel {

  public static final String TITLE = "Multiple gap report";

  private static final int NUM_VISIBLE_SEED_NAMES = 10;
  private static final int NUM_VISIBLE_GAP_VIEWS = 20;
  private static final int GAP_VIEW_HEIGHT = 30; //25;

  private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.00%");
  private boolean startup;

  private final JList seedNameList;
  private JScrollPane seedNameScrollPane;
  private final JSpinner startSpinner, endSpinner;
  private final JCheckBox emptyCheckBox;
  private JTextField globField;
  private JComboBox typeBox;
  private JButton searchButton;
  private final JLabel statusLabel;
  private final JProgressBar progressBar;
  private JList list;
  private final JLabel ioLabel;
  private JComboBox sortComboBox;
  private final JFileChooser fileChooser;
  private DBConnectionThread dbconnstatus;
  private List<Object> seedNames;
  private Object seedName;
  private JButton seedNameButton;   // button to get the seedNames
  private DBConnectionThread dbconnedge;
  //private DBConnectionThread dbconnstatus;

  /* These Comparators are used to sort the list of GapsLists. */
  private static final Comparator<Object> NAME_COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object a, Object b) {
      GapsList ga, gb;
      int cmp;

      if (a instanceof java.lang.String && b instanceof java.lang.String) {
        return ((String) a).compareTo((String) b);
      }
      ga = (GapsList) a;
      gb = (GapsList) b;
      cmp = ga.seedName.compareTo(gb.seedName);
      if (cmp != 0) {
        return cmp;
      }

      return ga.type.compareTo(gb.type);
    }

    @Override
    public String toString() {
      return "Name";
    }
  };

  private static final Comparator AVAILABILITY_COMPARATOR = new Comparator() {
    @Override
    public int compare(Object a, Object b) {
      GapsList ga, gb;
      float availA, availB;

      ga = (GapsList) a;
      gb = (GapsList) b;

      if (gb.start == null || gb.end == null) {
        return -1;
      }
      if (ga.start == null || ga.end == null) {
        return 1;
      }

      availA = ga.getAvailability();
      availB = gb.getAvailability();

      if (availA > availB) {
        return -1;
      } else if (availA < availB) {
        return 1;
      } else {
        return 0;
      }
    }

    @Override
    public String toString() {
      return "Availability";
    }
  };

  private static final Comparator[] COMPARATORS = {
    NAME_COMPARATOR, AVAILABILITY_COMPARATOR
  };
  private static final Comparator<Object> DEFAULT_COMPARATOR = NAME_COMPARATOR;

  /**
   * This class represents times in the past so they can go in timeBox. Make time things public so
   * single gap report can access them
   */
  public static final class Time {

    public String name;
    public int field;
    public int amount;

    public Time(String name, int field, int amount) {
      this.name = name;
      this.field = field;
      this.amount = amount;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static final Time[] TIMES = {
    new Time("1 day ago", Calendar.DATE, -1),
    new Time("7 days ago", Calendar.DATE, -7),
    new Time("30 days ago", Calendar.DATE, -30),
    new Time("6 months ago", Calendar.MONTH, -6)
  };

  /**
   * This is a simple mutable list model. It is less general than DefaultListModel but is
   * thread-safe. It supports adding elements in order according to a Comparator.
   */
  private static final class SimpleListModel extends AbstractListModel {

    List<Object> list;
    Comparator<Object> comparator;

    public SimpleListModel() {
      clear();
      setComparator(null);
    }

    public synchronized void setList(List<Object> list) {
      this.list = list;
      fireContentsChanged(this, 0, list.size() - 1);
    }

    public synchronized List getList() {
      return new ArrayList<Object>(list);
    }

    public synchronized void clear() {
      setList(new ArrayList<Object>());
    }

    public synchronized void add(Object o) {
      int low, mid, high;
      int cmp;

      if (comparator == null) {
        list.add(o);
        fireIntervalAdded(this, list.size() - 1, list.size() - 1);
        return;
      }

      low = 0;
      high = list.size();
      while (true) {
        mid = (low + high) / 2;
        if (low >= high) {
          break;
        }
        cmp = comparator.compare(o, list.get(mid));
        if (cmp < 0) {
          high = mid;
        } else {
          low = mid + 1;
        }
      }
      list.add(mid, o);
      fireIntervalAdded(this, list.size() - 1, list.size() - 1);
    }

    public synchronized void setComparator(Comparator<Object> comparator) {
      this.comparator = comparator;
      Collections.sort(list, comparator);
      fireContentsChanged(this, 0, list.size() - 1);
    }

    @Override
    public synchronized int getSize() {
      return list.size();
    }

    @Override
    public synchronized Object getElementAt(int index) {
      return list.get(index);
    }
  }

  /**
   * The GapsLists are presented in a JList. This class provides a graphical representation of a
   * GapsList to put in the JList.
   */
  private final static class GapsListRenderer extends JPanel
          implements ListCellRenderer {

    private final JLabel seedNameTypeLabel, statsLabel;
    private final GapView gapView;

    public GapsListRenderer() {
      super();

      Box box;

      setOpaque(true);
      setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
      box = Box.createVerticalBox();

      seedNameTypeLabel = new JLabel();
      box.add(seedNameTypeLabel);
      statsLabel = new JLabel();
      box.add(statsLabel);
      box.add(Box.createVerticalGlue());

      /* Allow enough space for the two labels. */
      box.setPreferredSize(new Dimension(200, GAP_VIEW_HEIGHT));
      box.setMaximumSize(box.getPreferredSize());
      add(box);
      add(Box.createHorizontalStrut(10));
      gapView = new GapView();
      add(gapView);

      setPreferredSize(new Dimension(getPreferredSize().width,
              GAP_VIEW_HEIGHT));
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
      GapsList gapsList;

      if (value == null) {
        seedNameTypeLabel.setText("");
        statsLabel.setText("");
        gapView.setGaps(null);
      } else {
        gapsList = (GapsList) value;
        Font font = new Font("Roman", Font.PLAIN, 10);

        // if the percent says 100, but we have gaps, set it to 99.99% to show gaps.
        // JP: MultiGapReportPanel shows one more gap than gapReportPanel, but calls
        // the same size() fcn... 
        float percent = gapsList.getAvailability();
        //if(PERCENT_FORMAT.format(percent).equals("100.00%") && gapsList.size() > 0){
        if (percent > .9999f) {
          percent = 1.0f;
        }

        seedNameTypeLabel.setText(gapsList.seedName + " " + gapsList.type + " ("
                + PERCENT_FORMAT.format(percent) + " / " + (gapsList.size()) + " gaps)");
        statsLabel.setText("avgLen: " + gapsList.getAveGapLen() / 1000
                + " s   longest: " + gapsList.getLongestGapLen() / 1000 + " s");
        seedNameTypeLabel.setFont(font);
        statsLabel.setFont(font);
        gapView.setGaps(gapsList);
      }

      if (isSelected) {
        setBackground(list.getSelectionBackground());
        seedNameTypeLabel.setForeground(list.getSelectionForeground());
        statsLabel.setForeground(list.getSelectionForeground());
        gapView.setBackground(list.getSelectionBackground());
      } else {
        setBackground(list.getBackground());
        seedNameTypeLabel.setForeground(list.getForeground());
        statsLabel.setForeground(list.getForeground());
        gapView.setBackground(list.getBackground());
      }

      return this;
    }
  }

  /**
   * Create a new MultiGapReportPanel.
   */
  public MultiGapReportPanel() {
    startup = true;
    ActionListener searchActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        search();
      }
    };

    // add getSeedNameButtonListener
    ActionListener getSeedNameListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        seedNameButton.setVisible(false);
        seedNameScrollPane.setVisible(true);
        globField.setEnabled(true);
        typeBox.setEnabled(true);
        searchButton.setEnabled(true);
        //getSeedNames();
      }
    };
    // add getSeedNameButtonListener
    ActionListener getTypeListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        seedNameButton.setVisible(false);
        seedNameScrollPane.setVisible(true);
        //getSeedNames();
      }
    };

    UC.Look((MultiGapReportPanel) this);

    JPanel panel, auxPanel;
    Box box, auxBox;
    JLabel label;
    JButton saveImageButton, saveStatsButton;
    JComboBox timeBox;
    try {

      if (dbconnstatus == null) {
        dbconnstatus = new DBConnectionThread(Util.getProperty("StatusDBServer"), "readonly", "status",
                false, false, "gap", Util.getOutput());
      }
      if (!dbconnstatus.waitForConnection()) {
        if (!dbconnstatus.waitForConnection()) {
          if (!dbconnstatus.waitForConnection()) {
            Util.prta("MGP: ***** Did not get a status dbconnstatus for gaps!");
          }
        }
      }
      dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false, "edgeGap", Util.getOutput());
      if (!dbconnedge.waitForConnection()) {
        if (!dbconnedge.waitForConnection()) {
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              Util.prta("MGP:  *** did not get an edge dbconnstatus");
            }
          }
        }
      }

    } catch (InstantiationException e) {
      Util.prta(e + "Opening read only dbconnstatus to status!");
    }
    dbconnstatus = DBConnectionThread.getThread("gap");
    Util.prta("MGP: gap=" + dbconnstatus + " uc=" + UC.getConnection()
            + " status=" + DBConnectionThread.getConnection("status")
            + " anss=" + DBConnectionThread.getConnection("anss"));
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
    UC.Look(panel);

    seedNameList = new JList(new SimpleListModel());
    seedNameList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    seedNameList.setVisibleRowCount(NUM_VISIBLE_SEED_NAMES);
    auxBox = Box.createVerticalBox();
    UC.Look(auxBox);
    label = new JLabel("SEED names:");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(label);
    seedNameScrollPane = new JScrollPane(seedNameList,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    seedNameScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(seedNameScrollPane);

    // add button here to get rid of scroll pane until btn pressed
    seedNameScrollPane.setVisible(false);
    seedNameButton = new JButton("Get Names");
    seedNameButton.addActionListener(getSeedNameListener);
    auxBox.add(seedNameButton);
    auxBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    panel.add(auxBox);

    panel.add(Box.createHorizontalStrut(10));

    auxPanel = new JPanel();
    auxPanel.setLayout(new BoxLayout(auxPanel, BoxLayout.PAGE_AXIS));
    UC.Look(auxPanel);

    auxPanel.add(Box.createVerticalGlue());

    box = Box.createHorizontalBox();

    auxBox = Box.createVerticalBox();
    label = new JLabel("Start date:");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(label);
    startSpinner = new JSpinner(new SpinnerDateModel());
    startSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(startSpinner);
    auxBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    auxBox.setMaximumSize(new Dimension(auxBox.getMaximumSize().width,
            auxBox.getPreferredSize().height));
    box.add(auxBox);

    box.add(Box.createHorizontalStrut(2));

    auxBox = Box.createVerticalBox();
    label = new JLabel("End date:");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(label);
    endSpinner = new JSpinner(new SpinnerDateModel());
    endSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(endSpinner);
    auxBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    auxBox.setMaximumSize(new Dimension(auxBox.getMaximumSize().width,
            auxBox.getPreferredSize().height));
    box.add(auxBox);

    box.add(Box.createHorizontalStrut(4));

    timeBox = new JComboBox(TIMES);
    timeBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) { //Util.prt("Item state changed ev="+event);
        Time time;

        time = (Time) event.getItem();
        setDates(time.field, time.amount);
        if (!startup) {
          search();
        }
      }
    });
    timeBox.setSelectedIndex(1);
    timeBox.setSelectedIndex(0);
    timeBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    timeBox.setMaximumSize(timeBox.getPreferredSize());
    box.add(timeBox);

    box.add(Box.createVerticalStrut(10));

    box.add(Box.createHorizontalGlue());
    Util.prta("MGP: empty rec");
    emptyCheckBox = new JCheckBox("Include empty records");
    emptyCheckBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    UC.Look(emptyCheckBox);
    box.add(emptyCheckBox);

    box.setMaximumSize(new Dimension(box.getMaximumSize().width,
            box.getPreferredSize().height));

    auxPanel.add(box);

    auxPanel.add(Box.createVerticalStrut(4));

    box = Box.createHorizontalBox();

    auxBox = Box.createVerticalBox();
    label = new JLabel("Additional SEED name patterns:");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(label);
    globField = new JTextField(30);
    globField.addActionListener(searchActionListener);
    globField.setEnabled(false);
    globField.setAlignmentX(Component.LEFT_ALIGNMENT);
    globField.setMaximumSize(new Dimension(globField.getPreferredSize().width,
            globField.getPreferredSize().height));
    auxBox.add(globField);
    auxBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    box.add(auxBox);

    box.add(Box.createHorizontalStrut(10));
    Util.prta("MGP: Type");
    auxBox = Box.createVerticalBox();
    label = new JLabel("Type:");
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(label);
    typeBox = new JComboBox();
    typeBox.setEnabled(false);
    typeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    auxBox.add(typeBox);
    auxBox.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    box.add(auxBox);

    box.add(Box.createHorizontalGlue());

    searchButton = new JButton("Search");
    searchButton.addActionListener(searchActionListener);
    searchButton.setEnabled(false);
    searchButton.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    box.add(searchButton);
    Util.prta("MGP: getTypes()");
    getTypes();
    // fill the typeBox before the seed names are filled

    box.setMaximumSize(new Dimension(box.getMaximumSize().width,
            box.getPreferredSize().height));

    auxPanel.add(box);

    auxPanel.add(Box.createVerticalStrut(4));

    box = Box.createHorizontalBox();

    // JP: change text to say (only if seed names gotten) on [] and *, *   ?
    Util.prta("MGP: big label");
    label = new JLabel("<html>"
            + "'*' is a wildcard; \"US*\" matches everything that starts with \"US\".<br>"
            + "'?' matches any single character: \"USBMO&nbsp;&nbsp;?HN\" matches \"USBMO&nbsp;&nbsp;BHN\" and \"USBMO&nbsp;&nbsp;LHN\".<br>"
            + "Square brackets match any character inside; \"USBOZ*BH[EN]*\" matches \"USBOZ&nbsp;&nbsp;BHE\" and \"USBOZ&nbsp;&nbsp;BHN\".<br>"
            + "Multiple patterns can be separated by commas: \"USDGMT*, *BHZ*\"."
            + "</html>");
    label.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    box.add(label);

    box.add(Box.createHorizontalStrut(10));
    box.add(Box.createHorizontalGlue());

    box.setMaximumSize(new Dimension(box.getMaximumSize().width,
            box.getPreferredSize().height));

    auxPanel.add(box);

    auxPanel.setAlignmentY(Component.BOTTOM_ALIGNMENT);

    panel.add(auxPanel);

    panel.setMaximumSize(new Dimension(panel.getMaximumSize().width,
            panel.getPreferredSize().height));
    add(panel);

    add(Box.createVerticalStrut(10));

    statusLabel = new JLabel("Waiting for user input...");
    statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    add(statusLabel);

    progressBar = new JProgressBar();
    progressBar.setStringPainted(true);
    progressBar.setVisible(false);
    add(progressBar);

    add(Box.createVerticalStrut(10));

    list = new JList(new SimpleListModel());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new GapsListRenderer());
    ((SimpleListModel) list.getModel()).setComparator(DEFAULT_COMPARATOR);
    add(new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
    list.setVisibleRowCount(NUM_VISIBLE_GAP_VIEWS);

    add(Box.createVerticalStrut(10));

    box = Box.createHorizontalBox();
    saveImageButton = new JButton("Save to postscript");
    saveImageButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        popupSaveDialog();
      }
    });
    box.add(saveImageButton);
    saveStatsButton = new JButton("Save statistics");
    saveStatsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        popupSaveStatsDialog();
      }
    });
    box.add(saveStatsButton);
    box.add(Box.createHorizontalStrut(20));
    ioLabel = new JLabel();
    box.add(ioLabel);
    box.add(Box.createHorizontalGlue());
    box.add(new JLabel("Sort by "));
    sortComboBox = new JComboBox(COMPARATORS);
    sortComboBox.setSelectedItem(DEFAULT_COMPARATOR);
    sortComboBox.setMaximumSize(sortComboBox.getPreferredSize());
    sortComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        ((SimpleListModel) list.getModel()).setComparator(
                (Comparator<Object>) sortComboBox.getSelectedItem());
      }
    });
    box.add(sortComboBox);
    add(box);

    fileChooser = new JFileChooser();
    Util.prta("MGP: start getSeedNames()");

    getSeedNames();        // get seed names even though we wont show them
    Util.prta("MGP: end getSeedNames()");
  }

  /* Initialize the SEED name list in a separate thread because it takes a
       little while. */
  private void getSeedNames() {
    new Thread() {
      @Override
      public void run() {
        final List types;
        //try{sleep(60000);} catch(InterruptedException e) {}
        try {
          statusLabel.setText("Loading SEED names...");
          seedNames = getSEEDNames(dbconnedge.getConnection());
          Util.prta("getSeedNames() completed.");
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              Iterator iterator;

              ((SimpleListModel) seedNameList.getModel()).setList(seedNames);
              seedNameScrollPane.setMinimumSize(seedNameScrollPane.getPreferredSize());
              revalidate();
              statusLabel.setText(seedNames.size() + " SEED names loaded");
            }
          });
        } catch (SQLException e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              statusLabel.setText("Could not load SEED names.");
            }
          });
        }
      }
    }.start();
  }

  /**
   * Perform the search based on the user's current selection. Matching GapsLists are added to the
   * JList. The search is run in a separate thread.
   */
  private void search() {
    ((SimpleListModel) list.getModel()).clear();

    new Thread() {
      @Override
      public void run() {
        final List<Object> matches;
        final String type;
        final Date start, end;
        final boolean empty;
        List patterns;
        Statement statement;
        Iterator iterator;

        // don't use matches if all the seeds have not been found yet
        try {
          if (seedName == null) {
            statusLabel.setText("Searching for seeds...");
            matches = getSEEDNames2(dbconnedge.getConnection(), globField.getText());
          } else {
            patterns = globsToPatterns(globField.getText());
            matches = getMatchingSEEDNames(seedNames, patterns);
            /* Add the selections from the selection list, making sure there are
              no duplicates. */
            matches.removeAll(Arrays.asList(seedNameList.getSelectedValues()));
            matches.addAll(Arrays.asList(seedNameList.getSelectedValues()));
          }

          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              statusLabel.setVisible(false);
              progressBar.setMaximum(matches.size());
              progressBar.setValue(0);
              progressBar.setVisible(true);
            }
          });

          type = (String) typeBox.getSelectedItem();
          start = (Date) startSpinner.getValue();
          end = (Date) endSpinner.getValue();
          empty = emptyCheckBox.isSelected();

          try {
            iterator = matches.iterator();
            while (iterator.hasNext()) {
              final GapsList gaps = new GapsList(new HoldingsList(dbconnstatus.getConnection(),
                      (String) iterator.next(), type, start, end));
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  progressBar.setValue(progressBar.getValue() + 1);
                  //if (!empty && gaps.size() == 1 && gaps.getAvailability() <0.01)   //DCK - this prevented no gap sections (all data present) from plotting
                  //  return;                         // Alernate behavior is to do this and no-gap sections are omitted
                  ((SimpleListModel) list.getModel()).add(gaps);
                }
              });
            }
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                int size;

                size = list.getModel().getSize();
                if (size == 1) {
                  statusLabel.setText(size + " match.");
                } else {
                  statusLabel.setText(size + " matches.");
                }
              }
            });
          } catch (SQLException e) {
            statusLabel.setText("Couldn't retrieve gap data. e=" + e.getMessage());
          } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
          } finally {
            progressBar.setVisible(false);
            statusLabel.setVisible(true);
          }
        } catch (SQLException e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              statusLabel.setText("Could not load SEED names.");
            }
          });
        }
      }
    }.start();
  }

  /**
   * Get a list of the distinct SEED names that exist on the database over dbconnstatus.
   *
   * @param dbconnstatus the database dbconnstatus to use
   * @return a list of SEED names
   */
  private static List<Object> getSEEDNames(Connection dbconnstatus) throws SQLException {
    Statement statement;
    ResultSet rs;
    ArrayList<Object> result;

    result = new ArrayList<Object>();
    statement = dbconnstatus.createStatement();
    try {
      rs = statement.executeQuery("SELECT channel FROM edge.channel");
      while (rs.next()) {
        result.add(rs.getString("channel"));
      }
    } finally {
      statement.close();
    }
    Collections.sort(result, NAME_COMPARATOR);
    Util.prta("MGP: getSEEDNames() done.");
    return result;
  }

  /* Initialize the SEED name list in a separate thread because it takes a
       little while. */
  private void getTypes() {
    new Thread() {
      @Override
      public void run() {
        final List types;
        //try{sleep(60000);} catch(InterruptedException e) {}
        try {
          types = getTypes(dbconnstatus.getConnection());
          Iterator iterator = types.iterator();
          while (iterator.hasNext()) {
            typeBox.addItem(iterator.next());
          }
          typeBox.setMaximumSize(typeBox.getPreferredSize());
          globField.setEnabled(true);
          typeBox.setEnabled(true);
          searchButton.setEnabled(true);

        } catch (SQLException e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              statusLabel.setText("Could not load SEED types.");
            }
          });
        }
        Util.prta("MGP: Load getTypes() completed. items=" + typeBox.getItemCount());
      }
    }.start();
  }

  /**
   * Get a list of the distinct types that exist on the database over dbconnstatus.
   *
   * @param dbconnstatus the database dbconnstatus to use
   * @return a list of types
   */
  private static List getTypes(Connection dbconnstatus) throws SQLException {
    Statement statement;
    ResultSet rs;
    ArrayList<Object> result;

    result = new ArrayList<Object>();

    statement = dbconnstatus.createStatement();
    try {
      rs = statement.executeQuery(
              "SELECT DISTINCT type FROM status.holdings WHERE left(seedname,2)='US' OR left(seedname,2)='GS'");
      while (rs.next()) {
        result.add(rs.getString("type").trim());
      }
      rs.close();
      rs = statement.executeQuery(
              "SELECT DISTINCT type FROM status.holdingshist WHERE left(seedname,2)='US' OR left(seedname,2)='GS'");
      while (rs.next()) {
        String type = rs.getString("type").trim();
        result.add(type);
      }
      rs.close();
      rs = statement.executeQuery(
              "SELECT DISTINCT type FROM status.holdingshist2 WHERE left(seedname,2)='US' OR left(seedname,2)='GS'");
      while (rs.next()) {
        String type = rs.getString("type").trim();
        for (Object result1 : result) {
          if (result1.equals(type)) {
          }
        }
        result.add(type);
      }
      rs.close();
    } finally {
      statement.close();
    }
    Collections.sort(result, NAME_COMPARATOR);
    Util.prta("MGP: Type fetch done");
    return result;
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

  /**
   * Get a list of SEED names that match any of a list of patterns.
   *
   * @param seedNames the list of SEED names to filter
   * @param patterns a list of {@link Pattern}s against which the SEED names are filtered
   * @return a list of those SEED names that match any of the patterns in {@code
   *         patterns}.
   */
  private static List<Object> getMatchingSEEDNames(List seedNames, List patterns) {
    Iterator seedNameIterator, patternIterator;
    String seedName;
    Pattern pattern;
    ArrayList<Object> result;

    result = new ArrayList<Object>();

    seedNameIterator = seedNames.iterator();
    while (seedNameIterator.hasNext()) {
      seedName = (String) seedNameIterator.next();
      patternIterator = patterns.iterator();
      while (patternIterator.hasNext()) {
        pattern = (Pattern) patternIterator.next();
        if (pattern.matcher(seedName).matches()) {
          result.add(seedName);
        }
      }
    }

    return result;
  }

  /**
   * Convert a string containing a comma- and whitespace-separated list of globs to a list of
   * {@link Pattern}s.
   *
   * @param globString a comma- and whitespace-separated list of globs
   * @return a list of Patterns corresponding to the globs
   * @throws IllegalArgumentException if any of the globs is invalid in some way
   * @see #globToRegex(String)
   */
  private static List globsToPatterns(String globString) {
    String[] globs;
    String regex;
    int i;
    ArrayList<Object> result;

    result = new ArrayList<Object>();

    globs = globString.trim().split(" *, *");
    for (i = 0; i < globs.length; i++) {
      regex = globToRegex(globs[i]);
      result.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    return result;
  }

  /**
   * Convert a glob to a regular expression string.
   *
   * The syntax for globs is based on that of the Unix shell. '*' matches any number of characters.
   * '?' matches any single character. '[abc]' matches 'a', 'b', or 'c'. '[^abc]' matches any
   * character but 'a', 'b', and 'c'. Special characters can be escaped by a backslash.
   *
   * @param glob a string containing a glob pattern
   * @return a string containing a regular expression
   * @throws IllegalArgumentException if the glob is invalid in some way (unmatched brackets,
   * premature end of string, etc.)
   */
  private static String globToRegex(String glob) {
    int i;
    char c;
    int charClass;
    StringBuffer regex;

    regex = new StringBuffer();
    charClass = -1;
    for (i = 0; i < glob.length(); i++) {
      c = glob.charAt(i);
      if (c == '*') {
        regex.append(".*");
      } else if (c == '?') {
        regex.append(".");
      } else if (c == '\\') {
        i++;
        if (i >= glob.length()) {
          throw new IllegalArgumentException("End of string while processing '\\' at position " + (i - 1) + " in glob \"" + glob + "\"");
        }
        c = glob.charAt(i);
        if (Character.isLetter(c)) {
          regex.append(c);
        } else {
          regex.append("\\").append(c);
        }
      } else if (c == '[') {
        charClass = i;
        regex.append(c);
      } else if (c == ']') {
        charClass = -1;
        regex.append(c);
      } else if (c == '^' && charClass >= 0) {
        regex.append(c);
      } else if (!Character.isLetter(c)) {
        regex.append("\\").append(c);
      } else {
        regex.append(c);
      }
    }

    if (charClass >= 0) {
      throw new IllegalArgumentException("End of string while reading character class that started at position " + charClass + " in glob \"" + glob + "\"");
    }

    return regex.toString();
  }

  /**
   * Display a save dialog and save the current gap data to the selected file if the user desires.
   */
  private void popupSaveDialog() {
    File file;
    FileWriter writer;
    int status;

    status = fileChooser.showSaveDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      file = fileChooser.getSelectedFile();
      try {
        writer = new FileWriter(file);
        writePostScript(((SimpleListModel) list.getModel()).getList(), writer);
        writer.close();
        ioLabel.setText("Gaps lists saved to " + file.getName() + ".");
      } catch (IOException e) {
        ioLabel.setText(e.getMessage());
      }
    } else if (status == JFileChooser.CANCEL_OPTION) {
      ioLabel.setText("Save canceled.");
    }
  }

  /**
   * Display a save dialog and save the current gap statistics to the selected file if the user
   * desires.
   */
  private void popupSaveStatsDialog() {
    File file;
    FileWriter writer;
    int status;

    status = fileChooser.showSaveDialog(this);
    if (status == JFileChooser.APPROVE_OPTION) {
      file = fileChooser.getSelectedFile();
      try {
        writer = new FileWriter(file);
        writeStatsFile(((SimpleListModel) list.getModel()).getList(), writer);
        writer.close();
        ioLabel.setText("Gaps stats saved to " + file.getName() + ".");
      } catch (IOException e) {
        ioLabel.setText(e.getMessage());
      }
    } else if (status == JFileChooser.CANCEL_OPTION) {
      ioLabel.setText("Save canceled.");
    }
  }

  /**
   * Write a graphical representation of all the {@link GapsList}s currently shown to {@code Writer}
   * in PostScript format. The output is the opposite of what is show by the JList; gaps are filled
   * in and holdings are white, instead of the other way around. As many pages as are needed to show
   * all the GapsLists are printed.
   *
   * @param gaps the list of GapsLists to write
   * @param writer where to write to
   */
  public static void writePostScript(List gaps, Writer writer) {
    /* The width of the page in PostScript points (1/72 inch). */
    final double PAGE_WIDTH = 8.5 * 72;
    /* The height of the page in PostScript points (1/72 inch). */
    final double PAGE_HEIGHT = 11 * 72;
    /* The margin around all four sides of the page. */
    final double PAGE_MARGIN = 0.5 * 72;
    /* The height of each gap bar. */
    final double BAR_HEIGHT = 0.5 * 72;
    /* The name of the font used for labels. */
    final String LABEL_FONT = "Helvetica";
    /* The width of the label before the bar. */
    final double LABEL_WIDTH = 2.0 * 72;
    /* The height of labels. */
    final double LABEL_HEIGHT = BAR_HEIGHT / 4;
    /* The minimum visible width of a gap. Gaps that would be show smaller than
       this will be shown this big. */
    final double MINIMUM_GAP_WIDTH = 0.2;
    /* The color of the brackets on each side of the gap bars. */
    final Color BRACKET_COLOR = Color.GRAY.brighter();
    /* How many entries fit on a page between the margins. */
    final int ENTRIES_PER_PAGE = (int) ((PAGE_HEIGHT - 2 * PAGE_MARGIN) / BAR_HEIGHT);

    PrintWriter out;
    Iterator iterator;
    GapsList gapsList;
    SpansList.Span gap;
    double duration;
    int i, n;

    out = new PrintWriter(new BufferedWriter(writer));
    out.print("%!PS-Adobe-3.0\n"
            + "%%BoundingBox: 0 0 " + (int) PAGE_WIDTH + " " + (int) PAGE_HEIGHT + "\n"
            + "%%HiResBoundingBox: 0 0 " + PostScript.FF.format(PAGE_WIDTH) + " " + PostScript.FF.format(PAGE_HEIGHT) + "\n"
            + "%%EndComments\n"
            + "\n"
            + "/width " + PostScript.FF.format(PAGE_WIDTH) + " def\n"
            + "/height " + PostScript.FF.format(PAGE_HEIGHT) + " def\n"
            + "/margin " + PostScript.FF.format(PAGE_MARGIN) + " def\n"
            + "\n"
            + "/labelfont /" + LABEL_FONT + " findfont def\n"
            + "/labelwidth " + PostScript.FF.format(LABEL_WIDTH) + " def\n"
            + "/labelheight " + PostScript.FF.format(LABEL_HEIGHT) + " def\n"
            + "\n"
            + "/barwidth width margin 2 mul sub labelwidth sub def\n"
            + "/barheight " + PostScript.FF.format(BAR_HEIGHT) + " def\n"
            + "\n"
            + "/minwidth " + PostScript.FF.format(MINIMUM_GAP_WIDTH) + " def\n"
            + "\n"
            + "/bracketcolor { " + PostScript.colorToString(BRACKET_COLOR) + " } def\n"
            + "\n"
            + "/drawbox {\n"
            + "\texch barwidth mul labelwidth add /l exch def\n"
            + "\tbarwidth mul labelwidth add l sub dup minwidth lt { pop minwidth } if\n"
            + "\tl add /r exch def\n"
            + "\tnewpath\n"
            + "\tl 0 moveto r 0 lineto r barheight lineto l barheight lineto\n"
            + "\tclosepath\n"
            + "\tfill\n"
            + "} def\n"
            + "\n"
            + "/drawbrackets {\n"
            + "\tgsave bracketcolor setcolor\n"
            + "\tnewpath labelwidth barheight 4 div add 0 moveto\n"
            + "\tbarheight 4 div neg 0 rlineto\n"
            + "\t0 barheight rlineto\n"
            + "\tbarheight 4 div 0 rlineto stroke\n"
            + "\tnewpath labelwidth barwidth add barheight 4 div sub 0 moveto\n"
            + "\tbarheight 4 div 0 rlineto\n"
            + "\t0 barheight rlineto\n"
            + "\tbarheight 4 div neg 0 rlineto stroke\n"
            + "\tgrestore\n"
            + "} def\n"
            + "\n"
            + "labelfont labelheight scalefont setfont\n");

    /* To make the PostScript output match the screen output; that is, to make
       the holdings dark and the gaps light, first draw a dark box over the
       whole gap bar and draw the gaps in white with "1.0 setcolor". */
    iterator = gaps.iterator();
    while (iterator.hasNext()) {
      out.print("\n"
              + "margin height margin sub translate\n");
      n = 0;
      while (iterator.hasNext() && n < ENTRIES_PER_PAGE) {
        gapsList = (GapsList) iterator.next();
        duration = gapsList.end.getTime() - gapsList.start.getTime();
        out.print("\n"
                + "0 barheight neg translate\n"
                + "0 barheight labelheight sub 2 div moveto\n"
                + "(" + PostScript.escape(gapsList.seedName)
                + " " + PostScript.escape(gapsList.type)
                + " " + PostScript.escape("(" + PERCENT_FORMAT.format(gapsList.getAvailability()) + ")")
                + ") show\n"
                + "drawbrackets\n");
        for (i = 0; i < gapsList.size(); i++) {
          gap = gapsList.get(i);
          out.print(PostScript.FF.format((gap.start.getTime() - gapsList.start.getTime()) / duration)
                  + " " + PostScript.FF.format((gap.end.getTime() - gapsList.start.getTime()) / duration)
                  + " drawbox\n");
        }
        n++;
      }
      out.print("\n"
              + "showpage\n");
    }

    out.print("\n");
    out.print("%%EOF\n");
    out.flush();
  }

  /**
   * Write a file that contains the statistical information of the gap data. Outputs to the file
   * specified in writer. Outputs the seedname, availability, number of gaps, the longest gap, and
   * the average gap length.
   *
   * @param gaps the list of GapsLists to write
   * @param writer where to write to
   */
  public static void writeStatsFile(List gaps, Writer writer) {
    PrintWriter out;
    Iterator iterator;
    GapsList gapsList;
    SpansList.Span gap;

    out = new PrintWriter(new BufferedWriter(writer));
    out.print("Name         Availability  #Gaps  LongestGap(sec)  AverageLength(sec)\n");

    iterator = gaps.iterator();
    while (iterator.hasNext()) {
      gapsList = (GapsList) iterator.next();
      out.print(((String) (gapsList.seedName)).concat("             ").substring(0, 13)
              + (PERCENT_FORMAT.format(gapsList.getAvailability()).concat("              ")).substring(0, 14)
              + Integer.toString(gapsList.size()).concat("       ").subSequence(0, 7)
              + Long.toString(gapsList.getLongestGapLen() / 1000).concat("                 ").substring(0, 17)
              + gapsList.getAveGapLen() / 1000 + "\n");
    }
    out.flush();
  }

  /**
   * Get a list of the distinct SEED names, which are similar to the string in seed, that exist on
   * the database over dbconnstatus. Use this when the list of seed names have not been generated
   * yet.
   *
   * @param dbconnstatus the database dbconnstatus to use
   * @param seed the string to match names with
   * @return a list of SEED names
   */
  private static List<Object> getSEEDNames2(Connection dbconnstatus, String seed) throws SQLException {
    Statement statement;
    ResultSet rs;
    ArrayList<Object> result;
    Util.prta("MGP: getSEEDNames2() start.");
    seed = seed.replaceAll("\\*", "%");   // % are for database query
    seed = seed.replaceAll("\\?", "_");   // _ are for database 

    result = new ArrayList<Object>();
    statement = dbconnstatus.createStatement();
    try {
      rs = statement.executeQuery("SELECT DISTINCT seedname FROM status.holdings "
              + "WHERE seedName LIKE '" + seed + "'");
//      rs = statement.executeQuery("SELECT DISTINCT seedname FROM status.holdings " +
//           "WHERE seedName REGEXP '"+seed+"'");
      while (rs.next()) {
        result.add(rs.getString("seedname"));
      }
    } finally {
      statement.close();
    }
    Collections.sort(result, NAME_COMPARATOR);
    Util.prta("MGP: getSEEDNames2() done");
    return result;
  }
}
