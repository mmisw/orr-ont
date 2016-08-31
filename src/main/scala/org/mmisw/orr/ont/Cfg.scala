// generated by tscfg 0.3.2 on Wed Aug 31 13:42:28 PDT 2016
// source: src/main/resources/orront.spec.conf

package org.mmisw.orr.ont

object Cfg {
  object Admin {
    def apply(c: com.typesafe.config.Config): Admin = {
      Admin(
        c.getString("email"),
        if(c.hasPathOrNull("extra")) Some(c.getString("extra")) else None,
        c.getString("password")
      )
    }
  }
  case class Admin(
    email    : String,
    extra    : Option[String],
    password : String
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "email    = " + '"' + this.email + '"' + "\n"+
      i+ "extra    = " + (if(this.extra.isDefined) "Some(" +'"' +this.extra.get+ '"' + ")" else "None") + "\n"+
      i+ "password = " + '"' + this.password + '"' + "\n"
    }
  }
  object Agraph {
    def apply(c: com.typesafe.config.Config): Agraph = {
      Agraph(
        if(c.hasPathOrNull("host")) c.getString("host") else "localhost",
        c.getString("password"),
        if(c.hasPathOrNull("port")) c.getInt("port") else 10035,
        if(c.hasPathOrNull("repoName")) c.getString("repoName") else "mmiorr",
        c.getString("sparqlEndpoint"),
        c.getString("userName")
      )
    }
  }
  case class Agraph(
    host           : String,
    password       : String,
    port           : Int,
    repoName       : String,
    sparqlEndpoint : String,
    userName       : String
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "host           = " + '"' + this.host + '"' + "\n"+
      i+ "password       = " + '"' + this.password + '"' + "\n"+
      i+ "port           = " + this.port + "\n"+
      i+ "repoName       = " + '"' + this.repoName + '"' + "\n"+
      i+ "sparqlEndpoint = " + '"' + this.sparqlEndpoint + '"' + "\n"+
      i+ "userName       = " + '"' + this.userName + '"' + "\n"
    }
  }
  object Branding {
    def apply(c: com.typesafe.config.Config): Branding = {
      Branding(
        if(c.hasPathOrNull("footer")) Some(c.getString("footer")) else None,
        c.getString("instanceName")
      )
    }
  }
  case class Branding(
    footer       : Option[String],
    instanceName : String
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "footer       = " + (if(this.footer.isDefined) "Some(" +'"' +this.footer.get+ '"' + ")" else "None") + "\n"+
      i+ "instanceName = " + '"' + this.instanceName + '"' + "\n"
    }
  }
  object Email {
    object Account {
      def apply(c: com.typesafe.config.Config): Account = {
        Account(
          c.getString("password"),
          c.getString("username")
        )
      }
    }
    case class Account(
      password : String,
      username : String
    ) {
      override def toString: String = toString("")
      def toString(i:String): String = {
        i+ "password = " + '"' + this.password + '"' + "\n"+
        i+ "username = " + '"' + this.username + '"' + "\n"
      }
    }
    object Server {
      def apply(c: com.typesafe.config.Config): Server = {
        Server(
          if(c.hasPathOrNull("debug")) c.getBoolean("debug") else false,
          c.getString("host"),
          c.getInt("port"),
          if(c.hasPathOrNull("prot")) c.getString("prot") else "smtps"
        )
      }
    }
    case class Server(
      debug : Boolean,
      host  : String,
      port  : Int,
      prot  : String
    ) {
      override def toString: String = toString("")
      def toString(i:String): String = {
        i+ "debug = " + this.debug + "\n"+
        i+ "host  = " + '"' + this.host + '"' + "\n"+
        i+ "port  = " + this.port + "\n"+
        i+ "prot  = " + '"' + this.prot + '"' + "\n"
      }
    }
    def apply(c: com.typesafe.config.Config): Email = {
      Email(
        Account(c.getConfig("account")),
        c.getString("from"),
        c.getString("mailer"),
        c.getString("replyTo"),
        Server(c.getConfig("server"))
      )
    }
  }
  case class Email(
    account : Cfg.Email.Account,
    from    : String,
    mailer  : String,
    replyTo : String,
    server  : Cfg.Email.Server
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "account:\n" + this.account.toString(i+"    ")+
      i+ "from    = " + '"' + this.from + '"' + "\n"+
      i+ "mailer  = " + '"' + this.mailer + '"' + "\n"+
      i+ "replyTo = " + '"' + this.replyTo + '"' + "\n"+
      i+ "server:\n" + this.server.toString(i+"    ")
    }
  }
  object Files {
    def apply(c: com.typesafe.config.Config): Files = {
      Files(
        c.getString("baseDirectory")
      )
    }
  }
  case class Files(
    baseDirectory : String
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "baseDirectory = " + '"' + this.baseDirectory + '"' + "\n"
    }
  }
  object Firebase {
    def apply(c: com.typesafe.config.Config): Firebase = {
      Firebase(
        c.getString("secret")
      )
    }
  }
  case class Firebase(
    secret : String
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "secret = " + '"' + this.secret + '"' + "\n"
    }
  }
  object GoogleAnalytics {
    def apply(c: com.typesafe.config.Config): GoogleAnalytics = {
      GoogleAnalytics(
        if(c.hasPathOrNull("propertyId")) Some(c.getString("propertyId")) else None
      )
    }
  }
  case class GoogleAnalytics(
    propertyId : Option[String]
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "propertyId = " + (if(this.propertyId.isDefined) "Some(" +'"' +this.propertyId.get+ '"' + ")" else "None") + "\n"
    }
  }
  object Mongo {
    def apply(c: com.typesafe.config.Config): Mongo = {
      Mongo(
        if(c.hasPathOrNull("db")) c.getString("db") else "orr-ont",
        if(c.hasPathOrNull("host")) c.getString("host") else "localhost",
        if(c.hasPathOrNull("ontologies")) c.getString("ontologies") else "ontologies",
        if(c.hasPathOrNull("organizations")) c.getString("organizations") else "organizations",
        if(c.hasPathOrNull("port")) c.getInt("port") else 27017,
        if(c.hasPathOrNull("pw")) Some(c.getString("pw")) else None,
        if(c.hasPathOrNull("user")) Some(c.getString("user")) else None,
        if(c.hasPathOrNull("users")) c.getString("users") else "users"
      )
    }
  }
  case class Mongo(
    db            : String,
    host          : String,
    ontologies    : String,
    organizations : String,
    port          : Int,
    pw            : Option[String],
    user          : Option[String],
    users         : String
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "db            = " + '"' + this.db + '"' + "\n"+
      i+ "host          = " + '"' + this.host + '"' + "\n"+
      i+ "ontologies    = " + '"' + this.ontologies + '"' + "\n"+
      i+ "organizations = " + '"' + this.organizations + '"' + "\n"+
      i+ "port          = " + this.port + "\n"+
      i+ "pw            = " + (if(this.pw.isDefined) "Some(" +'"' +this.pw.get+ '"' + ")" else "None") + "\n"+
      i+ "user          = " + (if(this.user.isDefined) "Some(" +'"' +this.user.get+ '"' + ")" else "None") + "\n"+
      i+ "users         = " + '"' + this.users + '"' + "\n"
    }
  }
  object Notifications {
    def apply(c: com.typesafe.config.Config): Notifications = {
      Notifications(
        if(c.hasPathOrNull("recipientsFilename")) Some(c.getString("recipientsFilename")) else None
      )
    }
  }
  case class Notifications(
    recipientsFilename : Option[String]
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "recipientsFilename = " + (if(this.recipientsFilename.isDefined) "Some(" +'"' +this.recipientsFilename.get+ '"' + ")" else "None") + "\n"
    }
  }
  object Recaptcha {
    def apply(c: com.typesafe.config.Config): Recaptcha = {
      Recaptcha(
        if(c.hasPathOrNull("privateKey")) Some(c.getString("privateKey")) else None
      )
    }
  }
  case class Recaptcha(
    privateKey : Option[String]
  ) {
    override def toString: String = toString("")
    def toString(i:String): String = {
      i+ "privateKey = " + (if(this.privateKey.isDefined) "Some(" +'"' +this.privateKey.get+ '"' + ")" else "None") + "\n"
    }
  }
  def apply(c: com.typesafe.config.Config): Cfg = {
    Cfg(
      Admin(c.getConfig("admin")),
      Agraph(c.getConfig("agraph")),
      Branding(c.getConfig("branding")),
      Email(c.getConfig("email")),
      Files(c.getConfig("files")),
      Firebase(c.getConfig("firebase")),
      GoogleAnalytics(c.getConfig("googleAnalytics")),
      Mongo(c.getConfig("mongo")),
      Notifications(c.getConfig("notifications")),
      Recaptcha(c.getConfig("recaptcha"))
    )
  }
}
case class Cfg(
  admin           : Cfg.Admin,
  agraph          : Cfg.Agraph,
  branding        : Cfg.Branding,
  email           : Cfg.Email,
  files           : Cfg.Files,
  firebase        : Cfg.Firebase,
  googleAnalytics : Cfg.GoogleAnalytics,
  mongo           : Cfg.Mongo,
  notifications   : Cfg.Notifications,
  recaptcha       : Cfg.Recaptcha
) {
  override def toString: String = toString("")
  def toString(i:String): String = {
    i+ "admin:\n" + this.admin.toString(i+"    ")+
    i+ "agraph:\n" + this.agraph.toString(i+"    ")+
    i+ "branding:\n" + this.branding.toString(i+"    ")+
    i+ "email:\n" + this.email.toString(i+"    ")+
    i+ "files:\n" + this.files.toString(i+"    ")+
    i+ "firebase:\n" + this.firebase.toString(i+"    ")+
    i+ "googleAnalytics:\n" + this.googleAnalytics.toString(i+"    ")+
    i+ "mongo:\n" + this.mongo.toString(i+"    ")+
    i+ "notifications:\n" + this.notifications.toString(i+"    ")+
    i+ "recaptcha:\n" + this.recaptcha.toString(i+"    ")
  }
}