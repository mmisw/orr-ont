package org.mmisw.orr.ont.service

/**
 * preliminary
 */
trait TripleStoreService {

  def getSize(contextOpt: Option[String] = None): Either[Throwable, String]

  def loadUri(uri: String, formats: Map[String, String]): Either[Throwable, String]

  def reloadUri(uri: String): Either[Throwable, String]

  def unloadUri(uri: String): Either[Throwable, String]

}
