package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RefUtilTest : BehaviorSpec({

  Given("schemaNameFromRef") {
    Then("returns the last path segment of a canonical ref") {
      RefUtil.schemaNameFromRef("#/components/schemas/Tyrannosaur") shouldBe "Tyrannosaur"
    }
    Then("decodes JSON Pointer escapes (~1 → /, ~0 → ~)") {
      RefUtil.schemaNameFromRef("#/components/schemas/foo~1bar") shouldBe "foo/bar"
      RefUtil.schemaNameFromRef("#/components/schemas/name~0tilde") shouldBe "name~tilde"
    }
    Then("decodes in the spec-required order (~1 before ~0 so ~01 stays as ~1)") {
      RefUtil.schemaNameFromRef("#/components/schemas/~01") shouldBe "~1"
    }
  }

  Given("isLocalComponentsRef") {
    Then("true for #/components/schemas/<Name>") {
      RefUtil.isLocalComponentsRef("#/components/schemas/Tyrannosaur") shouldBe true
    }
    Then("false for external refs") {
      RefUtil.isLocalComponentsRef("other.yaml#/components/schemas/Tyrannosaur") shouldBe false
    }
    Then("false for non-schemas fragments") {
      RefUtil.isLocalComponentsRef("#/definitions/Tyrannosaur") shouldBe false
    }
    Then("false for deeper paths under components/schemas") {
      RefUtil.isLocalComponentsRef("#/components/schemas/Outer/Inner") shouldBe false
    }
  }

  Given("isExternalRef") {
    Then("true when the ref has content before the fragment") {
      RefUtil.isExternalRef("other.yaml#/components/schemas/Foo") shouldBe true
    }
    Then("false for a purely-local fragment ref") {
      RefUtil.isExternalRef("#/components/schemas/Foo") shouldBe false
    }
    Then("false for a ref with no fragment at all") {
      RefUtil.isExternalRef("Foo") shouldBe false
    }
  }
})
