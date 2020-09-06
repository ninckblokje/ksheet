package ninckblokje.ksheet

import kotlinx.cinterop.*
import kotlinx.cli.*
import ninckblokje.ksheet.BuildKonfig.ksheetRevision
import ninckblokje.ksheet.BuildKonfig.ksheetVersion
import platform.posix.*

class ArgParserAndArgsAndOpts (
    val parser: ArgParser,
    val ksheetOpt: SingleOption<String, DefaultRequiredType.Required>,
    val listOpt: SingleNullableOption<Boolean>,
    val versionOpt: SingleNullableOption<Boolean>,
    val subjectArg: SingleNullableArgument<String>,
    val sectionArg: SingleNullableArgument<String>
)

fun main(args: Array<String>) {
    val parserAndAll = getArgParser()
    parserAndAll.parser.parse(args)

    val ksheetFile = fopen(parserAndAll.ksheetOpt.value, "r")
    if (ksheetFile == null) {
        perror("Unable to open ${parserAndAll.ksheetOpt.value}")
        return
    }

    try {
        val content: List<String>? = when {
            parserAndAll.versionOpt.value == true -> getVersion()
            parserAndAll.listOpt.value == true -> findEntries(ksheetFile)
            parserAndAll.subjectArg.value != null && parserAndAll.sectionArg.value != null -> findEntry(ksheetFile, parserAndAll.subjectArg.value!!, parserAndAll.sectionArg.value!!)
            else -> null
        }

        if (content == null) {
            getArgParser().parser.parse(arrayOf("help"))
        } else {
            printContent(content)
        }
    } finally {
        fclose(ksheetFile)
    }
}

fun findEntries(ksheetFile: CPointer<FILE>): List<String> {
    val content = mutableListOf<String>()

    memScoped {
        var subject: String? = null

        val bufferLength = 64 * 1024
        val buffer = allocArray<ByteVar>(bufferLength)

        while(true) {
            val line = fgets(buffer, bufferLength, ksheetFile)?.toKString()?.trim() ?: break

            if (line.startsWith("## ")) {
                subject = line.replaceFirst("## ", "")
            } else if (subject != null && line.startsWith("### ")) {
                content.add("$subject ${line.replaceFirst("### ", "")}")
            }
        }
    }

    return content
}

fun findEntry(ksheetFile: CPointer<FILE>, subject: String, section: String): List<String> {
    val content = mutableListOf<String>()

    memScoped {
        var subjectFound = false
        var sectionFound = false
        var contentFound = false

        val bufferLength = 64 * 1024
        val buffer = allocArray<ByteVar>(bufferLength)

        while(true) {
            val line = fgets(buffer, bufferLength, ksheetFile)?.toKString()?.trim() ?: break

            if (contentFound) {
                if (line == "````") break
                else content.add(line)
            } else if (sectionFound) {
                if (line.startsWith("````")) contentFound = true
            } else if (subjectFound) {
                if (line == "### $section") sectionFound = true
            } else {
                if (line == "## $subject") subjectFound = true
            }
        }
    }

    return content
}

fun getArgParser(): ArgParserAndArgsAndOpts {
    val parser = ArgParser("ksheet")
    return ArgParserAndArgsAndOpts(
        parser,
        parser.option(ArgType.String, shortName = "f", fullName = "file", description = "Cheat sheet Mardown file").required(),
        parser.option(ArgType.Boolean, shortName = "l", fullName = "list", description = "Show all possible entries"),
        parser.option(ArgType.Boolean, shortName = "v", fullName = "version", description = "Display version"),
        parser.argument(ArgType.String, fullName = "subject", description = "Subject (heading 2) to return").optional(),
        parser.argument(ArgType.String, fullName = "section", description = "Section (heading 3) to return").optional()
    )
}

fun getVersion(): List<String> {
    return listOf(
        "ksheet version $ksheetVersion, revision $ksheetRevision",
        "See: https://github.com/ninckblokje/ksheet"
    )
}

fun printContent(content: List<String>) {
    content.forEach { s -> println(s) }
}