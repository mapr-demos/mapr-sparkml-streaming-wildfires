#!/bin/bash
# PURPOSE: Monitor input streams for forest fire kmeans clustering
# USAGE: websocketd --port=3433 --dir=. --devconsole; open http://10.1.1.15:3433/ml_output_stream.sh
# AUTHOR: Ian Downard

/opt/mapr/kafka/kafka-0.9.0/bin/kafka-console-consumer.sh --topic /user/mapr/ml_output:kmeans001 --new-consumer --zookeeper 10.1.1.14:5181 --bootstrap-server this.will.be.ignored:9092 &
while true; do
while read -t 1 LINE; do
echo $LINE | /opt/mapr/kafka/kafka-0.9.0/bin/kafka-console-producer.sh --topic /user/mapr/ml_input:requester001 --broker-list this.will.be.ignored:9092
done

done
