import java.io.PrintWriter

scalaVersion := "2.11.1"

fork in test := true

resolvers += "Cloudera repo" at "https://repository.cloudera.com/artifactory/cloudera-repos"

ivyConfigurations += config("hadoop")

libraryDependencies ++= Seq(
  "org.apache.hadoop" % "hadoop-client"   % "2.3.0-mr1-cdh5.0.0"  % "hadoop",
  "org.apache.hive"   % "hive-exec"       % "0.12.0-cdh5.0.0"     % "hadoop",
  "org.apache.hive"   % "hive-jdbc"     % "0.12.0-cdh5.0.0" excludeAll(
    ExclusionRule(organization = "junit"),
    ExclusionRule(organization = "org.apache.avro")
  ),
  "org.apache.hadoop" % "hadoop-core"     % "2.3.0-mr1-cdh5.0.0",
  "org.apache.hadoop" % "hadoop-common"   % "2.3.0-cdh5.0.0",
  "org.apache.hadoop" % "hadoop-hdfs"     % "2.3.0-cdh5.0.0",
  "org.apache.hadoop" % "hadoop-common"   % "2.3.0-cdh5.0.0"      % "test" classifier("tests"),
  "org.apache.hadoop" % "hadoop-hdfs"     % "2.3.0-cdh5.0.0"      % "test" classifier("tests"),
  "org.apache.hadoop" % "hadoop-test"     % "2.3.0-mr1-cdh5.0.0"  % "test" exclude("net.java.dev.jets3t", "jets3t"),
  "org.apache.hadoop" % "hadoop-auth"     % "2.3.0-cdh5.0.0"      % "test",
  "org.specs2"        %% "specs2"         % "2.3.12"              % "test"
)

val dumpHadoopClasspath = TaskKey[Unit]("dump-hadoop-classpath", "Dumps hadoop classpath in a file")

dumpHadoopClasspath := {
  val printer = new PrintWriter("hadoop.classpath")
  printer.print(update.value.select(configurationFilter("hadoop")).map(_.getCanonicalPath).mkString(":"))
  printer.close()
}

test in Test <<= (test in Test) dependsOn dumpHadoopClasspath
