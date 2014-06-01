scalaVersion := "2.11.1"

resolvers += "Cloudera repo" at "https://repository.cloudera.com/artifactory/cloudera-repos"

libraryDependencies ++= Seq(
  "org.apache.hive" % "hive-jdbc" % "0.12.0-cdh5.0.0" excludeAll(
    ExclusionRule(organization = "junit"),
    ExclusionRule(organization = "org.apache.avro")
  ),
  "org.apache.hadoop" % "hadoop-common" % "2.3.0-cdh5.0.0",
  "org.apache.hadoop" % "hadoop-hdfs"   % "2.3.0-cdh5.0.0",
  "org.apache.hadoop" % "hadoop-common" % "2.3.0-cdh5.0.0"  % "test" classifier("tests"),
  "org.apache.hadoop" % "hadoop-hdfs"   % "2.3.0-cdh5.0.0"  % "test" classifier("tests"),
  "org.specs2"        %% "specs2"       % "2.3.12"          % "test"
)