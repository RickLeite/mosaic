package com.databricks.labs.mosaic.core.raster.operator.retile

import com.databricks.labs.mosaic.core.raster.io.RasterCleaner.dispose
import com.databricks.labs.mosaic.core.raster.operator.gdal.GDALTranslate
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.utils.PathUtils

/** ReTile is a helper object for retiling rasters. */
object ReTile {

    /**
      * Retiles a raster into tiles. Empty tiles are discarded. The tile size is
      * specified by the user via the tileWidth and tileHeight parameters.
      *
      * @param tile
      *   The raster to retile.
      * @param tileWidth
      *   The width of the tiles.
      * @param tileHeight
      *   The height of the tiles.
      * @return
      *   A sequence of MosaicRasterTile objects.
      */
    def reTile(
        tile: => MosaicRasterTile,
        tileWidth: Int,
        tileHeight: Int
    ): Seq[MosaicRasterTile] = {
        val raster = tile.getRaster
        val (xR, yR) = raster.getDimensions
        val xTiles = Math.ceil(xR / tileWidth).toInt
        val yTiles = Math.ceil(yR / tileHeight).toInt

        val tiles = for (x <- 0 until xTiles; y <- 0 until yTiles) yield {
            val xMin = if (x == 0) x * tileWidth else x * tileWidth - 1
            val yMin = if (y == 0) y * tileHeight else y * tileHeight - 1
            val xOffset = if (xMin + tileWidth + 1 > xR) xR - xMin else tileWidth + 1
            val yOffset = if (yMin + tileHeight + 1 > yR) yR - yMin else tileHeight + 1

            val rasterUUID = java.util.UUID.randomUUID.toString
            val fileExtension = raster.getRasterFileExtension
            val rasterPath = PathUtils.createTmpFilePath(rasterUUID, fileExtension)
            val shortDriver = raster.getDriversShortName

            val result = GDALTranslate.executeTranslate(
              rasterPath,
              isTemp = true,
              raster,
              command = s"gdal_translate -of $shortDriver -srcwin $xMin $yMin $xOffset $yOffset -co COMPRESS=DEFLATE"
            )

            val isEmpty = result.isEmpty

            if (isEmpty) dispose(result)

            (isEmpty, result)

        }

        val (_, valid) = tiles.partition(_._1)

        valid.map(t => new MosaicRasterTile(null, t._2, raster.getParentPath, raster.getDriversShortName))

    }

}
