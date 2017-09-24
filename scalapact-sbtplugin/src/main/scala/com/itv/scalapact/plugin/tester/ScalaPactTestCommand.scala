package com.itv.scalapact.plugin.tester

import com.itv.scalapact.shared.{IPactReader, IPactWriter}
import com.itv.scalapactcore.common.ColourOuput._
import sbt._

import scala.io.Source
import scala.language.implicitConversions
import com.itv.scalapactcore.common.PactReaderWriter._

object ScalaPactTestCommand {

  lazy val pactTestCommandHyphen: Command = Command.command("pact-test")(pactTest)
  lazy val pactTestCommandCamel: Command = Command.command("pactTest")(pactTest)

  private lazy val pactTest: State => State = state => {

      println("*************************************".white.bold)
      println("** ScalaPact: Running tests        **".white.bold)
      println("*************************************".white.bold)

      println("> ScalaPact running: clean + test commands first")

      val cleanState = Command.process("clean", state)
      val testedState = Command.process("test", cleanState)

      println("*************************************".white.bold)
      println("** ScalaPact: Squashing Pact Files **".white.bold)
      println("*************************************".white.bold)

      val pactDir = new java.io.File("target/pacts")

      if (pactDir.exists && pactDir.isDirectory) {
        val files = pactDir.listFiles().toList.filter(f => f.getName.endsWith(".json"))

        println(("> " + files.length + " files found that could be Pact contracts").white.bold)
        println(files.map("> - " + _.getName).mkString("\n").white)

        val groupedFileList: List[(String, List[File])] = files.groupBy { f =>
          f.getName.split("_").take(2).mkString("_")
        }.toList

        val errorCount = groupedFileList.map { g =>
          squashPactFiles(g._1, g._2)
        }.sum

        println(("> " + groupedFileList.length + " pacts found:").white.bold)
        println(groupedFileList.map(g => "> - " + g._1.replace("_", " -> ") + "\n").mkString)
        println("> " + errorCount + " errors")

      } else {
        println("No Pact files found in 'target/pacts'. Make sure you have Pact CDC tests and have run 'sbt test' or 'sbt pact-test'.".red)
      }

      testedState
    }

  private def squashPactFiles(name: String, files: List[File])(implicit pactReader: IPactReader, pactWriter: IPactWriter): Int = {
    //Yuk!
    var errorCount = 0

    val pactList = {
      files.map { file =>
        val fileContents = try {
          val contents = Option(Source.fromFile(file).getLines().mkString)

          file.delete()

          contents
        } catch {
          case e: Throwable =>
            println(("Problem reading Pact file at path: " + file.getCanonicalPath).red)
            errorCount = errorCount + 1
            None
        }
        fileContents.flatMap { t =>
          pactReader.jsonStringToPact(t) match {
            case Right(r) => Option(r)
            case Left(_) =>
              errorCount += 1
              None
          }
        }
      }
        .collect { case Some(s) => s }
    }

    if (pactList.nonEmpty) {
      val head = pactList.head
      val combined = pactList.tail.foldLeft(head) { (accumulatedPact, nextPact) =>
        accumulatedPact.copy(interactions = accumulatedPact.interactions ++ nextPact.interactions)
      }

      PactContractWriter.writePactContracts(combined.provider.name)(combined.consumer.name)(pactWriter.pactToJsonString(combined))
    }

    errorCount
  }

}
