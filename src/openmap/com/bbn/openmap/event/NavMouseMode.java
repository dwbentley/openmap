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
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/event/NavMouseMode.java,v $
// $RCSfile: NavMouseMode.java,v $
// $Revision: 1.1.1.1 $
// $Date: 2003/02/14 21:35:48 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.event;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.*;
import java.beans.beancontext.*;
import java.util.Iterator;

import com.bbn.openmap.InformationDelegator;
import com.bbn.openmap.LatLonPoint;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.Proj;
import com.bbn.openmap.util.Debug;

/**
 * The Navigation Mouse Mode interprets mouse clicks and mouse drags
 * to recenter and rescale the map.  The map is centered on the
 * location where a click occurs.  If a box is drawn by clicking down
 * and dragging the mouse, the map is centered on the dot in the
 * center of the box, and the scale is adjusted so the screen fills
 * the area designated by the box.
 * <p>
 * You MUST add this MouseMode as a ProjectionListener to the MapBean
 * to get it to work.  If you use a MouseDelegator with the bean, it
 * will take care of that for you.
 */
public class NavMouseMode extends CoordMouseMode {

    /**
     * Mouse Mode identifier, which is "Navigation".
     */
    public final static transient String modeID = "Navigation";

    protected Point point1, point2;
    protected boolean autoZoom = false;

    /**
     * Construct a NavMouseMode.
     * Sets the ID of the mode to the modeID, the consume mode to
     * true, and the cursor to the crosshair. 
     */
    public NavMouseMode() {
	this(true);
    }

    /**
     * Construct a NavMouseMode.
     * Lets you set the consume mode.  If the events are consumed,
     * then a MouseEvent is sent only to the first MapMouseListener
     * that successfully processes the event.  If they are not
     * consumed, then all of the listeners get a chance to act on the
     * event.
     * @param shouldConsumeEvents the mode setting.
     */
    public NavMouseMode(boolean shouldConsumeEvents) {
	super(modeID, shouldConsumeEvents);
	// override the default cursor
	setModeCursor(cursor.getPredefinedCursor(cursor.CROSSHAIR_CURSOR));
    }

    /**
     * Handle a mousePressed MouseListener event.  Erases the old
     * navigation rectangle if there is one, and then keeps the press
     * point for reference later.
     * @param e MouseEvent to be handled
     */
    public void mousePressed(MouseEvent e) {
	if (Debug.debugging("mousemode")) {
	    System.out.println(getID()+"|NavMouseMode.mousePressed()");
 	}
	if (! mouseSupport.fireMapMousePressed(e) && 
	    e.getSource() instanceof MapBean) {
	    // set the new first point
	    point1 = e.getPoint();
	    // ensure the second point isn't set.
	    point2 = null;
	}
    }

    /**
     * Handle a mouseReleased MouseListener event.
     * If there was no drag events, or if there was only a small
     * amount of dragging between the occurence of the mousePressed
     * and this event, then recenter the map.  Otherwise we get the
     * second corner of the navigation rectangle and try to figure
     * out the best scale and location to zoom in to based on that
     * rectangle.
     * @param e MouseEvent to be handled
     */
    public void mouseReleased(MouseEvent e) {
	if (Debug.debugging("mousemode")) {
	    System.out.println(getID()+"|NavMouseMode.mouseReleased()");
 	}
	Object obj = e.getSource();
	if (! mouseSupport.fireMapMouseReleased(e)) {
	    if (!(obj instanceof MapBean) || 
		!autoZoom || point1 == null) return;
	    MapBean map = (MapBean)obj;
	    Projection projection = map.getProjection();
	    Proj p = (Proj)projection;

	    synchronized (this) {
		point2 = e.getPoint();
		int dx = Math.abs(point2.x -point1.x);
		int dy = Math.abs(point2.y -point1.y);

		// Don't bother redrawing if the rectangle is too small
		if ((dx < 5) || (dy < 5)) {
		    // clean up the rectangle, since point2 has the old value.
		    paintRectangle(map, point1, point2); 

		    // If rectangle is too small in both x and y then
		    // recenter the map
		    if ((dx < 5) && (dy < 5)) {
			LatLonPoint llp = projection.inverse(e.getPoint());

			boolean shift = e.isShiftDown();
			boolean control = e.isControlDown();
			boolean notLeftButton = (e.getModifiers() & InputEvent.BUTTON1_MASK) == 0;
			if (control) {
			    if (shift) {
				p.setScale(p.getScale() * 2.0f);
			    } else {
				p.setScale(p.getScale() / 2.0f);
			    }
			}

			p.setCenter(llp);
			map.setProjection(p);
		    }
		    return;
		}

		// Figure out the new scale
		float newScale = 
		    com.bbn.openmap.proj.ProjMath.getScale(point1,
							   point2,
							   projection);

		// Figure out the center of the rectangle
		int centerx = Math.min(point1.x, point2.x) + dx/2;
		int centery = Math.min(point1.y, point2.y) + dy/2;
		com.bbn.openmap.LatLonPoint center = projection.inverse(centerx, 
									centery);

		// Fire events on main map to change view to match rect1
		// 	  System.out.println("point1: " +point1);
		// 	  System.out.println("point2: " +point2);
		//        System.out.println("Centerx: " +centerx + 
		//             " Centery: " + centery);
//		System.out.println("New Scale: " + newScale);
//		System.out.println("New Center: " +center);

		// Set the parameters of the projection and then set
		// the projection of the map.  This way we save having
		// the MapBean fire two ProjectionEvents.
		p.setScale(newScale);
		p.setCenter(center);
		map.setProjection(p);
	    }
	    // reset the points
	    point1 = null;
	    point2 = null;
	}
    }

    /**
     * Handle a mouseEntered MouseListener event. The boolean autoZoom
     * is set to true, which will make the delegate ask the map to
     * zoom in to a box that is drawn.
     * @param e MouseEvent to be handled
     */
    public void mouseEntered(MouseEvent e) {
	if (Debug.debugging("mousemodedetail")) {
	    System.out.println(getID()+"|NavMouseMode.mouseEntered()");
 	}
	if (! mouseSupport.fireMapMouseEntered(e)) {
	    autoZoom = true;
	}
    }

    /**
     * Handle a mouseExited MouseListener event. The boolean autoZoom
     * is set to false, which will cause the delegate to NOT ask the
     * map to zoom in on a box.  If a box is being drawn, it will be
     * erased.  The point1 is kept in case the mouse comes back on the
     * screen with the button still down.  Then, a new box will be
     * drawn with the original mouse press position.
     * @param e MouseEvent to be handled
     */
    public void mouseExited(MouseEvent e) {
	if (Debug.debugging("mousemodedetail")) {
	    System.out.println(getID()+"|NavMouseMode.mouseExited()");
 	}
	if (! mouseSupport.fireMapMouseExited(e) && 
	    e.getSource() instanceof MapBean) {
	    // don't zoom in, because the mouse is off the window.
	    autoZoom = false;
	    // clean up the last box drawn
	    paintRectangle((MapBean)e.getSource(), point1, point2);
	    // set the second point to null so that a new box will be
	    // drawn if the mouse comes back, and the box will use the old
	    // starting point, if the mouse button is still down.
	    point2 = null;
	}
    }

    // Mouse Motion Listener events
    ///////////////////////////////

    /**
     * Handle a mouseDragged MouseMotionListener event. A rectangle is
     * drawn from the mousePressed point, since I'm assuming that I'm
     * drawing a box to zoom the map to.  If a previous box was drawn,
     * it is erased.
     * @param e MouseEvent to be handled
     */
    public void mouseDragged(MouseEvent e){
	if (Debug.debugging("mousemodedetail")) {
	    System.out.println(getID()+"|NavMouseMode.mouseDragged()");
 	}
	if (! mouseSupport.fireMapMouseDragged(e) && 
	    e.getSource() instanceof MapBean) {
	    if (!autoZoom) return;

	    // clean up the old rectangle, since point2 has the old value.
	    paintRectangle((MapBean)e.getSource(), point1, point2);	
	    // paint new rectangle
//  	    point2 = e.getPoint();
	    point2 = getRatioPoint((MapBean)e.getSource(), 
				   point1, e.getPoint());
	    paintRectangle((MapBean)e.getSource(), point1, point2);
	}
	fireMouseLocation(e);
    }

    /**
     * Handle a mouseMoved MouseMotionListener event. Nothing happens.
     * @param e MouseEvent to be handled
     */
    public void mouseMoved(MouseEvent e){
	if (Debug.debugging("mousemodedetail")) {
	    System.out.println(getID()+"|NavMouseMode.mouseMoved()");
 	}
	mouseSupport.fireMapMouseMoved(e);
	fireMouseLocation(e);
    }

    /**
     * Given a MapBean, which provides the projection, and the
     * starting point of a box (pt1), look at pt2 to see if it
     * represents the ratio of the projection map size.  If it
     * doesn't, provide a point that does.
     */
    protected Point getRatioPoint(MapBean map, Point pt1, Point pt2) {
	Projection proj = map.getProjection();
	float mapRatio = (float)proj.getHeight()/(float)proj.getWidth();

	float boxHeight = (float)(pt1.y - pt2.y);
	float boxWidth = (float)(pt1.x - pt2.x);
	float boxRatio = Math.abs(boxHeight/boxWidth);
	int isNegative = -1;
	if (boxRatio > mapRatio) {
	    // box is too tall, adjust boxHeight
	    if (boxHeight < 0) isNegative = 1;
	    boxHeight = Math.abs(mapRatio*boxWidth);
	    pt2.y = pt1.y + (isNegative*(int)boxHeight);

	} else if (boxRatio < mapRatio) {
	    // box is too wide, adjust boxWidth
	    if (boxWidth < 0) isNegative = 1;
	    boxWidth = Math.abs(boxHeight/mapRatio);
	    pt2.x = pt1.x + (isNegative*(int)boxWidth);
	}
	return pt2;
    }

    /**
     * Draws or erases boxes between two screen pixel points.  The
     * graphics from the map is set to XOR mode, and this method uses
     * two colors to make the box disappear if on has been drawn at
     * these coordinates, and the box to appear if it hasn't.
     * @param pt1 one corner of the box to drawn, in window pixel
     * coordinates.
     * @param pt2 the opposite corner of the box.
     */
    protected void paintRectangle(MapBean map, Point pt1, Point pt2){
	Graphics g = map.getGraphics();
	g.setXORMode(java.awt.Color.lightGray);
	g.setColor(java.awt.Color.darkGray);

	if (pt1 != null && pt2 != null){
	    int width = Math.abs(pt2.x - pt1.x);
	    int height = Math.abs(pt2.y - pt1.y);

	    if (width == 0) width++;
	    if (height == 0) height++;

	    g.drawRect(pt1.x < pt2.x ? pt1.x : pt2.x, 
		       pt1.y < pt2.y ? pt1.y : pt2.y, 
		       width, height);
	    g.drawRect(pt1.x < pt2.x ? pt1.x + (pt2.x - pt1.x)/2 - 1 : 
		       pt2.x + (pt1.x - pt2.x)/2 - 1, 
		       pt1.y < pt2.y ? pt1.y + (pt2.y - pt1.y)/2 - 1 : 
		       pt2.y + (pt1.y - pt2.y)/2 - 1, 2, 2);
	}
    }
}