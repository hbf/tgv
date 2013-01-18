import sbt._
import Keys._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.{EclipseKeys, EclipseCreateSrc}

// EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

object TgvBuild extends Build {

  lazy val buildSettings = Defaults.defaultSettings ++ multiJvmSettings ++ Seq(
    organization := "com.dreizak.tgv",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.10.0"
  )
  
  lazy val tgv = Project(
    id = "tgv",
    base = file("."),
    settings = buildSettings ++
      Seq(libraryDependencies ++= Dependencies.Tgv) ++
      Seq(
        EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,
        EclipseKeys.withSource := true,
        EclipseKeys.configurations := Set(Compile, Test, MultiJvm),
        resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
        resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/repo",
        // See https://groups.google.com/forum/?fromgroups=#!topic/scalatest-users/vg0xrVC5jxU
        testOptions <+= (target in Test) map {
          t => Tests.Argument(TestFrameworks.ScalaTest, "junitxml(directory=\"%s\")" format (t / "test-reports"))
        }
      )
  ).
    configs(MultiJvm).
    // Ex.: dependency-graph-ml (see https://github.com/jrudolph/sbt-dependency-graph#readme)
    settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)

  // See http://doc.akka.io/docs/akka/snapshot/dev/multi-jvm-testing.html
  lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    
    // disable parallel tests
    // parallelExecution in Test := false,
    // jvmOptions in MultiJvm := Seq("-agentpath:/Applications/YourKit_Java_Profiler_11.0.9.app/bin/mac/libyjpagent.jnilib"),
    
    // make sure that MultiJvm tests are executed by the default test target
    executeTests in Test <<=
      ((executeTests in Test), (executeTests in MultiJvm)) map {
        case ((_, testResults), (_, multiJvmResults)) =>
          val results = testResults ++ multiJvmResults
          (Tests.overall(results.values), results)
      }
  )

  object Dependencies {
    //========================== Logging ================================================================================================================================
    //val logback              = "ch.qos.logback"              % "logback-classic"                      % "1.0.7"
    val log4j                  = "log4j"                       % "log4j"                                % "1.2.16"
    val slf4j                  = "org.slf4j"                   % "slf4j-api"                            % "1.6.6"
    val slf4jlog4j             = "org.slf4j"                   % "slf4j-log4j12"                        % "1.6.6"
    val slf4s                  = "com.weiglewilczek.slf4s"     % "slf4s_2.9.1"                          % "1.0.7"
    
    //========================== Utilities ==============================================================================================================================
    val guava                  = "com.google.guava"            % "guava"                                % "13.0.1"
    val jsr305ForGuava         = "com.google.code.findbugs"    % "jsr305"                               % "1.3.9"
  
    //========================== Dependency injection ===================================================================================================================  
    val scalaGuice             = "net.codingwell"              %% "scala-guice"                         % "3.0.2"
    
    //========================== Configuration ==========================================================================================================================  
    val rocoto                 = "org.99soft.guice"            % "rocoto"                               % "6.1"

    //========================== Akka ===================================================================================================================================
    val iteratees              = "play"                        % "play-iteratees_2.10"                  % "2.1-RC2"
    
    //========================== Caching ================================================================================================================================  
  
    //========================== HTTP ===================================================================================================================================
    val asyncHttpClient        = "com.ning"                    % "async-http-client"                    % "1.8.0-SNAPSHOT"
    val commonsLang            = "commons-lang"                % "commons-lang"                         % "2.6"
    //val gson                   = "com.google.code.gson"        % "gson"                                 % "2.2.2"
    //val xom                    = "xom"                         % "xom"                                  % "1.2.5"
    //val json4s                 = "org.json4s"                  % "json4s-native_2.10.0-RC3"             % "3.1.0-SNAPSHOT"
    //val json4sJackson          = "org.json4s"                  % "json4s-jackson_2.10.0-RC3"            % "3.1.0-SNAPSHOT"
    //val scalesXml              = "org.scalesxml"               % "scales-xml_2.10.0-RC2"                % "0.4.4"

    //========================== Misc ==================================================================================================================================
    
    object Test {
      val junit                = "junit"                       % "junit"                                % "4.10"                    % "test"
      val scalaTest            = "org.scalatest"               % "scalatest_2.10.0-RC3"                 % "2.0.M5-B1"                  % "test"
      val mockito              = "org.mockito"                 % "mockito-all"                          % "1.9.0"                   % "test"
      val jmock                = "org.jmock"                   % "jmock"                                % "2.5.1"                   % "test" // for deterministic scheduler tests
      val junitIntf            = "com.novocode"                % "junit-interface"                      % "0.8"                     % "test"
      val jetty                = "org.eclipse.jetty"           % "jetty-server"                         % "8.1.0.v20120127"         % "test"
    }

    val Tgv = Seq(log4j, slf4jlog4j, slf4s, guava, jsr305ForGuava, scalaGuice, rocoto, iteratees, asyncHttpClient, commonsLang, Test.junit, Test.scalaTest, Test.mockito, Test.jmock, /*Test.junitIntf,*/ Test.jetty)
  }
}