package org.mmisw.orr.ont

case class Ontology(uri:    String,
                    name:   String
                   )

case class User(id:          Option[String],
                userName:    String,
                firstName:   String,
                lastName:    String
               )

case class Result(uri:    String,
                  comment:   String
                 )
