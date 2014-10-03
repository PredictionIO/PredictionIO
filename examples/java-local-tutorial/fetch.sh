#!/bin/bash

set -e

TARGET_BASE_DIR="data"

mkdir -p $TARGET_BASE_DIR
curl http://files.grouplens.org/papers/ml-100k.zip -o $TARGET_BASE_DIR/ml-100k.zip
cd $TARGET_BASE_DIR
unzip ml-100k.zip

echo "Data dir: ${TARGET_BASE_DIR}/ml-100k/"


