package org.mmisw.orr.ont.util

import java.io.File

object FileUtils {

  /**
    * @param file      The file to make readable
    * @param baseDir   Limit to adjust permissions in ancestor directories (by default, `/`)
    * @return          None or Some("error: ...")
    */
  // introduced for #29 "triple store not being updated"
  def makeReadableByAnyone(file: File, baseDir: File = new File("/")): Option[String] = {
    var f = file
    while (f != null) {
      if (f.isFile || f.isDirectory) {
        lazy val abs = f.getAbsolutePath
        try {
          if (!f.setReadable(true, false))
            return Some(s"error: could not set readable to $abs")
          if (f.isDirectory && !f.setExecutable(true, false))
            return Some(s"error: could not set executable to $abs")
        }
        catch {
          case e: SecurityException =>
            return Some(s"error: SecurityException for $abs: ${e.getMessage}")
        }
      }
      val p = f.getParentFile
      f = if (baseDir != f && baseDir != p) p else null
    }
    None
  }
}
