#!/bin/bash
mvn clean
mvn install -DskipTests -Dcheckstyle.skip=true -Djava.util.logging.config.file=core/src/test/resources/logging.properties
