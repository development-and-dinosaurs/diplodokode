package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.StringOrDouble
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.TagValue

/**
 * Behavioural tests for the Union2/Union3 interfaces exercised through hand-written fixtures that
 * mirror generator output. Covers invoke construction, fold, and the OrNull/throwing accessors.
 */
class UnionBehaviourTest : BehaviorSpec({

    Given("a Union2 (StringOrDouble) instance") {
        Then("invoke with a String constructs a StringValue") {
            StringOrDouble("hello") shouldBe StringOrDouble.StringValue("hello")
        }

        Then("invoke with a Double constructs a DoubleValue") {
            StringOrDouble(9.5) shouldBe StringOrDouble.DoubleValue(9.5)
        }

        Then("fold on a StringValue returns the first lambda result") {
            StringOrDouble("hello").fold(onFirst = { "string: $it" }, onSecond = { "double: $it" }) shouldBe "string: hello"
        }

        Then("fold on a DoubleValue returns the second lambda result") {
            StringOrDouble(9.5).fold(onFirst = { "string: $it" }, onSecond = { "double: $it" }) shouldBe "double: 9.5"
        }

        Then("firstOrNull on a StringValue returns the string") {
            StringOrDouble("hello").firstOrNull() shouldBe "hello"
        }

        Then("firstOrNull on a DoubleValue returns null") {
            StringOrDouble(9.5).firstOrNull() shouldBe null
        }

        Then("secondOrNull on a DoubleValue returns the double") {
            StringOrDouble(9.5).secondOrNull() shouldBe 9.5
        }

        Then("secondOrNull on a StringValue returns null") {
            StringOrDouble("hello").secondOrNull() shouldBe null
        }

        Then("first on a StringValue returns the string") {
            StringOrDouble("hello").first() shouldBe "hello"
        }

        Then("first on a DoubleValue throws") {
            runCatching { StringOrDouble(9.5).first() }.isFailure shouldBe true
        }

        Then("second on a DoubleValue returns the double") {
            StringOrDouble(9.5).second() shouldBe 9.5
        }

        Then("second on a StringValue throws") {
            runCatching { StringOrDouble("hello").second() }.isFailure shouldBe true
        }
    }

    Given("a Union3 (TagValue: string|boolean|integer) instance") {
        Then("invoke with a String constructs a StringValue") {
            TagValue("herbivore") shouldBe TagValue.StringValue("herbivore")
        }

        Then("invoke with a Boolean constructs a BooleanValue") {
            TagValue(true) shouldBe TagValue.BooleanValue(true)
        }

        Then("invoke with an Int constructs an IntValue") {
            TagValue(42) shouldBe TagValue.IntValue(42)
        }

        Then("fold on a StringValue returns the first lambda result") {
            TagValue("herbivore").fold(onFirst = { "s:$it" }, onSecond = { "b:$it" }, onThird = { "i:$it" }) shouldBe "s:herbivore"
        }

        Then("fold on a BooleanValue returns the second lambda result") {
            TagValue(true).fold(onFirst = { "s:$it" }, onSecond = { "b:$it" }, onThird = { "i:$it" }) shouldBe "b:true"
        }

        Then("fold on an IntValue returns the third lambda result") {
            TagValue(42).fold(onFirst = { "s:$it" }, onSecond = { "b:$it" }, onThird = { "i:$it" }) shouldBe "i:42"
        }

        Then("firstOrNull returns the string value or null") {
            TagValue("herbivore").firstOrNull() shouldBe "herbivore"
            TagValue(true).firstOrNull() shouldBe null
            TagValue(42).firstOrNull() shouldBe null
        }

        Then("secondOrNull returns the boolean value or null") {
            TagValue(true).secondOrNull() shouldBe true
            TagValue("herbivore").secondOrNull() shouldBe null
            TagValue(42).secondOrNull() shouldBe null
        }

        Then("thirdOrNull returns the int value or null") {
            TagValue(42).thirdOrNull() shouldBe 42
            TagValue("herbivore").thirdOrNull() shouldBe null
            TagValue(true).thirdOrNull() shouldBe null
        }

        Then("third on an IntValue returns the int") {
            TagValue(42).third() shouldBe 42
        }

        Then("third on a non-IntValue throws") {
            runCatching { TagValue("herbivore").third() }.isFailure shouldBe true
        }
    }
})
