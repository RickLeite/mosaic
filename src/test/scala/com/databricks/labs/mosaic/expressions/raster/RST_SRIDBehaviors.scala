package com.databricks.labs.mosaic.expressions.raster

import com.databricks.labs.mosaic.core.geometry.api.GeometryAPI
import com.databricks.labs.mosaic.core.index.IndexSystem
import com.databricks.labs.mosaic.functions.MosaicContext
import com.databricks.labs.mosaic.test.mocks
import org.apache.spark.sql.QueryTest
import org.scalatest.matchers.should.Matchers._

trait RST_SRIDBehaviors extends QueryTest {

    def sridBehavior(indexSystem: IndexSystem, geometryAPI: GeometryAPI): Unit = {
        val mc = MosaicContext.build(indexSystem, geometryAPI)
        mc.register()
        val sc = spark
        import mc.functions._
        import sc.implicits._

        val rastersAsPaths = spark.read
            .format("gdal")
            .option("raster_storage", "disk")
            .load("src/test/resources/modis")

        val rastersInMemory = spark.read
            .format("gdal")
            .option("raster_storage", "in-memory")
            .load("src/test/resources/modis")

        val df = rastersAsPaths
            .withColumn("result", rst_srid($"path"))
            .select("result")

        rastersInMemory
            .createOrReplaceTempView("source")

        noException should be thrownBy spark.sql("""
                                                   |select rst_srid(raster) from source
                                                   |""".stripMargin)

        noException should be thrownBy rastersInMemory
            .withColumn("result", rst_srid($"raster"))
            .select("result")

        val result = df.as[Double].collect().max

        result > 0 shouldBe true

        an[Exception] should be thrownBy spark.sql("""
                                                     |select rst_srid() from source
                                                     |""".stripMargin)

    }

}