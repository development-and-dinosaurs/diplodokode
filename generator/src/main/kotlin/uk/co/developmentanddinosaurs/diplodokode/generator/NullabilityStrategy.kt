package uk.co.developmentanddinosaurs.diplodokode.generator

import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

interface NullabilityStrategy {
  fun isNullable(propertyName: String, propertySchema: Schema, requiredProperties: Set<String>): Boolean
}

class SpecDrivenNullabilityStrategy : NullabilityStrategy {
  override fun isNullable(propertyName: String, propertySchema: Schema, requiredProperties: Set<String>): Boolean =
      !requiredProperties.contains(propertyName) || propertySchema.nullable == true
}

class AllNullableStrategy : NullabilityStrategy {
  override fun isNullable(propertyName: String, propertySchema: Schema, requiredProperties: Set<String>): Boolean = true
}

class AllNonNullableStrategy : NullabilityStrategy {
  override fun isNullable(propertyName: String, propertySchema: Schema, requiredProperties: Set<String>): Boolean = false
}
