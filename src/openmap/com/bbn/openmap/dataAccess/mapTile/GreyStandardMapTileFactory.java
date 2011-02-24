package com.bbn.openmap.dataAccess.mapTile;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.logging.Level;

import com.bbn.openmap.image.BufferedImageHelper;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.util.cacheHandler.CacheObject;

/**
 * A StandardMapTileFactory that converts the tiles to greyscale for display.
 * 
 * @author ddietrick
 */
public class GreyStandardMapTileFactory
        extends StandardMapTileFactory {

    /**
     * 
     */
    public GreyStandardMapTileFactory() {
        super();
    }

    public GreyStandardMapTileFactory(Component layer, String rootDir, String tileFileExt) {
        super(layer, rootDir, tileFileExt);
    }

    /**
     * Overriding the method that creates empty tiles for places with no
     * coverage. We need to set the no-coverage attributes for the
     * EmptyTileHandler (if it's a SimpleEmptyTileHandler) to null, so it
     * doesn't create empty tiles when it's beyond the coverage zoom level
     * limit. Those tiles are normally returned as clear, but in the conversion
     * to greyscale they turn black.
     * 
     * @param key the cache key for this object
     * @param x the uv x coordinate of the tile
     * @param y the uv y coordinate of the tile
     * @param zoomLevel the zoom level for the tile
     * @param proj the projection being used for the map.
     * @return CacheObject, or null if the empty tile should be blank.
     */
    public CacheObject getEmptyTile(Object key, int x, int y, int zoomLevel, Projection proj) {
        EmptyTileHandler empTileHandler = getEmptyTileHandler();
        if (empTileHandler instanceof SimpleEmptyTileHandler) {
            ((SimpleEmptyTileHandler) empTileHandler).setNoCoverageAtts(null);
        }

        return super.getEmptyTile(key, x, y, zoomLevel, proj);
    }

    /**
     * Callback method that converts tiles to greyscale.
     * 
     * @param origImage Any java Image
     * @param imageWidth pixel width
     * @param imageHeight pixel height
     * @return BufferedImage with any changes necessary.
     * @throws InterruptedException
     */
    protected BufferedImage preprocessImage(Image origImage, int imageWidth, int imageHeight)
            throws InterruptedException {

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = image.getGraphics();
        g.drawImage(origImage, 0, 0, null);
        g.dispose();
        return image;
    }

}