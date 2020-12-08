import sbtassembly.{MergeStrategy, PathList}

object Merging {
  def customMergeStrategy(oldStrategy: (String) => MergeStrategy): (String => MergeStrategy) = {
    case PathList("io", "sundr", _ @_*)                   => MergeStrategy.first
    case PathList("org", "bouncycastle", _ @_*)           => MergeStrategy.first
    case PathList("com", "google", "code", "gson", _ @_*) => MergeStrategy.first
    case "reference.conf"                                 => MergeStrategy.concat
    case "module-info.class" =>
      MergeStrategy.discard // JDK 8 does not use the file module-info.class so it is safe to discard the file.
    case "META-INF/versions/9/module-info.class" =>
      MergeStrategy.discard // JDK 8 does not use the file module-info.class so it is safe to discard the file.
    case x => oldStrategy(x)
  }
}
