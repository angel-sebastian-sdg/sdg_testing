package com.sdg.ingestion

import org.apache.spark.SparkConf
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{array, col, lit, split, sum, to_date}
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}

object test {

  def testQty(spark: SparkSession): Unit = {
    import spark.implicits._

    val data = Seq(
      (1,"jan'22",10),
      (1,"jun'22",30),
      (1,"jan'21",8),
      (1,"may'22",20),
      (1,"jun'21",25),
      (1,"may'21",18),
      (2,"jul'22",28),
      (2,"jan'22",100),
      (2,"feb'22",90)
    )

    val df: DataFrame = data.toDF("dis_id", "month", "qty")
      .withColumn("month_name", split(col("month"), "'").getItem(0))
      .withColumn("year", split(col("month"), "'").getItem(1).cast(IntegerType))
      .withColumn("last_year", split(col("month"), "'").getItem(1).cast(IntegerType).minus(1))

    // Create extended dataframe to split month, year and calculate the last year
    val extendedDf = df.as("x")
      .join(
        df.as("y"),
        (col("x.month_name") === col("y.month_name"))
          .and(col("x.last_year") === col("y.year"))
          .and(col("x.dis_id") === col("y.dis_id")),
        "left"
      ).select(col("x.dis_id"), col("x.month"), col("x.qty"), col("y.qty").as("qty_ly"))

    //     extendedDf.show(false)
    //     df.show(false)

    val months = Seq(
      "jan'21", "feb'21", "mar'21", "apr'21", "may'21", "jun'21","jul'21", "aug'21", "sep'21", "oct'21", "nov'21", "dec'21","jan'22", "feb'22", "mar'22", "apr'22", "may'22", "jun'22","jul'22", "aug'22", "sep'22", "oct'22", "nov'22", "dec'22"
    )

    val disId = extendedDf.select("dis_id").distinct()
    val calendarData = disId.crossJoin(months.toDF("month"))
    calendarData.show(false)

    // Create calendar DataFrame
    val calendarSchema = StructType(Seq(
      StructField("dis_id", IntegerType, true),
      StructField("month", StringType, true)
    ))

    // Create a DataFrame with calendar data and set calendar_qty to 0 for all rows
    val calendarDF = spark.createDataFrame(calendarData.rdd, calendarSchema)
      .withColumn("calendar_qty", lit(0))  // Set calendar_qty to 0 for calendar data
      .join(extendedDf, Seq("dis_id", "month"), "left")
      .na.fill(0, Seq("qty")) // Fill null values in original_qty column with 0
      .drop("calendar_qty")  // Drop calendar_qty column
      .orderBy(col("dis_id"), to_date(col("month"), "MMM''yy")) // Order by dis_id and month*/

    // Calculate qty_3m using a window function to sum qty over a 3-month window partitioned by dis_id
    val windowSpec3m = Window.partitionBy(array("dis_id")).rowsBetween(-2, 0)
    val result3m = calendarDF.withColumn("qty_3m", sum("qty").over(windowSpec3m)) // Calculate qty_3m using window function

    result3m.filter($"qty" =!= 0 && $"qty_3m" =!= 0).show()

  }


  def testUnion(spark: SparkSession): Unit = {
    import spark.implicits._
    val data1 = Seq(
      (1,"AA",10),
      (1,"BB",30),
      (1,"CC",20)
    )

    val table1 = data1.toDF("A", "C", "D")

    val data2 = Seq(
      (1,"OTHER A"),
      (1,"OTHER B"),
      (1,"OTHER C")
    )

    val table2 = data2.toDF("A", "B")

    var table1Improved = table1
    var table2Improved = table2;

    // add the new columns to the table 1 (from table 2)
    table2.columns.foreach( columnName => {
      if(!table1.columns.contains(columnName)) {
        table1Improved = table1Improved.withColumn(columnName, lit(""))
      }
    })

    // add the new columns to the table 2 (from table 1)
    table1.columns.foreach( columnName => {
      if(!table2.columns.contains(columnName)) {
        table2Improved = table2Improved.withColumn(columnName, lit(""))
      }
    })

    //Joining both by name
    table1Improved.unionByName(table2Improved).show(false)
  }



  def main(args: Array[String]): Unit = {

    val sparkConf: SparkConf = new SparkConf().
      setMaster("local[*]").
      setAppName("SDG-Test")

    val spark: SparkSession = SparkSession.builder().config(sparkConf).
      enableHiveSupport().
      getOrCreate()

//      testUnion(spark)
      testQty(spark)

  }

}
