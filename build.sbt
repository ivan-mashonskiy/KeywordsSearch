name := "KeywordSearch"
 
version := "1.0"

lazy val `keywordsearch` = (project in file(".")).enablePlugins(PlayScala)
      
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
      
scalaVersion := "2.12.2"

libraryDependencies ++= Seq( ws, guice )


      