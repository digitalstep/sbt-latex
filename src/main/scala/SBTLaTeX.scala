import sbt._
import Keys._

object SBTLaTeX extends Plugin {
  val pdfLatex = SettingKey[String]("pdflatex", "Executable for PDF generation, such as pdflatex, xelatex, ...")
  val pdfLatexDefault = pdfLatex := "pdflatex"

  val useBibTex = SettingKey[Boolean]("use-bibtex", "Flag whether to use BibTex or not")
  val useBibTexDefault = useBibTex := false

  val latexSourceDirectory = TaskKey[File](
    "latex-source-directory",
    "LaTeX source directory")

  val latexSourceDirectoryDefinition = latexSourceDirectory <<= baseDirectory map {
    _ / "src" / "main" / "latex"
  }

  val latexSourceFiles = TaskKey[Seq[File]]("latex-source-files", "LaTeX source files")

  val latexSourceFileDefinition = latexSourceFiles <<= latexSourceDirectory map { dir ⇒
    (dir ** "*.tex").get.filterNot(_.getPath.contains("#"))
  }

  val latexUnmanagedBase = TaskKey[File]("latex-unmanaged-base",
    "Directory containing external files needed to build the PDF, e.g. *.sty, *.bst")

  val latexUnmanagedBaseDefinition = latexUnmanagedBase <<= unmanagedBase map identity

  val latexResourceDirectory = TaskKey[File]("latex-resource-directory",
    "Directory containing files needed to build the PDF, e.g. *.bib, *.png")

  val latexResourceDirectoryDefinition =
    latexResourceDirectory <<= (resourceDirectory in Compile) map identity

  val latex = TaskKey[Unit](
    "latex",
    "Compiles LaTeX source to PDF")

  val latexDefinition = latex <<=
    (pdfLatex, useBibTex, latexSourceDirectory, latexSourceFiles, latexUnmanagedBase, latexResourceDirectory, cacheDirectory, target, streams) map {
      (pdfLatex, useBibTex, latexSourceDirectory, latexSourceFiles, latexUnmanagedBase, latexResourceDirectory, cacheDirectory, target, streams) =>
        // Create the cache directory and copy the source files and dependencies
        // there.

        val latexCache = cacheDirectory / "latex"

        def toCache(files: File*) = {
          IO.createDirectory(latexCache)
          files.foreach(IO.copyDirectory(_, latexCache))
        }

        toCache(latexSourceDirectory, latexUnmanagedBase, latexResourceDirectory)

        for (latexSourceFile <- latexSourceFiles) {
          val pdflatex = Process(
            Seq(pdfLatex,
              "-file-line-error", // tell xelatex to quit if there's an error, not drop
              "-halt-on-error", // into some arcane, ancient, xelatex shell.
              latexSourceFile.getName),
            latexCache)

          val bibtex = Process(Seq("bibtex", latexSourceFile.getName.replace(".tex", ".aux")), latexCache)

          // TODO: Handle build error.
          if (useBibTex) {
            pdflatex.!
            bibtex.!
            pdflatex.!
            pdflatex.!
          } else {
            pdflatex.!
          }

          //////////////////////////////////////////////////////////////////////

          // Copy it to the final destination.
          val pdfName = latexSourceFile.getName.replace(".tex", ".pdf")
          IO.copyFile(latexCache / pdfName, target / pdfName)
          streams.log.info("PDF written to %s.".format(target / pdfName))
        }
    }

  //////////////////////////////////////////////////////////////////////////////

  val watchSourcesDefinition = watchSources <++=
    (latexSourceFiles, latexUnmanagedBase, latexResourceDirectory) map {
      (latexSourceFiles, latexUnmanagedBase, latexResourceDirectory) =>
        latexSourceFiles ++ Seq(
          latexUnmanagedBase,
          latexResourceDirectory)
    }

  //////////////////////////////////////////////////////////////////////////////

  override val settings = Seq(
    sbtPlugin := true,
    name := "sbt-latex",
    latexSourceDirectoryDefinition,
    latexSourceFileDefinition,
    latexUnmanagedBaseDefinition,
    latexResourceDirectoryDefinition,
    latexDefinition,
    pdfLatexDefault,
    useBibTexDefault,
    watchSourcesDefinition)
}
