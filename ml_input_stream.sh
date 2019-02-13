#!/bin/bash
# PURPOSE: Monitor input streams for forest fire kmeans clustering
# USAGE: websocketd --port=3433 --dir=. --devconsole; open http://10.1.1.15:3433/ml_input_stream.sh
# AUTHOR: Ian Downard

/opt/mapr/kafka/kafka-*/bin/kafka-console-consumer.sh --topic /user/mapr/ml_input:requester001 --bootstrap-server this.will.be.ignored:9092 &
while true; do
while read -t 1 LINE; do
echo $LINE | /opt/mapr/kafka/kafka-*/bin/kafka-console-producer.sh --topic /user/mapr/ml_input:requester001 --broker-list this.will.be.ignored:9092
done

done
