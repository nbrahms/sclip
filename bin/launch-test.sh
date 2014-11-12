#!/bin/bash

DIR=$(dirname $0)
WHOAMI=`whoami`
CLASSPATH="$DIR/../target/scala-2.10/classes:$DIR/../target/scala-2.10/test-classes:/Users/$WHOAMI/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.10.5.jar"

java -cp $CLASSPATH org.nbrahms.sclip.Application --host 127.0.0.1
