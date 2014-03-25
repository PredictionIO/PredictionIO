#!/usr/bin/env sh

# PredictionIO Third Party Software Installation

# Get the absolute path of the build script
SCRIPT="$0"
while [ -h "$SCRIPT" ] ; do
	SCRIPT=`readlink "$SCRIPT"`
done

# Get the base directory of the repo
DIR=`dirname $SCRIPT`/..
cd $DIR
BASE=`pwd`

. "$BASE/bin/common.sh"
. "$BASE/bin/vendors.sh"

# Detect existing installations in search path
if vendor_graphchi_exists ; then
	echo "Found GraphChi C++ Collaborative Filtering Toolkit in vendors area. Assuming it has been installed."
else
	echo "Cannot find GraphChi C++ Collaborative Filtering Toolkit from vendors area. Installing it from the Internet."
	install_graphchi "$VENDORS_PATH"
fi

# Detect existing installations in search path
if [ $(process_exists "mongod") -gt "0" ] ; then
	echo "mongod is running. Skipping MongoDB installation."
elif command_exists "mongod" ; then
	echo "Found mongod in search path. Assuming MongoDB has been installed."
elif vendor_mongodb_exists ; then
	echo "Found mongod in vendors area. Assuming MongoDB has been installed."
else
	while true; do
		read -p "Cannot find mongod from process list, search path, nor vendors area. Do you want to automatically install $VENDOR_MONGODB_NAME? [y/n] " yn
		case $yn in
			[Yy]* ) install_mongodb "$VENDORS_PATH"; break;;
			[Nn]* ) break;;
			* ) echo "Please answer 'y' or 'n'.";;
		esac
	done
fi

# Detect existing installations in search path
if command_exists "hadoop" ; then
	echo "Found hadoop in search path. Assuming Apache Hadoop has been installed."
elif vendor_hadoop_exists ; then
	echo "Found hadoop in vendors area. Assuming Apache Hadoop has been installed."
else
	while true; do
		read -p "Cannot find hadoop from search path nor vendors area. Do you want to automatically install $VENDOR_HADOOP_NAME? (Please make sure you can SSH to the localhost without a password.) [y/n] " yn
		case $yn in
			[Yy]* ) install_hadoop "$VENDORS_PATH"; break;;
			[Nn]* ) break;;
			* ) echo "Please answer 'y' or 'n'.";;
		esac
	done
fi
