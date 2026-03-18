package uk.co.developmentanddinosaurs.diplodokode.generator

interface NamingStrategy {
  fun className(specName: String): String
  fun propertyName(specName: String): String
  fun enumConstant(specValue: String): String
}

private val SEPARATOR_REGEX = Regex("[^A-Za-z0-9]+")
private val INVALID_IDENTIFIER_REGEX = Regex("[^A-Za-z0-9_]")
private val CAMEL_CASE_REGEX = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

private fun splitWords(specName: String): List<String> =
    specName.split(SEPARATOR_REGEX).filter { it.isNotEmpty() }

private fun splitConstantWords(specName: String): List<String> =
    splitWords(specName).flatMap { it.split(CAMEL_CASE_REGEX) }.filter { it.isNotEmpty() }

private fun prefixIfDigitLeading(name: String): String =
    if (name.isNotEmpty() && name.first().isDigit()) "_$name" else name

class DefaultNamingStrategy : NamingStrategy {

  override fun className(specName: String): String {
    val result = splitWords(specName)
        .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    return prefixIfDigitLeading(result.ifEmpty { "_" })
  }

  override fun propertyName(specName: String): String {
    val words = splitWords(specName)
    val result = words.mapIndexed { i, word ->
      if (i == 0) word.replaceFirstChar { it.lowercase() }
      else word.replaceFirstChar { it.uppercase() }
    }.joinToString("")
    return prefixIfDigitLeading(result.ifEmpty { "_" })
  }

  override fun enumConstant(specValue: String): String {
    val words = splitConstantWords(specValue)
    val result = if (words.isEmpty()) "_" else words.joinToString("_") { it.uppercase() }
    return prefixIfDigitLeading(result)
  }
}

class PreserveNamingStrategy : NamingStrategy {

  override fun className(specName: String): String {
    // Class names must be clean identifiers — replace invalid chars but don't change case
    val sanitised = specName.replace(INVALID_IDENTIFIER_REGEX, "_")
    return prefixIfDigitLeading(sanitised.ifEmpty { "_" })
  }

  override fun propertyName(specName: String): String =
    // Return the spec name as-is; KotlinPoet backtick-escapes invalid chars and keywords
    // when rendering (e.g. "content-type" → `content-type`, "class" → `class`)
    specName

  override fun enumConstant(specValue: String): String {
    // Enum constants can't use backticks — sanitise but preserve case
    val sanitised = specValue.replace(INVALID_IDENTIFIER_REGEX, "_")
    return prefixIfDigitLeading(sanitised.ifEmpty { "_" })
  }
}
