#! /usr/bin/env bash

cd `dirname .`
#sbt assembly

rm -rv tmp; mkdir tmp
cd tmp
ln -s ../deps .
java -jar ../ogss.jar build ../src/main/spec/oil.skill -L Java --package ogss.oil -d ../lib
cd ..

rm -rv src/main/java/ogss/oil
cp -rv tmp/ogss src/main/java
rm -rv tmp

sbt assembly
cp target/scala*/ogss.jar .
