#!/usr/bin/env sh

# PredictionIO Third Party Software Utilities

# This script should be sourced with $BASE set to the base of the binary package

# Utilities
command_exists () {
	command -v "$1" >/dev/null 2>&1
}

ostype () {
	case $(uname) in
		Linux*)
			echo "linux";;
		Darwin*)
			echo "osx";;
	esac
}

hosttype () {
	case $(uname -m) in
		i[3456]86)
			echo "i686";;
		x86_64)
			echo "x86_64";;
	esac
}

OS=$(ostype)
ARCH=$(hosttype)

process_exists () {
	echo $(ps -ef | grep "$1" | grep -v "grep" | wc -l)
}

# Third party software
VENDORS_PATH="$BASE/vendors"

VENDOR_GRAPHCHI_VERSION="0a6545ccb7"
VENDOR_HADOOP_VERSION="1.2.1"
VENDOR_MONGODB_VERSION="2.4.9"

VENDOR_GRAPHCHI_PATH="$VENDORS_PATH/graphchi-cpp-cf-$OS-$ARCH-$VENDOR_GRAPHCHI_VERSION"
VENDOR_HADOOP_PATH="$VENDORS_PATH/hadoop-$VENDOR_HADOOP_VERSION"
VENDOR_MONGODB_PATH="$VENDORS_PATH/mongodb-$OS-$ARCH-$VENDOR_MONGODB_VERSION"

VENDOR_GRAPHCHI_NAME="GraphChi C++ Collaborative Filtering Toolkit $VENDOR_GRAPHCHI_VERSION"
VENDOR_HADOOP_NAME="Apache Hadoop $VENDOR_HADOOP_VERSION"
VENDOR_MONGODB_NAME="MongoDB $VENDOR_MONGODB_VERSION"

install_graphchi () {
	local FN="graphchi-cpp-cf-$OS-$ARCH-$VENDOR_GRAPHCHI_VERSION.tar.gz"
	local URL="http://download.prediction.io/graphchi-cpp-cf/$FN"
	echo "Going to download and install $VENDOR_GRAPHCHI_NAME ($URL)..."
	local VENDORS_PATH=$1
	mkdir -p $VENDORS_PATH
	cd $VENDORS_PATH
	curl -O $URL
	tar zxvf $FN
	cp $VENDOR_GRAPHCHI_PATH/* $BASE/bin
}

install_mongodb () {
	local FN="mongodb-$OS-$ARCH-$VENDOR_MONGODB_VERSION.tgz"
	local URL="http://fastdl.mongodb.org/$OS/$FN"
	echo "Going to download and install $VENDOR_MONGODB_NAME ($URL)..."
	local VENDORS_PATH=$1
	mkdir -p $VENDORS_PATH
	cd $VENDORS_PATH
	curl -O $URL
	tar zxvf $FN
}

install_hadoop () {
	while true; do
		read -p "Please supply the absolute path to your Java installation: " JAVA_HOME
		if [ $JAVA_HOME ] && [ -d $JAVA_HOME ] && [ -x "$JAVA_HOME/bin/java" ] ; then
			echo "Using directory $JAVA_HOME as your Java installation..."
			break
		else
			echo "Please provide a valid Java installation directory"
		fi
	done
	echo "Going to download and install $VENDOR_HADOOP_NAME..."
	local VENDORS_PATH=$1
	mkdir -p $VENDORS_PATH
	cd $VENDORS_PATH
	curl -O http://archive.apache.org/dist/hadoop/common/hadoop-$VENDOR_HADOOP_VERSION/hadoop-$VENDOR_HADOOP_VERSION-bin.tar.gz
	tar zxvf hadoop-$VENDOR_HADOOP_VERSION-bin.tar.gz
	echo "Configuring Hadoop in pseudo-distributed mode..."
	cp ../conf/hadoop/* $VENDOR_HADOOP_PATH/conf
	echo "export JAVA_HOME=$JAVA_HOME" >> $VENDOR_HADOOP_PATH/conf/hadoop-env.sh
	echo "Configuring PredictionIO to use Hadoop in vendors area..."
	echo "io.prediction.commons.settings.hadoop.home=$VENDOR_HADOOP_PATH" >> ../conf/predictionio.conf
	echo "Trying to format HDFS..."
	$VENDOR_HADOOP_PATH/bin/hadoop namenode -format
}

start_mongodb () {
	echo "Going to start MongoDB..."
	mkdir -p "$VENDORS_PATH/mongodb/data"
	mkdir -p "$VENDORS_PATH/mongodb/logs"
	$VENDOR_MONGODB_PATH/bin/mongod --config conf/mongodb/mongodb.conf >/dev/null 2>&1 &
}

start_hadoop () {
	echo "Going to start Hadoop..."
	$VENDOR_HADOOP_PATH/bin/start-all.sh
}

stop_hadoop () {
	echo "Going to stop Hadoop..."
	$VENDOR_HADOOP_PATH/bin/stop-all.sh
}

vendor_graphchi_exists () {
	[ -e "$VENDOR_GRAPHCHI_PATH/als" ]
}

vendor_mongodb_exists () {
	[ -e "$VENDOR_MONGODB_PATH/bin/mongod" ]
}

vendor_hadoop_exists () {
	[ -e "$VENDOR_HADOOP_PATH/bin/hadoop" ]
}
