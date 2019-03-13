#! /usr/bin/env bash

cd `dirname .`
#sbt assembly

rm -rv tmp; mkdir tmp
cd tmp
ln -s ../deps .
java -jar ../skill.jar ../src/main/spec/oil.skill -L ogss --package ogss.oil -d ../lib
cd ..

rm -rv src/main/java/ogss/oil
cp -rv tmp/ogss src/main/java
rm -rv tmp

sbt assembly
cp target/scala*/ogss.jar .
