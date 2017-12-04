# The Problem

Minimize the cost and time to respond to fires by staging firefighting assets as close as possible to where fires are likely to occur.

# The Solution

Predict where forest fires are prone to occur by partitioning the locations of past burns into clusters whose centroids can be used to optimally place heavy fire fighting equipment as near as possible to where fires are likely to occur. The K-Means clustering algorithm is perfectly suited for this purpose.

The goal of this project is to show some of the features that MapR provides which make it a delightful data layer to use for data science. Implementing ML applications on MapR has several advantages:
* MapR provides data scientists easy self-service access to data
* MapR makes it easy and fast to backup and recover data from snapshots
* MapR provides standard APIs so data can be ingested and processed with commonly used tools
* MapR is applicable to All Data, such as files, tables, or streams, regardless of format.
* MapR provides data locatity features so training data can be localized on nodes with GPUs
* MapR provides integrated tooling (e.g. Spark, Zeppelin) for a frictionless DataOps experience

