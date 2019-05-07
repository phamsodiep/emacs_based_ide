#!/bin/bash

mkdir -p lib
MONGODB_JAVA_DRV=$(find ~/.m2/repository/org/mongodb -name mongo-java-driver-3.2.1.jar)
JDT_0=$(find ~/.m2/repository/org/eclipse -name org.eclipse.core.runtime-3.15.200.jar)
JDT_1=$(find ~/.m2/repository/org/eclipse -name org.eclipse.jdt.core-3.15.0.jar)
JDT_2=$(find ~/.m2/repository/org/eclipse -name org.eclipse.osgi-3.13.300.jar)
JDT_3=$(find ~/.m2/repository/org/eclipse -name org.eclipse.equinox.common-3.10.300.jar)
JDT_4=$(find ~/.m2/repository/org/eclipse -name org.eclipse.equinox.preferences-3.7.300.jar)
JDT_5=$(find ~/.m2/repository/org/eclipse -name org.eclipse.core.contenttype-3.7.300.jar)
JDT_6=$(find ~/.m2/repository/org/eclipse -name org.eclipse.core.jobs-3.10.300.jar)
JDT_7=$(find ~/.m2/repository/org/eclipse -name org.eclipse.core.resources-3.13.300.jar)

if [[ ! -z "$MONGODB_JAVA_DRV" ]]; then
  cp $MONGODB_JAVA_DRV lib
fi

if [[ ! -z "$JDT_0" ]]; then
  cp $JDT_0 lib
fi

if [[ ! -z "$JDT_1" ]]; then
  cp $JDT_1 lib
fi

if [[ ! -z "$JDT_2" ]]; then
  cp $JDT_2 lib
fi

if [[ ! -z "$JDT_3" ]]; then
  cp $JDT_3 lib
fi

if [[ ! -z "$JDT_4" ]]; then
  cp $JDT_4 lib
fi

if [[ ! -z "$JDT_5" ]]; then
  cp $JDT_5 lib
fi

if [[ ! -z "$JDT_6" ]]; then
  cp $JDT_6 lib
fi

if [[ ! -z "$JDT_7" ]]; then
  cp $JDT_7 lib
fi

echo "Please add: (set 'PWD (getenv \"PWD\"))"
echo "Please add: (setenv \"SRC_PARSER_WS\" PWD)"
echo "            => into your emacs init.el file"
