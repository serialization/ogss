#! /usr/bin/env bash

cd `dirname .`

rm -rv src/main/scala/ogss/oil

java -jar ogss.jar build src/main/spec/oil.skill -L Scala --package ogss.oil -o src/main/scala -d lib

sbt clean assembly
cp target/scala*/ogss.jar .
