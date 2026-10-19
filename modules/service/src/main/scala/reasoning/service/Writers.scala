package reasoning.service

import com.thinkmorestupidless.ankka.auth.oidc.{Oidc, OidcConfig, OidcVerifier}
import com.thinkmorestupidless.ankka.http.{Acl, AuthDecision, Caller, Principal}

/**
 * Who a caller is taken to be, as a writer.
 *
 * Decided here, from the connection, and nowhere else: nothing in a request's body or headers names
 * the writer. Another ankka service is the identity its certificate carries, and a machine
 * registered on an organization the identity the platform verified for it. Outside a cluster there
 * are no certificates and every caller is `local`, which is for a laptop only.
 *
 * A person arrives through the gateway with a token, verified by `ankka-auth-oidc` against the
 * issuers the service lists in ankka's `ANKKA_AUTH_*` variables, and is `person:<subject>@<issuer
 * name>`. A service that lists no issuer refuses the gateway.
 */
object Writers:

  val Realm = "reasoning"

  /** A caller the connection names without a token, or `None` for the gateway. */
  def of(caller: Caller): Option[String] = caller match
    case Caller.Service(project, name)      => Some(s"service:$project/$name")
    case Caller.Machine(organization, name) => Some(s"machine:$organization/$name")
    case Caller.Local                       => Some("local")
    case Caller.Gateway                     => None

  /** The writer a verified token names. */
  def person(subject: String, issuer: String): String = s"person:$subject@$issuer"

  /**
   * The verifier of people's tokens for the issuers the environment lists, or `None` when it lists
   * none. Throws, naming every problem, when the list is misconfigured, so the service does not
   * start with a gateway it cannot serve as it was asked to.
   */
  def people(env: Map[String, String] = sys.env): Option[OidcVerifier] =
    OidcConfig.fromEnv(env) match
      case Left(problems) =>
        throw IllegalStateException(
          problems.mkString("the issuers of people's tokens are misconfigured:\n  - ", "\n  - ", "")
        )
      case Right(config) if config.isEmpty => None
      case Right(config)                   => Some(Oidc.verifier(config))

  /** The one ACL of every endpoint: it admits a caller it can name and makes it the principal. */
  def acl(people: Option[OidcVerifier]): Acl =
    val token = people.map(Oidc.authenticate(_, Realm)).collect { case Acl.Authenticate(decide) =>
      decide
    }
    Acl.Authenticate { request =>
      (of(request.caller), token) match
        case (Some(writer), _) => AuthDecision.Allow(Principal(writer))
        case (None, None) =>
          AuthDecision.Unauthenticated(
            s"""realm="$Realm", error="this service accepts no person's token""""
          )
        case (None, Some(decide)) =>
          decide(request) match
            case AuthDecision.Allow(principal) =>
              principal.issuer.fold(AuthDecision.Unauthenticated(s"""realm="$Realm""""))(issuer =>
                AuthDecision.Allow(Principal(person(principal.subject, issuer)))
              )
            case refused => refused
    }
