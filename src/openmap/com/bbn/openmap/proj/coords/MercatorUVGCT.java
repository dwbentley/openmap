package com.bbn.openmap.proj.coords;

import java.awt.geom.Point2D;

/**
 * Convert between mercator uv coordinates that are used for map tiles and
 * lat/lon degrees. Setting the zoom level sets the number of expected pixels in
 * the Mercator uv projection in both directions.
 */
public class MercatorUVGCT
      extends AbstractGCT
      implements GeoCoordTransformation {

   int zoomLevel;

   public MercatorUVGCT(int zoomLevel) {
      this.zoomLevel = zoomLevel;
   }

   public Point2D forward(double lat, double lon, Point2D ret) {
      if (ret == null) {
         ret = new Point2D.Double();
      }

      ret.setLocation(((lon + 180) / 360.0 * Math.pow(2, zoomLevel)), ((1.0 - Math.log(Math.tan(lat * Math.PI / 180.0)
            + (1.0 / Math.cos(lat * Math.PI / 180.0)))
            / Math.PI) / 2.0 * (Math.pow(2, zoomLevel))));
      return ret;
   }

   public LatLonPoint inverse(double uvx, double uvy, LatLonPoint ret) {
      if (ret == null) {
         ret = new LatLonPoint.Double();
      }

      ret.setLocation(360 / Math.pow(2, zoomLevel) * uvx - 180, -90 + 360 / Math.PI
            * Math.atan(Math.exp((-2 * Math.PI * uvy) / Math.pow(2, zoomLevel) + Math.PI)));
      return ret;
   }

   public int getZoomLevel() {
      return zoomLevel;
   }

   public void setZoomLevel(int zoomLevel) {
      this.zoomLevel = zoomLevel;
   }

}