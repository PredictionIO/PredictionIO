#!/usr/bin/env bash

# Copyright 2015 TappingStone, Inc.
#
# This script will install PredictionIO onto your computer!
#
# Documentation: http://docs.prediction.io
#
# License: http://www.apache.org/licenses/LICENSE-2.0

OS=`uname`
PIO_VERSION=0.9.1-SNAPSHOT
SPARK_VERSION=1.2.0
ELASTICSEARCH_VERSION=1.4.2
HBASE_VERSION=0.98.6
PIO_DIR=$HOME/PredictionIO
USER_PROFILE=$HOME/.profile
PIO_FILE=PredictionIO-$PIO_VERSION.tar.gz
TEMP_DIR=/tmp

# Ask a yes/no question, with a default of "yes".
confirm () {
  echo -ne $@ "[Y/n] "
  read -r response

  case $response in
    [yY][eE][sS]|[yY]|"")
      true
      ;;
    [nN][oO]|[nN])
      false
      ;;
    *)
      confirm $@
      ;;
  esac
}

echo -e "\033[1;32mWelcome to PredictionIO $PIO_VERSION!\033[0m"

# Detect OS
if [[ "$OS" = "Darwin" ]]; then
  echo "Mac OS detected!"
  SED_CMD="sed -i ''"
elif [[ "$OS" = "Linux" ]]; then
  echo "Linux OS detected!"
  SED_CMD="sed -i"
else
  echo -e "\033[1;31mYour OS $OS is not yet supported for automatic install :(\033[0m"
  echo -e "\033[1;31mPlease do a manual install!\033[0m"
  exit 1
fi

if [[ $USER ]]; then
  echo "Using user: $USER"
else
  echo "No user found - this is OK!"
fi

if [[ "$OS" = "Linux" && $(cat /proc/1/cgroup) == *cpu:/docker/* ]]; then
  # Docker
  # REQUIRED: No user input for Docker!
  echo -e "\033[1;33mDocker detected!\033[0m"
  echo -e "\033[1;33mForcing Docker defaults!\033[0m"
  pio_dir=$PIO_DIR
  vendors_dir=$pio_dir/vendors

  spark_dir=$vendors_dir/spark-$SPARK_VERSION
  elasticsearch_dir=$vendors_dir/elasticsearch-$ELASTICSEARCH_VERSION
  hbase_dir=$vendors_dir/hbase-$HBASE_VERSION
  zookeeper_dir=$vendors_dir/zookeeper

  echo "--------------------------------------------------------------------------------"
  echo -e "\033[1;32mOK, looks good!\033[0m"
  echo "You are going to install PredictionIO to: $pio_dir"
  echo -e "Vendor applications will go in: $vendors_dir\n"
  echo "Spark: $spark_dir"
  echo "Elasticsearch: $elasticsearch_dir"
  echo "HBase: $hbase_dir"
  echo "ZooKeeper: $zookeeper_dir"
  echo "--------------------------------------------------------------------------------"

  # Java Install
  echo -e "\033[1;36mStarting Java install...\033[0m"

  sudo apt-get update
  sudo apt-get install openjdk-7-jdk libgfortran3 -y

  echo -e "\033[1;32mJava install done!\033[0m"

  JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")
elif [[ "$1" == "-y" ]]; then
  # Non-interactive
  echo -e "\033[1;33mNon-interactive installation requested!\033[0m"
  echo -e "\033[1;33mForcing defaults!\033[0m"
  pio_dir=$PIO_DIR
  vendors_dir=$pio_dir/vendors

  spark_dir=$vendors_dir/spark-$SPARK_VERSION
  elasticsearch_dir=$vendors_dir/elasticsearch-$ELASTICSEARCH_VERSION
  hbase_dir=$vendors_dir/hbase-$HBASE_VERSION
  zookeeper_dir=$vendors_dir/zookeeper

  echo "--------------------------------------------------------------------------------"
  echo -e "\033[1;32mOK, looks good!\033[0m"
  echo "You are going to install PredictionIO to: $pio_dir"
  echo -e "Vendor applications will go in: $vendors_dir\n"
  echo "Spark: $spark_dir"
  echo "Elasticsearch: $elasticsearch_dir"
  echo "HBase: $hbase_dir"
  echo "ZooKeeper: $zookeeper_dir"
  echo "--------------------------------------------------------------------------------"

  # Java Install
  echo -e "\033[1;36mStarting Java install...\033[0m"

  sudo apt-get update
  sudo apt-get install openjdk-7-jdk libgfortran3 -y

  echo -e "\033[1;32mJava install done!\033[0m"

  JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")
else
  # Interactive
  while true; do
    echo -e "\033[1mWhere would you like to install PredictionIO?\033[0m"
    read -e -p "Installation path ($PIO_DIR): " pio_dir
    pio_dir=${pio_dir:-$PIO_DIR}

    read -e -p "Vendor path ($pio_dir/vendors): " vendors_dir
    vendors_dir=${vendors_dir:-$pio_dir/vendors}

    if confirm "Recieve updates?"; then
      guess_email=''
      if hash git 2>/dev/null; then
        # Git installed!
        guess_email=$(git config --global user.email)
      fi

      if [ -n "$guess_email" ]; then
        read -e -p "Email ($guess_email): " email
      else
        read -e -p "Enter email: " email
      fi
      email=${email:-$guess_email}

      url="http://direct.prediction.io/$PIO_VERSION/install.json/install/install/$email/"
      curl --silent $url > /dev/null
    fi

    spark_dir=$vendors_dir/spark-$SPARK_VERSION
    elasticsearch_dir=$vendors_dir/elasticsearch-$ELASTICSEARCH_VERSION
    hbase_dir=$vendors_dir/hbase-$HBASE_VERSION
    zookeeper_dir=$vendors_dir/zookeeper

    echo "--------------------------------------------------------------------------------"
    echo -e "\033[1;32mOK, looks good!\033[0m"
    echo "You are going to install PredictionIO to: $pio_dir"
    echo -e "Vendor applications will go in: $vendors_dir\n"
    echo "Spark: $spark_dir"
    echo "Elasticsearch: $elasticsearch_dir"
    echo "HBase: $hbase_dir"
    echo "ZooKeeper: $zookeeper_dir"
    echo "--------------------------------------------------------------------------------"
    if confirm "\033[1mIs this correct?\033[0m"; then
      break;
    fi
  done

  # Java Install
  if [[ $OS = "Linux" ]] && confirm "\033[1mWould you like to install Java?\033[0m"; then
    echo -e "\033[1mSelect your linux distribution:\033[0m"
    select distribution in "Debian/Ubuntu" "Other"; do
      case $distribution in
        "Debian/Ubuntu")
          echo -e "\033[1;36mStarting Java install...\033[0m"

          echo -e "\033[33mThis script requires superuser access!\033[0m"
          echo -e "\033[33mYou will be prompted for your password by sudo:\033[0m"

          sudo apt-get update
          sudo apt-get install openjdk-7-jdk libgfortran3 -y

          echo -e "\033[1;32mJava install done!\033[0m"
          break
          ;;
        "Other")
          echo -e "\033[1;31mYour disribution not yet supported for automatic install :(\033[0m"
          echo -e "\033[1;31mPlease install Java manually!\033[0m"
          exit 2
          ;;
        *)
          ;;
      esac
    done
  fi

  # Try to find JAVA_HOME
  echo "Locating JAVA_HOME..."
  if [[ "$OS" = "Darwin" ]]; then
    JAVA_VERSION=`echo "$(java -version 2>&1)" | grep "java version" | awk '{ print substr($3, 2, length($3)-2); }'`
    JAVA_HOME=`/usr/libexec/java_home`
  elif [[ "$OS" = "Linux" ]]; then
    JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")
  fi
  echo "Found: $JAVA_HOME"

  # Check JAVA_HOME
  while [ ! -f "$JAVA_HOME/bin/javac" ]; do
    echo -e "\033[1;31mJAVA_HOME is incorrect!\033[0m"
    echo -e "\033[1;33mJAVA_HOME should be a directory containing \"bin/javac\"!\033[0m"
    read -e -p "Please enter JAVA_HOME manually: " JAVA_HOME
  done;
fi

if [ -n "$JAVA_VERSION" ]; then
  echo "Your Java version is: $JAVA_VERSION"
fi
echo "JAVA_HOME is now set to: $JAVA_HOME"

# PredictionIO
echo -e "\033[1;36mStarting PredictionIO setup in:\033[0m $pio_dir"
cd $TEMP_DIR

# delete existing tmp file before download again
if [[ -e  $PIO_FILE ]]; then
  if confirm "Delete existing temporary file?"; then
    rm $PIO_FILE
  fi
fi

if [[ ! -e $PIO_FILE ]]; then
  echo "Downloading PredictionIO..."
  curl -O https://d8k1yxp8elc6b.cloudfront.net/$PIO_FILE
fi
tar zxf $PIO_FILE
rm -rf $pio_dir
mv PredictionIO-$PIO_VERSION $pio_dir

if [[ $USER ]]; then
  chown -R $USER $pio_dir
fi

echo "Updating ~/.profile to include: $pio_dir"
PATH=$PATH:$pio_dir/bin
echo "export PATH=\$PATH:$pio_dir/bin" >> $USER_PROFILE

echo -e "\033[1;32mPredictionIO setup done!\033[0m"

mkdir $vendors_dir

# Spark
echo -e "\033[1;36mStarting Spark setup in:\033[0m $spark_dir"
if [[ ! -e spark-$SPARK_VERSION-bin-hadoop2.4.tgz ]]; then
  echo "Downloading Spark..."
  curl -O http://d3kbcqa49mib13.cloudfront.net/spark-$SPARK_VERSION-bin-hadoop2.4.tgz
fi
tar xf spark-$SPARK_VERSION-bin-hadoop2.4.tgz
rm -rf $spark_dir
mv spark-$SPARK_VERSION-bin-hadoop2.4 $spark_dir

echo "Updating: $pio_dir/conf/pio-env.sh"
$SED_CMD "s|SPARK_HOME=/path_to_apache_spark|SPARK_HOME=$spark_dir|g" $pio_dir/conf/pio-env.sh

echo -e "\033[1;32mSpark setup done!\033[0m"

# Elasticsearch
echo -e "\033[1;36mStarting Elasticsearch setup in:\033[0m $elasticsearch_dir"
if [[ ! -e elasticsearch-$ELASTICSEARCH_VERSION.tar.gz ]]; then
  echo "Downloading Elasticsearch..."
  curl -O https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-$ELASTICSEARCH_VERSION.tar.gz
fi
tar zxf elasticsearch-$ELASTICSEARCH_VERSION.tar.gz
rm -rf $elasticsearch_dir
mv elasticsearch-$ELASTICSEARCH_VERSION $elasticsearch_dir

echo "Updating: $elasticsearch_dir/config/elasticsearch.yml"
echo 'network.host: 127.0.0.1' >> $elasticsearch_dir/config/elasticsearch.yml

echo "Updating: $pio_dir/conf/pio-env.sh"
echo "PIO_STORAGE_SOURCES_ELASTICSEARCH_HOME=$elasticsearch_dir" >> $pio_dir/conf/pio-env.sh

echo -e "\033[1;32mElasticsearch setup done!\033[0m"

# HBase
echo -e "\033[1;36mStarting HBase setup in:\033[0m $hbase_dir"
if [[ ! -e hbase-$HBASE_VERSION-hadoop2-bin.tar.gz ]]; then
  echo "Downloading HBase..."
  curl -O http://archive.apache.org/dist/hbase/hbase-$HBASE_VERSION/hbase-$HBASE_VERSION-hadoop2-bin.tar.gz
fi
tar zxf hbase-$HBASE_VERSION-hadoop2-bin.tar.gz
rm -rf $hbase_dir
mv hbase-$HBASE_VERSION-hadoop2 $hbase_dir

echo "Creating default site in: $hbase_dir/conf/hbase-site.xml"
cat <<EOT > $hbase_dir/conf/hbase-site.xml
<configuration>
  <property>
    <name>hbase.rootdir</name>
    <value>file://$hbase_dir/data</value>
  </property>
  <property>
    <name>hbase.zookeeper.property.dataDir</name>
    <value>$zookeeper_dir</value>
  </property>
</configuration>
EOT

echo "Updating: $hbase_dir/conf/hbase-env.sh to include $JAVA_HOME"
$SED_CMD "s|# export JAVA_HOME=/usr/java/jdk1.6.0/|export JAVA_HOME=$JAVA_HOME|" $hbase_dir/conf/hbase-env.sh

echo "Updating: $pio_dir/conf/pio-env.sh"
echo "PIO_STORAGE_SOURCES_HBASE_HOME=$hbase_dir" >> $pio_dir/conf/pio-env.sh
$SED_CMD "s|HBASE_CONF_DIR=\$PIO_HOME/conf|HBASE_CONF_DIR=$hbase_dir/conf|" $pio_dir/conf/pio-env.sh

echo -e "\033[1;32mHBase setup done!\033[0m"

echo "Updating permissions on: $vendors_dir"

if [[ $USER ]]; then
  chown -R $USER $vendors_dir
fi

echo -e "\033[1;32mInstallation done!\033[0m"





echo "--------------------------------------------------------------------------------"
echo -e "\033[1;32mInstallation of PredictionIO $PIO_VERSION complete!\033[0m"
echo -e "\033[1;33mIMPORTANT: You still have to start PredictionIO and dependencies manually:\033[0m"
echo -e "Run: '\033[1mpio-start-all\033[0m'"
echo -e "Check the status with: '\033[1mpio status\033[0m'"
echo -e "Use: '\033[1mpio [train|deploy|...]\033[0m' commands"
echo -e "Please report any problems to: \033[1;34msupport@prediction.io\033[0m"
echo -e "\033[1;34mDocumentation at: http://docs.prediction.io\033[0m"
echo "--------------------------------------------------------------------------------"
