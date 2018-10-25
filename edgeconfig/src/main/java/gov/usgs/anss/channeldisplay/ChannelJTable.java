/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.channeldisplay;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.guidb.CommLink;
import gov.usgs.anss.guidb.Cpu;
import gov.usgs.anss.gui.SimpleAudioPlayer;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.util.*;
import gov.usgs.edge.snw.SNWGroup;
import gov.usgs.edge.snw.SNWGroupPanel;
import gov.usgs.edge.snw.SNWRule;
import gov.usgs.anss.guidb.HoldingsList;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

public final class ChannelJTable extends JScrollPane {

  private ArrayList cells;         // cells contain a ArrayList JLabels
  private String columnLabel;
  private int maxcols;
  private int ncols;
  private int columnWidth;
  private int cellsUsed;
  private int maxCellsUsed;
  private int selectedRow, selectedCol;
  private int lastSelectedRow, lastSelectedCol;
  //static Font monofont = new Font("Courier New", Font.PLAIN, 12);;
  private static final Font monofont = new Font(Font.MONOSPACED, Font.PLAIN, 12);;
  private TableColumn [] tabColumns;
  private boolean terminate;
  private PopupForm popup;
  private DBConnectionThread dbconnedge,oracleght,dbconnstatus;
  public PopupForm getPopup() {return popup;}
  public void close() {terminate=true;dbconnedge=null;  cells=null;tabColumns=null;this.removeAll(); }
  private void makeDBConnections() {
    if(terminate) return;
    if(!ChannelDisplay.isNoDB()) {
      try {
        boolean doit=false;
        dbconnedge = DBConnectionThread.getThread("edge");
        if(dbconnedge == null) doit=true;
        else if(dbconnedge.getConnection() == null) doit =true;
        else if(dbconnedge.getConnection().isClosed()) doit=true; 
        if(doit) {
          dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"),"user", "edge", false, false, "edge", Util.getOutput());
          if(!dbconnedge.waitForConnection())
            if(!dbconnedge.waitForConnection())
              if(!dbconnedge.waitForConnection())
                if(!dbconnedge.waitForConnection())
                  if(!dbconnedge.waitForConnection())
                    if(!dbconnedge.waitForConnection())
                      Util.prt("Did not get connectino to edge database");

        }

        if(Util.isNEICHost()) {
          doit =false;
          /*if(oracleght == null) doit=true;
          else if(oracleght.getConnection() == null) doit =true;
          else if(oracleght.getConnection().isClosed()) doit=true;
          if(doit) {
            oracleght = DBConnectionThread.getThread("ght");
            if(oracleght == null)
              //oracleght = new DBConnectionThread((Util.getProperty("DBServer").indexOf("127.0.0.1") >=0?
              //  "127.0.0.1":"igskcicgasordb2"), // was gldintdb and TEAM2 port 1521
              //        "PROD1","adm_sel","ham11*ret", false, false, "ght", "oracle");
              oracleght = new DBConnectionThread((Util.getProperty("DBServer").contains("127.0.0.1")?
                "127.0.0.1":"igskcicgvmdbint/5432"), // was gldintdb and TEAM2 port 1521
                      "ghsct1","adm_owner","ham23*ret", false, false, "ght", "postgres", Util.getOutput());
            if(!oracleght.waitForConnection())
              if(!oracleght.waitForConnection()) Util.prt("Did not make prompt postgres connection"+oracleght);
          }*/
        }
        if(!dbconnedge.waitForConnection())
          if(!dbconnedge.waitForConnection()) 
            Util.prt("DID not make prompt database connection "+dbconnedge);
        dbconnstatus = new DBConnectionThread(Util.getProperty("StatusDBServer"),"user", "status", false, false, "status", Util.getOutput());
        if(!dbconnstatus.waitForConnection())
          if(!dbconnstatus.waitForConnection())
            if(!dbconnstatus.waitForConnection())
              Util.prt("Did not make prompt status database connection "+dbconnstatus);
         UC.setConnection(dbconnedge.getConnection());

      }
      catch(InstantiationException e) {
        Util.prt("Could not open a status DB connection ro e="+e.getMessage());
        dbconnedge=null;

      }
      catch(SQLException e) {
        Util.prta("SQLException trying to open the SQL connections e="+e.getMessage());
        e.printStackTrace();

      }
    }
  }
  static final int minWidth=1020;
  Dimension full= new Dimension(1020,750);
  Dimension small= new Dimension(205,150);
  public final void setFull() {
    setPreferredSize(full);maxcols=4;}
  public void setSmall() {setPreferredSize(small);maxcols=1;}
  /** Creates a new instance of ConnectionJTable 
   * @param cl The column label
   * @param ncolumns The number of columns to use in the display
   * @param colwid The width of the column in pixels
   * 
   */
  public ChannelJTable(String cl, int ncolumns, int colwid) {
    //java.awt.EventQueue.invokeLater(new Runnable() {
    //  public void run() {
        popup = new PopupForm();
   //   }
      
    //});
    columnLabel=cl;
    cells =new ArrayList(1);       // This will be discarded when data is read
    columnWidth=colwid;
    ncols=ncolumns;
    maxcols=ncols;
    ArrayList v = new ArrayList(maxcols);
    for(int i=0; i<maxcols; i++) v.add(new ColoredString(new StringBuffer(40),UC.white));
    cells.add(v);
    maxCellsUsed=cells.size();
    setPreferredSize(new java.awt.Dimension(1000, 790));
    tabColumns = new TableColumn[ncolumns];

    if( (dbconnedge == null || dbconnstatus == null) && !ChannelDisplay.isNoDB() ) makeDBConnections();
    table = new JTable(
    // The table model describes how we get data, column names etc.
      new AbstractTableModel() {
        @Override
        public int getRowCount(){ return cellsUsed;}
        @Override
        public int getColumnCount() {return ncols;}
        @Override
        public Object getValueAt(int row, int column) {
          try {
            return ((ArrayList) cells.get(row)).get(column);
          }
          catch(ArrayIndexOutOfBoundsException e) {
            Util.prt("GetValue at "+row+","+column+" is out of bounds cells.size="+cells.size());
            if(cells.size() > row) Util.prt("size of column="+((ArrayList) cells.get(row)).size());
            //Util.sleep(300);
          }          
          catch(IndexOutOfBoundsException e) {
            Util.prt("GetValue at "+row+","+column+" is out of bounds cells.size="+cells.size());
            if(cells.size() > row) Util.prt("size of column="+((ArrayList) cells.get(row)).size());
            //Util.sleep(300);
          }
          return null;
        }
      @Override
        public Class getColumnClass(int col) {return ((ArrayList) cells.get(0)).get(col).getClass();}
      @Override
        public String getColumnName(int i) {return columnLabel;}

     })           // End of art list on newJtable();
     { 
       // override getCellEditor so the right one is use
      @Override
       public TableCellEditor getCellEditor(int row, int col) {
         return null;
       }
     };

     // Populate the table with the current DB data
     //refreshTable();
     //for(int i=0; i<cells.size(); i++) printRow((ArrayList) cells.get(i));
      table.setDefaultRenderer(ColoredString.class, new ChannelRenderer()); 
     // For each column, set the preferred width (possibly other behaviors)
     for (int i=0; i<maxcols; i++) {
       TableColumn c = table.getColumnModel().getColumn(i);
       c.setPreferredWidth(columnWidth);
       //Util.prt("col="+i+" name="+table.getColumnName(i)+" class="+table.getColumnClass(i)+
       // " rend="+table.getDefaultRenderer(table.getColumnClass(i)));
     }
     
    setViewportView(table);
    //table.setFont(new Font("Courier",Font.PLAIN,12));
    //table.getTableHeader().setFont(new Font("Courier", Font.PLAIN, 12));
    //Font f = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    table.setFont(monofont);
    table.getTableHeader().setFont(monofont);

    //table.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
    //table.getTableHeader().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    Util.prt(monofont.toString()+" nm="+monofont.getFontName()+" "+monofont.getName()+" fam="+monofont.getFamily());

    setFull();
    //setPreferredSize(new Dimension (1020, 750));
    table.setRowSelectionAllowed(true);             // Only row selections allowed for now
    table.setPreferredSize(new Dimension(1020, 200));
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e)
      {
        //Ignore extra messages.
        if (e.getValueIsAdjusting()) return;
        
        ListSelectionModel lsm = (ListSelectionModel)e.getSource();
        if (!lsm.isSelectionEmpty())
        {
          selectedRow = lsm.getMinSelectionIndex();
          //doNewSelection();
        }
      }
    });
    //table.setColumnSelectionAllowed(true);
    table.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            //Ignore extra messages.
            if (e.getValueIsAdjusting()) return;

            ListSelectionModel lsm = (ListSelectionModel)e.getSource();
            if (!lsm.isSelectionEmpty())  {
                selectedCol = lsm.getMinSelectionIndex();
                doNewSelection();
            }
        }
    });
    
    for(int i=0; i<ncols; i++) tabColumns[i]=table.getColumnModel().getColumn(i);
}     //  End of ChannelJTable Constructor
  ArrayList<SNWGroup> snwGroupList;
  ArrayList<SNWRule> snwRuleList;
  ArrayList<CommLink> commlinkList;
  ArrayList<Cpu> cpuList;
  private void doNewSelection() {
    try {
      if(terminate) return;
      if(selectedRow != lastSelectedRow || selectedCol != lastSelectedCol) {
        if(snwGroupList == null) {
          try {
            snwGroupList = new ArrayList<>(20);
            if(dbconnedge == null && !ChannelDisplay.isNoDB()) makeDBConnections();
            if(dbconnedge == null) {Util.prt("No dbconnection to edge!!!");return;}
            try (Statement stmt = dbconnedge.getNewStatement(false); 
                    ResultSet rss = stmt.executeQuery("SELECT * FROM edge.snwgroup order by snwgroup")) {
              while(rss.next()) {
                snwGroupList.add(new SNWGroup(rss));
              }
            }
          }
          catch(SQLException e) {
            Util.prta("Got an SQL excepion building snwgroup list"+e.getMessage());
            makeDBConnections();
          }
          Collections.sort(snwGroupList);
        }
        if(snwRuleList == null) {
          try {
            snwRuleList = new ArrayList<>(20);
            try (Statement stmt = dbconnedge.getNewStatement(false); ResultSet rss = stmt.executeQuery("SELECT * FROM edge.snwrule order by snwrule")) {
              while(rss.next()) {
                snwRuleList.add(new SNWRule(rss));
              }
            }
          }
          catch(SQLException e) {
            Util.prta("Got an SQL excepion building snwrule list"+e.getMessage());
            makeDBConnections();
          }
        }
        if(commlinkList == null) {
          try {
            commlinkList = new ArrayList<>(20);
            try (Statement stmt = dbconnedge.getNewStatement(false); ResultSet rss = stmt.executeQuery("SELECT * FROM anss.commlink")) {
              while(rss.next()) {
                commlinkList.add(new CommLink(rss));
              }
            }
          }
          catch(SQLException e) {
            Util.prta("Got an SQL excepion building commlink list"+e.getMessage());
            makeDBConnections();
          }
        }
        if(cpuList == null) {
          try {
            cpuList = new ArrayList<>(20);
            try (Statement stmt = dbconnedge.getNewStatement(false); 
                    ResultSet rss = stmt.executeQuery("SELECT * FROM anss.cpu")) {
              while(rss.next()) {
                cpuList.add(new Cpu(rss));
              }
            }     
          }
          catch(SQLException e) {
            Util.prta("Got an SQL excepion building cpu list"+e.getMessage());
            makeDBConnections();
          }
        }
        UC.setConnection(dbconnedge.getConnection());      // keep UC connection fresh
        lastSelectedRow = selectedRow; lastSelectedCol = selectedCol;
        ColoredString cs = (ColoredString) ((ArrayList) cells.get(selectedRow)).get(selectedCol);
        Util.prta("row,col="+selectedRow+" "+selectedCol+" "+cs.getStatusInfo());
        String s = cs.getText().toString();
        if(s.startsWith("+")) s=s.substring(1);
        if(s.indexOf("UTC") > 12) {
          Util.prta("Play /tones/chord3.wav");
           URL url = getClass().getResource("/tones/chord3.wav");
           SimpleAudioPlayer tmp = new SimpleAudioPlayer(url);
           return;
        }
        
        // Is this a group or a channel, these heuristics could fail if the groups between 10-13 characters with no spaces in 8,9
        if(s.length() < 13) s = (s + "               ").substring(0,13);
        if(s.substring(0,13).contains("-") || !s.substring(8,9).equals(" ") || s.substring(9,10).equals(" ") ||
                s.substring(0,6).equalsIgnoreCase("IMPORT") || s.substring(0,8).equalsIgnoreCase("SeedLink") ||
                s.substring(0,3).equals("NOC")) {
          String grp = s.substring(0,22).trim();
          Util.prta("Doing a group! ="+grp);
          if(grp.contains("k/M is KBytes")) ChannelDisplay.toggleForceBad();
          for (SNWGroup snwGroupList1 : snwGroupList) {
            if (snwGroupList1.getSNWGroup().contains(grp)) {
              try {
                if(oracleght != null) {
                  oracleght.reopen();
                  try (Statement stmt = oracleght.getNewStatement(false)) {
                    if (oracleght == null) {
                      popup.setText(snwGroupList1.getDocumentation());
                    } else {
                      popup.setText(SNWGroupPanel.doTags(snwGroupList1.getDocumentation(), stmt));
                    }
                    popup.setTitle("SNWGroup Documentation");
                  }
                }
                else {
                  popup.setText(snwGroupList1.getDocumentation());    
                }
              }catch(SQLException e) {
                Util.prt("Got SQL exception trying to do popup e="+e);
              }
              //JOptionPane.showMessageDialog(null, snwGroupList.get(i).getDocumentation(),"SNWGroup Documentation",JOptionPane.PLAIN_MESSAGE);
            }
          }
          return;
        }
        if(s.length() < 14) return;       // must be in the white or comments
        String seedname=s.substring(9,11)+s.substring(0,8)+s.substring(11,13);
        doChannel(seedname);

      }
    }
    catch(RuntimeException e) {
      e.printStackTrace();
    }
  }

  private void doChannel(String seedname) {
    StringBuilder sb = new StringBuilder(200);
    //Util.prta("seedname="+seedname+" s="+s);
    ResultSet rs = null;
    Statement stmtstatus = null;
    Statement stmtedge=null;
    Util.prta("doChannel("+seedname+")");
    if(ChannelDisplay.isNoDB()) return;
      Util.prta("Hit metadata server");
      try {
        Socket ss = new Socket("137.227.224.97",2052);
        ss.getOutputStream().write(("-c r -s "+seedname.replaceAll(" ","-")+"\n").getBytes());
        byte [] buf = new byte[2000];
        for(;;) {
          int l = ss.getInputStream().read(buf);
          if(l < 0) break;
          String s = new String(buf, 0, l);
          sb.append(s);
          if(s.contains("* <EOR>")) {
            int i = sb.indexOf("* NETWORK");
            int j = sb.indexOf("* EFF");
            if(i > 0 && j > 0) sb.replace(i, j, "");
            i = sb.indexOf("* ENDD");
            j = sb.indexOf("* DESC");
            if(i > 0 && j > 0) sb.replace(i, j, "");

            i = sb.indexOf("* SENS-SEED");
            if(i > 0) sb.replace(i, sb.length(), "");
            break;
          }
        }
      }
      catch(IOException e) {
        Util.SocketIOErrorPrint(e,"Reading metat data from ChannelDisplay");
      }
      popup.setTitle("Station Status");
      popup.setText(sb.toString());
      try {
      dbconnstatus.reopen();
      stmtstatus = dbconnstatus.getNewStatement(false);
      stmtedge = dbconnedge.getNewStatement(false);

      rs = stmtstatus.executeQuery("SELECT MAX(id) FROM status.dasstatus");
    }
    catch(SQLException e) {
      Util.prt("Got database Except getting max(id) try reconnect");
      try{Thread.sleep(5000);} catch(InterruptedException e2) {}
      makeDBConnections();
      try {
        stmtstatus = dbconnstatus.getNewStatement(false);
        rs = stmtstatus.executeQuery("SELECT MAX(id) FROM status.dasstatus");
      }
      catch(SQLException e2) {Util.prta("Reconnect apparently failed d="+e2.getMessage());
      sb.append("**** database connection is not working ").append(dbconnstatus.toString()).append("\n");}
    }
    int maxid;
    if(rs != null && stmtstatus != null) {
      try {
        rs.next();
        maxid=rs.getInt(1);
        rs.close();
        Util.prta("Maxid="+maxid+" seedname="+seedname);
        java.util.Date beg = new java.util.Date();
        beg.setTime(System.currentTimeMillis() - 90*86400000L);
        java.util.Date end = new java.util.Date();
        end.setTime(System.currentTimeMillis());
        HoldingsList holdings = new HoldingsList(dbconnstatus.getConnection(), seedname, "CW",beg, end);
        
        sb.append("      ------- Start  ----   --------- End ------ Total spans (< 10 minutes omitted)=").append(holdings.getSpansListSize()).append("\n");
        double gap;
        for( int i=0; i<holdings.getSpansListSize(); i++) {
          if(holdings.get(i).end.getTime() - holdings.get(i).start.getTime() > 600000) {
            if(i < holdings.getSpansListSize()-1) gap = ( holdings.get(i+1).start.getTime() - holdings.get(i).end.getTime() )/86400000.;
            else gap=0.;
          
            sb.append("Span: ").append(Util.ascdatetime(holdings.get(i).start.getTime()).substring(0,19)).append(" - ").
               append(Util.ascdatetime(holdings.get(i).end.getTime()).substring(0,19)).append(gap > 0.?" "+Util.df23(gap)+" days gap":"").append("\n");
          }
        }
        sb.append("    ------------------ Status ----------------\n");
        rs = stmtstatus.executeQuery("SELECT * FROM status.dasstatus WHERE station='"+
                seedname.substring(0,7)+"' AND ID>"+(maxid-10000));
        Util.prta("Maxid select complete");
        while(rs.next()) {
          if(rs.isLast()) {
            Util.prta("Maxid found last");
            String type="Q680";
            if(rs.getByte("dastype") == 33) type="Q330";
            else if(rs.getByte("dastype") == 1) type = "Q730";

            sb.append(seedname).append(" DAS Status at ").append(rs.getTimestamp("time").toString().substring(0, 16)).
                    append("   clockQual=").append(rs.getByte("clockquality")).append(" type=").append(type).append("\n");
            sb.append("MassPos(1-3): ").append(rs.getByte("masspos1")).append(" ").
                    append(rs.getByte("masspos2")).append(" ").append(rs.getByte("masspos3")).
                    append(" (ch 4-6): ").append(rs.getByte("masspos4")).append(" ").
                    append(rs.getByte("masspos5")).append(" ").append(rs.getByte("masspos6")).append("\n");
            sb.append("temp=").append(rs.getByte("temp1")).append(" temp2=").
                    append(rs.getByte("temp2")).append(" volt1=").append(rs.getShort("volt1") / 10.).
                    append(" volt2=").append(rs.getShort("volt2") / 10.).append("\n");
            sb.append("Time base=").append(rs.getShort("timebase")).append(" pllint=").
                    append(rs.getShort("pllint")).append(" gpbits=").
                    append(Util.toHex(rs.getByte("gpsbits"))).append(" pllbits=").
                    append(Util.toHex(rs.getByte("pllbits"))).append("\n");
            //Util.prta(sb.toString());
          }
        }
        rs.close();
      }
      catch(SQLException e){
        Util.prta("Could not get dasstatus for seedname="+seedname+" e="+e);
      }
      if(sb.length() < 10) sb.append(seedname).append("  has no DAS status records\n");
      // Now output stuff from TCPStation if it is present
      Util.prta("Select TCPStation");
      if(stmtedge != null) {
        try {
          rs = stmtedge.executeQuery("SELECT * FROM anss.tcpstation WHERE tcpstation='"+seedname.substring(2,7).trim()+"'");
          if(rs.next()) {
            TCPStation t = new TCPStation(rs);
            sb.append("\n    TCPSTATION  IP=").append(t.getIP()).append(" mask=").append(rs.getString("ipmask"));
            if(t.getCommlinkOverrideID() > 0)  
              for (CommLink commlinkList1 : commlinkList) {
              if (t.getCommlinkOverrideID() == commlinkList1.getID()) {
                sb.append(" BackHaul=").append(commlinkList1.getCommLink()).append(" ");
              }
            }
            if(t.getCommLinkID() > 0)  
              for (CommLink commlinkList1 : commlinkList) {
              if (t.getCommLinkID() == commlinkList1.getID()) {
                sb.append(" LastMile=").append(commlinkList1.getCommLink()).append(" ");
              }
            }
            sb.append("\n");

            // Handle Q330 config
            if(t.getNQ330s() > 0) {
              sb.append("  Q330 config - use tunnel=").append(rs.getString("q330tunnel")).
                      append("   Single IP(POC Site)=").append(t.getAllowPOC()).append("\n");
              sb.append(t.getQ330toString());
            }
            rs.close();

            try {
              try (ResultSet rss = stmtstatus.executeQuery("SELECT MAX(id) FROM status.ping")) {
                rss.next();
                maxid=rss.getInt(1);
              }
            //Util.prta("Maxid="+maxid);
              rs = stmtstatus.executeQuery("SELECT * FROM status.ping WHERE station='"+
                      seedname.substring(0,2)+"-"+seedname.substring(2,7)+"' AND ID>"+(maxid-10000));
              int min=1000000000;
              int max=-1000000;
              long avg=0;
              Timestamp start=null;
              Timestamp last=null;
              int outseq=0;
              int seq=0;
              int count=0;
              while(rs.next()) {
                if(start==null) {
                  start=rs.getTimestamp("time");
                  seq=rs.getShort("pingseq")-1;
                }
                int ping = rs.getShort("pingtime");
                if(ping < min) min=ping;
                if(ping > max) max=ping;
                avg += ping;
                count++;
                if(rs.getShort("pingseq") != seq+1) {
                  outseq++;
                  seq=rs.getShort("pingseq");
                }
                else seq++;
                last=rs.getTimestamp("time");
              }
              rs.close();
              if(count > 0) sb.append("#pings=").append(count).append(" min=").append(min).
                      append(" max=").append(max).append(" avg=").append(avg / count).
                      append(" ms. #seqerr=").append(outseq).append(" over last ").
                      append(last != null && start != null?(last.getTime() - start.getTime()) / 1000:"-1").append(" secs. last at ").
                      append(last != null?last.toString().substring(0, 19):"null").append("\n");
              else sb.append("#pings=0  This station does not have any pings.\n");
            }
            catch(SQLException e) {
              Util.prt("Could not get ping status for "+seedname+" e="+e);
            }
          }
          try {
            rs =stmtedge.executeQuery("SELECT * FROM anss.nsnstation WHERE nsnstation='"+seedname.substring(2,7).trim()+"'");
            if(rs.next()) {
              sb.append("\n   NSNSTATION net=").append(rs.getInt("netid")).append(" node=").
                      append(rs.getInt("nodeid")).append(" type=").append(rs.getString("stationtype")).
                      append(" higain=").append(rs.getString("highgain")).append(" seisType=").
                      append(rs.getString("seismometer")).append(" lowgain=").
                      append(rs.getString("lowgain")).append(" DAS=").
                      append(rs.getString("quanterratype")).append("\n    seed network=").
                      append(rs.getString("seednetwork")).append("\n");
            }
            rs.close();
          }
          catch(SQLException e) {
            Util.prt("Could not get nsnstation for seedname="+seedname+" e="+e);
          }
          // Now do the SNW config infor
          Util.prta("Select from snwstation");
          try {
            rs = stmtedge.executeQuery(
                "SELECT * FROM edge.snwstation,edge.protocol,edge.operator WHERE network='"+
                seedname.substring(0,2)+"' AND "+
                " snwstation='"+seedname.substring(2,7).trim()+
                "' AND protocol.id=protocolid AND operator.id=operatorid");
            if(rs.next()) {
              sb.append("\n   SNWSTATION  lat=").append(rs.getDouble("latitude")).
                      append(" long=").append(rs.getDouble("longitude")).append(" elev=").
                      append(rs.getDouble("elevation")).append(" ").append(rs.getString("description")).
                      append(" ID=").append(rs.getInt("id")).append("\n");
              sb.append("        Operator : ").append(rs.getString("operator")).
                      append(" Protocol : ").append(rs.getString("protocol")).append("\n");
              if(rs.getInt("disable") != 0) sb.append("**** Disabled to ").
                      append(rs.getString("disableexpires")).append(" ").
                      append(rs.getString("disableComment")).append("\n");
              if(!rs.getString("analystcomment").equals("")) sb.append("*** Analyst comment : ").
                      append(rs.getString("analystcomment")).append("\n");
              long groupMask = rs.getLong("groupmask");
              sb.append("SNW Groups : ");
              for (SNWGroup snwGroupList1 : snwGroupList) {
                if ((groupMask & (1L << (snwGroupList1.getID() - 1))) != 0) {
                  sb.append(snwGroupList1.getSNWGroup()).append(", ");
                }
              }
              sb.append("\n");
              int ruleid=rs.getInt("snwruleid");
              for (SNWRule snwRuleList1 : snwRuleList) {
                if (ruleid == snwRuleList1.getID()) {
                  sb.append("SNWRule : ").append(snwRuleList1.getSNWRule()).append("  ");
                }
              }
              sb.append("  Dynamic Groups : Proc=").append(rs.getString("process")).
                      append(" cpu=").append(rs.getString("cpu")).append(" remIp=").
                      append(rs.getString("remoteip")).append(" remproc=").
                      append(rs.getString("remoteprocess")).append(" sort=").
                      append(rs.getString("sort")).append("\n");
            }
            rs.close();
          }
          catch(SQLException e) {
            Util.prt("Could not get snwstation for seedname="+seedname+" e="+e);
          }
        }
        catch(SQLException e) {
          Util.prta("SQLException getting dassstatus="+e.getMessage());
          makeDBConnections();
        }
      }
    } // end if no null on stmt or rs
    try{
      // Add on data from the last epoch from the metadata server

      Util.prta("Do popup");
      popup.setTitle("Station Status");
      popup.setText(sb.toString());
      if(stmtstatus != null)
        if(!stmtstatus.isClosed()) stmtstatus.close();
      //JOptionPane.showMessageDialog(null, sb.toString(),"Station Status",JOptionPane.PLAIN_MESSAGE);
    }
    catch(SQLException e) {
      Util.prta("SQLException getting dassstatus="+e.getMessage());
      makeDBConnections();
    }
  }
  /** refreshTable - initializes cells and editors based on current DB values.  
   *it is destructive and reflect the current data base each time it is called
   * @param msgs The array of messages to paint
   * @param colors The array with the colors for each message
   * @param cs The status info which will be decoded 
   * @param size the size used in the arrays
   */
   synchronized public void refreshTable(StringBuffer [] msgs, Color [] colors,StatusInfo [] cs, int size) {
    if(terminate) return;
    // celss and editors are completely rebuilt each time
    int off=(size+2)/Math.max(ncols,1);
    cellsUsed=off;
    //Util.prta("size="+getRootPane().getContentPane().getSize());
    int xsize = (int) getRootPane().getContentPane().getSize().getWidth();
    int ysize = (int) getRootPane().getContentPane().getSize().getHeight();

    int nc=(xsize-28)/columnWidth;
    nc = Math.max(1, nc);
    if(nc > maxcols) nc = maxcols;
    if(nc != ncols) {
      Util.prta("refresh table change columns="+nc+" was "+ncols+" xs="+xsize+" colW="+columnWidth+" ys="+ysize);
      if(nc > ncols) for(int i=ncols; i<nc; i++) table.addColumn(tabColumns[i]);
      else if( nc < ncols) for(int i=ncols-1; i>=nc; i--) table.removeColumn(tabColumns[i]);
      ncols=nc;
    }
    setPreferredSize(new Dimension(nc*columnWidth+30, ysize-75));
    setSize(new Dimension(nc*columnWidth+30, ysize - 75)); // NEW
    table.setPreferredSize(new Dimension(ncols*(columnWidth+5), 17*cellsUsed));
    
    //Util.prta("refress len="+msgs.length+" off="+off+" cell cap="+cells.capacity()+" Size="+cells.size());
    if(cells.size() < off) {
      Util.prta("expand cell size to "+off+" from "+maxCellsUsed+" capacity="+cells.size());
      for(int i=maxCellsUsed; i<off; i++) {
        ArrayList v = new ArrayList(maxcols);
        for(int j=0; j<maxcols; j++) {
          v.add( new ColoredString(new StringBuffer(""), UC.white));
          //e.add( new DefaultCellEditor(ta));
        }
        cells.add(v);
        maxCellsUsed=cells.size();
      } 
    }
      
    //Util.prta("draw off="+off+" ncols="+ncols);
    for(int i=0; i<off; i++) {
      for(int j=0; j<ncols; j++) {
        ColoredString ta = (ColoredString) ((ArrayList) cells.get(i)).get(j);
        if( (i+j*off) <size) {
          ta.setText(msgs[i+j*off]);
          ta.setColor(colors[i+j*off]);
          ta.setStatusInfo(cs[i+j*off]);
        }
        else {
          ta.setText("");
          ta.setColor(UC.white);
        }
      }
    }
    
    //NEW:
    if(xsize != full.getWidth() || ysize != full.getHeight()) {
      full = new Dimension(xsize, (int) (ysize));
      super.setPreferredSize(full);
      ChannelDisplay.theChannelDisplay.getThePanel().setPreferredSize(full);
      ChannelDisplay.theChannelDisplay.getRootPane().setPreferredSize(full);
      ChannelDisplay.theChannelDisplay.doImmediateUpdate();
      // cells and editors are completely rebuilt each time
      //Util.prta("size="+getRootPane().getContentPane().getSize());


      setPreferredSize(new Dimension(xsize-20, (int)(ysize-75)));
      //setPreferredSize(new Dimension(xsize-20, ysize-255));
      //table.setPreferredSize(new Dimension(xsize-20,ysize-160));
      //table.setPreferredSize(new Dimension(xsize-20,Math.max(size*17, ysize-160)));
      //NEW:*/
    }

    // This is the magical incantation to get the table to repaint with changes in all rows
    table.tableChanged(new TableModelEvent(table.getModel()));
    ChannelDisplay.theChannelDisplay.invalidate();//NEW
  }
 
  // Getter methods to allow access to table
  public ListSelectionModel getTableSelectionModel() { return table.getSelectionModel();}
  public int getSelectedRow() {return table.getSelectedRow();}
  public JTable getJTable() {return table;}
  public int getRowCount() {return cells.size();}
  public int getNcols() {return ncols;}
  public int getMaxcols() {return maxcols;}
  //public void setFont(Font f) {table.setFont(f);}
 
  // Variables declaration - do not modify
  private javax.swing.JTable table;
  // End of variables declaration
   
  public final class ColoredString {
    private Color color;
    private StringBuffer text;
    private StatusInfo cs;
    public ColoredString(StringBuffer s, Color c) {color=c; text=s; setFont(monofont);}
    public Color getColor() {return color;}
    public StringBuffer getText() {return text;}
    public StatusInfo getStatusInfo() {return cs;}
    @Override
    public String toString() {return text.toString()+" "+color;}
    public void setText(StringBuffer s) {text=s;}
    public void setStatusInfo(StatusInfo c) { cs=c;}
    
    public void setText(String s) {
      if(text.length() > 0) text=text.delete(0,text.length()); 
      text.append(s);
    }
    public void setColor(Color c) {color=c;}
  }
  
  
  public final static class ChannelRenderer extends DefaultTableCellRenderer {
    public ChannelRenderer() { super();}
    @Override
    public void setValue(Object value) {
      if(value == null) {
         Util.prta("Got a null to render");
        return;
      }
      setFont(monofont);
      setText( ((ColoredString)value).getText().toString());
      setBackground( ((ColoredString) value).getColor());
    }
  }
  


  // This main displays the form Pane by itself
  public static void main(String args[]) {
  StatusSocketReader channels;
  int nlab=80;
  StringBuffer [] msgs = new StringBuffer[nlab];
  ChannelStatus [] cs = new ChannelStatus[nlab];
  Color [] colors = new Color[nlab];
  int ncols = 4;
  for(int i=0; i<nlab; i++) {
    msgs[i] = new StringBuffer("String input "+i);
    cs[i] = new ChannelStatus("IU"+i+"            ");
    switch (i%ncols) {
      case 0:
        colors[i] = UC.white;
        break;
      case 1:
        colors[i]=UC.yellow;
        break;
      case 2:
        colors[i]=UC.red;
        break;
      case 3:
        colors[i]=UC.white;
        break;
      default:
        break;
    }
  }
    //		User u = new User("test");
  //User u = new gov.usgs.anss.util.User();
    
    /*channels = new StatusSocketReader(ChannelStatus.class, "vdl1.cr.usgs.gov", 
      AnssPorts.CHANNEL_SERVER_PORT);*/

      // we need to attach a ConnectionJTable to a panel for debuggin display, 
      // create the JPanel with layout dimensions, add the JTable and display
      JPanel p = new JPanel(new FlowLayout());
      p.setPreferredSize(new Dimension(UC.XSIZE, UC.YSIZE));
     ChannelJTable t = new ChannelJTable("Column Label", ncols,250);
      p.add(t);
      Show.inFrame(p, 1080,800);
      Util.prt("Column 0 class="+t.getJTable().getColumnClass(0)+" renderer="+
        t.getJTable().getDefaultRenderer(t.getJTable().getColumnClass(0)));
        t.refreshTable( msgs, colors, cs, nlab);
     /* if(channels != null)
      {
        //int nmsgs=channels.length();
        //msgs = channels.getObjects();
      } else return;*/

  }  
}
