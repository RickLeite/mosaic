package com.databricks.labs.mosaic.core.raster.operator.retile

import com.databricks.labs.mosaic.core.raster.api.GDAL
import com.databricks.labs.mosaic.core.raster.io.RasterCleaner.dispose
import com.databricks.labs.mosaic.core.raster.operator.gdal.GDALTranslate
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.utils.PathUtils

import scala.collection.immutable

/** OverlappingTiles is a helper object for retiling rasters. */
object OverlappingTiles {

    /**
      * Retiles a raster into overlapping tiles.
      * @note
      *   The overlap percentage is a percentage of the tile size.
      *
      * @param tile
      *   The raster to retile.
      * @param tileWidth
      *   The width of the tiles.
      * @param tileHeight
      *   The height of the tiles.
      * @param overlapPercentage
      *   The percentage of overlap between tiles.
      * @return
      *   A sequence of MosaicRasterTile objects.
      */
    def reTile(
        tile: => MosaicRasterTile,
        tileWidth: Int,
        tileHeight: Int,
        overlapPercentage: Int
    ): immutable.Seq[MosaicRasterTile] = {
        val raster = tile.getRaster
        val (xSize, ySize) = raster.getDimensions

        val overlapWidth = Math.ceil(tileWidth * overlapPercentage / 100.0).toInt
        val overlapHeight = Math.ceil(tileHeight * overlapPercentage / 100.0).toInt

        val tiles = for (i <- 0 until xSize by (tileWidth - overlapWidth)) yield {
            for (j <- 0 until ySize by (tileHeight - overlapHeight)) yield {
                val xOff = i
                val yOff = j
                val width = Math.min(tileWidth, xSize - i)
                val height = Math.min(tileHeight, ySize - j)

                val uuid = java.util.UUID.randomUUID.toString
                val fileExtension = GDAL.getExtension(tile.getDriver)
                val rasterPath = PathUtils.createTmpFilePath(uuid, fileExtension)
                val shortName = raster.getRaster.GetDriver.getShortName

                val result = GDALTranslate.executeTranslate(
                  rasterPath,
                  isTemp = true,
                  raster,
                  command = s"gdal_translate -of $shortName -srcwin $xOff $yOff $width $height"
                )

                val isEmpty = result.isEmpty

                if (isEmpty) dispose(result)

                (isEmpty, result)
            }
        }

        // TODO: The rasters should not be passed by objects.

        val (_, valid) = tiles.flatten.partition(_._1)

        valid.map(t => new MosaicRasterTile(null, t._2, raster.getParentPath, raster.getDriversShortName))

    }

}
