#!/usr/bin/env bash

# Copyright 2014 TappingStone, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

FWDIR="$(cd `dirname $0`; pwd)"
DISTDIR="$FWDIR/dist"

VERSION=$(grep version $FWDIR/build.sbt | grep ThisBuild | grep -o '".*"' | sed 's/"//g')

echo "Building binary distribution for PredictionIO $VERSION..."

cd $FWDIR
sbt/sbt core/publishLocal data/publishLocal engines/publishLocal engines/assemblyPackageDependency tools/assembly

cd $FWDIR
rm -rf $DISTDIR
mkdir -p $DISTDIR/bin
mkdir -p $DISTDIR/conf
mkdir -p $DISTDIR/lib
mkdir -p $DISTDIR/project
mkdir -p $DISTDIR/sbt

cp $FWDIR/bin/* $DISTDIR/bin
cp $FWDIR/conf/* $DISTDIR/conf
cp $FWDIR/project/build.properties $DISTDIR/project
cp $FWDIR/sbt/sbt $DISTDIR/sbt
cp $FWDIR/sbt/sbt-launch-lib.bash $DISTDIR/sbt
cp $FWDIR/assembly/*assembly*jar $DISTDIR/lib
cp $FWDIR/engines/target/scala-2.10/engines*jar $DISTDIR/lib

rm -f $DISTDIR/lib/*javadoc.jar
rm -f $DISTDIR/lib/*sources.jar
rm -f $DISTDIR/conf/pio-env.sh
mv $DISTDIR/conf/pio-env.sh.template $DISTDIR/conf/pio-env.sh

touch $DISTDIR/RELEASE

TARNAME="PredictionIO-$VERSION.tar.gz"
TARDIR="PredictionIO-$VERSION"
cp -r $DISTDIR $TARDIR

tar zcvf $TARNAME $TARDIR
rm -rf $TARDIR

echo "PredictionIO binary distribution created at $TARNAME"
