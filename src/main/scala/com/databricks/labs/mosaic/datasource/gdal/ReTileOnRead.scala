package com.databricks.labs.mosaic.datasource.gdal

import com.databricks.labs.mosaic.core.index.{IndexSystem, IndexSystemFactory}
import com.databricks.labs.mosaic.core.raster.gdal.MosaicRasterGDAL
import com.databricks.labs.mosaic.core.raster.io.RasterCleaner
import com.databricks.labs.mosaic.core.raster.operator.retile.BalancedSubdivision
import com.databricks.labs.mosaic.core.types.RasterTileType
import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.datasource.Utils
import com.databricks.labs.mosaic.datasource.gdal.GDALFileFormat._
import org.apache.hadoop.fs.{FileStatus, FileSystem}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.types._

/** An object defining the retiling read strategy for the GDAL file format. */
object ReTileOnRead extends ReadStrategy {

    // noinspection DuplicatedCode
    /**
      * Returns the schema of the GDAL file format.
      * @note
      *   Different read strategies can have different schemas.
      *
      * @param options
      *   Options passed to the reader.
      * @param files
      *   List of files to read.
      * @param parentSchema
      *   Parent schema.
      * @param sparkSession
      *   Spark session.
      *
      * @return
      *   Schema of the GDAL file format.
      */
    override def getSchema(
        options: Map[String, String],
        files: Seq[FileStatus],
        parentSchema: StructType,
        sparkSession: SparkSession
    ): StructType = {
        val trimmedSchema = parentSchema.filter(field => field.name != CONTENT && field.name != LENGTH)
        val indexSystem = IndexSystemFactory.getIndexSystem(sparkSession)
        StructType(trimmedSchema)
            .add(StructField(UUID, LongType, nullable = false))
            .add(StructField(X_SIZE, IntegerType, nullable = false))
            .add(StructField(Y_SIZE, IntegerType, nullable = false))
            .add(StructField(BAND_COUNT, IntegerType, nullable = false))
            .add(StructField(METADATA, MapType(StringType, StringType), nullable = false))
            .add(StructField(SUBDATASETS, MapType(StringType, StringType), nullable = false))
            .add(StructField(SRID, IntegerType, nullable = false))
            .add(StructField(LENGTH, LongType, nullable = false))
            .add(StructField(TILE, RasterTileType(indexSystem.getCellIdDataType), nullable = false))
    }

    /**
      * Reads the content of the file.
      * @param status
      *   File status.
      * @param fs
      *   File system.
      * @param requiredSchema
      *   Required schema.
      * @param options
      *   Options passed to the reader.
      * @param indexSystem
      *   Index system.
      *
      * @return
      *   Iterator of internal rows.
      */
    override def read(
        status: FileStatus,
        fs: FileSystem,
        requiredSchema: StructType,
        options: Map[String, String],
        indexSystem: IndexSystem
    ): Iterator[InternalRow] = {
        val inPath = status.getPath.toString
        val uuid = getUUID(status)
        val sizeInMB = options.getOrElse("sizeInMB", "16").toInt

        val tiles = localSubdivide(inPath, sizeInMB)

        val rows = tiles.map(tile => {
            val trimmedSchema = StructType(requiredSchema.filter(field => field.name != TILE))
            val fields = trimmedSchema.fieldNames.map {
                case PATH              => status.getPath.toString
                case MODIFICATION_TIME => status.getModificationTime
                case UUID              => uuid
                case X_SIZE            => tile.getRaster.xSize
                case Y_SIZE            => tile.getRaster.ySize
                case BAND_COUNT        => tile.getRaster.numBands
                case METADATA          => tile.getRaster.metadata
                case SUBDATASETS       => tile.getRaster.subdatasets
                case SRID              => tile.getRaster.SRID
                case LENGTH            => tile.getRaster.getMemSize
                case other             => throw new RuntimeException(s"Unsupported field name: $other")
            }
            // Writing to bytes is destructive so we delay reading content and content length until the last possible moment
            val row = Utils.createRow(fields ++ Seq(tile.formatCellId(indexSystem).serialize()))
            RasterCleaner.dispose(tile)
            row
        })

        rows.iterator
    }

    /**
      * Subdivides a raster into tiles of a given size.
      * @param inPath
      *   Path to the raster.
      * @param sizeInMB
      *   Size of the tiles in MB.
      *
      * @return
      *   A tuple of the raster and the tiles.
      */
    def localSubdivide(inPath: String, sizeInMB: Int): Seq[MosaicRasterTile] = {
        val raster = MosaicRasterGDAL.readRaster(inPath, inPath)
        val inTile = new MosaicRasterTile(null, raster, inPath, raster.getDriversShortName)
        val tiles = BalancedSubdivision.splitRaster(inTile, sizeInMB)
        RasterCleaner.dispose(raster)
        RasterCleaner.dispose(inTile)
        tiles
    }

}
