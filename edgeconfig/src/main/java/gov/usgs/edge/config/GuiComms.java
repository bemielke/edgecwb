/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.edge.config;

/**
 *
 * This class is a convenient location for Gui objects to use to communicate
 * with each other or call methods on each other
 * 
 * 
 * @author bmielke
 */
public class GuiComms {
  private EdgeMomInstancePanel edgeMomInstance;
  private EdgeMomInstanceSetupPanel edgeMomInstanceSetup;
  private EdgeConfig edgeConfig;
  
  public GuiComms(EdgeConfig edgeConfig) {
    this.edgeConfig = edgeConfig;
  }
  
  public void setEdgeMomInstance(EdgeMomInstancePanel edgeMomInstance) {
    this.edgeMomInstance = edgeMomInstance;
  }
  
  public void setEdgeMomInstanceSetup(EdgeMomInstanceSetupPanel edgeMomInstanceSetup) {
    this.edgeMomInstanceSetup = edgeMomInstanceSetup;
  }
  
  public void reloadEdgeMomInstanceSetup(int instanceId) {
    edgeMomInstanceSetup.externalReload(instanceId);
  }
  
  public void setSelectedSubTab(String parentTabName, String subTabTitle) {
    edgeConfig.setSelectedSubTab(parentTabName, subTabTitle);
  }
}
