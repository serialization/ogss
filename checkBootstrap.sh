#! /usr/bin/env bash

# reset ogss jar to match version from repository
git checkout ogss.jar

# build a new jar
./bootstrap.sh

# build OIL with the new jar
./bootstrap.sh

# ensure that the new OIL can build itself
./bootstrap.sh
