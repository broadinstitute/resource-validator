import sbtassembly.{MergeStrategy, PathList}

object Merging {
  def customMergeStrategy(oldStrategy: (String) => MergeStrategy): (String => MergeStrategy) = {
    case PathList("io", "sundr", xs @ _*)                   => MergeStrategy.first
    case PathList("org", "bouncycastle", xs @ _*)           => MergeStrategy.first
    case PathList("com", "google", "code", "gson", xs @ _*) => MergeStrategy.first
    case "reference.conf"                                   => MergeStrategy.concat
    case "module-info.class" =>
      MergeStrategy.discard // JDK 8 does not use the file module-info.class so it is safe to discard the file.
    case x => oldStrategy(x)
  }
}
