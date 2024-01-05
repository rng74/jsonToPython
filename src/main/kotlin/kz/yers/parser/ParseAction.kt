package kz.yers.parser

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlinx.serialization.json.*

class ParseAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let {
            val editor = FileEditorManager.getInstance(it)
            val document = editor.selectedTextEditor?.document
            val currentContent = document?.text
            val rootType = ScratchRootType.getInstance()
            rootType.createScratchFile(e.project, "new_data.py", Language.ANY, "")?.let { scratchFile ->
                val newDocument = FileDocumentManager.getInstance().getDocument(scratchFile)
                val newContent = modifyText(currentContent.orEmpty())
                newDocument?.setText(newContent)
                editor.openFile(scratchFile, true)
            }
        }
    }

    private fun parseJson(jsonElement: JsonElement, className: String): String {
        var result = "class $className(BaseModel):\n"
        val list = mutableListOf<Pair<String, JsonElement>>()
        if (jsonElement is JsonObject) {
            jsonElement.forEach { key, value ->
                when (value) {
                    is JsonPrimitive -> {
                        val tmp = if (value.isString) "str"
                        else if (value is JsonNull) "Optional[str]"
                        else "float"
                        result += "    $key: $tmp\n"
                    }

                    is JsonObject -> {
                        list.add(key to value)
                        result += "    $key: ${key.capitalize()}\n"
                    }

                    is JsonArray -> {
                        result += if (value.size == 0) {
                            "    $key: []\n"
                        } else {
                            list.add(key to value.first())
                            "    $key: List[${key.capitalize()}]\n"
                        }
                    }
                }
            }
        }
        list.forEach {
            result += "\n" + parseJson(it.second, it.first.capitalize())
        }
        return result
    }

    private fun simplifyJsonArrays(jsonElement: JsonElement): JsonElement {
        return when (jsonElement) {
            is JsonArray -> {
                if (jsonElement.size > 1) {
                    JsonArray(listOf(simplifyJsonArrays(jsonElement[0])))
                } else {
                    JsonArray(jsonElement.map { simplifyJsonArrays(it) })
                }
            }

            is JsonObject -> {
                JsonObject(jsonElement.mapValues { (_, value) -> simplifyJsonArrays(value) })
            }

            else -> jsonElement
        }
    }

    private fun modifyText(text: String): String {
        val jsonElement = Json.parseToJsonElement(text)
        if (jsonElement is JsonObject) {
            val simplifiedJson = simplifyJsonArrays(jsonElement)
            return parseJson(simplifiedJson, "NewData")
        } else {
            throw IllegalArgumentException("JSON must represent an object.")
        }
    }
}