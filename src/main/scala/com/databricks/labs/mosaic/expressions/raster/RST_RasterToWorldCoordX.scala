package com.databricks.labs.mosaic.expressions.raster

import com.databricks.labs.mosaic.core.raster.api.GDAL
import com.databricks.labs.mosaic.core.raster.gdal.MosaicRasterGDAL
import com.databricks.labs.mosaic.expressions.base.{GenericExpressionFactory, WithExpressionInfo}
import com.databricks.labs.mosaic.expressions.raster.base.Raster2ArgExpression
import com.databricks.labs.mosaic.functions.MosaicExpressionConfig
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry.FunctionBuilder
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, NullIntolerant}
import org.apache.spark.sql.types._

/** Returns the world coordinates of the raster (x,y) pixel. */
case class RST_RasterToWorldCoordX(
    raster: Expression,
    x: Expression,
    y: Expression,
    expressionConfig: MosaicExpressionConfig
) extends Raster2ArgExpression[RST_RasterToWorldCoordX](raster, x, y, DoubleType, returnsRaster = false, expressionConfig)
      with NullIntolerant
      with CodegenFallback {

    /**
      * Returns the world coordinates of the raster x pixel by applying
      * GeoTransform. This ensures the projection of the raster is respected.
      */
    override def rasterTransform(raster: => MosaicRasterGDAL, arg1: Any, arg2: Any): Any = {
        val x = arg1.asInstanceOf[Int]
        val y = arg2.asInstanceOf[Int]
        val gt = raster.getRaster.GetGeoTransform()

        val (xGeo, _) = GDAL.toWorldCoord(gt, x, y)
        xGeo
    }

}

/** Expression info required for the expression registration for spark SQL. */
object RST_RasterToWorldCoordX extends WithExpressionInfo {

    override def name: String = "rst_rastertoworldcoordx"

    override def usage: String =
        """
          |_FUNC_(expr1, expr2, expr3) - Returns the x coordinate of the pixel in world coordinates using geo transform of the raster.
          |""".stripMargin

    override def example: String =
        """
          |    Examples:
          |      > SELECT _FUNC_(raster_tile, x, y);
          |        11.2
          |  """.stripMargin

    override def builder(expressionConfig: MosaicExpressionConfig): FunctionBuilder = {
        GenericExpressionFactory.getBaseBuilder[RST_RasterToWorldCoordX](3, expressionConfig)
    }

}
