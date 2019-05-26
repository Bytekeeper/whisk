package org.whisk

object PathMatcher {
    fun toRegex(path: String) =
            path.replace(".", "\\.")
                .replace("?", "\\w")
                .replace("**", ".*")
                .replace("(?<!\\.)\\*".toRegex(), "[^/]+")
                .toRegex()
}