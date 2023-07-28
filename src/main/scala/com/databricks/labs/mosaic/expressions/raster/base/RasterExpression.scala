package com.databricks.labs.mosaic.expressions.raster.base

import com.databricks.labs.mosaic.core.raster.MosaicRaster
import com.databricks.labs.mosaic.core.raster.api.RasterAPI
import com.databricks.labs.mosaic.core.raster.gdal_raster.RasterCleaner
import com.databricks.labs.mosaic.expressions.base.GenericExpressionFactory
import com.databricks.labs.mosaic.functions.MosaicExpressionConfig
import org.apache.spark.sql.catalyst.expressions.{Expression, NullIntolerant, UnaryExpression}
import org.apache.spark.sql.types.DataType

import scala.reflect.ClassTag

/**
  * Base class for all raster expressions that take no arguments. It provides
  * the boilerplate code needed to create a function builder for a given
  * expression. It minimises amount of code needed to create a new expression.
  * @param rasterExpr
  *   The expression for the raster. If the raster is stored on disc, the path
  *   to the raster is provided. If the raster is stored in memory, the bytes of
  *   the raster are provided.
  * @param outputType
  *   The output type of the result.
  * @param expressionConfig
  *   Additional arguments for the expression (expressionConfigs).
  * @tparam T
  *   The type of the extending class.
  */
abstract class RasterExpression[T <: Expression: ClassTag](
    rasterExpr: Expression,
    outputType: DataType,
    returnsRaster: Boolean,
    expressionConfig: MosaicExpressionConfig
) extends UnaryExpression
      with NullIntolerant
      with Serializable
      with RasterExpressionSerialization {

    /**
      * The raster API to be used. Enable the raster so that subclasses dont
      * need to worry about this.
      */
    protected val rasterAPI: RasterAPI = RasterAPI(expressionConfig.getRasterAPI)
    rasterAPI.enable()

    override def child: Expression = rasterExpr

    /** Output Data Type */
    override def dataType: DataType = outputType

    /**
      * The function to be overridden by the extending class. It is called when
      * the expression is evaluated. It provides the raster to the expression.
      * It abstracts spark serialization from the caller.
      * @param raster
      *   The raster to be used.
      * @return
      *   The result of the expression.
      */
    def rasterTransform(raster: MosaicRaster): Any

    /**
      * Evaluation of the expression. It evaluates the raster path and the loads
      * the raster from the path. It handles the clean up of the raster before
      * returning the results.
      * @param input
      *   The input raster as either a path or bytes.
      *
      * @return
      *   The result of the expression.
      */
    override def nullSafeEval(input: Any): Any = {
        val raster = rasterAPI.readRaster(input, rasterExpr.dataType)
        val result = rasterTransform(raster)
        val serialized = serialize(result, returnsRaster, dataType, rasterAPI, expressionConfig)
        RasterCleaner.dispose(raster)
        RasterCleaner.dispose(result)
        serialized
    }

    override def makeCopy(newArgs: Array[AnyRef]): Expression = GenericExpressionFactory.makeCopyImpl[T](this, newArgs, 1, expressionConfig)

    override def withNewChildInternal(newFirst: Expression): Expression = makeCopy(Array(newFirst))

}