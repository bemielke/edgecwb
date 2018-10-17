/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.util;
import java.sql.*;
//import java.awt.Color;
//import java.util.Vector;
import javax.swing.*;

import jcexceptions.*;
//import java.awt.Point;
//import java.util.StringTokenizer;
//import java.lang.reflect.*;
import java.util.ArrayList;

/*
 * SQLReportParser.java
 *
 * Created on November 4, 2004, 4:57 PM
 */

/**
 *
 * @author  davidketchum
 */
public class SQLReportParser {
  JPanel panel;     // Panel to manage for user input data
  String sql;
  String vs[];
  ArrayList<Variable> vars;
  /** Creates a new instance of SQLReportParser 
   All variables must be define as $name|TAG#
   * @param s The string
   * @param pl The panel to use for display
   */
  public SQLReportParser(String s,JPanel pl) {
    sql=s.replace('\n',' ');
    panel=pl;
    Util.prt("s="+s+"\nsql="+sql);
    vars= new ArrayList<>(10);
    vs = sql.split("\\$");
    

    // For each thing starting with a $
    for(int i=1; i<vs.length; i++) {
      String [] parms = vs[i].split("[{}]");
      //Util.prt("parms.length="+parms.length);
      //for(int j=0;j<parms.length; j++) Util.prt("parms["+j+"]="+parms[j]);
      Variable v = new Variable(parms[0], parms[1]);
      Util.prt("SQLReport : Add variable="+v.toString());
      boolean found=false;
      for (Variable var : vars) {
        if (var.getName().equalsIgnoreCase(parms[0])) {
          found=true;
        }
      }
      if(!found) vars.add(v);
    }
    
    //		User u = new User("test");
    int ncols=2;
    int nrows=vars.size();
    if(vars.size() > 4) {nrows=(vars.size()+1)/2;ncols=4;}
    
    UC.Look(panel);
    panel.setLayout(new java.awt.GridLayout(nrows,ncols));
    panel.removeAll();

    panel.setBorder(new javax.swing.border.EtchedBorder());
    panel.setMinimumSize(new java.awt.Dimension(UC.XSIZE-50, nrows*22));
    panel.setMaximumSize(new java.awt.Dimension(UC.XSIZE-50, nrows*22));
    panel.setPreferredSize(new java.awt.Dimension(UC.XSIZE-50, nrows*22));
    
    for (Variable var : vars) {
      Variable v = (Variable) var;
      panel.add(v.getLabel());
      panel.add(v.getField());
    }
    if(vars.size() > 0) ((Variable) vars.get(0)).getField().requestFocus();
    panel.validate();
  }
  public javax.swing.JPanel getPanel() {return panel;}
  public String getSql() {
    boolean dbg=false;
    String [] opts = sql.replaceAll("\\n","").split("[\\[\\]]");
    if(dbg) {Util.prt("Opts length="+opts.length);
      for(int i=0; i<opts.length; i++) Util.prt(i+" opt="+opts[i]);
    }
    Util.prt("SQLReport getSQL starting SQL is \n"+sql);
    StringBuilder sb = new StringBuilder(sql.length());
    sb.append(opts[0]).append("\n");       // This should be SELECT ... through first []
    Util.prt("Starting Phrase ="+opts[0]);
    for(int i=1; i<opts.length-1; i++) {
      if(!opts[i].matches("\\s*")) {      // is it not just white space
        Util.prt("SQLReport Option : "+i+" opt="+opts[i]);

        String [] parts = opts[i].split("[\\${}]",4);
        if(parts.length == 4) {
          if(dbg) {Util.prt("parts len="+parts.length); 
          for(int j=0; j<parts.length; j++) Util.prt(j+" parts="+parts[j]);}


          // Look through the vars and see if we have a match in the [] pair
          for (Variable var : vars) {
            Variable v = (Variable) var;
            if(dbg) Util.prt("Var search for "+parts[1] +" = "+v.getName());
            if(parts[1].equalsIgnoreCase(v.getName())) {
              // If user has not entered anything, bail
              if(v.getText().equals("")) {
                Util.prt("Match found text empty skip");
                break;
              }
              else {    // add what proceeds and comes after the variable
                Util.prt("Match found Phrase="+parts[0]+"'"+v.getText()+"'"+parts[3]);
                sb.append(parts[0]);
                if(parts[1].substring(0,1).equals("_")) sb.append(v.getText()).append(" ");
                else sb.append("'").append(v.getText()).append("' ");
                if(parts.length == 4) sb.append(parts[3]).append("\n");
                break;
              }
            }
          } // for each variable that might match
        }   // is this a opt phrase
        else {sb.append(opts[i]).append(" ");}
      }   // If this is not just white space
    }   // for each [] enclosed phrase
    if(opts.length > 1) sb.append(opts[opts.length-1]);  // If not just a single string (no opts)
    Util.prt("SQLReport getSql() return=\n"+sb.toString());
    
    // Now make a pass to elimate all Variable fields
    int start;
    int end;
    while ( (start = sb.indexOf("{")) > 0 ) {
      end = sb.indexOf("}");
      if(end <=0 ) Util.prt("**** error no matching } in "+sb.toString());
      int var = sb.indexOf("$");
      //Util.prt("Replace tag "+var+" to "+end+" start="+start);
      if(var >= start) Util.prt("**** error variable not right"+var+" to "+start);
      String name = sb.substring(var,start);
      name=name.substring(1);
      //Util.prt("variable found="+name);
      String sub="$"+name;
      for (Variable var1 : vars) {
        Variable v = (Variable) var1;
        if(name.equalsIgnoreCase(v.getName())) {
          if(name.contains("'")) sub=v.getText();
          else sub="'"+v.getText()+"'";
        }
      }
      //Util.prt("replace "+var+" to "+end+" "+sb.substring(var,end+1)+" with "+sub);
      sb.replace(var,end+1,sub);
      //Util.prt("after replace sb="+sb.toString());
    }
    String s = sb.toString();
    for (Variable var : vars) {
      s = s.replaceAll("%" + var.name.trim(), "'" + var.getText().trim() + "'");
    }
    return s;
  }


  class Variable {
    String name;
    String tag;
    String def;
    javax.swing.JLabel label;
    javax.swing.JTextField box;
    
    public Variable(String nm, String tg) {
      name=nm; tag=tg; 
      String [] sp = tg.split("=");
      if(sp.length >=2) {
        def = sp[1].trim();
        tag=sp[0];
      }
      else def="";
      label = new javax.swing.JLabel(); label.setText(tag);
      label.setHorizontalAlignment(SwingConstants.RIGHT);
      box = new javax.swing.JTextField(); box.setColumns(10);//box.setPreferredSize(new java.awt.Dimension(70,18));
      box.setText(def);
    }
    @Override
    public String toString() {return name+"|"+tag;}
    public String getText() {return box.getText();}
    public String getName() {return name;}
    public String getDefault() {return def;}
    public javax.swing.JLabel getLabel() {return label;}
    public javax.swing.JTextField getField() {return box;}
    
  }
  static SQLReportParser p;
   static public void executeQueryActionPerformed(java.awt.event.ActionEvent evt) {
    Util.prt("Sql="+p.getSql());
   }

   public static void main(String args[]) {
    JDBConnectionOld jcjbl;
    Connection C;
    new User("dkt");
      String s = "SELECT * FROM location WHERE \n"+
     "location = $loc{Enter location:} AND\n" +
     "[latitude < $lat{Enter latitude (upper):} AND]\n" +
     "[longitude > $long{Enter longitude(lower):} AND]\n" +
     "[Elevation > $elev{Enter Elevation(Minimum):} AND]\n" +
     "[Name = $name{Enter Name:} AND]\n" +
     "[Address = $address{Enter Address:} AND]\n" +
     "[Test > $test{Enter test (Minimum):} AND]\n" +
     "ID>0";
   
   
    try {
      Util.prt(" *** start="+UC.JDBCDriver()+" "+UC.JDBCDatabase());
      jcjbl = new JDBConnectionOld(UC.JDBCDriver(),
        UC.JDBCDatabase());
      C = JDBConnectionOld.getConnection();
      User u = new User(C,"dck","karen");
      
      UC.setConnection(C);
      JPanel panel = new JPanel();
      JPanel pn2= new JPanel();
      p = new SQLReportParser(s,pn2);
      panel.add(p.getPanel());
      JButton executeQuery = new javax.swing.JButton();
      executeQuery.setText("Execute Query");
      executeQuery.addActionListener(new java.awt.event.ActionListener() {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
          executeQueryActionPerformed(evt);
        }
      });

      panel.add(executeQuery);
     Show.inFrame(panel, UC.XSIZE, UC.YSIZE);
     } catch (SQLException e) {
      System.err.println("SQLException on getting $NAME");
    } catch (JCJBLBadPassword E) {
      Util.prt("bad password");
    }
     
   }
  
}
