// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/InformationDelegator.java,v $
// $RCSfile: InformationDelegator.java,v $
// $Revision: 1.6 $
// $Date: 2003/08/28 21:57:00 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.beans.beancontext.*;
import java.net.URL;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.accessibility.*;

import com.bbn.openmap.event.*;
import com.bbn.openmap.gui.MapPanelChild;
import com.bbn.openmap.gui.OMComponentPanel;
import com.bbn.openmap.gui.StatusLightPanel;
import com.bbn.openmap.gui.WindowSupport;
import com.bbn.openmap.layer.util.LayerUtils;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.PropUtils;
import com.bbn.openmap.util.WebBrowser;
import com.bbn.openmap.util.propertyEditor.*;

/**
 * The InformationDelegator manages the display of information
 * requested by Layers and other map components.  It can bring up a
 * web browser to display web pages and files, and pop up a message
 * window to provide status information to the user.  It also has a
 * visible status window that contains a layer status indicator, and
 * an information line that can display short messages.
 * <p>
 * InformationDelegators are added to layers, and the layer fires
 * events through the InfoDisplayListener interface.  The
 * InformationDelegator has a method called listenToLayers() that lets
 * you give it an array of layers, and it adds itself as a
 * InfoDisplayListener to those layers.
 * <p>
 * The InformationDelegator lets you alter its behavior with property
 * settings:
 * <pre>
 * # Make the status lights buttons that bring up layer palettes.
 * infoDelegator.triggers=true
 * # Show the layer status lights.
 * infoDelegator.showLights=true
 * # Show the information text line
 * infoDelegator.showInfoLine=true
 * </pre>
 */
public class InformationDelegator extends OMComponentPanel
    implements InfoDisplayListener, PropertyChangeListener, ProgressListener, MapPanelChild {

    protected JLabel infoLineHolder;
    protected JLabel infoLineHolder2;

    protected WebBrowser browser;
    protected StatusLightPanel statusBar;

    protected JProgressBar progressBar;

    protected MapBean map;

    protected ToolTipManager ttmanager; 
    protected String propertyPrefix;

    private String fudgeString = " ";
    /** 
     * Used to remember what the MouseModeCursor is, which is the
     * base cursor setting for the MapBean.  The gesture modes set
     * this cursor, and it gets used when the currentMapBeanCursor is
     * null.
     */
    protected Cursor fallbackMapBeanCursor = Cursor.getDefaultCursor();
    /**
     * Used to remember any cursor that may bave been requested by a
     * layer. This is usually null, unless a layer has requested a
     * cursor.  The MapBean gesture modes set the
     * fallbackMapBeanCursor instead.
     */
    protected Cursor currentMapBeanCursor = null;
    protected boolean waitingForLayers = false;
    protected boolean showWaitCursor = false;

    /**
     * Flag to show the status lights.
     */
    protected boolean showLights = true;
    public final static String ShowLightsProperty = "showLights";
    /**
     * Flaf to show the information line.
     */
    protected boolean showInfoLine = true;
    public final static String ShowInfoLineProperty = "showInfoLine";

    public final static int MAP_OBJECT_INFO_LINE = 0; // Default
    public final static int COORDINATE_INFO_LINE = 1;

    protected ArrayList infoLineOrganizer = new ArrayList();

    public InformationDelegator() {
	super();

	initInfoWidgets();
    }

    /**
     * If you want to subclass the InformationDelegator and have it
     * handle messages differently, you can override this method to
     * set the widgets you want.
     */
    public void initInfoWidgets() {

	Debug.message("info", "InformationDelegator.initInfoWidgets");

	GridBagLayout gridbag = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();

	setFont(new Font("Helvetica", Font.PLAIN, 9));
	setLayout(gridbag);
	
	progressBar = new JProgressBar();
	gridbag.setConstraints(progressBar, c);
	add(progressBar);
	progressBar.setVisible(false);

	JPanel infoLinePanel = new JPanel();
	c.weightx=1;
	c.anchor=GridBagConstraints.WEST;
	c.fill=GridBagConstraints.HORIZONTAL;

	gridbag.setConstraints(infoLinePanel, c);

	GridBagLayout gridbag2 = new GridBagLayout();
	GridBagConstraints c2 = new GridBagConstraints();
	infoLinePanel.setLayout(gridbag2);

	infoLineHolder = new JLabel(fudgeString);
	c2.weightx = 1;
	c2.fill=GridBagConstraints.HORIZONTAL;
	c2.anchor=GridBagConstraints.WEST;
	c2.insets = new Insets(3, 10, 3, 10);
	gridbag2.setConstraints(infoLineHolder, c2);
	infoLinePanel.add(infoLineHolder);

	infoLineHolder2 = new JLabel(fudgeString, SwingConstants.RIGHT);
	c2.weightx = 0;
	c2.anchor=GridBagConstraints.EAST;
	gridbag2.setConstraints(infoLineHolder2, c2);
	infoLinePanel.add(infoLineHolder2);

	addInfoLine(COORDINATE_INFO_LINE, infoLineHolder);
	addInfoLine(MAP_OBJECT_INFO_LINE, infoLineHolder2);

	add(infoLinePanel);
	infoLinePanel.setVisible(showInfoLine);

	c.weightx=0;
	c.anchor=GridBagConstraints.EAST;
 	statusBar = new StatusLightPanel();
	gridbag.setConstraints(statusBar, c);
	add(statusBar);
  	statusBar.setVisible(showLights);
    }

    /**
     * Set the MapBean so that when the mouse mode changes, the cursor
     * can change.  This gets called from findAndInit if a MapHandler
     * is involved with the application.
     */
    public void setMap(MapBean map) {
	if (this.map != null) {
	    this.map.removePropertyChangeListener(this);
	}

	this.map = map;
	if (map != null) {
	    map.addPropertyChangeListener(this);
	    fallbackMapBeanCursor = map.getCursor();
	}
   }

    /**
     * Listen for changes to the active mouse mode and for any
     * changes to the list of available mouse modes.  If the active
     * mouse mode is "gestures", then the lat lon updates to the
     * status line are deactivated.
     */
    public void propertyChange(PropertyChangeEvent evt) {

  	if (evt.getPropertyName() == MouseDelegator.ActiveModeProperty) {
	    MapMouseMode mmm = (MapMouseMode)evt.getNewValue();
	    setAllLabels(fudgeString);
  	    setResetCursor(mmm.getModeCursor());
  	} else if (evt.getPropertyName() == MapBean.CursorProperty) {
	    fallbackMapBeanCursor = ((Cursor)evt.getNewValue());
	} else if (evt.getPropertyName() == MapBean.LayersProperty) {
	    resetForLayers((Layer[])evt.getNewValue(), (Layer[])evt.getOldValue());
	    setAllLabels(fudgeString);
	} else if (evt.getPropertyName() == MapBean.ProjectionProperty) {
	    setAllLabels(fudgeString);
	}
    }

    /**
     * Set the InformationDelegator on Layers.  Usually called because
     * the layers changed on the map, and we need to add the
     * InformationDelegator as an InfoDisplayListener for the new
     * layers, and remove it from the old layers.
     */
    public void resetForLayers(Layer[] connectToLayers, 
			       Layer[] removeFromLayers) {

	int i = 0;
	if (removeFromLayers != null && removeFromLayers.length != 0) {
	    int removeLength = removeFromLayers.length;
	    for (i = 0; i < removeLength; i++) {
		removeFromLayers[i].removeInfoDisplayListener(this);
	    }
	}

	if (connectToLayers != null && connectToLayers.length != 0) {
	    int removeLength = connectToLayers.length;
	    for (i = 0; i < removeLength; i++) {
		connectToLayers[i].addInfoDisplayListener(this);
	    }
	}
    }

    /**
     * Receive a ProgressEvent, and use it if possible.
     */
    public void updateProgress(ProgressEvent evt) {
	if (progressBar != null) {
	    int type = evt.getType();
	    if (type == ProgressEvent.START ||
		type == ProgressEvent.UPDATE) {

		progressBar.setVisible(true);
		progressBar.setValue(evt.getPercentComplete());
		setLabel(evt.getTaskDescription());
	    } else {
		progressBar.setVisible(false);
	    }
	}
    }

    public void addInfoLine(int refIndex, JLabel iLine) {
	try {
	    infoLineOrganizer.set(refIndex, iLine);
	} catch (IndexOutOfBoundsException ioobe) {
	    while (refIndex > 0 && infoLineOrganizer.size() <= refIndex + 1) {
		infoLineOrganizer.add(iLine);
	    }
	}
    }

    public void removeInfoLine(int refIndex) {
	try {
	    infoLineOrganizer.set(refIndex, null);
	} catch (IndexOutOfBoundsException iiobe) {}
    }

    /**
     * Set the information line label.
     * @param str String
     */
    public void setLabel(String str) {
	setLabel(str, MAP_OBJECT_INFO_LINE);
    }

    public void setAllLabels(String str) {
	for (int i = 0; i < infoLineOrganizer.size(); i++) {
	    setLabel(str, i);
	}
    }

    /**
     * Set the information line label.
     * @param str String
     * @param int the designator used to specify which information
     * line to use to display the string.
     */
    public void setLabel(String str, int infoLineDesignator) {
	JLabel iLine;
	try {
	    iLine = (JLabel)infoLineOrganizer.get(infoLineDesignator);
	} catch (IndexOutOfBoundsException ioobe) {
	    // This should be OK.
	    iLine = (JLabel)infoLineOrganizer.get(MAP_OBJECT_INFO_LINE);
	}

	if (iLine != null) {
	    iLine.setText(str);
	}

	// HACK This wasn't necessary in JDK1.1.5 and Swing1.0.1, but is now.
	// Without the following two lines, the infoline doesn't show up at all
//  	infoLineHolder.invalidate();
//  	this.validate();
    }

    /**
     * The method that updates the InformationDelegator display window
     * with the correct layer representation.  A status light reset
     * method.
     */
    protected void setStatusBar() {
	statusBar.reset();
    }

    public void initBrowser() {
	setBrowser(new WebBrowser());
    }

    public void setBrowser(WebBrowser wb) {
	browser = wb;
	browser.setInfoDelegator(this);
    }

    public WebBrowser getBrowser() {
	if (browser == null) {
	    initBrowser();
	}
	return browser;
    }

    /**
     * Callback method.
     */
    public void checkBrowser() {
	if (browser != null) browser.exitValue();
    }

    /**
     * Try to display a URL in a web browser.
     */
    public void displayURL(String url) {
	WebBrowser wb = getBrowser();
	if (wb != null) {
	    wb.launch(url);
	}	    
    }
    
    /**
     * Display a html String in a window.
     */
    public void displayBrowserContent(String content) {
	com.bbn.openmap.gui.MiniBrowser.display(content);
// 	WebBrowser wb = getBrowser();
// 	if (wb != null) {
// 	    wb.writeAndLaunch(content);
// 	}
    }
    
    /**
     * Display a line of text in a info line.
     */
    public void displayInfoLine(String infoLine) {
	displayInfoLine(infoLine, MAP_OBJECT_INFO_LINE);
    }

    /**
     * Display a line of text in a designated info line.
     */
    public void displayInfoLine(String infoLine, int labelDesignator) {
	if (infoLineHolder != null) {
	    setLabel((infoLine != null && infoLine.length() > 0)?infoLine:fudgeString, labelDesignator);
	}
    }

    /**
     * Display a message in a pop-up window.
     */
    public void displayMessage(String title, String message) {
	JOptionPane.showMessageDialog(null, message, title,
				      JOptionPane.INFORMATION_MESSAGE);
    }

    ///////////////////////////////////////////
    //  InfoDisplayListener interface

    /**
     * Handle layer requests to have a URL displayed in a Browser.
     *
     * @param event InfoDisplayEvent
     */
    public void requestURL(InfoDisplayEvent event) {
	displayURL(event.getInformation());
    }

    /**
     * Handle layer requests to have a message displayed in a dialog
     * window.
     *
     * @param event InfoDisplayEvent
     */
    public void requestMessage(InfoDisplayEvent event) {	    
	Layer l = event.getLayer();
	String layername = (l == null) ? null : l.getName();
	displayMessage("Message from " + 
		       layername + " layer:",
		       event.getInformation());
    }
    
    /**
     * Handle layer requests to have an information line displayed
     * in an application status window.
     *
     * @param event InfoDisplayEvent
     */
    public void requestInfoLine(InfoDisplayEvent event) {
	displayInfoLine(event.getInformation(), event.getPreferredLocation());
    }

    /**
     * Handle layer requests that plain text or html text be
     * displayed in a browser.
     *
     * @param event InfoDisplayEvent
     */
    public void requestBrowserContent(InfoDisplayEvent event) {
	displayBrowserContent(event.getInformation());
    }

    /**
     * If a tooltip is required over a spot on the map then a
     * <code>MouseMapListener</code> should pass a MouseEvent to this
     * method. The Swing ToolTipManager is used to achieve this. A
     * call to this method should always be followed by a call to
     * <code>hideToolTip</code>
     *
     * @param me A MouseEvent from a <code>MapMouseListener</code>
     * which indicates where the tooltip is to appear
     * @param text A String value of the tooltip text which is to
     * appear (this can also be HTML wih Java 1.3)
     */
    public void requestShowToolTip(MouseEvent me, InfoDisplayEvent event) { 
	//shows a tooltip over the map
	if (map != null) {
	    if (ttmanager == null) { 
		//make sure the MapBean is registered first
		ttmanager=ToolTipManager.sharedInstance(); 
		ttmanager.registerComponent(map);
	    }
	    map.setToolTipText(event.getInformation()); 
	    ttmanager.mouseEntered(me);
	}
    }

    /**
     * This method should follow a call to showToolTip in order to
     * indicate that the tooltip should no longer be displayed. This
     * method should always follow a call to <code>showToolTip</code?
     *
     * @param me A MouseEvent which passes from a MapMouseListener to
     * indicate that a tooltip should disappear 
     */
    public void requestHideToolTip(MouseEvent me) { 
	//disables a tooltip over the map
	if (ttmanager==null) {
	    return; //in case showToolTip was never called
	}
	ttmanager.mouseExited(me); 
	initToolTip();
    }

    /**
     * This method should be called to intialize the tooltip status so
     * that an old tooltip doesn't remain when a layer starts
     * listening to mouse events.  
     */
    public void initToolTip() {
	if (ttmanager == null) {
	    return; //in case showToolTip was never called
	}

	if (map != null) {
	    map.setToolTipText(null);
	}
    }

    /**
     * Change the cursor for the MapBean.  If the MapBean hasn't been
     * set, then nothing will happen on the screen. If a null value is
     * passed in, the cursor is reset to the MouseMode value.  If the
     * InformationDelegator is alowed to show the wait cursor, and the
     * layers are busy, the wait cursor will take precidence.  The
     * requested cursor from a layer will be set if the layers finish.
     *
     * @param cursor java.awt.Cursor to change the cursor to.
     */
    public void requestCursor(java.awt.Cursor cursor) {
	// This is interpreted as a release from a requester
	if (cursor == null) {
	    // If we're not supposed to be showing the wait cursor...
	    if (showWaitCursor && !waitingForLayers)
		resetCursor();
	    // Set this to null, so that when we're done waiting for
	    // the layers, we'll just reset.
	    currentMapBeanCursor = null;
	}
	else if (this.map != null) {
	    Cursor newCursor;
	    // If we're supposed to be showing the watch, do it, but
	    // save the request for when the layers are done.
	    if (showWaitCursor && waitingForLayers) {
		newCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
		currentMapBeanCursor = cursor;
	    }
	    else newCursor = cursor;

	    map.setCursor(newCursor);
	}
    }

    /**
     * Set the cursor to use when the waiting is done, if a layer
     * hasn't asked for one to be displayed.  For the MouseMode
     * changes, this is automatically called.
     */
    public void setResetCursor(java.awt.Cursor cursor) {
	fallbackMapBeanCursor = cursor;
    }

    /** 
     * Sets the cursor over the mapbean to the assigned default, or
     * whatever has been set by the MouseMode.
     */
    public void resetCursor() {
	if (this.map != null)
	    map.setCursor(fallbackMapBeanCursor);
    }

    /**
     * If the value passed in is true, the cursor over the MapBean
     * will be the waiting cursor layers are off working.  The status
     * lights will work, too, no matter what the value is.  If false,
     * the cursor won't change if the layers are working.
     */
    public void setShowWaitCursor(boolean value) {
	showWaitCursor = value;
    }

    /**
     * Returns whether the wait cursor will be shown if the layers
     * are working.
     */
    public boolean isShowWaitCursor() {
	return showWaitCursor;
    }

    ////////////  MapHandlerChild methods overridden from OMComponentPanel

    /**
     * Called when an object has been added to the BeanContext.  The
     * InformationDelegator will look for certain objects it needs.
     */
    public void findAndInit(Object someObj) {
	if (someObj instanceof MapBean) {
	    setMap((MapBean)someObj);
	}
	if (someObj instanceof MouseDelegator) {
	    MouseDelegator md = (MouseDelegator)someObj;
	    md.addPropertyChangeListener(this);
	}

	statusBar.findAndInit(someObj);
    }

    /**
     * Called when an object is being removed from the BeanContext.
     * Will cause the object to be disconnected from the
     * InformationDelegator if it is being used.
     */
    public void findAndUndo(Object someObj) {
	if (someObj instanceof MapBean) {
	    setMap(null);
	}
	if (someObj instanceof MouseDelegator) {
	    MouseDelegator md = (MouseDelegator)someObj;
	    md.removePropertyChangeListener(this);
	}

	statusBar.findAndUndo(someObj);
    }

    ///////  PropertyConsumer methods overridden from OMComponentPanel

    public void setProperties(String prefix, Properties props) {
	setPropertyPrefix(prefix);
	prefix = PropUtils.getScopedPropertyPrefix(prefix);

	statusBar.setProperties(prefix, props);
	setShowLights(LayerUtils.booleanFromProperties(props, prefix + ShowLightsProperty, showLights));
	setShowInfoLine(LayerUtils.booleanFromProperties(props, prefix + ShowInfoLineProperty, showInfoLine));

	String pl = props.getProperty(prefix + PreferredLocationProperty);
	if (pl != null) {
	    setPreferredLocation(pl);
	}
    }

    public Properties getProperties(Properties props) {
	if (props == null) {
	    props = new Properties();
	}

	statusBar.getProperties(props);
	String prefix = PropUtils.getScopedPropertyPrefix(this);
	props.put(prefix + ShowLightsProperty, new Boolean(showLights).toString());
	props.put(prefix + ShowInfoLineProperty, new Boolean(showInfoLine).toString());
	props.put(prefix + PreferredLocationProperty, getPreferredLocation());
	return props;
    }

    public Properties getPropertyInfo(Properties props) {
	if (props == null) {
	    props = new Properties();
	}

	statusBar.getPropertyInfo(props);
	props.put(ShowLightsProperty, "Show the layer status lights");
	props.put(ShowLightsProperty + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");
	props.put(ShowInfoLineProperty, "Show the information line below the map");
	props.put(ShowInfoLineProperty + ScopedEditorProperty, "com.bbn.openmap.util.propertyEditor.YesNoPropertyEditor");
	props.put(PreferredLocationProperty, "The preferred BorderLayout direction to place this component.");

	return props;
    }

    /////// Setters and Getters

    public void setInfoLineHolder(JLabel ilh) {
	infoLineHolder = ilh;
    }

    public JLabel getInfoLineHolder() {
	return infoLineHolder;
    }

    public void setProgressBar(JProgressBar progressBar) {
	this.progressBar = progressBar;
    }

    public JProgressBar getProgressBar() {
	return progressBar;
    }

    public void setShowLights(boolean set) {
	showLights = set;
	statusBar.setVisible(set);
    }

    public boolean getShowLights() {
	return showLights;
    }

    public void setShowInfoLine(boolean set) {
	showInfoLine = set;
	infoLineHolder.setVisible(set);
    }
    
    public boolean getShowInfoLine() {
	return showInfoLine;
    }

    public void setLightTriggers(boolean set) {
	statusBar.setLightTriggers(set);
    }

    public boolean getLightTriggers() {
	return statusBar.getLightTriggers();
    }

    public void setFloatable(boolean value) {}

    /**
     * BorderLayout.SOUTH by default for this class.
     */
    protected String preferredLocation = java.awt.BorderLayout.SOUTH;

    /**
     * MapPanelChild method.
     */
    public void setPreferredLocation(String value) {
	preferredLocation = value;
    }

    /** 
     * MapPanelChild method. 
     */
    public String getPreferredLocation() {
	return preferredLocation;
    }
}

