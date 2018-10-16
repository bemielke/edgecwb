/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.util;

import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.*;

/**
 * This is a JComboBox that displays a list of SEED names and types retrieved
 * from a database connection. The entries in the list have the type
 * SEEDNameTypeBox.SEEDNameType.
 */
public class SEEDNameTypeBox extends JComboBox
{
  /* Yield to other threads after every YIELD_AFTER items are added to the
     box. */
  private final int YIELD_AFTER = 100;
  /* The number of items shown at once in the drop-down box. */
  private final int MAXIMUM_ROW_COUNT = 18;

  private String loadingText = "";

  /**
   * This is a simple container for a SEED name and a type so they can be stored
   * together in a SEEDNameTypeBox.
   */
  public static class SEEDNameType implements Comparable<SEEDNameType>
  {
    public String seedName, type;
    
    /**
     * Create a new SEEDNameType.
     *
     * @param seedName the SEED name
     * @param type the type
     */
    public SEEDNameType(String seedName, String type)
    {
      this.seedName = seedName;
      this.type = type;
    }

    /**
     * Compare this SEEDNameType to o (which must be a SEEDNameType object).
     * First the seedName fields are compared, and if they are equal, the type
     * fields are compared.
     *
     * @param o the other object to compare to
     * @throws ClassCastException if o is not a SEEDNameType object
     */
    @Override
    public int compareTo(SEEDNameType o)
    {
      SEEDNameType other;
      int d;

      other = (SEEDNameType) o;
      d = seedName.compareTo(other.seedName);
      if (d != 0)
        return d;
      d = type.compareTo(other.type);

      return d;
    }
  }

  /**
   * This is a ListCellRenderer that renders a SEEDNameType in a JComboBox. It
   * renders the SEED name left-justified and the type right-justified, with a
   * little space at both ends.
   */
  private class SEEDNameTypeRenderer extends JPanel implements ListCellRenderer
  {
    private final JLabel seedNameLabel, typeLabel;

    public SEEDNameTypeRenderer()
    {
      super();
      
      setOpaque(true);
      setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
      add(Box.createHorizontalStrut(5));
      seedNameLabel = new JLabel();
      add(seedNameLabel);
      add(Box.createHorizontalStrut(5));
      add(Box.createHorizontalGlue());
      typeLabel = new JLabel();
      add(typeLabel);
      /* This seems to need to be farther from the edge to avoid being
         covered by the combo box's drop-down arrow. */
      add(Box.createHorizontalStrut(20));
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
    {
      SEEDNameType nameType;
      
      if (value == null) {
        seedNameLabel.setEnabled(false);
        seedNameLabel.setText(loadingText);
        typeLabel.setText("");
      } else {
        nameType = (SEEDNameType) value;
        seedNameLabel.setEnabled(true);
        seedNameLabel.setText(nameType.seedName);
        typeLabel.setText(nameType.type);
      }

      if (isSelected) {
        setBackground(list.getSelectionBackground());
        seedNameLabel.setForeground(list.getSelectionForeground());
        typeLabel.setForeground(list.getSelectionForeground());
      } else {
        setBackground(list.getBackground());
        seedNameLabel.setForeground(list.getForeground());
        typeLabel.setForeground(list.getForeground());
      }
      
      return this;
    }
  }

  /**
   * Create a new SEEDNameTypeBox.
   */
  public SEEDNameTypeBox()
  {
    super();

    setRenderer(new SEEDNameTypeRenderer());
    setMaximumRowCount(MAXIMUM_ROW_COUNT);
  }

  /**
   * Select the first entry whose seedName matches seedName, or if no such entry
   * exists, select the first entry after where it would be. This method uses a
   * binary search, so the entries of the combo box must be sorted.
   *
   * @param seedName the seedName for which to search
   */
  public void selectSEEDName(String seedName)
  {
    SEEDNameType nameType;
    int low, mid, high;

    if (getItemCount() == 0)
      return;

    /* This is a binary search with a few differences. It has a bias towards
       smaller indexes, meaning that when keys compare equal it continues in the
       lower half. It doesn't stop early when a key compares equal, because it
       needs to find the matching entry with the lowest index. */
    low = 0;
    high = getItemCount() - 1;
    while (true) {
      mid = (low + high) / 2;
      if (low >= high)
        break;
      nameType = (SEEDNameType) getItemAt(mid);
      if (seedName.compareTo(nameType.seedName) <= 0)
        high = mid;
      else
        low = mid + 1;
    }

    setSelectedIndex(mid);
  }

  /**
   * Fill the box with entries from connection. All the distinct SEED names and
   * type from the table status.holdings are inserted, sorted in order according
   * to {@link SEEDNameType#compareTo(Object)}. This method is safe to call from
   * a thread other than Swing's event-dispatching thread, because updates to
   * the component are run through SwingUtilities.invokeLater.
   *
   * @param connection The database connection from which to get the entries
   */
  public synchronized void initialize(final Connection connection)
  {
    Statement statement;
    ResultSet rs;
    String query;
    final ArrayList<SEEDNameType> entries = new ArrayList<>();

    loadingText = "Loading...";
    repaint();

    try {
      statement = connection.createStatement();
      query = "SELECT DISTINCT seedname, type FROM status.holdings ORDER BY seedname, type";
      rs = statement.executeQuery(query);
      while (rs.next())
        entries.add(new SEEDNameType(rs.getString("seedname"), rs.getString("type")));
      statement.close();
      Collections.sort( entries);

      /* Running invokeLater is safe to do even if we are already in the
         event-dispatching thread. */
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          removeAllItems();
          for (int i = 0; i < entries.size(); i++) {
            addItem(entries.get(i));
            if ((i + 1) % YIELD_AFTER == 0)
              try{Thread.sleep(10);} catch(InterruptedException e) {}
          }
          setMaximumSize(getPreferredSize());
        }
      });
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Error in initialize.");
    }
    Util.prta("Seednametype box done.");
    loadingText = "";
    repaint();
  }
}
