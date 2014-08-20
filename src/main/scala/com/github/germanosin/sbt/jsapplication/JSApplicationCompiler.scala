package com.github.germanosin.sbt.jsapplication

import java.io.{FileWriter, File}

import com.github.germanosin.sbt.jsapplication.JSApplication.FileOpResultMappings
import com.google.common.collect.ImmutableMap
import com.google.javascript.jscomp.CompilerOptions.LanguageMode
import com.google.javascript.jscomp._
import com.typesafe.config.{ConfigOrigin, ConfigException, ConfigFactory, Config}
import com.typesafe.sbt.web.incremental.{OpFailure, OpSuccess}
import com.typesafe.sbt.web.{LineBasedProblem, GeneralProblem}
import sbt.File
import xsbti.{Severity, Problem}
import com.google.javascript.jscomp.{Compiler => ClosureCompiler}
import scala.collection.JavaConversions._

/**
 * Created by germanosin on 19.08.14.
 *
 */
case class JSApplicationFile(val files:List[File], compilerOptions:CompilerOptions)



class JSApplicationCompiler(val source:File, target:File, targetSourceMap:File, sourceDirs:List[File], isSourceMap:Boolean, compilationLevel:String) {

  def compile():(FileOpResultMappings,Seq[Problem]) = {
    val compiler = new ClosureCompiler()
    val options = new CompilerOptions()

    if (isSourceMap) {

      val sourceMapLocationMappings = sourceDirs.map(
        sourceDir => new SourceMap.LocationMapping(sourceDir.getAbsolutePath.replaceAll("\\\\","\\/")+"/", "")
      ).toList



      options.setSourceMapOutputPath(targetSourceMap.getAbsolutePath)
      options.setSourceMapFormat(SourceMap.Format.DEFAULT)
      options.setSourceMapLocationMappings(sourceMapLocationMappings)
    }

    Option(compilationLevelMap.get(compilationLevel) : CompilationLevel).
      getOrElse(CompilationLevel.SIMPLE_OPTIMIZATIONS).
        setOptionsForCompilationLevel(options)

    readJsApplicationFile(source, sourceDirs, options) match {
      case Left(jsApplicationFile) => {
        val files = jsApplicationFile.files
        val compilerOptions = jsApplicationFile.compilerOptions
        val inputs = files.map(f => SourceFile.fromFile(f))
        val externs:List[SourceFile] = List.empty
        val result = compiler.compile(externs,inputs,compilerOptions)
        result.success match {
          case true => {
            val readSources = files.toSet ++ Set(source)
            var writtenSources:Set[File] = Set(target)

            val sourceMapAddon = if (isSourceMap) s"""
              //# sourceMappingURL=${targetSourceMap.getName}
            """ else ""
            val sourceContent = compiler.toSource + sourceMapAddon
            // //# sourceMappingURL=/path/to/file.js.map
            scala.tools.nsc.io.File(target).writeAll(sourceContent)            


            if (isSourceMap) {
              targetSourceMap.createNewFile()
              val sourceMapWriter = new FileWriter(targetSourceMap)
              compiler.getSourceMap.appendTo(sourceMapWriter,target.getName)
              sourceMapWriter.flush()
              sourceMapWriter.close()
              writtenSources = writtenSources ++ Set(targetSourceMap)
            }

            (FileOpResultMappings( source -> OpSuccess(readSources, writtenSources)), List.empty)
          }
          case false => {
            val errors = result.errors.map(
              error => {
                files.find(_.getAbsolutePath == error.sourceName).map(
                  file =>  new LineBasedProblem(error.description, Severity.Error, error.getLineNumber, error.getCharno, getFileLine(file,error.getLineNumber),file)
                )
              }
            ).flatten
            (FileOpResultMappings( source -> OpFailure), errors)
          }
        }
      }
      case Right(errors) => (FileOpResultMappings( source -> OpFailure), errors)
    }
  }

  def readJsApplicationFile(source:File, sourceDirs:List[File], options:CompilerOptions):Either[JSApplicationFile,Seq[Problem]] = {
    try {
      val config = ConfigFactory.parseFile(source).resolve()
      val additionalSourceDirs = readOption(config.getStringList("sourceDirs")).map(
        _.map(
          sourceDir => new File(sourceDir)
        ).filter(_.exists)
      ).getOrElse(List())
      val allSourceDirs = sourceDirs ++ additionalSourceDirs
      val results = config.getStringList("files").map(
        f => findFile(f, sourceDirs) match {
          case Some(file) => file
          case None => new GeneralProblem("File didn't exists: "+f, source)
        }
      ).toList

      val problems = results.filter(_.isInstanceOf[Problem])

      readCompilerOptions(config,options)

      problems.isEmpty match {
        case true => Left(JSApplicationFile(results.map( x=> x.asInstanceOf[File]),options))
        case false => Right(problems.map( x=> x.asInstanceOf[Problem]))
      }

    } catch {
      case e: ConfigException => Right(Seq(configError(e.origin, e.getMessage, e, source)))
    }
  }

  private def readCompilerOptions(config:Config, compilerOptions:CompilerOptions) {
    readOption(config.getConfig("options")).map(
      optionsConfig => {

        readOption(optionsConfig.getBoolean("checkSymbols")).map(
          checkSymbols => compilerOptions.setCheckSymbols(checkSymbols)
        )

        readOption(optionsConfig.getBoolean("transformAmdModules")).map(
          transformAmdModules => compilerOptions.setCheckSymbols(transformAmdModules)
        )

        readOption(optionsConfig.getBoolean("processClosurePrimitives")).map(
          processClosurePrimitives => compilerOptions.setClosurePass(processClosurePrimitives)
        )

        readOption(optionsConfig.getBoolean("manageClosureDependencies")).map(
          manageClosureDependencies => compilerOptions.setManageClosureDependencies(manageClosureDependencies)
        )

        readOption(optionsConfig.getString("compilationLevel")).map(
          compilationLevel => {
            Option(compilationLevelMap.get(compilationLevel) : CompilationLevel).
              getOrElse(CompilationLevel.SIMPLE_OPTIMIZATIONS).
              setOptionsForCompilationLevel(compilerOptions)
          }
        )

        readOption(optionsConfig.getString("languageIn")).map(
          languageIn => Option(CompilerOptions.LanguageMode.fromString(languageIn) : LanguageMode ).map(
            languageMode => compilerOptions.setLanguageIn(languageMode)
          )
        )

        readOption(optionsConfig.getString("languageOut")).map(
          languageOut => Option(CompilerOptions.LanguageMode.fromString(languageOut) : LanguageMode ).map(
            languageMode => compilerOptions.setLanguageIn(languageMode)
          )
        )
      }
    )
  }

  private def configError(origin: ConfigOrigin, message: String, e: Throwable, source:File):Problem = {
    Option(origin: ConfigOrigin).map(
      origin => Option(origin.lineNumber(): java.lang.Integer).map(
        line => new LineBasedProblem(e.getMessage,Severity.Error,origin.lineNumber(),0,getFileLine(source,line),source)
      ).getOrElse(
          new GeneralProblem(e.getMessage, source)
        )
    ).getOrElse(
        new GeneralProblem(e.getMessage, source)
      )
  }

  private def readOption[T](v: => T): Option[T] = {
    try {
      Option(v)
    } catch {
      case e: Exception => None
    }
  }

  def findFile(filename:String , sourceDirs:List[File]):Option[File] =
    sourceDirs.find(new File(_, filename).exists) match {
      case Some(sourceDir) => Some(new File(sourceDir, filename))
      case None => None
    }

  def getFileLine(source:File, lineNum:Int):String = {
    val file = try Left(io.Source.fromFile(source)) catch {
      case exc : Throwable=> Right(exc.getMessage)
    }
    val line = lineNum-1;
    val eitherLine = (for(f <- file.left;
                          line <- f.getLines.toStream.drop(line).headOption.toLeft("too few lines").left) yield
      if (line == "") Right("line is empty") else Left(line)).joinLeft

    eitherLine match {
      case Left(lineString) => lineString
      case _ => ""
    }
  }

  private def compilationLevelMap  =
    ImmutableMap.of(
      "WHITESPACE_ONLY",
      CompilationLevel.WHITESPACE_ONLY,
      "SIMPLE",
      CompilationLevel.SIMPLE_OPTIMIZATIONS,
      "SIMPLE_OPTIMIZATIONS",
      CompilationLevel.SIMPLE_OPTIMIZATIONS,
      "ADVANCED",
      CompilationLevel.ADVANCED_OPTIMIZATIONS,
      "ADVANCED_OPTIMIZATIONS",
      CompilationLevel.ADVANCED_OPTIMIZATIONS)
}
