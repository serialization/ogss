#! /usr/bin/env bash

cd `dirname .`

rm -rv src/main/java/ogss/oil

java -jar ogss.jar build src/main/spec/oil.skill -L Java --package ogss.oil -o src/main/java -d lib

sbt clean assembly
cp target/scala*/ogss.jar .
