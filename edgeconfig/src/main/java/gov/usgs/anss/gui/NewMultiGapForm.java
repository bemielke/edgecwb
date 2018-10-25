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
import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;
import javax.swing.*;

/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class NewMultiGapForm extends javax.swing.JPanel {

  public static final String TITLE = "New Multiple gap report";
  private static final int NUM_VISIBLE_SEED_NAMES = 10;
  private static final int NUM_VISIBLE_GAP_VIEWS = 20;
  private static final int GAP_VIEW_HEIGHT = 30; //25;
  private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.00%");
  private final JFileChooser fileChooser;
  //private JLabel ioLabel;
  private DBConnectionThread connection;

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
  private static final Comparator<Object> DEFAULT_COMPARATOR = COMPARATORS[0];//NAME_COMPARATOR;

  /**
   * Opens database connection *
   */
  private void dbopen() {

    if (connection == null) {
      connection = DBConnectionThread.getThread("gap");
    }
    if (connection == null) {
      connection = DBConnectionThread.getThread("status");
    }
    if (connection == null) {
      connection = DBConnectionThread.getThread("edge");
    }
    if (connection == null) {
      connection = DBConnectionThread.getThread("metadata");
    }
    if (connection == null) {
      Util.prt("***** NewMultiGap Form cannot find the DBConnection thread to use");
      Util.prt(DBConnectionThread.getThreadList());
    } else {
      Util.prta("NMGF: use c=" + connection);
    }
  }

  private void getTypes() {
    new Thread() {
      @Override
      public void run() {
        final List types;
        //try{sleep(60000);} catch(InterruptedException e) {}
        try {
          types = getTypes(connection);
          Iterator iterator = types.iterator();
          while (iterator.hasNext()) {
            typeBox.addItem(iterator.next());
          }
          typeBox.setMaximumSize(typeBox.getPreferredSize());
          typeBox.setEnabled(true);

        } catch (SQLException e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              statusLabel.setText("NMGF:Could not load SEED ENW gettypes.");
            }
          });
        }
        Util.prta("NMGF:Load NEW getTypes() completed. items=" + typeBox.getItemCount());
      }
    }.start();
    Util.prta("NMGF: getTypes() NEW thread started");
  }

  /**
   * Get a list of the distinct types that exist on the database over connection.
   *
   * @param connection the database connection to use
   * @return a list of types
   */
  private static List getTypes(DBConnectionThread connection) throws SQLException {
    Statement statement;
    ResultSet rs;
    ArrayList<Object> result;

    result = new ArrayList<Object>();

    statement = connection.getNewStatement(false);
    try {
      Util.prta("NMGF: getTypes start holdings");
      rs = statement.executeQuery(
              "SELECT DISTINCT type FROM status.holdings");
      while (rs.next()) {
        result.add(rs.getString("type").trim());
      }
      rs.close();
      Util.prta("NMGF: getTypes start holdingshist c=" + connection.toString());
      rs = statement.executeQuery(
              "SELECT DISTINCT type FROM status.holdingshist WHERE left(seedname,2)='US' OR left(seedname,2)='GS'");
      while (rs.next()) {
        String type = rs.getString("type").trim();
        for (int i = 0; i < result.size(); i++) {
          if (result.get(i).equals(type)) {
            continue;
          }
        }
        result.add(type);
      }
      rs.close();
      Util.prta("NMGF: getTypes start holdingshist2 c=" + connection.toString());
      rs = statement.executeQuery(
              "SELECT DISTINCT type FROM status.holdingshist2 WHERE left(seedname,2)='US' OR left(seedname,2)='GS'");
      while (rs.next()) {
        String type = rs.getString("type").trim();
        for (int i = 0; i < result.size(); i++) {
          if (result.get(i).equals(type)) {
            continue;
          }
        }
        result.add(type);
      }
      Util.prta("NMGF: getTypes completed");
      rs.close();
    } finally {
      statement.close();
    }
    return result;
  }

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

  private static final class GapsListRenderer extends JPanel
          implements ListCellRenderer {

    private final JLabel seedNameTypeLabel;
    private final JLabel statsLabel;
    private final GapView gapView;

    /**
     * The GapsLists are presented in a JList. This class provides a graphical representation of a
     * GapsList to put in the JList.
     */
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

  /*private Date stringtodate(String strDate) 
 {
    DateFormat formatter ; 
    Date date = new Date();
  
   try {  
     
   formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
   date = (Date)formatter.parse(strDate);
   return date;
    }
    catch (ParseException e) {
      statusLabel.setText(e.getMessage());
    }
    return date;
} */
  private void search() {
    ((SimpleListModel) list.getModel()).clear();

    new Thread() {
      @Override
      public void run() {
        final List<Object> matches;
        final List patterns;
        final String type, seed;
        final Date start, end;
        final boolean empty;

        Statement statement;
        Iterator iterator;

        // don't use matches if all the seeds have not been found yet
        try {
          ioLabel.setText("");
          seed = (String) seedTxtFld.getText();
          type = (String) typeBox.getSelectedItem();
          //start = (Date) stringtodate(startDateFld.getText());
          start = Util.stringToDate2(startDateFld.getText());

          //end = (Date) stringtodate(endDateFld.getText());
          end = Util.stringToDate2(endDateFld.getText());
          matches = getSEEDNames(connection.getConnection(), seed);
          jProgressBar.setValue(0);
          jProgressBar.setMaximum(matches.size());

          try {
            iterator = matches.iterator();
            while (iterator.hasNext()) {
              final GapsList gaps = new GapsList(new HoldingsList(connection.getConnection(),
                      (String) iterator.next(), type, start, end));
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  jProgressBar.setValue(jProgressBar.getValue() + 1);
                  //DCK - this prevented no gap sections (all data present) from plotting
                  if (!showEmptyChannels.isSelected() && gaps.size() == 1 && gaps.getAvailability() < 0.01) {
                    return;                         // Alernate behavior is to do this and no-gap sections are omitted
                  }
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
          } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
          } finally {

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
   * Get a list of the distinct SEED names, which are similar to the string in seed, that exist on
   * the database over connection.
   *
   * @param connection the database connection to use
   * @param seed the string to match names with
   * @return a list of SEED names
   */
  private static List<Object> getSEEDNames(Connection connection, String seed) throws SQLException {
    Statement statement;
    ResultSet rs;
    ArrayList<Object> result;

    /*seed = seed.replaceAll("\\*", "%");   // % are for database query
    seed = seed.replaceAll("\\?", "_");   // _ are for database */
    result = new ArrayList<Object>();
    statement = connection.createStatement();
    try {
      /* rs = statement.executeQuery("SELECT DISTINCT seedname FROM status.holdings " +
           "WHERE seedName LIKE '"+seed+"'"); */
      rs = statement.executeQuery("SELECT DISTINCT seedname FROM status.holdingshist "
              + "WHERE seedName REGEXP '" + seed + "'");
      while (rs.next()) {
        result.add(rs.getString("seedname"));
      }
    } finally {
      statement.close();
    }
    Collections.sort(result, NAME_COMPARATOR);

    return result;
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
        writePostScript(((NewMultiGapForm.SimpleListModel) list.getModel()).getList(), writer);
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
        writeStatsFile(((NewMultiGapForm.SimpleListModel) list.getModel()).getList(), writer);
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

  public NewMultiGapForm(DBConnectionThread conn) {
    connection = conn;
    initComponents();
    Look();
    fileChooser = new JFileChooser();
    UC.Look(jPanel1);
    dbopen();
    Util.prta("NMGF: start getTypes");
    getTypes();
    Util.prta("NMGF: end getTypes");
  }

  private void Look() {
    UC.Look(this);

  }

  /**
   * This method is called from within the constructor to initialize the form. WARNING: Do NOT
   * modify this code. The content of this method is always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel() ;
        searchBtn = new javax.swing.JButton();
        startDateFld = new javax.swing.JTextField();
        startlbl = new javax.swing.JLabel();
        endDateFld = new javax.swing.JTextField();
        endlbl = new javax.swing.JLabel();
        seedTxtFld = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        typeBox = new javax.swing.JComboBox();
        typelbl = new javax.swing.JLabel();
        statusLabel = new javax.swing.JLabel();
        jProgressBar = new javax.swing.JProgressBar();
        showEmptyChannels = new javax.swing.JRadioButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        list = new javax.swing.JList(new SimpleListModel());
        saveStatistics = new javax.swing.JButton();
        ioLabel = new javax.swing.JLabel();

        searchBtn.setText("Search");
        searchBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchBtnActionPerformed(evt);
            }
        });

        startDateFld.setText("YYYY-MM-DD HH:MM:SS");
        startDateFld.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startDateFldActionPerformed(evt);
            }
        });

        startlbl.setText("Start Date:");

        endDateFld.setText("YYYY-MM-DD HH:MM:SS");

        endlbl.setText("End Date:");

        jLabel1.setText("Seedname (NNSSSSSCCCLL) : Regular expressions is supported. (. single char, [BL] B or L)");

        typelbl.setText("Type:");

        statusLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

        showEmptyChannels.setText("Show All Gap chans");
        showEmptyChannels.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showEmptyChannelsActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                        .add(jPanel1Layout.createSequentialGroup()
                            .addContainerGap()
                            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(jPanel1Layout.createSequentialGroup()
                                    .add(startDateFld, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 189, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                        .add(jPanel1Layout.createSequentialGroup()
                                            .add(12, 12, 12)
                                            .add(endlbl))
                                        .add(jPanel1Layout.createSequentialGroup()
                                            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                            .add(endDateFld, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 189, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                        .add(jPanel1Layout.createSequentialGroup()
                                            .add(12, 12, 12)
                                            .add(typelbl))
                                        .add(jPanel1Layout.createSequentialGroup()
                                            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                            .add(typeBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                            .add(18, 18, 18)
                                            .add(showEmptyChannels))))
                                .add(startlbl)))
                        .add(jPanel1Layout.createSequentialGroup()
                            .add(242, 242, 242)
                            .add(searchBtn))
                        .add(jPanel1Layout.createSequentialGroup()
                            .addContainerGap()
                            .add(jLabel1))
                        .add(jPanel1Layout.createSequentialGroup()
                            .addContainerGap()
                            .add(seedTxtFld, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 557, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(jPanel1Layout.createSequentialGroup()
                            .addContainerGap()
                            .add(jProgressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 560, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(startlbl)
                    .add(typelbl)
                    .add(endlbl))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(startDateFld, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(typeBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 28, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(endDateFld, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(showEmptyChannels))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(seedTxtFld, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(searchBtn)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(statusLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 14, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jProgressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(514, 514, 514))
        );

        jScrollPane2.setPreferredSize(new java.awt.Dimension(350, 300));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new GapsListRenderer());
        ((SimpleListModel) list.getModel()).setComparator(DEFAULT_COMPARATOR);
        add(new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
    list.setVisibleRowCount(NUM_VISIBLE_GAP_VIEWS);
    list.setVisibleRowCount(12);
    jScrollPane2.setViewportView(list);

    saveStatistics.setText("Save Statistics");
    saveStatistics.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveStatisticsActionPerformed(evt);
        }
    });

    org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
        layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
        .add(layout.createSequentialGroup()
            .add(184, 184, 184)
            .add(saveStatistics)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(ioLabel)
            .add(137, 137, 137))
        .add(layout.createSequentialGroup()
            .addContainerGap()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(jScrollPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 572, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addContainerGap())
    );
    layout.setVerticalGroup(
        layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
        .add(layout.createSequentialGroup()
            .add(jPanel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 220, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(29, 29, 29)
            .add(jScrollPane2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 400, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(35, 35, 35)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                .add(saveStatistics)
                .add(ioLabel))
            .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
    );
    }// </editor-fold>//GEN-END:initComponents

  private void startDateFldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startDateFldActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_startDateFldActionPerformed

  private void searchBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchBtnActionPerformed
    search();
  }//GEN-LAST:event_searchBtnActionPerformed

  private void showEmptyChannelsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showEmptyChannelsActionPerformed

  }//GEN-LAST:event_showEmptyChannelsActionPerformed

  private void saveStatisticsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveStatisticsActionPerformed
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
  }//GEN-LAST:event_saveStatisticsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField endDateFld;
    private javax.swing.JLabel endlbl;
    private javax.swing.JLabel ioLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JList list;
    private javax.swing.JButton saveStatistics;
    private javax.swing.JButton searchBtn;
    private javax.swing.JTextField seedTxtFld;
    private javax.swing.JRadioButton showEmptyChannels;
    private javax.swing.JTextField startDateFld;
    private javax.swing.JLabel startlbl;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JComboBox typeBox;
    private javax.swing.JLabel typelbl;
    // End of variables declaration//GEN-END:variables
}
