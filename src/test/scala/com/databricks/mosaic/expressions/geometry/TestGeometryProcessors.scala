package com.databricks.mosaic.expressions.geometry
import com.databricks.mosaic.core.geometry.GeometryAPI.JTS
import com.databricks.mosaic.core.index.H3IndexSystem
import com.databricks.mosaic.functions.MosaicContext
import org.scalatest._
import com.databricks.mosaic.test.SparkTest
import org.locationtech.jts.io.WKTReader
import com.databricks.mosaic.mocks

class TestGeometryProcessors extends FunSuite with Matchers with SparkTest {

  val mosaicContext: MosaicContext = MosaicContext(H3IndexSystem, JTS)
  import mosaicContext.functions._

  val wktReader = new WKTReader()
  val referenceGeoms = mocks.wkt_rows.map(g => wktReader.read(g.head))

  test("Test area calculation") {
    mosaicContext.register(spark)
    val ss = spark
    import ss.implicits._

    val expected = referenceGeoms.map(_.getArea)
    val result = mocks.getWKTRowsDf
      .select(st_area($"wkt"))
      .as[Double]
      .collect()

    result should contain theSameElementsAs expected

    mocks.getWKTRowsDf.createOrReplaceTempView("source")

    val sqlResult = spark.sql("select st_area(wkt) from source")
      .as[Double]
      .collect()

    sqlResult should contain theSameElementsAs expected

  }

  test("Test centroid calculation (2-dimensional)") {
    mosaicContext.register(spark)
    val ss = spark
    import ss.implicits._

    val expected = referenceGeoms.map(_.getCentroid.getCoordinate).map(c => (c.x, c.y))
    val result = mocks.getWKTRowsDf
      .select(st_centroid2D($"wkt").alias("coord"))
      .selectExpr("coord.*")
      .as[Tuple2[Double, Double]]
      .collect()

    result should contain theSameElementsAs expected

    mocks.getWKTRowsDf.createOrReplaceTempView("source")

    val sqlResult = spark
      .sql("""with subquery (
        | select st_centroid2D(wkt) as coord from source
        |) select coord.* from subquery""".stripMargin)
      .as[Tuple2[Double, Double]]
      .collect()

    sqlResult should contain theSameElementsAs expected
  }
  
}