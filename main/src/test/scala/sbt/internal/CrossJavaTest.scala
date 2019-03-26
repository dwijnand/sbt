/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import org.scalatest._
import sbt.internal.CrossJava.JavaDiscoverConfig._

class CrossJavaTest extends FunSuite with DiagrammedAssertions {
  test("The Java home selector should select the most recent") {
    assert(
      List("jdk1.8.0.jdk", "jdk1.8.0_121.jdk", "jdk1.8.0_45.jdk")
        .sortWith(CrossJava.versionOrder)
        .last == "jdk1.8.0_121.jdk"
    )
  }

  test("The Linux Java home selector should correctly pick up Fedora Java installations") {
    val conf = new LinuxDiscoverConfig(sbt.io.syntax.file(".")) {
      override def candidates(): Vector[String] =
        """
            |java-1.8.0-openjdk-1.8.0.162-3.b12.fc28.x86_64
            |java-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64
            |java-1.8.0
            |java-1.8.0-openjdk
            |java-openjdk
            |jre-1.8.0
            |jre-1.8.0-openjdk
            |jre-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64
            |jre-openjdk
          """.stripMargin.split("\n").filter(_.nonEmpty).toVector
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.8")
    assert(file.getName == "java-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64")
  }

  test("The Linux Java home selector should correctly pick up Oracle RPM installations") {
    val conf = new LinuxDiscoverConfig(sbt.io.syntax.file(".")) {
      override def candidates(): Vector[String] = Vector("jdk1.8.0_172-amd64")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.8")
    assert(file.getName == "jdk1.8.0_172-amd64")
  }

  test("The Windows Java home selector should correctly pick up a JDK") {
    val conf = new WindowsDiscoverConfig(sbt.io.syntax.file(".")) {
      override def candidates() = Vector("jdk1.7.0")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.7")
    assert(file.getName == "jdk1.7.0")
  }

  test("The JAVA_HOME selector should correctly pick up a JDK") {
    val conf = new JavaHomeDiscoverConfig {
      override def home() = Some("/opt/jdk8")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "8")
    assert(file.getName == "jdk8")
  }

  test("The JAVA_HOME selector correctly pick up an Oracle JDK") {
    val conf = new JavaHomeDiscoverConfig {
      override def home() = Some("/opt/oracle-jdk-bin-1.8.0.181")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.8")
    assert(file.getName == "oracle-jdk-bin-1.8.0.181")
  }
}
