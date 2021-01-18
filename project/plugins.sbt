// The Lagom plugin
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.4")

// Adds the Java agent for Kamon tracing
// https://kamon.io/docs/latest/guides/installation/setting-up-the-agent/#play-framework
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.8" % "2.0.6")
