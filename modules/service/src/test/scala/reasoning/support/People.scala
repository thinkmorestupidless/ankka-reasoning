package reasoning.support

import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.thinkmorestupidless.ankka.auth.oidc.{Issuer, OidcConfig, OidcVerifier}

import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.time.Instant

/**
 * An identity provider of people, in this JVM: one signing key, handed to the verifier directly in
 * place of a keys URL, and tokens minted to order. The service fixtures accept its tokens, as a
 * deployed service accepts the tokens of the issuers its environment lists.
 */
object People:

  /** What a service would call the issuer in `ANKKA_AUTH_ISSUERS`, and so what a writer names. */
  val Name     = "people"
  val Url      = "https://people.example.test"
  val Audience = "reasoning"

  private val (privateKey, publicKey) =
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    val pair = generator.generateKeyPair()
    (pair.getPrivate.asInstanceOf[RSAPrivateKey], pair.getPublic.asInstanceOf[RSAPublicKey])

  private val keys = new JWKSet(new RSAKey.Builder(publicKey).keyID("k1").build())

  val verifier: OidcVerifier =
    OidcVerifier(
      OidcConfig(Vector(Issuer(Name, Url, s"$Url/keys", Audience))),
      _ => ImmutableJWKSet(keys)
    )

  /** A token for `subject`, from this issuer unless told otherwise. */
  def token(subject: String, issuer: String = Url): String =
    val now = Instant.now()
    val claims = new JWTClaimsSet.Builder()
      .subject(subject)
      .issuer(issuer)
      .audience(Audience)
      .expirationTime(java.util.Date.from(now.plusSeconds(300)))
      .build()
    val jwt = new SignedJWT(
      new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").`type`(JOSEObjectType.JWT).build(),
      claims
    )
    jwt.sign(new RSASSASigner(privateKey))
    jwt.serialize()
