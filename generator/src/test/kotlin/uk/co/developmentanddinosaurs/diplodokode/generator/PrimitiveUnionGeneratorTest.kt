package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema
import java.io.File

class PrimitiveUnionGeneratorTest : BehaviorSpec({

    Given("a spec with a data class that has a oneOf string|number property") {
        val spec = File("src/test/resources/primitive-union-property-api.yaml")
        val generator = DiplodokodeGenerator(GeneratorConfig())
        val files = generator.generateFromSpec(spec)
        val code = files.find { it.name == "StringOrDouble" }!!.toString()
        val dinosaurCode = files.find { it.name == "Dinosaur" }!!.toString()

        Then("a StringOrDouble file is generated") {
            files.any { it.name == "StringOrDouble" } shouldBe true
        }

        Then("only one StringOrDouble file is generated even though two schemas share the same union type") {
            files.count { it.name == "StringOrDouble" } shouldBe 1
        }

        Then("the data class property uses StringOrDouble as its type") {
            dinosaurCode shouldContain "val score: StringOrDouble?"
        }

        Then("the StringOrDouble file contains a sealed interface") {
            code shouldContain "sealed interface StringOrDouble"
        }

        Then("the StringOrDouble file contains value class wrappers for each variant") {
            code shouldContain "value class StringValue"
            code shouldContain "value class DoubleValue"
        }

        Then("the value classes hold the correct types") {
            code shouldContain "val `value`: String"
            code shouldContain "val `value`: Double"
        }

        Then("no serializer is generated when no serialisation strategy is configured") {
            code shouldNotContain "Serializer"
        }

        Then("a Union2 file is generated alongside StringOrDouble") {
            files.any { it.name == "Union2" } shouldBe true
        }

        Then("StringOrDouble extends Union2<String, Double>") {
            code shouldContain "Union2<String, Double>"
        }

        Then("a fold function is generated inside StringOrDouble") {
            code shouldContain "override fun <R> fold"
            code shouldContain "is StringValue -> onFirst(value)"
            code shouldContain "is DoubleValue -> onSecond(value)"
        }

        Then("a companion object with invoke overloads is generated") {
            code shouldContain "companion object"
            code shouldContain "operator fun invoke"
        }
    }

    Given("a spec with a data class that has a oneOf string|number property and serialisation enabled") {
        val spec = File("src/test/resources/primitive-union-property-api.yaml")
        val generator = DiplodokodeGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))
        val files = generator.generateFromSpec(spec)
        val code = files.find { it.name == "StringOrDouble" }!!.toString()

        Then("the sealed interface is annotated with @Serializable(with = ...)") {
            code shouldContain "@Serializable(with = StringOrDoubleSerializer::class)"
        }

        Then("a StringOrDoubleSerializer object is generated") {
            code shouldContain "object StringOrDoubleSerializer"
        }

        Then("the serializer implements KSerializer<StringOrDouble>") {
            code shouldContain "KSerializer<StringOrDouble>"
        }

        Then("the serialize function dispatches on each variant type") {
            code shouldContain "is StringOrDouble.StringValue -> encoder.encodeString(value.value)"
            code shouldContain "is StringOrDouble.DoubleValue -> encoder.encodeDouble(value.value)"
        }

        Then("the deserialize function reads a JsonElement and dispatches by primitive type") {
            code shouldContain "JsonDecoder"
            code shouldContain "element.isString -> StringOrDouble.StringValue(element.content)"
            code shouldContain "!element.isString && element.content.toDoubleOrNull() != null -> StringOrDouble.DoubleValue(element.content.toDouble())"
            code shouldContain "else -> throw SerializationException"
        }
    }

    Given("two schemas with the same primitive types declared in different orders") {
        val generator = DiplodokodeGenerator(GeneratorConfig())

        val stringFirst = generator.generateFromSpec(File("src/test/resources/primitive-union-property-api.yaml"))
        val numberFirstSpec = File.createTempFile("number-first", ".yaml").also { it.deleteOnExit() }.also {
            it.writeText(
                """
                openapi: 3.0.3
                info:
                  title: Number First API
                  version: "1.0"
                components:
                  schemas:
                    Triceratops:
                      type: object
                      properties:
                        score:
                          oneOf:
                            - type: number
                            - type: string
                """.trimIndent()
            )
        }
        val numberFirst = generator.generateFromSpec(numberFirstSpec)

        Then("both produce a file named StringOrDouble, not DoubleOrString") {
            stringFirst.any { it.name == "StringOrDouble" } shouldBe true
            numberFirst.any { it.name == "StringOrDouble" } shouldBe true
            numberFirst.any { it.name == "DoubleOrString" } shouldBe false
        }

        Then("both produce identical StringOrDouble files") {
            val a = stringFirst.find { it.name == "StringOrDouble" }!!.toString()
            val b = numberFirst.find { it.name == "StringOrDouble" }!!.toString()
            a shouldBe b
        }
    }

    Given("a spec where top-level schemas are themselves oneOf primitives") {
        val spec = File("src/test/resources/named-union-api.yaml")
        val generator = DiplodokodeGenerator(GeneratorConfig())
        val files = generator.generateFromSpec(spec)

        Then("MeasurementValue is generated with its spec-given name") {
            files.any { it.name == "MeasurementValue" } shouldBe true
        }

        Then("MeasurementValue is a sealed interface extending Union2<String, Double>") {
            val code = files.find { it.name == "MeasurementValue" }!!.toString()
            code shouldContain "sealed interface MeasurementValue"
            code shouldContain "Union2<String, Double>"
        }

        Then("MeasurementValue has StringValue and DoubleValue wrappers") {
            val code = files.find { it.name == "MeasurementValue" }!!.toString()
            code shouldContain "value class StringValue"
            code shouldContain "value class DoubleValue"
        }

        Then("MeasurementValue has a fold function and companion with invoke overloads") {
            val code = files.find { it.name == "MeasurementValue" }!!.toString()
            code shouldContain "override fun <R> fold"
            code shouldContain "companion object"
            code shouldContain "operator fun invoke"
        }

        Then("TagValue is a sealed interface extending Union3<String, Boolean, Int>") {
            val code = files.find { it.name == "TagValue" }!!.toString()
            code shouldContain "sealed interface TagValue"
            code shouldContain "Union3<String, Boolean, Int>"
        }

        Then("a Union2 file is generated for MeasurementValue") {
            files.any { it.name == "Union2" } shouldBe true
        }

        Then("a Union3 file is generated for TagValue") {
            files.any { it.name == "Union3" } shouldBe true
        }

        Then("Union3 has first, second, third accessors") {
            val code = files.find { it.name == "Union3" }!!.toString()
            code shouldContain "fun firstOrNull()"
            code shouldContain "fun secondOrNull()"
            code shouldContain "fun thirdOrNull()"
            code shouldContain "fun first()"
            code shouldContain "fun second()"
            code shouldContain "fun third()"
        }

        Then("Fossil uses MeasurementValue and TagValue as its property types") {
            val code = files.find { it.name == "Fossil" }!!.toString()
            code shouldContain "val length: MeasurementValue?"
            code shouldContain "val tag: TagValue?"
        }
    }

    Given("a spec with an inline oneOf property and a custom naming strategy") {
        val customNaming = object : NamingStrategy {
            override fun className(specName: String) = "My$specName"
            override fun propertyName(specName: String) = specName
            override fun enumConstant(specValue: String) = specValue.uppercase()
        }
        val spec = File("src/test/resources/primitive-union-property-api.yaml")
        val generator = DiplodokodeGenerator(GeneratorConfig(namingStrategy = customNaming))
        val files = generator.generateFromSpec(spec)

        Then("the generated union file uses the naming strategy transformed name") {
            files.any { it.name == "MyStringOrDouble" } shouldBe true
        }

        Then("the data class property references the naming strategy transformed type") {
            val dinosaurCode = files.find { it.name == "MyDinosaur" }!!.toString()
            dinosaurCode shouldContain "MyStringOrDouble"
        }
    }

    Given("a PrimitiveUnionGenerator directly") {
        val config = GeneratorConfig()
        val generator = PrimitiveUnionGenerator(config)

        When("given a string|integer union") {
            val schema = Schema(
                oneOf = listOf(
                  Schema(type = "string"),
                  Schema(type = "integer"),
                )
            )
            val code = generator.generate("StringOrInt", schema).toString()

            Then("it generates StringValue and IntValue wrappers") {
                code shouldContain "value class StringValue"
                code shouldContain "value class IntValue"
            }

            Then("IntValue holds an Int") {
                code shouldContain "val `value`: Int"
            }
        }

        When("given a string|boolean|number union") {
            val schema = Schema(
                oneOf = listOf(
                  Schema(type = "string"),
                  Schema(type = "boolean"),
                  Schema(type = "number"),
                )
            )
            val code = generator.generate("StringOrBooleanOrDouble", schema).toString()

            Then("it generates three value class wrappers") {
                code shouldContain "value class StringValue"
                code shouldContain "value class BooleanValue"
                code shouldContain "value class DoubleValue"
            }
        }

        When("given a union of all four primitive types") {
            val schema = Schema(
                oneOf = listOf(
                  Schema(type = "string"),
                  Schema(type = "boolean"),
                  Schema(type = "integer"),
                  Schema(type = "number"),
                )
            )
            val config = GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy)
            val code = PrimitiveUnionGenerator(config).generate("StringOrBooleanOrIntOrDouble", schema).toString()

            Then("it generates four value class wrappers") {
                code shouldContain "value class StringValue"
                code shouldContain "value class BooleanValue"
                code shouldContain "value class IntValue"
                code shouldContain "value class DoubleValue"
            }

            Then("the deserializer checks each type in disambiguation order") {
                code shouldContain "element.isString -> StringOrBooleanOrIntOrDouble.StringValue(element.content)"
                code shouldContain "!element.isString && element.content.toBooleanStrictOrNull() != null -> StringOrBooleanOrIntOrDouble.BooleanValue(element.content.toBooleanStrict())"
                code shouldContain "!element.isString && element.content.toIntOrNull() != null -> StringOrBooleanOrIntOrDouble.IntValue(element.content.toInt())"
                code shouldContain "!element.isString && element.content.toDoubleOrNull() != null -> StringOrBooleanOrIntOrDouble.DoubleValue(element.content.toDouble())"
                code shouldContain "else -> throw SerializationException"
            }

            Then("the serializer handles all four variants") {
                code shouldContain "is StringOrBooleanOrIntOrDouble.StringValue -> encoder.encodeString(value.value)"
                code shouldContain "is StringOrBooleanOrIntOrDouble.BooleanValue -> encoder.encodeBoolean(value.value)"
                code shouldContain "is StringOrBooleanOrIntOrDouble.IntValue -> encoder.encodeInt(value.value)"
                code shouldContain "is StringOrBooleanOrIntOrDouble.DoubleValue -> encoder.encodeDouble(value.value)"
            }
        }
    }
})
