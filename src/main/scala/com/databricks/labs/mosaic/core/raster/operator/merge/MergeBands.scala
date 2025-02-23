package com.databricks.labs.mosaic.core.raster.operator.merge

import com.databricks.labs.mosaic.core.raster.gdal.MosaicRasterGDAL
import com.databricks.labs.mosaic.core.raster.io.RasterCleaner.dispose
import com.databricks.labs.mosaic.core.raster.operator.gdal.{GDALBuildVRT, GDALTranslate}
import com.databricks.labs.mosaic.utils.PathUtils

/** MergeBands is a helper object for merging raster bands. */
object MergeBands {

    /**
      * Merges the raster bands into a single raster.
      *
      * @param rasters
      *   The rasters to merge.
      * @param resampling
      *   The resampling method to use.
      * @return
      *   A MosaicRaster object.
      */
    def merge(rasters: => Seq[MosaicRasterGDAL], resampling: String): MosaicRasterGDAL = {
        val rasterUUID = java.util.UUID.randomUUID.toString
        val outShortName = rasters.head.getRaster.GetDriver.getShortName

        val vrtPath = PathUtils.createTmpFilePath(rasterUUID, "vrt")
        val rasterPath = PathUtils.createTmpFilePath(rasterUUID, "tif")

        val vrtRaster = GDALBuildVRT.executeVRT(
          vrtPath,
          isTemp = true,
          rasters,
          command = s"gdalbuildvrt -separate -resolution highest"
        )

        val result = GDALTranslate.executeTranslate(
          rasterPath,
          isTemp = true,
          vrtRaster,
          command = s"gdal_translate -r $resampling -of $outShortName -co COMPRESS=DEFLATE"
        )

        dispose(vrtRaster)

        result
    }

    /**
      * Merges the raster bands into a single raster. This method allows for
      * custom pixel sizes.
      *
      * @param rasters
      *   The rasters to merge.
      * @param pixel
      *   The pixel size to use.
      * @param resampling
      *   The resampling method to use.
      * @return
      *   A MosaicRaster object.
      */
    def merge(rasters: => Seq[MosaicRasterGDAL], pixel: (Double, Double), resampling: String): MosaicRasterGDAL = {
        val rasterUUID = java.util.UUID.randomUUID.toString
        val outShortName = rasters.head.getRaster.GetDriver.getShortName

        val vrtPath = PathUtils.createTmpFilePath(rasterUUID, "vrt")
        val rasterPath = PathUtils.createTmpFilePath(rasterUUID, "tif")

        val vrtRaster = GDALBuildVRT.executeVRT(
          vrtPath,
          isTemp = true,
          rasters,
          command = s"gdalbuildvrt -separate -resolution user -tr ${pixel._1} ${pixel._2}"
        )

        val result = GDALTranslate.executeTranslate(
          rasterPath,
          isTemp = true,
          vrtRaster,
          command = s"gdalwarp -r $resampling -of $outShortName -co COMPRESS=DEFLATE -overwrite"
        )

        dispose(vrtRaster)

        result
    }

}
