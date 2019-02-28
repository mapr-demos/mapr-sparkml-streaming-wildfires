# Objectives 

The goal of this project is to show some of the features that MapR provides which make it a delightful data layer to use for data science. Implementing ML applications on MapR has several advantages:

* MapR provides data scientists easy self-service access to data
* MapR makes it easy and fast to backup and recover data from snapshots
* MapR provides standard APIs so data can be ingested and processed with commonly used tools
* MapR is applicable to All Data, such as files, tables, or streams, regardless of format.
* MapR provides data locatity features so training data can be localized on nodes with GPUs
* MapR provides integrated tooling (e.g. Spark, Zeppelin) for a frictionless DataOps experience

# The Problem

Minimize the cost and time to respond to fires by staging firefighting assets as close as possible to where fires are likely to occur.

# The Solution

<img src="https://github.com/mapr-demos/mapr-sparkml-streaming-wildfires/blob/master/images/fire_centroids.png?raw=true" width="33%" align="right" hspace="10">

The United States Forest Service provides datasets that describe forest fires that have occurred in Canada and the United States since year 2000. That data can be downloaded from [https://fsapps.nwcg.gov/gisdata.php](https://fsapps.nwcg.gov/gisdata.php). This is the data we used for this study.

Predict where forest fires are prone to occur by partitioning the locations of past burns into clusters whose centroids can be used to optimally place heavy fire fighting equipment as near as possible to where fires are likely to occur. The K-Means clustering algorithm is perfectly suited for this purpose.


# Usage

Compile spark app that wraps the kmeans model with streams, and copy to cluster node (e.g. nodea):
```
cd /Users/idownard/development/mapr-sparkml-streaming-fires
mvn package
scp target/mapr-sparkml-streaming-fires-1.0-jar-with-dependencies.jar nodea:~/
```

A pre-built fat jar and kmeans model have been added to this repo so you don't have to run `mvn package` or compile the model in the provided Zeppelin notebook. Run these commands to put the model and jar in the right place:

Copy the kmeans model:
```
scp saved_model.tgz nodea:~/
ssh nodea tar -C /mapr/demo.mapr.com/user/mapr/data -xvf saved_model.tgz
```
Copy the fat jar for applying the kmeans model on streaming lat/long coordinates. The fat jar has been split to fit within github's 100MB file size limit. Join the pieces with cat:
```
cat mapr-sparkml-streaming-fires-1.0-jar-with-dependencies.jar-* > mapr-sparkml-streaming-fires-1.0-jar-with-dependencies.jar
scp mapr-sparkml-streaming-fires-1.0-jar-with-dependencies.jar nodea:~/
```

Upload websocket scripts to another cluster node (e.g. 10.1.1.15):
```
scp *.sh nodeb:~/
```


Connect to cluster and create streams:
```
maprcli stream create -path /user/mapr/ml_input -produceperm p -consumeperm p -topicperm p -ttl 604800
maprcli stream topic create -path /user/mapr/ml_input -topic requester001
maprcli stream create -path /user/mapr/ml_output -produceperm p -consumeperm p -topicperm p -ttl 604800
maprcli stream topic create -path /user/mapr/ml_output -topic kmeans001
```

Run stream consumer to pipe lat/log requests in the ml_input stream:
```
ssh nodeb ml_input_stream.sh
ssh nodeb ml_output_stream.sh
```

Run spark app:
```
ssh nodea
/opt/mapr/spark/spark-2.1.0/bin/spark-submit --class com.sparkkafka.fire.SparkKafkaConsumerProducer --master local[2] ~/mapr-sparkml-streaming-fires-1.0-jar-with-dependencies.jar /user/mapr/data/save_fire_model  /user/mapr/ml_input:requester001 /user/mapr/ml_output:kmeans001
```

Open Zeppelin, [http://nodea:7000]())

Import, open, and run the [Forest Fire Notebook](https://github.com/mapr-demos/mapr-sparkml-streaming-wildfires/blob/master/notebook/Forest%20Fire%20Prediction.json) in Zeppelin.

Make the following observations from the notebook:

* Note how we download data. Note how this would be much harder if our platform was not POSIX.
* Note how we snapshot data. Talk about how snapshots are essential for iterating concepts
* Note how the USDA shapefile is converted to to csv. Also note how we need two different schemas. These types of comlexities are common in data wrangling. 
* Note where we fit the kmeans model. Most models have far more parameters than two, and often require trial and error to get right, so A/B testing and monitoring is really important.
* Note how we can save a model
* Note how we can apply the model to look up a cluster id for a lat/long
* Also note how we run the model as a spark job, which reads lat/longs from stream and output cluster ids in real time.

Open [http://nodeb:3433/ml_input_stream.sh](), enter 44.5,-111.9 coordinate
Open [http://nodeb:3433/ml_output_stream.sh]() and watch it output the cluster centroid (cid) and its lat/long coordinates.

Talk about how these websockets are a common interface for web services and microservices. Here we're using them to visualize an API implemented with MapR Streams.

This solution is architected in a synchronous ML pipeline as shown below:

<img src="https://github.com/mapr-demos/mapr-sparkml-streaming-wildfires/blob/master/images/synchronous_pipeline.png?raw=true" width="66%" align="center" hspace="10">

