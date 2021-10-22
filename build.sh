#!/bin/bash

filelist=`find src/main/kotlin -name "*.kt" | xargs`

echo $filelist
kotlinc $filelist -include-runtime -d parse-release.jar