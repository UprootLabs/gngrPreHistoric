set -e -x

rm -rf ../../Platform_Core/info/gngr/db/

H2_JAR=../../Platform_Core/lib/h2-1.4.180.jar

java -cp $H2_JAR org.h2.tools.RunScript -url "jdbc:h2:./test-db.h2" -user "sa" -password "" -script ../../Platform_Core/info/gngr/schema.sql

java -cp jOOQ-lib/jooq-3.4.0.jar:jOOQ-lib/jooq-meta-3.4.0.jar:jOOQ-lib/jooq-codegen-3.4.0.jar:$H2_JAR:./ org.jooq.util.GenerationTool ./jooq-config.xml

rm test-db.h2.*

