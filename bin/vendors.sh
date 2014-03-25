#!/usr/bin/env sh

# PredictionIO Third Party Software Utilities

# Utilities
command_exists () {
    command -v "$1" >/dev/null 2>&1
}

# Third party software
VENDORS_PATH="$BASE/vendors"
VENDOR_SBT="$VENDORS_PATH/sbt-0.13.1/sbt"
VENDOR_PLAY_VERSION="2.2.2"
VENDOR_PLAY="$VENDORS_PATH/play-$VENDOR_PLAY_VERSION/play"
VENDOR_MAHOUT="$VENDORS_PATH/mahout-distribution-0.9"
VENDOR_GRAPHCHI_CPP_CF_LINUX32="$VENDORS_PATH/graphchi-cpp-cf-linux-i686-0a6545ccb7"
VENDOR_GRAPHCHI_CPP_CF_LINUX64="$VENDORS_PATH/graphchi-cpp-cf-linux-x86_64-0a6545ccb7"

install_sbt () {
    echo "Going to download and install sbt 0.13.1..."
    local VENDORS_PATH=$1/sbt-0.13.1
    mkdir -p $VENDORS_PATH
    cd $VENDORS_PATH
    curl -O http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.13.1/sbt-launch.jar
    echo 'java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512M -jar `dirname $0`/sbt-launch.jar "$@"' > sbt
    chmod a+x sbt
    cd $BASE
}

install_play () {
    echo "Going to download and install Play Framework $VENDOR_PLAY_VERSION..."
    local VENDORS_PATH=$1
    mkdir -p $VENDORS_PATH
    cd $VENDORS_PATH
    curl -O http://downloads.typesafe.com/play/$VENDOR_PLAY_VERSION/play-$VENDOR_PLAY_VERSION.zip
    unzip play-$VENDOR_PLAY_VERSION.zip
    cd $BASE
}

install_mahout () {
    echo "Going to download and install Apache Mahout 0.9..."
    mkdir -p $VENDORS_PATH
    cd $VENDORS_PATH
    echo "Retrieving Apache mirror list..."
    curl -o apache_mahout_mirrors.txt http://www.apache.org/dyn/closer.cgi/mahout/0.9/mahout-distribution-0.9.tar.gz
    MAHOUT_URL=$(cat apache_mahout_mirrors.txt | grep -m 1 "<strong>.*</strong>" | sed 's/.*<strong>//' | sed 's/<\/strong>.*//')
    echo "Found mirror: $MAHOUT_URL"
    curl -O $MAHOUT_URL
    #curl -O http://archive.apache.org/dist/mahout/0.8/mahout-distribution-0.8.tar.gz
    tar zxvf mahout-distribution-0.9.tar.gz
    cd $BASE
}

install_graphchi_cpp_cf_linux32 () {
    echo "Going to download and install GraphChi C++ CF Toolbox for Linux 32-bit..."
    mkdir -p $VENDORS_PATH
    cd $VENDORS_PATH
    curl -O http://download.prediction.io/graphchi-cpp-cf/graphchi-cpp-cf-linux-i686-0a6545ccb7.tar.gz
    tar zxvf graphchi-cpp-cf-linux-i686-0a6545ccb7.tar.gz
    cd $BASE
}

install_graphchi_cpp_cf_linux64 () {
    echo "Going to download and install GraphChi C++ CF Toolbox for Linux 64-bit..."
    mkdir -p $VENDORS_PATH
    cd $VENDORS_PATH
    curl -O http://download.prediction.io/graphchi-cpp-cf/graphchi-cpp-cf-linux-x86_64-0a6545ccb7.tar.gz
    tar zxvf graphchi-cpp-cf-linux-x86_64-0a6545ccb7.tar.gz
    cd $BASE
}

# Detect existing installations in search path
# Do not use existing sbt to enforce JVM settings
#if command_exists "sbt" ; then
#   echo "Using sbt in search path. No additional JVM optimization will be set."
#   SBT=sbt
if [ -x "$VENDOR_SBT" ] ; then
    echo "Using sbt 0.13.1 in vendors."
    SBT="$VENDOR_SBT"
elif install_sbt "$VENDORS_PATH" ; then
    SBT="$VENDOR_SBT"
else
    echo "Unable to locate sbt 0.13.1 and automatic installation failed. Aborting." >&2
    exit 1
fi

# Do not use existing Play due to potential compatibility issue
#if command_exists "play" ; then
#   PLAY=play
if [ -x "$VENDOR_PLAY" ] ; then
    echo "Using Play Framework $VENDOR_PLAY_VERSION in vendors."
    PLAY="$VENDOR_PLAY"
elif install_play "$VENDORS_PATH" ; then
    PLAY="$VENDOR_PLAY"
else
    echo "Unable to locate Play Framework $VENDOR_PLAY_VERSION and automatic installation failed. Aborting." >&2
    exit 1
fi

if [ -r "$VENDOR_MAHOUT/mahout-core-0.9-job.jar" ] ; then
    echo "Using Apache Mahout 0.9 in vendors."
elif install_mahout ; then
    echo ""
else
    echo "Unable to locate Apache Mahout 0.9 and automatic installation failed. Aborting." >&2
    exit 1
fi

if [ -r "$VENDOR_GRAPHCHI_CPP_CF_LINUX32/als" ] ; then
    echo "Using GraphChi C++ CF Toolbox for Linux 32-bit."
elif install_graphchi_cpp_cf_linux32 ; then
    echo ""
else
    echo "Unable to locate GraphChi C++ CF Toolbox for Linux 32-bit and automatic installation failed. Aborting." >&2
    exit 1
fi

if [ -r "$VENDOR_GRAPHCHI_CPP_CF_LINUX64/als" ] ; then
    echo "Using GraphChi C++ CF Toolbox for Linux 64-bit."
elif install_graphchi_cpp_cf_linux64 ; then
    echo ""
else
    echo "Unable to locate GraphChi C++ CF Toolbox for Linux 64-bit and automatic installation failed. Aborting." >&2
    exit 1
fi

