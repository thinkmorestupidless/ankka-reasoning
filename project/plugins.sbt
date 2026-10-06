addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"        % "2.6.2")
// Releases: sbt-ci-release bundles sbt-dynver (the version comes from the git tag, never a file),
// sbt-pgp (signing) and the Central Portal's upload. `sbt ci-release` on a tag publishes the three
// layer modules; every other project carries `publish / skip`.
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")
