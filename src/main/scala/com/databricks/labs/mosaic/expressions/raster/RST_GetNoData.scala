package com.databricks.labs.mosaic.expressions.raster

import com.databricks.labs.mosaic.core.types.model.MosaicRasterTile
import com.databricks.labs.mosaic.expressions.base.{GenericExpressionFactory, WithExpressionInfo}
import com.databricks.labs.mosaic.expressions.raster.base.RasterExpression
import com.databricks.labs.mosaic.functions.MosaicExpressionConfig
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry.FunctionBuilder
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, NullIntolerant}
import org.apache.spark.sql.types.{ArrayType, DoubleType}

/** The expression for extracting the no data value of a raster. */
case class RST_GetNoData(
    rastersExpr: Expression,
    expressionConfig: MosaicExpressionConfig
) extends RasterExpression[RST_GetNoData](
      rastersExpr,
      ArrayType(DoubleType),
      returnsRaster = false,
      expressionConfig = expressionConfig
    )
      with NullIntolerant
      with CodegenFallback {

    /**
      * Extracts the no data value of a raster.
      *
      * @param tile
      *   The raster to be used.
      * @return
      *   The no data value of the raster.
      */
    override def rasterTransform(tile: => MosaicRasterTile): Any = {
        tile.getRaster.getBands.map(_.noDataValue)
    }

}

/** Expression info required for the expression registration for spark SQL. */
object RST_GetNoData extends WithExpressionInfo {

    override def name: String = "rst_get_no_data"

    override def usage: String =
        """
          |_FUNC_(expr1) - Returns a raster clipped by provided vector.
          |""".stripMargin

    override def example: String =
        """
          |    Examples:
          |      > SELECT _FUNC_(raster_tile);
          |        {index_id, clipped_raster, parentPath, driver}
          |        {index_id, clipped_raster, parentPath, driver}
          |        ...
          |  """.stripMargin

    override def builder(expressionConfig: MosaicExpressionConfig): FunctionBuilder = {
        GenericExpressionFactory.getBaseBuilder[RST_GetNoData](1, expressionConfig)
    }

}
