package reasoning.service

import com.thinkmorestupidless.ankka.http.{Acl, AuthDecision, Caller, Principal}

/**
 * Who a caller is taken to be, as a writer.
 *
 * Decided here, from the connection, and nowhere else: nothing in a request's body or headers names
 * the writer. Another ankka service is the identity its certificate carries. Outside a cluster
 * there are no certificates and every caller is `local`, which is for a laptop only.
 *
 * A person arriving through the gateway would be identified by a token. ankka's verifier for one,
 * `ankka-auth-oidc`, is not in a published release yet, so the gateway is refused until it is.
 */
object Writers:

  def of(caller: Caller): Option[String] = caller match
    case Caller.Service(project, name) => Some(s"service:$project/$name")
    case Caller.Local                  => Some("local")
    case Caller.Gateway                => None

  /** The one ACL of every endpoint: it admits a caller it can name and makes it the principal. */
  val acl: Acl = Acl.Authenticate { request =>
    of(request.caller) match
      case Some(writer) => AuthDecision.Allow(Principal(writer))
      case None =>
        AuthDecision.Unauthenticated(
          "realm=\"reasoning\", error=\"a caller through the gateway cannot be identified as a writer yet\""
        )
  }
