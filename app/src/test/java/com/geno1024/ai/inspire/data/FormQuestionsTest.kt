package com.geno1024.ai.inspire.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormQuestionsTest {

    private fun form(vararg fields: V2FormField) = V2FormInfo(
        id = "form-1",
        sessionId = "session-1",
        title = "Choose",
        fields = fields.toList(),
    )

    private fun multiselect(
        key: String = "pick",
        maxItems: Int? = null,
        options: Int = 2,
    ) = V2FormField(
        key = key,
        type = "multiselect",
        title = "Pick some",
        options = (1..options).map {
            V2FormOption(value = "v$it", label = "Option $it")
        },
        maxItems = maxItems,
    )

    @Test
    fun `multiselect is multiple even when maxItems is absent`() {
        val question = form(multiselect()).toQuestionRequest().questions.single()
        assertTrue(question.multiple)
        assertEquals(listOf("Option 1", "Option 2"), question.options.map { it.label })
    }

    @Test
    fun `multiselect stays multiple when maxItems allows more than one`() {
        val question = form(multiselect(maxItems = 3)).toQuestionRequest().questions.single()
        assertTrue(question.multiple)
    }

    @Test
    fun `text and boolean fields are not multiple`() {
        val questions = form(
            V2FormField(key = "note", type = "string"),
            V2FormField(key = "count", type = "integer"),
            V2FormField(key = "ok", type = "boolean"),
        ).toQuestionRequest().questions

        assertEquals(3, questions.size)
        assertTrue(questions.none { it.multiple })
    }

    @Test
    fun `one form contributes one question per visible mappable field`() {
        val request = form(
            multiselect(key = "a"),
            V2FormField(key = "b", type = "string"),
            V2FormField(key = "hidden", type = "string", hidden = true),
            V2FormField(key = "external", type = "external", title = "Open a page"),
        ).toQuestionRequest()

        assertEquals("form-1", request.id)
        assertEquals("session-1", request.sessionId)
        assertEquals(2, request.questions.size)
        assertTrue(request.questions[0].multiple)
        assertFalse(request.questions[1].multiple)
    }

    @Test
    fun `a form with no mappable fields still shows one free-form question`() {
        val question = form(V2FormField(key = "external", type = "external"))
            .toQuestionRequest().questions.single()
        assertEquals("Choose", question.question)
        assertFalse(question.multiple)
        assertTrue(question.custom)
    }

    @Test
    fun `question text falls back from description to title to key`() {
        val questions = form(
            V2FormField(key = "k1", type = "string", title = "Title", description = "Description"),
            V2FormField(key = "k2", type = "string", title = "Title"),
            V2FormField(key = "k3", type = "string"),
        ).toQuestionRequest().questions

        assertEquals(listOf("Description", "Title", "k3"), questions.map { it.question })
        assertEquals(listOf("Title", "Title", "Choose"), questions.map { it.header })
    }
}
