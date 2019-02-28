package com.sparkkafka.fire

// http://maprdocs.mapr.com/home/Spark/Spark_IntegrateMapRStreams.html

import org.apache.spark._

import org.apache.spark.SparkContext._
import org.apache.spark.streaming._

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.kafka010.{ ConsumerStrategies, KafkaUtils, LocationStrategies }
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.producer._
import org.apache.kafka.common.serialization.StringSerializer

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql._

import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.clustering.KMeansModel

import org.apache.spark.rdd.RDD

/**
 * Consumes messages from a topic in MapR Streams using the Kafka interface,
 * enriches the message with  the k-means model cluster id and publishs the result in json format
 * to another topic
 * Usage: SparkKafkaConsumerProducer  <model> <topicssubscribe> <topicspublish>
 *
 *   <model>  is the path to the saved model
 *   <topics> is a  topic to consume from
 *   <topicp> is a  topic to publish to
 * Example:
 *    $  spark-submit --class com.sparkkafka.uber.SparkKafkaConsumerProducer --master local[2] \
 * mapr-sparkml-streaming-uber-1.0.jar /user/user01/data/savemodel  /user/user01/stream:ubers /user/user01/stream:uberp
 *
 *    for more information
 *    http://maprdocs.mapr.com/home/Spark/Spark_IntegrateMapRStreams_Consume.html
 */

object SparkKafkaConsumerProducer extends Serializable {

  import org.apache.spark.streaming.kafka.producer._
  // schema for uber data   
  case class Fire(lat: Double, lon: Double) extends Serializable
  case class Center(cid: Integer, clat: Double, clon: Double) extends Serializable
  val schema = StructType(Array(
    StructField("lat", DoubleType, true),
    StructField("lon", DoubleType, true)
  ))

  def parseFire(str: String): Fire = {
    val p = str.split(",")
    Fire(p(0).toDouble, p(1).toDouble)
  }

  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      throw new IllegalArgumentException("You must specify the model path, subscribe topic and publish topic. For example /user/mapr/data/saved_model /user/mapr/stream:ml_input /user/mapr/stream:ml_output ")
    }

    val Array(modelpath, intopic, outtopic) = args
    System.out.println("Use model " + modelpath + " Subscribe to : " + intopic + " Publish to: " + outtopic)

    val brokers = "maprdemo:9092" // not needed for MapR Streams, needed for Kafka
    val groupId = "sparkApplication"
    val batchInterval = "2"
    val pollTimeout = "120000"

    val sparkConf = new SparkConf().setAppName("FireStream")
    val spark = SparkSession.builder().appName("ClusterFire").getOrCreate()
    val ssc = new StreamingContext(spark.sparkContext, Seconds(batchInterval.toInt))

    import spark.implicits._

    val producerConf = new ProducerConf(
      bootstrapServers = brokers.split(",").toList
    )
    // Create direct kafka stream with brokers and topics
    val topicsSet = intopic.split(",").toSet
    val kafkaParams = Map[String, String](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> brokers,
      ConsumerConfig.GROUP_ID_CONFIG -> groupId,
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG ->
        "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest",
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG ->
        "org.apache.kafka.common.serialization.StringDeserializer",
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "true",
      "spark.kafka.poll.time" -> pollTimeout,
      "spark.streaming.kafka.consumer.poll.ms" -> "8192"
    )

    // load model for getting clusters
    val model = KMeansModel.load(modelpath)
    // print out cluster centers 
    // model.clusterCenters.foreach(println)
    // create a dataframe with cluster centers to join with stream
    var ac = new Array[Center](100)
    var index: Int = 0
    model.clusterCenters.foreach(x => {
      ac(index) = Center(index, x(0), x(1));
      index += 1;
    })
    val cc: RDD[Center] = spark.sparkContext.parallelize(ac)
    val ccdf = cc.toDF()

    val consumerStrategy = ConsumerStrategies.Subscribe[String, String](topicsSet, kafkaParams)
    val messagesDStream = KafkaUtils.createDirectStream[String, String](
      ssc, LocationStrategies.PreferConsistent, consumerStrategy
    )
    // get message values from key,value
    val valuesDStream: DStream[String] = messagesDStream.map(_.value())

    valuesDStream.foreachRDD { rdd =>

      // There exists at least one element in RDD
      if (!rdd.isEmpty) {
        val count = rdd.count
        println("count received " + count)
        // Get the singleton instance of SparkSession
        val spark = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
        import spark.implicits._

        try {
          val df = rdd.map(parseFire).toDF()

          // get features to pass to model
          val featureCols = Array("lat", "lon")
          val assembler = new VectorAssembler().setInputCols(featureCols).setOutputCol("features")
          val df2 = assembler.transform(df)

          // get cluster categories from  model
          val categories = model.transform(df2)
          categories.show
          categories.createOrReplaceTempView("fire")

          // select values to join with cluster centers
          // convert results to JSON string to send to topic

          val clust = categories.select($"lat", $"lon", $"prediction".alias("cid"))
          val res = clust.join(ccdf, Seq("cid"))

          val tRDD: org.apache.spark.sql.Dataset[String] = res.toJSON

          val temp: RDD[String] = tRDD.rdd
          //temp.sendToKafka[StringSerializer](outtopic, producerConf)
          temp.sendToKafka(outtopic, producerConf)

          println("sending messages")
          temp.take(2).foreach(println)
        } catch {
          case e: java.lang.NumberFormatException => println(e)
        }
      }
    }

    // Start the computation
    println("start streaming")
    ssc.start()
    // Wait for the computation to terminate
    ssc.awaitTermination()

  }

}
