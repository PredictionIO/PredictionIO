#!/usr/bin/env sh

# PredictionIO Package Script

# NOTE: Run this script after bin/build.sh to package things up

# This scripts package everything up into a deployable package that runs off a single configuration file

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

# Package admin server
echo "Going to package PredictionIO Admin Server..."
cd $ADMIN_DIR
$PLAY stage

# Package API server
echo "Going to package PredictionIO API Server..."
cd $API_DIR
$PLAY stage

# Package scheduler server
echo "Going to package PredictionIO Scheduler Server..."
cd $SCHEDULER_DIR
$PLAY stage

# Packaging
rm -rf $PACKAGE_DIR $PACKAGE_DIR_LINUX32 $PACKAGE_DIR_LINUX64
mkdir -p "$PACKAGE_DIR/bin"
mkdir -p "$PACKAGE_DIR/lib"

cp -n $ADMIN_DIR/target/universal/stage/bin/predictionio-admin $PACKAGE_DIR/bin
cp -n $ADMIN_DIR/target/universal/stage/lib/* $PACKAGE_DIR/lib
cp -n $API_DIR/target/universal/stage/bin/predictionio-api $PACKAGE_DIR/bin
cp -n $API_DIR/target/universal/stage/lib/* $PACKAGE_DIR/lib
cp -n $SCHEDULER_DIR/target/universal/stage/bin/predictionio-scheduler $PACKAGE_DIR/bin
cp -n $SCHEDULER_DIR/target/universal/stage/lib/* $PACKAGE_DIR/lib

cp -R $DIST_DIR/bin/* $PACKAGE_DIR/bin
cp $BASE/bin/quiet.sh $PACKAGE_DIR/bin
cp -R $DIST_DIR/conf $PACKAGE_DIR

cp "$BASE/process/target/scala-2.10/predictionio-process-hadoop-scalding-assembly-$VERSION.jar" "$PACKAGE_DIR/lib"
cp "$BASE/process/engines/commons/evaluations/scala/paramgen/target/scala-2.10/predictionio-process-commons-evaluations-paramgen-assembly-$VERSION.jar" "$PACKAGE_DIR/lib"
cp "$BASE/process/engines/commons/evaluations/scala/u2itrainingtestsplit/target/scala-2.10/predictionio-process-commons-evaluations-scala-u2itrainingtestsplittime-assembly-$VERSION.jar" "$PACKAGE_DIR/lib"

cp $BASE/process/engines/commons/algorithms/scala/random/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/commons/algorithms/scala/random/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/commons/evaluations/scala/map/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/commons/evaluations/scala/map/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/commons/evaluations/scala/topkitems/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/commons/evaluations/scala/topkitems/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/commons/evaluations/scala/u2isplit/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/commons/evaluations/scala/u2isplit/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/itemrec/algorithms/scala/generic/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/itemrec/algorithms/scala/generic/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/itemrec/algorithms/scala/mahout/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/itemrec/algorithms/scala/mahout/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/itemrec/algorithms/scala/graphchi/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/itemrec/algorithms/scala/graphchi/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/itemsim/algorithms/scala/generic/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/itemsim/algorithms/scala/generic/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/itemsim/algorithms/scala/mahout/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/itemsim/algorithms/scala/mahout/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/process/engines/itemsim/algorithms/scala/graphchi/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/process/engines/itemsim/algorithms/scala/graphchi/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/tools/conncheck/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/tools/conncheck/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/tools/settingsinit/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/tools/settingsinit/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/tools/softwaremanager/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/tools/softwaremanager/target/pack/lib/* $PACKAGE_DIR/lib

cp $BASE/tools/users/target/pack/bin/* $PACKAGE_DIR/bin
cp -n $BASE/tools/users/target/pack/lib/* $PACKAGE_DIR/lib

mkdir -p $PACKAGE_DIR/vendors/mahout-distribution-0.9
cp $VENDOR_MAHOUT/mahout-core-0.9-job.jar $PACKAGE_DIR/vendors/mahout-distribution-0.9

cd $DIST_DIR/target

# Multi-arch targets

if test "$MULTI_ARCH" = "1" ; then
    cp -R $PACKAGE_DIR $PACKAGE_DIR_LINUX64
    mv $PACKAGE_DIR $PACKAGE_DIR_LINUX32

    cd $PACKAGE_DIR_LINUX32
    cp $VENDOR_GRAPHCHI_CPP_CF_LINUX32/* $PACKAGE_DIR_LINUX32/bin

    cd $PACKAGE_DIR_LINUX64
    cp $VENDOR_GRAPHCHI_CPP_CF_LINUX64/* $PACKAGE_DIR_LINUX64/bin

    cd $DIST_DIR/target

    if [ -e "$PACKAGE_NAME_LINUX32.zip" ] ; then
        rm "$PACKAGE_NAME_LINUX32.zip"
    fi
    if [ -e "$PACKAGE_NAME_LINUX64.zip" ] ; then
        rm "$PACKAGE_NAME_LINUX64.zip"
    fi 

    zip -q -r "$PACKAGE_NAME_LINUX32.zip" "$PACKAGE_NAME_LINUX32"
    zip -q -r "$PACKAGE_NAME_LINUX64.zip" "$PACKAGE_NAME_LINUX64"

    echo "Packaging finished:"
    echo "- $DIST_DIR/target/$PACKAGE_NAME_LINUX32.zip"
    echo "- $DIST_DIR/target/$PACKAGE_NAME_LINUX64.zip"
else
    rm "$PACKAGE_NAME.zip"
    zip -q -r "$PACKAGE_NAME.zip" "$PACKAGE_NAME"
  
    echo "Packaging finished at $DIST_DIR/target/$PACKAGE_NAME.zip"
fi
