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
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/layer/shape/ESRIPolygonRecord.java,v $
// $RCSfile: ESRIPolygonRecord.java,v $
// $Revision: 1.1.1.1 $
// $Date: 2003/02/14 21:35:48 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.layer.shape;

import java.io.IOException;
import com.bbn.openmap.omGraphics.*;
import com.bbn.openmap.omGraphics.geom.*;
import com.bbn.openmap.proj.ProjMath;

/**
 * The Polygon record type.  This class implements the ESRI Shapefile
 * polygon AND arc/polyline record types.
 * @author Ray Tomlinson
 * @author Tom Mitchell <tmitchell@bbn.com>
 * @author HACK-author blame it on aculline
 * @version $Revision: 1.1.1.1 $ $Date: 2003/02/14 21:35:48 $
 */
public class ESRIPolygonRecord extends ESRIRecord {

    /** Polygon or arc/polyline?. */
    protected int shapeType = SHAPE_TYPE_POLYGON;

    /** The bounding box. */ 
    public ESRIBoundingBox bounds;

    /** An array of polygons. */ 
    public ESRIPoly[] polygons;

    public ESRIPolygonRecord() {
	bounds = new ESRIBoundingBox();
	polygons = new ESRIPoly[0];
    }

    /**
     * Initialize a polygon record from the given buffer.
     *
     * @param b the buffer
     * @param off the offset into the buffer where the data starts
     */
    public ESRIPolygonRecord(byte b[], int off) throws IOException {
	super(b, off);

	int ptr = off+8;

	shapeType = readLEInt(b, ptr);
	ptr += 4;
	if ((shapeType != SHAPE_TYPE_POLYGON) && (shapeType != SHAPE_TYPE_ARC)) {
	    throw new IOException("Invalid polygon record.  Expected shape " +
				  "type " + SHAPE_TYPE_POLYGON + " or type " +
				  SHAPE_TYPE_ARC + ", but found " + shapeType);
	}
	boolean ispolyg = isPolygon();

	bounds = readBox(b, ptr);
	ptr += 32;		// A box is 4 doubles (4 x 8bytes)

	int numParts = readLEInt(b, ptr);
	ptr += 4;

	int numPoints = readLEInt(b, ptr);
	ptr += 4;

	if (numParts <= 0) return;

	polygons = new ESRIPoly[numParts];
	int origin = 0;
	int _len;
	for (int i = 0; i < numParts; i++) {

	    int nextOrigin = readLEInt(b, ptr);
	    ptr += 4;

	    if (i > 0) {
		_len = nextOrigin - origin;
		if (ispolyg) ++_len;//connect pairs
		polygons[i-1] = new ESRIPoly.ESRIFloatPoly(_len);
	    }
	    origin = nextOrigin;
	}
	_len = numPoints - origin;
	if (ispolyg) ++_len;//connect pairs
	polygons[numParts-1] = new ESRIPoly.ESRIFloatPoly(_len);
	for (int i = 0; i < numParts; i++) {
	    ptr += polygons[i].read(b, ptr, ispolyg);
	}
    }

    /**
     * Is this a polygon or a arc/polyline?
     * @return boolean
     */
    public boolean isPolygon() {
	return shapeType == SHAPE_TYPE_POLYGON;
    }

    /**
     * Set the poly type (polygon or arc/polyline).
     */
    public void setPolygon(boolean isPolygon) {
	shapeType = isPolygon ? SHAPE_TYPE_POLYGON : SHAPE_TYPE_ARC;
    }

    /**
     * Add a poly to the record.
     * @param radians coordinates: y,x,y,x,... (lat,lon) order in
     * RADIANS!
     */
    public void add(float radians[]) {
	ESRIPoly newPoly = new ESRIPoly.ESRIFloatPoly(radians);

	int numParts = polygons.length;
	ESRIPoly oldPolys[] = polygons;
	polygons = new ESRIPoly[numParts + 1];
	for (int i = 0; i < numParts; i++) {
	    polygons[i] = oldPolys[i];
	}

	polygons[numParts] = newPoly;

	int len = radians.length;
	for (int i=0; i<len; i+=2) {
	    // REMEMBER: switch to x,y order
	    bounds.addPoint(
		    ProjMath.radToDeg(radians[i+1]),//x (lon)
		    ProjMath.radToDeg(radians[i]));//y (lat)
	}
    }

    /**
     * Generates 2D OMGraphics and adds them to the given list.  If
     * you are using jdk1.1.X, you'll have to comment out this method,
     * because jdk1.1.X doesn't know about the java.awt.Stroke and
     * java.awt.Paint interfaces.
     *
     * @param list the graphics list
     * @param drawingAttributes the drawingAttributes to paint the poly.
     */
    public void addOMGraphics(OMGraphicList list,
			      DrawingAttributes drawingAttributes) {

	int nPolys = polygons.length;
	if (nPolys <= 0) return;
	OMPoly p=null;
	float[] pts;
	boolean ispolyg = isPolygon();
	OMGraphicList sublist = null;
	
	if (nPolys > 1) {
	    sublist = new OMGraphicList(10);
	    list.add(sublist);
	    sublist.setAppObject(new Integer(getRecordNumber())); 
	}

	for (int i=0, j, k; i<nPolys; i++) {
	    // these points are already in RADIAN lat,lon order!...
	    pts = ((ESRIPoly.ESRIFloatPoly)polygons[i]).getRadians();
	    int len = pts.length;
	    p = new OMPoly(pts,
			   OMGraphic.RADIANS,
			   OMGraphic.LINETYPE_STRAIGHT); 

	    drawingAttributes.setTo(p);
	    if (!ispolyg) {
		p.setIsPolygon(false);
	    }

	    if (sublist != null) {
		sublist.add(p);
	    } else {
		// There should be only one.
		p.setAppObject(new Integer(getRecordNumber())); 
		list.add(p);
	    }
	}
    }

    /**
     * Generates OMGeometry and adds them to the given list. 
     *
     * @param list the geometry list
     */
    public OMGeometry addOMGeometry(OMGeometryList list) {

	int nPolys = polygons.length;
	if (nPolys <= 0) {
	    return null;
	}

	float[] pts;
	boolean ispolyg = isPolygon();
	OMGeometry geom = null;

	for (int i=0, j, k; i<nPolys; i++) {
	    // these points are already in RADIAN lat,lon order!...
	    pts = ((ESRIPoly.ESRIFloatPoly)polygons[i]).getRadians();
	    int len = pts.length;
	    if (ispolyg) {
		
		geom = new PolygonGeometry.LL(pts,
					      OMGraphic.RADIANS,
					      OMGraphic.LINETYPE_STRAIGHT);
	    } else {
		geom = new PolylineGeometry.LL(pts,
					       OMGraphic.RADIANS,
					       OMGraphic.LINETYPE_STRAIGHT);
	    }
	    list.add(geom);
	}
	return geom;
    }

    /**
     * Gets this record's bounding box.
     *
     * @return a bounding box
     */
    public ESRIBoundingBox getBoundingBox() {
	return bounds;
    }

    /**
     * Gets this record's shape type as an int.  Shape types
     * are enumerated on the ShapeUtils class.
     *
     * @return the shape type as an int (either SHAPE_TYPE_POLYGON or
     * SHAPE_TYPE_ARC)
     */
    public int getShapeType() {
	return shapeType;
    }

    /**
     * Yields the length of this record's data portion.
     * <p>
     * (44 + (numParts * 4) + (numPoints * 16))
     * <br>
     * 3 Integers + 4 doubles == 3 * 4bytes + 4 * 8bytes == 12 + 32 == 44.
     *
     * @return number of bytes equal to the size of this record's data
     */
    public int getRecordLength() {
	int numParts = polygons.length;
	int numPoints = 0;
	for (int i = 0; i < numParts; i++) {
	    numPoints += polygons[i].nPoints;
	}
	return (44 + (numParts * 4) + (numPoints * 16));
    }

    /**
     * Writes this polygon to the given buffer at the given offset.
     *
     * @param b the buffer
     * @param off the offset
     * @return the number of bytes written
     */
    public int write(byte[] b, int off) {
	int nBytes = super.write(b, off);
	nBytes += writeLEInt(b, off + nBytes, shapeType);
	// bounds
	nBytes += writeBox(b, off + nBytes, bounds);
	// numparts
	int numParts = polygons.length;
	nBytes += writeLEInt(b, off + nBytes, numParts);
	// numpoints
	int numPoints = 0;
	for (int i = 0; i < numParts; i++) {
	    numPoints += polygons[i].nPoints;
	}
	nBytes += writeLEInt(b, off + nBytes, numPoints);
	// parts
	int ptr = 0;
	for (int i = 0; i < numParts; i++) {
	    nBytes += writeLEInt(b, off + nBytes, ptr);
	    ptr += polygons[i].nPoints;
	}

	// points
	for (int i = 0; i < numParts; i++) {
	    // REMEMBER: stored internally as y,x order (lat,lon order)
	    float[] pts = ((ESRIPoly.ESRIFloatPoly)polygons[i]).getRadians();
	    int nPts = pts.length;
	    for (int j=0; j<nPts; j+=2) {
		nBytes += writeLEDouble(
			b, off + nBytes, (double)ProjMath.radToDeg(pts[j+1]));//x (lon)
		nBytes += writeLEDouble(
			b, off + nBytes, (double)ProjMath.radToDeg(pts[j]));//y (lat)
	    }
	}

	// return number of bytes written
	return nBytes;
    }
}