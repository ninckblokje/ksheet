package ninckblokje.ksheet

import kotlinx.cinterop.*
import kotlinx.cli.*
import ninckblokje.ksheet.BuildKonfig.ksheetRevision
import ninckblokje.ksheet.BuildKonfig.ksheetVersion
import platform.posix.*

class ArgParserAndArgsAndOpts (
    val parser: ArgParser,
    val ksheetOpt: SingleOption<String, DefaultRequiredType.Default>,
    val listOpt: SingleOption<Boolean, DefaultRequiredType.Default>,
    val quietOpt: SingleOption<Boolean, DefaultRequiredType.Default>,
    val versionOpt: SingleOption<Boolean, DefaultRequiredType.Default>,
    val subjectArg: SingleNullableArgument<String>,
    val sectionArg: SingleNullableArgument<String>
)

fun main(args: Array<String>) {
    val parserAndAll = getArgParser()
    parserAndAll.parser.parse(args)

    val ksheetFile = openKSheetFile(parserAndAll.ksheetOpt.value)

    try {
        val content: List<String>? = when {
            parserAndAll.versionOpt.value -> getVersion()
            ksheetFile == null -> {
                perror("Unable to open ${parserAndAll.ksheetOpt.value}")
                return
            }
            parserAndAll.listOpt.value -> findEntries(ksheetFile)
            parserAndAll.subjectArg.value != null && parserAndAll.sectionArg.value != null -> findEntry(ksheetFile, parserAndAll.subjectArg.value!!, parserAndAll.sectionArg.value!!)
            else -> null
        }

        if (content == null) {
            getArgParser().parser.parse(arrayOf("help"))
        } else if (!parserAndAll.quietOpt.value) {
            printContent(content)
        }
    } finally {
        if (ksheetFile != null) fclose(ksheetFile)
    }
}

fun openKSheetFile(ksheetFilePath: String): CPointer<FILE>? {
    return when(val ksheetFile = fopen(ksheetFilePath, "r")) {
        null -> fopen(ksheetFilePath, "a+")
        else -> ksheetFile
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
        parser.option(ArgType.String, shortName = "f", fullName = "file", description = "Cheat sheet Mardown file").default(
            getDefaultKSheetFile()),
        parser.option(ArgType.Boolean, shortName = "l", fullName = "list", description = "Show all possible entries").default(false),
        parser.option(ArgType.Boolean, shortName = "q", fullName = "quiet", description = "No output").default(false),
        parser.option(ArgType.Boolean, shortName = "v", fullName = "version", description = "Display version").default(false),
        parser.argument(ArgType.String, fullName = "subject", description = "Subject (heading 2) to return").optional(),
        parser.argument(ArgType.String, fullName = "section", description = "Section (heading 3) to return").optional()
    )
}

fun getDefaultKSheetFile():String {
    return when (Platform.osFamily) {
        OsFamily.WINDOWS -> "${getenv("USERPROFILE")?.toKString()}\\csheet.md"
        else -> "${getenv("HOME")?.toKString()}/csheet.md"
    }
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