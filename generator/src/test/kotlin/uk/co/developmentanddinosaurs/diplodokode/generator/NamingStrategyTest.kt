package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class NamingStrategyTest : BehaviorSpec({

  Given("the DefaultNamingStrategy") {
    val strategy = DefaultNamingStrategy()

    When("converting class names") {
      Then("it uppercases the first character of a single word") {
        strategy.className("dinosaur") shouldBe "Dinosaur"
      }
      Then("it leaves already-capitalised names unchanged") {
        strategy.className("Tyrannosaur") shouldBe "Tyrannosaur"
      }
      Then("it converts hyphen-separated words to PascalCase") {
        strategy.className("fossil-record") shouldBe "FossilRecord"
      }
      Then("it converts underscore-separated words to PascalCase") {
        strategy.className("fossil_record") shouldBe "FossilRecord"
      }
      Then("it converts space-separated words to PascalCase") {
        strategy.className("fossil record") shouldBe "FossilRecord"
      }
      Then("it prefixes with underscore when the result starts with a digit") {
        strategy.className("3d-model") shouldBe "_3dModel"
      }
    }

    When("converting property names") {
      Then("it lowercases the first character of a single word") {
        strategy.propertyName("Name") shouldBe "name"
      }
      Then("it leaves already-lowercased names unchanged") {
        strategy.propertyName("weight") shouldBe "weight"
      }
      Then("it preserves internal camelCase in single-word names") {
        strategy.propertyName("isCarnivore") shouldBe "isCarnivore"
      }
      Then("it converts hyphen-separated words to camelCase") {
        strategy.propertyName("content-type") shouldBe "contentType"
      }
      Then("it converts underscore-separated words to camelCase") {
        strategy.propertyName("discovered_at") shouldBe "discoveredAt"
      }
      Then("it prefixes with underscore when the result starts with a digit") {
        strategy.propertyName("3d-position") shouldBe "_3dPosition"
      }
    }

    When("converting enum constants") {
      Then("it uppercases the value") {
        strategy.enumConstant("active") shouldBe "ACTIVE"
      }
      Then("it replaces hyphens with underscores") {
        strategy.enumConstant("my-status") shouldBe "MY_STATUS"
      }
      Then("it replaces dots with underscores") {
        strategy.enumConstant("v1.0") shouldBe "V1_0"
      }
      Then("it prefixes with underscore when the value starts with a digit") {
        strategy.enumConstant("404") shouldBe "_404"
      }
      Then("it handles values that are already uppercase") {
        strategy.enumConstant("ACTIVE") shouldBe "ACTIVE"
      }
      Then("it splits PascalCase values into SCREAMING_SNAKE_CASE") {
        strategy.enumConstant("DinosaurType") shouldBe "DINOSAUR_TYPE"
      }
      Then("it splits camelCase values into SCREAMING_SNAKE_CASE") {
        strategy.enumConstant("dinosaurType") shouldBe "DINOSAUR_TYPE"
      }
      Then("it handles consecutive uppercase letters correctly") {
        strategy.enumConstant("XMLParser") shouldBe "XML_PARSER"
      }
    }
  }

  Given("the PreserveNamingStrategy") {
    val strategy = PreserveNamingStrategy()

    When("converting class names") {
      Then("it returns a single word unchanged") {
        strategy.className("Tyrannosaur") shouldBe "Tyrannosaur"
      }
      Then("it preserves lowercase names unchanged") {
        strategy.className("dinosaur") shouldBe "dinosaur"
      }
      Then("it replaces hyphens with underscores") {
        strategy.className("my-schema") shouldBe "my_schema"
      }
      Then("it replaces spaces with underscores") {
        strategy.className("my schema") shouldBe "my_schema"
      }
      Then("it prefixes with underscore when the name starts with a digit") {
        strategy.className("3dModel") shouldBe "_3dModel"
      }
    }

    When("converting property names") {
      Then("it returns the spec name exactly as-is") {
        strategy.propertyName("Name") shouldBe "Name"
      }
      Then("it preserves snake_case") {
        strategy.propertyName("discovered_at") shouldBe "discovered_at"
      }
      Then("it preserves camelCase") {
        strategy.propertyName("isCarnivore") shouldBe "isCarnivore"
      }
      Then("it preserves hyphenated names for KotlinPoet to backtick-escape") {
        strategy.propertyName("content-type") shouldBe "content-type"
      }
      Then("it preserves Kotlin keywords for KotlinPoet to backtick-escape") {
        strategy.propertyName("class") shouldBe "class"
      }
    }

    When("converting enum constants") {
      Then("it preserves the original case") {
        strategy.enumConstant("active") shouldBe "active"
        strategy.enumConstant("ACTIVE") shouldBe "ACTIVE"
        strategy.enumConstant("Active") shouldBe "Active"
      }
      Then("it replaces hyphens with underscores without uppercasing") {
        strategy.enumConstant("my-status") shouldBe "my_status"
      }
      Then("it prefixes with underscore when the value starts with a digit") {
        strategy.enumConstant("404") shouldBe "_404"
      }
    }
  }

  Given("both strategies applied to the same inputs") {
    val default = DefaultNamingStrategy()
    val preserve = PreserveNamingStrategy()

    When("both handle a separator-containing class name") {
      Then("default produces PascalCase, preserve sanitises only") {
        default.className("fossil-record") shouldBe "FossilRecord"
        preserve.className("fossil-record") shouldBe "fossil_record"
      }
    }

    When("both handle a separator-containing property name") {
      Then("default produces camelCase, preserve keeps original for KotlinPoet to handle") {
        default.propertyName("content-type") shouldBe "contentType"
        preserve.propertyName("content-type") shouldBe "content-type"
      }
    }

    When("both handle a lowercase enum constant") {
      Then("default uppercases, preserve keeps original case") {
        default.enumConstant("active") shouldBe "ACTIVE"
        preserve.enumConstant("active") shouldBe "active"
      }
    }
  }
})
