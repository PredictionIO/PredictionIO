#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

usage ()
{
    echo "Usage: $0 [-h|--help] [--with-es=x]"
    echo ""
    echo "  -h|--help    Show usage"
    echo ""
    echo "  --with-es=1  Build distribution with Elasticsearch 1 support as default"
    echo "  --with-es=5  Build distribution with Elasticsearch 5 support as default"
}

ES_VERSION=1

for i in "$@"
do
case $i in
    -h|--help)
    usage
    shift
    exit
    ;;
    --with-es=*)
    ES_VERSION="${i#*=}"
    shift
    ;;
    *)
    usage
    exit 1
    ;;
esac
done

if [ "$ES_VERSION" = "1" ] || [ "$ES_VERSION" = "5" ]
then
    echo -e "\033[0;32mBuilding with Elasticsearch $ES_VERSION support as the default choice\033[0m"
else
    usage
    exit 1
fi

FWDIR="$(cd `dirname $0`; pwd)"
DISTDIR="${FWDIR}/dist"

VERSION=$(grep version ${FWDIR}/build.sbt | grep ThisBuild | grep -o '".*"' | sed 's/"//g')

echo "Building binary distribution for PredictionIO $VERSION..."

cd ${FWDIR}
sbt/sbt clean +common/publishLocal +data/publishLocal +core/publishLocal +e2/publishLocal
sbt/sbt +dataElasticsearch1/assembly
sbt/sbt +dataElasticsearch/assembly
sbt/sbt +dataHbase/assembly
sbt/sbt +dataHdfs/hadoop26:assembly
sbt/sbt +dataHdfs/hadoop27:assembly
sbt/sbt +dataJdbc/assembly
sbt/sbt +dataLocalfs/assembly
sbt/sbt +tools/assembly

cd ${FWDIR}
for scalaBinaryVersion in `find ${FWDIR}/assembly -maxdepth 1 -mindepth 1 -type d -exec basename {} \;`
do
rm -rf ${DISTDIR}
mkdir -p ${DISTDIR}/bin
mkdir -p ${DISTDIR}/conf
mkdir -p ${DISTDIR}/lib
mkdir -p ${DISTDIR}/lib/spark
mkdir -p ${DISTDIR}/lib/extra
mkdir -p ${DISTDIR}/project
mkdir -p ${DISTDIR}/sbt

cp ${FWDIR}/bin/* ${DISTDIR}/bin || :
cp ${FWDIR}/conf/* ${DISTDIR}/conf
cp ${FWDIR}/project/build.properties ${DISTDIR}/project
cp ${FWDIR}/sbt/sbt ${DISTDIR}/sbt
cp ${FWDIR}/assembly/$scalaBinaryVersion/*assembly*jar ${DISTDIR}/lib
cp ${FWDIR}/assembly/$scalaBinaryVersion/spark/*jar ${DISTDIR}/lib/spark

if [ "$ES_VERSION" = "5" ]
then
    mv ${DISTDIR}/lib/spark/pio-data-elasticsearch1-assembly-*.jar ${DISTDIR}/lib/extra
else
    mv ${DISTDIR}/lib/spark/pio-data-elasticsearch-assembly-*.jar ${DISTDIR}/lib/extra
fi

mv ${DISTDIR}/lib/spark/pio-data-hdfs27-assembly-*.jar ${DISTDIR}/lib/extra

rm -f ${DISTDIR}/lib/*javadoc.jar
rm -f ${DISTDIR}/lib/*sources.jar
rm -f ${DISTDIR}/conf/pio-env.sh
mv ${DISTDIR}/conf/pio-env.sh.template ${DISTDIR}/conf/pio-env.sh

touch ${DISTDIR}/RELEASE

TARNAME="PredictionIO_$scalaBinaryVersion-$VERSION.tar.gz"
TARDIR="PredictionIO-$VERSION"
cp -r ${DISTDIR} ${TARDIR}

tar zcvf ${TARNAME} ${TARDIR}
rm -rf ${TARDIR}

echo -e "\033[0;32mPredictionIO binary distribution created at $TARNAME\033[0m"
done
