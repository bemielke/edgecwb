/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;

import gov.usgs.anss.gui.UC;
import gov.usgs.anss.util.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import javax.swing.*;

/**
 * This is a modal dialog box that changes the password of the user on a JDBC
 * connection.
 */
public class ChangePasswordDialog extends JDialog
{
  /* Passwords may not be shorter than this. */
  private static final int MINIMUM_LENGTH = 5;

  /**
   * This constant indicates that the dialog should offer to set a password
   * that is currently blank.
   */
  public static final int SET_PASSWORD = 0;
  /**
   * This constant indicates that the dialog should offer to change a password
   * that is currently set.
   */
  public static final int CHANGE_PASSWORD = 1;

  private Connection connection;
  private JPasswordField passwordField, confirmField;
  private JLabel instructionLabel, errorLabel;

  /**
   * Create a new ChangePasswordDialog.
   *
   * @param owner The Frame that owns this dialog
   * @param connection The connection over which the password change is to take
   *                   place.
   * @param username The name of the user whose password to change. This is for
   *                 display only; the connection parameter actually determines
   *                 whose password will be changed.
   * @param reason Either SET_PASSWORD, to set a password that is currently
   *               blank, or CHANGE_PASSWORD, to change a password that is
   *               currently set. This parameter does not have an effect on the
   *               functioning of the dialog, only its appearance.
   */
  public ChangePasswordDialog(Frame owner, Connection connection,
          String username, int reason)
  {
    super(owner, true);

    JPanel panel;
    GridBagLayout layout;
    JLabel label;
    JButton button;
    Box box;

    this.connection = connection;

    setDefaultCloseOperation(HIDE_ON_CLOSE);

    panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    getContentPane().add(panel);

    box = Box.createHorizontalBox();
    instructionLabel = new JLabel();
    box.add(instructionLabel);
    box.add(Box.createHorizontalGlue());
    panel.add(box);

    panel.add(Box.createVerticalStrut(10));

    box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    label = new JLabel("Password: ", JLabel.TRAILING);
    box.add(label);
    passwordField = new JPasswordField(12);
    passwordField.setMaximumSize(passwordField.getPreferredSize());
    passwordField.requestFocusInWindow();
    box.add(passwordField);
    panel.add(box);

    panel.add(Box.createVerticalStrut(4));

    box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    label = new JLabel("Confirm password: ", JLabel.TRAILING);
    box.add(label);
    confirmField = new JPasswordField(12);
    confirmField.setMaximumSize(confirmField.getPreferredSize());
    box.add(confirmField);
    panel.add(box);

    panel.add(Box.createVerticalStrut(5));
    box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    errorLabel = new JLabel();
    box.add(errorLabel);
    box.add(Box.createHorizontalGlue());
    panel.add(box);
    panel.add(Box.createVerticalStrut(5));

    box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    button = new JButton("Cancel");
    button.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent event)
      {
        setVisible(false);
      }
    });
    box.add(button);

    box.add(Box.createHorizontalStrut(5));

    button = new JButton("Change password");
    getRootPane().setDefaultButton(button);
    button.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent event)
      {
        tryChangePassword();
      }
    });
    box.add(button);
    panel.add(box);

    if (reason == SET_PASSWORD) {
      setTitle("Set password for " + username);
      instructionLabel.setText("Your password is blank. You can set it now.");
    } else if (reason == CHANGE_PASSWORD) {
      setTitle("Change password for " + username);
      instructionLabel.setText("Change password for " + username);
    }

    setResizable(false);
    pack();
  }

  /**
   * Create a new ChangePasswordDialog.
   *
   * @param owner The Frame that owns this dialog
   * @param connection The connection over which the password change is to take
   *                   place.
   * @param username The name of the user whose password to change. This is for
   *                 display only; the connection parameter actually determines
   *                 whose password will be changed.
   */
  public ChangePasswordDialog(Frame owner, Connection connection,
          String username)
  {
    this(owner, connection, username, CHANGE_PASSWORD);
  }

  /**
   * Check that the two password fields match and that the password is
   * acceptable, and change the password if it is. If the password is changed
   * successfully then the dialog will become invisible.
   */
  private void tryChangePassword()
  {
    String password, confirm, error;

    password = new String(passwordField.getPassword());
    confirm = new String(confirmField.getPassword());
    if (!password.equals(confirm)) {
      confirmField.setText("");
      confirmField.requestFocusInWindow();
      setErrorLabel("The passwords don't match. Try again.");
    } else {
      error = checkPassword(password);
      if (error != null) {
        setErrorLabel(error);
      } else {
        try {
          changePassword(password);
          setVisible(false);
        } catch (SQLException e) {
          setErrorLabel("Error changing password: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Change the password.
   *
   * @param password The password
   */
  private void changePassword(String password) throws SQLException
  {
    Statement statement;
    String u;

    statement = UC.getConnection().createStatement();
    u = "SET PASSWORD = PASSWORD(" + Util.sqlEscape(password) + ")";
    statement.executeUpdate(u);
    statement.close();
  }

  /**
   * Check that the password is acceptable. It must be at least MINIMUM_LENGTH
   * characters long.
   *
   * @param password The password to check
   * @return null if the password is acceptable, or an error message explaining
   *         why it is not
   */
  private String checkPassword(String password)
  {
    if (password.length() < MINIMUM_LENGTH)
      return "Password is too short (" + MINIMUM_LENGTH + " characters minimum)";

    return null;
  }

  /**
   * Set the error label and repack the container to fit.
   *
   * @param message The message to display in the error label.
   */
  private void setErrorLabel(String message)
  {
    errorLabel.setText(message);
    pack();
  }
}
