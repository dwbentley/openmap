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
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/layer/plotLayer/PlotLayer.java,v $
// $RCSfile: PlotLayer.java,v $
// $Revision: 1.1.1.1 $
// $Date: 2003/02/14 21:35:48 $
// $Author: dietrick $
// 
// **********************************************************************



package com.bbn.openmap.layer.plotLayer;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javax.swing.*;

import com.bbn.openmap.*;
import com.bbn.openmap.event.*;
import com.bbn.openmap.omGraphics.*;
import com.bbn.openmap.proj.*;
import com.bbn.openmap.util.Debug;
import com.bbn.openmap.util.PaletteHelper;

/**
 *
 */
public class PlotLayer extends Layer implements MapMouseListener
{

    private static transient int counter = 0;
    private boolean boxy = true;
    private OMGraphicList omgraphics = null;

    private ScatterGraph graph = null;
    private boolean show_plot_ = false;

    // The currently selected graphic.
    private OMGraphic selectedGraphic;
    private Vector selectedGraphics = null;
    
    // Where do we get the data from? 
    // default to use GLOBE atmospheric temperature.
    private String datasource = "AT.gst_small.txt";

    // "http://globe.ngdc.noaa.gov/sda/student_data/AT.gst.txt"; 
    // "file:/home/gkeith/openmap/openmap/com/bbn/openmap/plotLayer/AT.gst.txt";
    // "file:/home/gkeith/openmap/openmap/com/bbn/openmap/plotLayer/AT.gst_thin.txt";
    // "file:/home/gkeith/openmap/openmap/com/bbn/openmap/plotLayer/AT.gst_small.txt";
    // "http://stout:80/~gkeith/plotlayer/AT.gst_small.txt";

    private GLOBETempData temperature_data = null;

    // The control palette
    private JPanel pal = null;

    /**
     * X position of the plot rectangle.
     */
    protected int plotX = 100;

    /**
     * Y position of the plot rectangle.
     */
    protected int plotY = 100;

    /**
     * Width of the plot rectangle.
     */
    protected int plotWidth = 320;

    /**
     * Height of the plot rectangle.
     */
    protected int plotHeight = 200;

    /**
     * Construct the PlotLayer.
     */
    public PlotLayer() {
	// precalculate for boxy
	boxy = true;

	getDataSource();
	graph = new ScatterGraph(678,790, null, 
				 temperature_data.overall_min_year_, 
				 temperature_data.overall_max_year_,
				 temperature_data.overall_min_temp_, 
				 temperature_data.overall_max_temp_);
	omgraphics = plotDataSources();
    }

    /** 
     * Implementing the ProjectionPainter interface.
     */
    public synchronized void renderDataForProjection(Projection proj, java.awt.Graphics g) {
	if (proj == null) {
	    Debug.error("PlotLayer.renderDataForProjection: null projection!");
	    return;
	}
	
	// The actual projection doesn't matter, since we are only 
	// drawing points in XY space
	setProjection(proj.makeClone());

	// Redimension the graph, to 
	graph.resize(plotX, plotY, plotWidth, plotHeight);
	omgraphics.project(getProjection(), true);
	paint(g);
    }

    /**
     * Invoked when the projection has changed or this Layer has been added to
     * the MapBean.
     * <p>
     * Perform some extra checks to see if reprojection of the graphics is
     * really necessary.
     * @param e ProjectionEvent
     *
     */    
    public void projectionChanged(ProjectionEvent e) {

	// The actual projection doesn't matter, since we are only 
	// drawing points in XY space
	setProjection(e);
	// Redimension the graph, to 
	graph.resize(plotX, plotY, plotWidth, plotHeight);
	omgraphics.project(getProjection(), true);
       	repaint();
    }

    /**
     * Paints the layer.
     *
     * @param g the Graphics context for painting
     */
    public void paint(java.awt.Graphics g) {
	Debug.message("basic", "PlotLayer.paint() " + omgraphics.size() + " graphics");
	omgraphics.render(g);
    }

    // Search for the data in the directories listing in the
    // CLASSPATH.  We should also check to see if the datafile is
    // specified as a URL so that we can load it as such.
    private GLOBETempData getDataSource() {

	if (temperature_data != null) {
	    return temperature_data;
	}

	// load the data from the CLASSPATH
	Vector dirs = Environment.getClasspathDirs();
	FileInputStream is = null;
	int nDirs = dirs.size();
	if (nDirs > 0) {
	    for (int i=0; i<nDirs; i++) {
		String dir = (String) dirs.elementAt(i);
		File datafile = new File(dir, datasource);
		if (datafile.isFile()) {
		    try {
			is = new FileInputStream(datafile);
//  			System.out.println("datafile="+datafile);
			break;
		    } catch (java.io.IOException e) {
			e.printStackTrace();
		    }
		}
	    }
	    if (is == null) {
		System.err.println(
			"Unable to load datafile \"" + datasource +
			"\" from CLASSPATH");
	    }
	} else {
	    System.err.println("No directories in CLASSPATH!");
	    System.err.println(
		    "Unable to load datafile \"" + datasource +
		    "\" from CLASSPATH");
	}
	if (is == null)
	    return null;

	// Parse the data
	try {
	    temperature_data = new GLOBETempData();
	    temperature_data.loadData(is);
	}
	catch (IOException e) {
	    System.err.println(e);
	}
	return temperature_data;
    }

    // Put the data points on the map.
    private OMGraphicList plotDataSources() {
	Debug.message("basic", "PlotLayer.plotDataSources()");
	int num_graphics = 0;
	
	OMGraphicList graphics = new OMGraphicList();
	graphics.setTraverseMode(OMGraphicList.LAST_ADDED_ON_TOP);
	graphics.clear();
      
	Enumeration site_enum = temperature_data.getAllSites();
	while (site_enum.hasMoreElements()) 
	    {
		GLOBESite site = (GLOBESite)site_enum.nextElement();
		//Debug.message("basic", "Plotlayer adds " + site.getName());
		graphics.addOMGraphic(site.getGraphic());
		num_graphics++;
	    }
	
	Debug.message("basic",
		      "Plotlayer found " + num_graphics + " distinct sites");

	// Find the sites that are visible on the map. 
	return graphics;
    }



    // Build and display the plot.
    private void generatePlot() {
//  	System.out.println("Generating Plot ");
	if (graph != null) {
	    graph.setDataPoints(selectedGraphics);
	    graph.plotData();
	}	
    }

    private void showPlot() {
	show_plot_ = true;

	generatePlot();

	if (graph != null) {
//  	    System.out.println("Making plot visible..");
	    omgraphics.addOMGraphic(graph.getPlotGraphics());
	}
	// generate the graphics for rendering.
	omgraphics.generate(getProjection(), false);
	repaint();
    }

    private void hidePlot() {
//  	System.out.println("Making plot IN-visible..");
	show_plot_ = false;
	if (graph != null) {
	    OMGraphic searchfor = graph.getPlotGraphics();
	    for (int i = 0; i < omgraphics.size() ; i++) {
		if ( searchfor.equals(omgraphics.getOMGraphicAt(i))) {
		    omgraphics.removeOMGraphicAt(i);
		}
	    }
	    omgraphics.project(getProjection(), false);
	}
	// We need to project here, in order to prepare 
	// the plot for rendering.
	//omgraphics.project(projection, false);
	repaint();
    }
    
    // add the data from the clicked site to the list of things 
    // we are drawing
    private void addSelectionToPlotList() {
	if (selectedGraphic != null) {
	    // Change the color of the clicked ones
	    selectedGraphic.setLinePaint(Color.blue);
	    
	    if (selectedGraphics == null) {
		selectedGraphics = new Vector();
	    }
	    
	    Object app_obj = selectedGraphic.getAppObject();
	    
	    if (app_obj instanceof GLOBESite) {
		GLOBESite site = (GLOBESite)app_obj;
		if ( ! selectedGraphics.contains(app_obj) ) {
		    Debug.message("basic", "Adding to plot list...");
		    selectedGraphics.addElement(site);
		    selectedGraphic.setFillPaint(Color.yellow);
		}
		else {
		    Debug.message("basic", "Removing from plot list...");
		    selectedGraphics.removeElement(site);
		    selectedGraphic.setFillPaint(Color.red);
		    selectedGraphic.setLinePaint(Color.red);
		}
		    
	    }
	} 
	else {
	    Debug.message("basic", "Nothing to add to plot list!");
	}
    }
  
  
    /**
     * Returns self as the <code>MapMouseListener</code> in order
     * to receive <code>MapMouseEvent</code>s.  If the implementation
     * would prefer to delegate <code>MapMouseEvent</code>s, it could
     * return the delegate from this method instead.
     *
     * @return The object to receive <code>MapMouseEvent</code>s or
     *         null if this layer isn't interested in
     *         <code>MapMouseEvent</code>s
     */
    public MapMouseListener getMapMouseListener() {
	return this;
    }
    
    
    public Component getGUI() {
	if (pal == null) {
	    ActionListener al = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    int index = Integer.parseInt(e.getActionCommand(), 10);
		    switch (index) {
		    case 0:
			if ( show_plot_ )
			    hidePlot();
			else
			    showPlot();
			break;
		    default:
			throw new RuntimeException("argh!");
		    }
		}
	    };
	    pal = PaletteHelper.
		createCheckbox("Plot Control", 
			       new String[] {"Show Temperature Plot"},
			       new boolean[] {show_plot_}, 
			       al);
	}
	return pal;
    }



    //----------------------------------------------------------------------
    // MapMouseListener interface implementation
    //----------------------------------------------------------------------


    /**
     * Indicates which mouse modes should send events to this
     * <code>Layer</code>.
     *
     * @return String[] of mouse mode names
     *
     * @see com.bbn.openmap.event.MapMouseListener
     * @see com.bbn.openmap.MouseDelegator
     */
    public String[] getMouseModeServiceList () {
	return new String[] {
	    SelectMouseMode.modeID
	};
    }

    //graphic position variables when moving the plot graphic
    private int prevX, prevY;
    private boolean grabbed_plot_graphics_ = false;


    /**
     * Called whenever the mouse is pressed by the user and
     * one of the requested mouse modes is active.
     *
     * @param e the press event
     * @return true if event was consumed (handled), false otherwise
     * @see #getMouseModeServiceList
     */
    public boolean mousePressed(MouseEvent e) {
	if ( show_plot_ && graph != null ) {
	    int x = e.getX();
	    int y = e.getY();
	    if ((x >= plotX) && (x <= plotX+plotWidth) && 
		(y >= plotY) && (y <= plotY+plotWidth)) {

		grabbed_plot_graphics_ = true;
		// grab the location
		prevX = x;
		prevY = y;
	    }
	}
	return false;
    }
 


    /**
     * Called whenever the mouse is released by the user and
     * one of the requested mouse modes is active.
     *
     * @param e the release event
     * @return true if event was consumed (handled), false otherwise
     * @see #getMouseModeServiceList
     */
    public boolean mouseReleased(MouseEvent e) {
	grabbed_plot_graphics_ = false;
	return false;
    }

    /**
     * Called whenever the mouse is clicked by the user and
     * one of the requested mouse modes is active.
     *
     * @param e the click event
     * @return true if event was consumed (handled), false otherwise
     * @see #getMouseModeServiceList
     */
    public boolean mouseClicked(MouseEvent e) {
//  	System.out.println("XY: " + e.getX() + " " + e.getY() );
	if (selectedGraphic != null && !show_plot_ ) {
	    switch (e.getClickCount()) {
	    case 1:  
		/** One click adds the site to our list of sites
		 *  to plot.
		 */
		addSelectionToPlotList();
		generatePlot();
		repaint();
		break;
	    case 2:
		/** Double click means generate the plot. 
		 */ 
//  		System.out.println("Saw DoubleClick!");
		repaint();
		break;
	    default:
		break;
	    }
	    return true;
	} else {
	    return false;
	}
    }

    /**
     * Called whenever the mouse enters this layer and
     * one of the requested mouse modes is active.
     *
     * @param e the enter event
     * @see #getMouseModeServiceList
     */
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * Called whenever the mouse exits this layer and
     * one of the requested mouse modes is active.
     *
     * @param e the exit event
     * @see #getMouseModeServiceList
     */
    public void mouseExited(MouseEvent e) {
    }

    /**
     * Called whenever the mouse is dragged on this layer and
     * one of the requested mouse modes is active.
     *
     * @param e the drag event
     * @return true if event was consumed (handled), false otherwise
     * @see #getMouseModeServiceList
     */
    public boolean mouseDragged(MouseEvent e) {
	if (grabbed_plot_graphics_) {
	    int x = e.getX();
	    int y = e.getY();
	    int dx = x-prevX;
	    int dy = y-prevY;

	    plotX += dx;
	    plotY += dy;
	    prevX = x;
	    prevY = y;

	    graph.resize(plotX, plotY, plotWidth, plotHeight);
	    OMGraphicList plotGraphics = graph.getPlotGraphics();
	    //regenerate the plot graphics
	    plotGraphics.generate(getProjection(), true);
	    repaint();
	}
	return false;
    }



    /**
     * Called whenever the mouse is moved on this layer and
     * one of the requested mouse modes is active.
     * <p>
     * Tries to locate a graphic near the mouse, and if it
     * is found, it is highlighted and the Layer is repainted
     * to show the highlighting.
     *
     * @param e the move event
     * @return true if event was consumed (handled), false otherwise
     * @see #getMouseModeServiceList
     */
    public boolean mouseMoved(MouseEvent e) {
	OMGraphic newSelectedGraphic;
	if ( show_plot_ && graph != null ) {
	    
	    newSelectedGraphic = graph.selectPoint(e.getX(), e.getY(), 4.0f);
	    
	    if (newSelectedGraphic != null) {
		String infostring = (String)(newSelectedGraphic.getAppObject());
		if (infostring != null) {
		    fireRequestInfoLine(infostring);		
		}
	    } else {
		fireRequestInfoLine("");
	    }
	    
	} else {
	    newSelectedGraphic = omgraphics.selectClosest(e.getX(),
							  e.getY(),
							  4.0f);
	
	    if (newSelectedGraphic != null &&
		(selectedGraphic == null ||
		 newSelectedGraphic != selectedGraphic)) {

		    Debug.message("basic", "Making selection...");

		    selectedGraphic = newSelectedGraphic;
		    //selectedGraphic.setLineColor(Color.yellow);
		    selectedGraphic.regenerate(getProjection());
		    
		    // display site info on map
		    GLOBESite site = (GLOBESite)(newSelectedGraphic.getAppObject());
		    if (site != null) {
			fireRequestInfoLine(site.getInfo());		
		    }

		    repaint();
	    } else if (selectedGraphic != null &&
		     newSelectedGraphic == null) { 

		    // revert color of un-moused object.
		    Debug.message("basic", "Clearing selection...");
		    //selectedGraphic.setLineColor(Color.red);
		    selectedGraphic.regenerate(getProjection());
		    fireRequestInfoLine("");
		    selectedGraphic = null;
		    repaint();
	    }  
	}
	return true;
    }
    
    /** Called whenever the mouse is moved on this layer and one of
     * the requested mouse modes is active, and the gesture is
     * consumed by another active layer.  We need to deselect anything
     * that may be selected.
     *
     * @see #getMouseModeServiceList */
    public void mouseMoved() {
	omgraphics.deselectAll();
	repaint();
    }


    /**
     * Initializes this layer from the given properties.
     * @param props the <code>Properties</code> holding settings for
     * this layer
     */
    public void setProperties(String prefix, Properties props) {
	super.setProperties(prefix, props);
    }
}