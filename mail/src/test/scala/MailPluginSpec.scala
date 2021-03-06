import com.icegreen.greenmail.util.GreenMail
import java.net.Socket
import javax.mail.internet.MimeMessage
import org.apache.commons.mail.{ EmailException, Email, SimpleEmail }
import org.specs2.mutable._
import info.schleichardt.play2.mailplugin._

import play.api.test.FakeApplication
import play.api.test.Helpers._

import DemoEmailProvider.newFilledSimpleEmail

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.util.Try

class MailPluginSpec extends Specification {
  "MailPlugin" should {
    "be in a mock version" in {
      MailPlugin.usesMockMail === true
    }

    "be activated in this app" in {
      val app: FakeApplication = EmailFakeApplication()
      running(app) {
        app.plugin(classOf[MailPlugin]).get.enabled === true
      }
    }

    "archive up to 5 mails" in {
      running(EmailFakeApplication()) {
        for (index <- 1 to 10) {
          val email: SimpleEmail = newFilledSimpleEmail()
          email.setSubject("subject " + index)
          Mailer.send(email)
        }
        val history = Mailer.history()
        history.size === 5
        history.get(0).getSubject === "subject 6"
        history.get(4).getSubject === "subject 10"
      }
    }

    "be able to configure mail archive size" in {
      val configMap: Map[String, String] = Map("smtp.archive.size" -> "20")
      val app: FakeApplication = EmailFakeApplication(additionalConfiguration = configMap)
      running(app) {
        for (index <- 1 to 30) {
          val email: SimpleEmail = newFilledSimpleEmail()
          email.setSubject("subject " + index)
          Mailer.send(email)
        }
        val history = Mailer.history()
        history.size === 20
        history.get(0).getSubject === "subject 11"
        history.get(4).getSubject === "subject 15"
      }
    }

    "grab the right configuration with standard profile" in {
      val configMap: Map[String, String] = Map("smtp.mock" -> "false")
      val app: FakeApplication = EmailFakeApplication(additionalConfiguration = configMap)
      running(app) {
        app.configuration.getBoolean("smtp.mock").get === false
        val mailConf = MailPlugin.configuration()
        mailConf.host === "localhost"
        mailConf.port === 26
        mailConf.useSsl === true
        mailConf.user.get === "michael"
        mailConf.password.get === "123456"
      }
    }

    "grab the right configuration with extra profile" in {
      val configMap: Map[String, String] = Map("smtp.mock" -> "false")
      val app: FakeApplication = EmailFakeApplication(additionalConfiguration = configMap)
      running(app) {
        app.configuration.getBoolean("smtp.mock").get === false
        val mailConf = MailPlugin.configuration("demoprofile")
        mailConf.host === "localhost"
        mailConf.port === 26
        mailConf.useSsl === true
        mailConf.user.get === "demoprofileuser"
        mailConf.password.get === "123456"
      }
    }

    "be able to add additional configuration" in {
      val additionalRecipient = "additionalRecipient@localhost"

      val interceptor: EmailSendInterceptor = new DefaultEmailSendInterceptor {
        override def afterConfiguration(email: Email, profile: String) {
          email.addBcc(additionalRecipient)
        }
      }

      running(EmailFakeApplication()) {
        val email = newFilledSimpleEmail()
        email.getBccAddresses.exists(_.toString.contains(additionalRecipient)) === false

        Mailer.setInterceptor(interceptor)
        Mailer.send(email)

        email.getBccAddresses.exists(_.toString.contains(additionalRecipient)) === true
      }
    }

    "be able to cause a send error for TDD" in {
      val interceptor: EmailSendInterceptor = new DefaultEmailSendInterceptor {
        override def afterConfiguration(email: Email, profile: String) {
          throw new EmailException("thrown for testing if the app reacts correctly on a mail server error")
        }
      }

      running(EmailFakeApplication()) {
        val email = newFilledSimpleEmail()
        Mailer.setInterceptor(interceptor)
        Mailer.send(email) must throwA[EmailException]
      }
    }

    def smtpTestPort() = com.icegreen.greenmail.util.ServerSetupTest.SMTP.getPort
    def smtpConfigForGreenMail(profile: String = "") = {
      val usesProfile = profile != null && !profile.trim.isEmpty
      val portKey = if (usesProfile) "smtp.profiles." + profile + ".port" else "smtp.port"
      val sslKey = if (usesProfile) "smtp.profiles." + profile + ".ssl" else "smtp.ssl"
      Map("smtp.mock" -> "false", portKey -> smtpTestPort().toString, sslKey -> "false")
    }

    def withGreenmail[T](greenMail: GreenMail = newSmtpGreenMail)(block: GreenMail => T): T = {
      @tailrec def waitUntilServerIsUp(remainingAttempts: Int) {
        require(remainingAttempts > 0, "greenmail server must be started")
        val port = greenMail.getSmtp.getPort
        if (Try(Option(new Socket("localhost", port)).map(_.close())).isFailure) {
          Thread.sleep(100)
          waitUntilServerIsUp(remainingAttempts - 1)
        }
      }

      try {
        greenMail.start() //this is non blocking
        waitUntilServerIsUp(10)
        block(greenMail)
      } finally {
        greenMail.stop()
      }
    }

    def newSmtpGreenMail = new GreenMail(com.icegreen.greenmail.util.ServerSetupTest.SMTP)

    "be able to send real mails" in {
      val app: FakeApplication = EmailFakeApplication(additionalConfiguration = smtpConfigForGreenMail())
      running(app) {
        withGreenmail() { greenMail =>
          val email: SimpleEmail = newFilledSimpleEmail()
          val subject = "the subject"
          email.setSubject(subject)
          Mailer.send(email)
          val receivedMessages: Array[MimeMessage] = greenMail.getReceivedMessages
          receivedMessages.size === 1
          receivedMessages(0).getSubject === subject
        }
      }
    }

    "be able to send real mails with alternate profile" in {
      val app: FakeApplication = EmailFakeApplication(additionalConfiguration = smtpConfigForGreenMail("demoprofile"))
      running(app) {
        withGreenmail() { greenMail =>
          val email: SimpleEmail = newFilledSimpleEmail()
          val subject = "the subject alternate profile"
          email.setSubject(subject)
          Mailer.send(email, "demoprofile")
          val receivedMessages: Array[MimeMessage] = greenMail.getReceivedMessages
          receivedMessages.size === 1
          receivedMessages(0).getSubject === subject
        }
      }
    }
  }

  def EmailFakeApplication(additionalConfiguration: Map[String, Any] = Map.empty): FakeApplication = {
    val conf = Map(
      "smtp.mock" -> true,
      "smtp.host" -> "localhost",
      "smtp.port" -> 26,
      "smtp.ssl" -> true,
      "smtp.user" -> "michael",
      "smtp.password" -> "123456",
      "smtp.profiles.demoprofile.host" -> "localhost",
      "smtp.profiles.demoprofile.port" -> 26,
      "smtp.profiles.demoprofile.ssl" -> true,
      "smtp.profiles.demoprofile.user" -> "demoprofileuser",
      "smtp.profiles.demoprofile.password" -> "123456",
      "logger.info.schleichardt.play2.mail" -> "INFO"
    ) ++ additionalConfiguration
    FakeApplication(additionalConfiguration = conf, additionalPlugins = Seq("info.schleichardt.play2.mailplugin.MailPlugin"))
  }
}
