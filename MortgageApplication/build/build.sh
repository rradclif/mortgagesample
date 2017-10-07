#!/bin/sh
##############################################################################################
##
##  Sample of a build Command Line Interface (CLI) shell script to invoke a groovy script
##
##  This sample shell script is provided as an easy way to invoke the Mortgage Application
##  sample build from the command line. 
##
##  Usage:
##  build.sh [-p propDir] [-f srcfile] [-l listFile]
##   -p propDir : specify alternate location of startup.properties file (optional)
##   -f srcFile : specify a program to build (relative path from git repo) (optional)
##   -l listFile : specify the file containing the list of files to build (optional)
##  
##  Examples:
##  build.sh -p /u/usr1/test/build  -f app1/cobol/prctv.cbl
##  build.sh -p /u/usr1/test/build  -l /u/usr1/test/build/buildList.txt
##    
###############################################################################################

# Set the DBB bin directory
DBB_HOME=

# Shell script arguments (can use to set defaults)
BUILD_FILE=
BUILD_LIST=
PROP_DIR=

while [ $# -gt 0 ]
do
   case "$1" in
     -f | --BUILD_FILE)       BUILD_FILE="$2"; shift;;
     -l | --BUILD_LIST)       BUILD_LIST="$2"; shift;;
     -p | --PROP_DIR)         PROP_DIR="$2"; shift;;
     -* | --*)                echo >&2 \
                              "Unknown argument $1."
                              exit 1;;
     *)                       break;;
   esac
   shift
done

export LIBPATH=$LIBPATH:$DBB_HOME/lib
export GROOVY_HOME=$DBB_HOME/groovy-2.4.12

CMD="$DBB_HOME/groovy-2.4.12/bin/groovy"

CLASSPATH="-classpath $DBB_HOME/lib/dbb-0.6.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/dbb-ext-0.9.9.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/dbb-html-1.0.0.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/libDBB_JNI64.so"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/httpclient-4.5.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/httpcore-4.4.1.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/log4j-1.2.15.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/commons-codec-1.10.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/commons-logging-1.0.4.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/JSON4J.jar"
CLASSPATH="$CLASSPATH:$DBB_HOME/lib/com.ibm.dmh.scan.classify.jar"


ARGS="-DpropDir=$PROP_DIR -DbuildFile=$BUILD_FILE -DbuildList=$BUILD_LIST build.groovy"

echo $CMD $ARGS
$CMD $CLASSPATH $ARGS
