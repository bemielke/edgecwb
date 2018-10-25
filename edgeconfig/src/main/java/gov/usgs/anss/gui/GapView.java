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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;

/**
 * This component is a graphical display of a GapsList.
 */
public final class GapView extends JComponent {

  private final Color DEFAULT_BACKGROUND = Color.WHITE;
  private final Color DEFAULT_FOREGROUND = Color.BLUE;

  private GapsList gaps;

  /**
   * Create a new GapView.
   */
  public GapView() {
    super();

    setOpaque(true);
    setBackground(DEFAULT_BACKGROUND);
    setForeground(DEFAULT_FOREGROUND);
  }

  /**
   * Set the list of gaps and the time interval that this component represents. Set to null for a
   * blank display.
   *
   * @param gaps The list of gaps to use
   */
  public void setGaps(GapsList gaps) {
    this.gaps = gaps;
    repaint();
  }

  /**
   * Paint this component. This method should not be called directly; it is handled by Swing's
   * drawing code.
   *
   * @param graphics
   */
  @Override
  public void paintComponent(Graphics graphics) {
    Graphics2D g;
    Dimension d;
    double duration;
    SpansList.Span gap;
    int i;

    g = (Graphics2D) graphics;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
    d = getSize();

    if (gaps == null || gaps.start == null || gaps.end == null) {
      g.setPaint(getBackground());
      g.fill(new Rectangle2D.Double(0, 0, d.width, d.height));
      return;
    } else {
      g.setPaint(getForeground());
      g.fill(new Rectangle2D.Double(0, 0, d.width, d.height));
    }

    g.setPaint(getBackground());

    duration = gaps.end.getTime() - gaps.start.getTime();

    for (i = 0; i < gaps.size(); i++) {
      gap = gaps.get(i);
      // make a visible white line if there is a gap, but its so small, cant see 
      if ((gap.end.getTime() - gap.start.getTime()) / duration * d.width < 1) {
        g.fill(new Rectangle2D.Double(
                (gap.start.getTime() - gaps.start.getTime()) / duration * d.width,
                0.0, 1, d.height));
      } else {
        g.fill(new Rectangle2D.Double(
                (gap.start.getTime() - gaps.start.getTime()) / duration * d.width,
                0.0,
                (gap.end.getTime() - gap.start.getTime()) / duration * d.width,
                d.height));
      }
    }
  }
}
