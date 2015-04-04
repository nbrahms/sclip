#!/bin/bash

VERSIONS=$@

echo "Publishing versions $VERSIONS"

sed -i .bak -e 's/version := "\(.*\)-SNAPSHOT"/version := "\1"/' build.sbt

for V in $VERSIONS
do
    sed -i .bak -e 's/scalaVersion := .*/scalaVersion := "'$V'"/' build.sbt
    sbt test publish
done

git co build.sbt

rm build.sbt.bak
