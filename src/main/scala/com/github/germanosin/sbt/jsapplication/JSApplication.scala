package com.github.germanosin.sbt.jsapplication

import sbt._
import com.typesafe.sbt.web._
import sbt.Keys._
import sbt.Task
import com.typesafe.sbt.web.incremental.{OpInputHasher, OpSuccess}
import sbt.Configuration
import xsbti.{Severity, Problem}
import com.typesafe.sbt.web.incremental._


object Import {

  object JSApplicationKeys {
    val jsapplication = TaskKey[Seq[File]]("jsapplication", "Invoke the jsapplication compiler")
    val sourceMap = SettingKey[Boolean]("jsapplication-source-map", "Outputs a v3 sourcemap.")
    val compilationLevel = SettingKey[String]("jsapplication-compress", "Compress output format.")
    val fileInputHasher = TaskKey[OpInputHasher[File]]("jsapplication-task-file-input-hasher", "A function that hashes a given file.")
    val taskMessage = SettingKey[String]("jsapplication-task-message", "The message to output for a task")
  }

}

object JSApplication extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  val autoImport = Import

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport.JSApplicationKeys._

  private[jsapplication] type FileOpResultMappings = Map[File, OpResult]

  private[jsapplication] def FileOpResultMappings(s: (File, OpResult)*): FileOpResultMappings = Map(s: _*)

  val jsApplicationUnscopedSettings = Seq(
    includeFilter := GlobFilter("main.jsapplication")
  )


  def jsApplicationFilesTask(task: TaskKey[Seq[File]],
                    config: Configuration): Def.Initialize[Task[Seq[File]]] = Def.task {
    val sources = ((Keys.sources in task in config).value ** ((includeFilter in task in config).value -- (excludeFilter in task in config).value)).get

    implicit val opInputHasher = (fileInputHasher in task in config).value

    val results: (Set[File], Seq[Problem]) = incremental.syncIncremental((streams in config).value.cacheDirectory / "run", sources) {
      modifiedSources: Seq[File] =>
        if (modifiedSources.size > 0) {
          streams.value.log.info(s"${(taskMessage in task in config).value} on ${
            modifiedSources.size
          } source(s)")

          val resultBatches: Seq[(FileOpResultMappings, Seq[Problem])] = modifiedSources.filter(f => f.exists()).filterNot(f => f.isDirectory).pair(relativeTo((sourceDirectories in task in config).value)) map {
            case (modifiedSource,modifiedPath)  => {
              val targetDir = (resourceManaged in task in config).value
              val taskCompilationLevel = (compilationLevel in task in config).value
              val isSourceMap = (sourceMap in task in config).value

              val replaceName = ".min.js"
              val targetJs = targetDir / modifiedPath.replaceAll("\\.jsapplication", replaceName)
              val targetMap = targetDir / modifiedPath.replaceAll("\\.jsapplication", ".js.map")
              val targetPath = targetJs.getParentFile
              val sourceDirs = List(modifiedSource.getParentFile) ++ (sourceDirectories in task in config).value ++ (resourceDirectories in task in config).value ++ (webModuleDirectories in task in config).value

              if (!targetPath.exists) targetPath.mkdirs()

              val compiler = new JSApplicationCompiler(modifiedSource, targetJs, targetMap, sourceDirs, isSourceMap, taskCompilationLevel)
              compiler.compile()
            }
          }

          resultBatches.foldLeft((FileOpResultMappings(), Seq[Problem]())) {
            (allCompletedResults, completedResult) =>
              val (prevOpResults, prevProblems) = allCompletedResults
              val (nextOpResults, nextProblems) = completedResult
              (prevOpResults ++ nextOpResults, prevProblems ++ nextProblems)
          }

        } else {
          (FileOpResultMappings(), Nil)
        }
    }

    val (filesWritten, problems) = results

    CompileProblems.report((reporter in task).value, problems)

    filesWritten.toSeq
  }

  override def projectSettings: Seq[Setting[_]] =  inTask(jsapplication)(
    inConfig(Assets)(jsApplicationUnscopedSettings) ++
      inConfig(TestAssets)(jsApplicationUnscopedSettings) ++
      Seq(
        moduleName := "JSApplication",
        taskMessage in Assets := "JSApplication compiling",
        taskMessage in TestAssets := "JSApplication test compiling",
        compilationLevel in Assets := "SIMPLE",
        compilationLevel in TestAssets := "SIMPLE",
        includeFilter in Assets := "*.jsapplication",
        excludeFilter in TestAssets := "_*.jsapplication",
        sourceMap in Assets := true,
        sourceMap in TestAssets := true
      )
  )  ++ addJSApplicationFilesTasks(jsapplication) ++ Seq(
    includeFilter in jsapplication := "*.jsapplication",
    excludeFilter in jsapplication := "_*.jsapplication",
    target in jsapplication := webTarget.value / jsapplication.key.label,
    fileInputHasher := OpInputHasher[File](f =>
      OpInputHash.hashString(f.getAbsolutePath)
    ),
    jsapplication in Assets := (jsapplication in Assets).dependsOn(webModules in Assets).value,
    jsapplication in TestAssets := (jsapplication in TestAssets).dependsOn(webModules in TestAssets).value
  )

  def  addJSApplicationFilesTasks(sourceFileTask: TaskKey[Seq[File]]) : Seq[Setting[_]] = {
    Seq(
      sourceFileTask in Assets := jsApplicationFilesTask(sourceFileTask, Assets).value,
      sourceFileTask in TestAssets := jsApplicationFilesTask(sourceFileTask, TestAssets).value,
      resourceManaged in sourceFileTask in Assets := webTarget.value / sourceFileTask.key.label / "main",
      resourceManaged in sourceFileTask in TestAssets := webTarget.value / sourceFileTask.key.label / "test",
      sourceFileTask := (sourceFileTask in Assets).value
    )  ++
      inConfig(Assets)(addUnscopedJsApplicationSourceFileTasks(sourceFileTask)) ++
      inConfig(TestAssets)(addUnscopedJsApplicationSourceFileTasks(sourceFileTask))
  }

  private def addUnscopedJsApplicationSourceFileTasks(sourceFileTask: TaskKey[Seq[File]]): Seq[Setting[_]] = {
    Seq(
      resourceGenerators <+= sourceFileTask,
      managedResourceDirectories += (resourceManaged in sourceFileTask).value
    )
  }
}