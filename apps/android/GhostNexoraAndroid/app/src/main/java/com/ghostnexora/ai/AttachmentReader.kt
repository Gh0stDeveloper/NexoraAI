package com.ghostnexora.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

object AttachmentReader {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_TEXT_CHARS = 80_000

    fun read(context: Context, uri: Uri, requestedMimeType: String? = null): PendingAttachment {
        val metadata = queryMetadata(context, uri)
        val mimeType = requestedMimeType
            ?: context.contentResolver.getType(uri)
            ?: mimeFromName(metadata.name)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val result = input.readBytes(MAX_BYTES + 1)
            require(result.size <= MAX_BYTES) { "El archivo supera el límite de 8 MB." }
            result
        } ?: error("No se pudo abrir el archivo seleccionado.")

        return when {
            mimeType.startsWith("image/") -> PendingAttachment(
                name = metadata.name,
                mimeType = mimeType,
                imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                sizeBytes = bytes.size.toLong(),
            )

            metadata.name.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf" -> {
                PDFBoxResourceLoader.init(context.applicationContext)
                val text = PDDocument.load(bytes).use { document -> PDFTextStripper().getText(document) }
                textAttachment(metadata.name, mimeType, text, bytes.size.toLong())
            }

            metadata.name.endsWith(".docx", ignoreCase = true) ||
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                val text = extractDocx(bytes)
                textAttachment(metadata.name, mimeType, text, bytes.size.toLong())
            }

            isTextLike(metadata.name, mimeType) -> {
                textAttachment(metadata.name, mimeType, bytes.toString(Charsets.UTF_8), bytes.size.toLong())
            }

            else -> error("Formato no compatible todavía. Usa TXT, código fuente, PDF, DOCX o una imagen.")
        }
    }

    private fun textAttachment(name: String, mimeType: String, text: String, size: Long): PendingAttachment {
        val cleaned = text.replace("\u0000", "").trim().take(MAX_TEXT_CHARS)
        require(cleaned.isNotBlank()) { "No se encontró texto legible en el archivo." }
        return PendingAttachment(
            name = name,
            mimeType = mimeType,
            textContent = cleaned,
            sizeBytes = size,
        )
    }

    private fun extractDocx(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return xml
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                }
                entry = zip.nextEntry
            }
        }
        return ""
    }

    private fun isTextLike(name: String, mimeType: String): Boolean {
        if (mimeType.startsWith("text/")) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in setOf(
            "txt", "md", "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "csv", "log",
            "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "css", "scss", "sql",
            "sh", "bash", "zsh", "c", "h", "cpp", "hpp", "cs", "go", "rs", "php", "rb", "swift",
            "gradle", "properties", "env", "dockerfile",
        )
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "text/plain"
    }

    private fun queryMetadata(context: Context, uri: Uri): FileMetadata {
        var name = "archivo"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return FileMetadata(name, size)
    }

    private data class FileMetadata(val name: String, val size: Long)
}

private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
    val buffer = ByteArray(8 * 1024)
    val output = java.io.ByteArrayOutputStream()
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        if (output.size() > limit) break
    }
    return output.toByteArray()
}
