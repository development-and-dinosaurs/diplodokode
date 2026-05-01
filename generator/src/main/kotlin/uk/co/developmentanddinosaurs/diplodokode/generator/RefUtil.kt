package uk.co.developmentanddinosaurs.diplodokode.generator

internal object RefUtil {

  private const val LOCAL_SCHEMA_PREFIX = "#/components/schemas/"

  /**
   * Extracts the schema name from a `$ref`, decoding JSON Pointer escape sequences.
   * Falls back to the last path segment for non-canonical refs so existing behaviour is preserved.
   */
  fun schemaNameFromRef(ref: String): String =
      decodeJsonPointer(ref.substringAfterLast("/"))

  /** True if the ref is a canonical local reference of the form `#/components/schemas/<Name>`. */
  fun isLocalComponentsRef(ref: String): Boolean {
    if (!ref.startsWith(LOCAL_SCHEMA_PREFIX)) return false
    val remainder = ref.substring(LOCAL_SCHEMA_PREFIX.length)
    return remainder.isNotEmpty() && !remainder.contains('/')
  }

  /** True if the ref points to a document other than the current one. */
  fun isExternalRef(ref: String): Boolean = ref.indexOf('#').let { it > 0 }

  /** JSON Pointer: `~1` → `/`, `~0` → `~`. Order matters: `~01` should decode to `~1`, not `/`. */
  private fun decodeJsonPointer(segment: String): String =
      segment.replace("~1", "/").replace("~0", "~")
}
