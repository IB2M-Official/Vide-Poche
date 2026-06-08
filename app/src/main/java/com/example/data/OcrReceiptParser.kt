package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

data class ParsedReceiptInfo(
    val title: String,
    val warrantyMonths: Int,
    val barcode: String?,
    val category: String,
    val notes: String?
)

object OcrReceiptParser {

    private val STORES = listOf(
        "Fnac", "Darty", "Boulanger", "Amazon", "Apple", "Carrefour", "Leclerc", "Auchan", 
        "Lidl", "Ikea", "Decathlon", "Leroy Merlin", "Castorama", "Conforama", "But", "Cdiscount"
    )

    private val CATEGORIES = mapOf(
        "Électronique" to listOf("tele", "tv", "oled", "macbook", "pc", "laptop", "ordinateur", "sony", "casque", "audio", "smartphone", "iphone", "samsung", "console", "switch", "ps5", "xbox"),
        "Électroménager" to listOf("machine", "aspirateur", "four", "lave", "cafe", "espresso", "frigo", "refrigerateur", "micro", "onde", "cuisiniere"),
        "Mode" to listOf("pantalon", "jean", "shirt", "veste", "chaussures", "zara", "h&m", "nike", "adidas", "decathlon", "pull", "robe"),
        "Alimentation" to listOf("carrefour", "leclerc", "lidl", "auchan", "supermarche", "monoprix", "epicerie", "courses", "pain", "lait", "viande"),
        "Divers" to emptyList()
    )

    /**
     * Effectue la reconnaissance de texte sur l'image et en déduit les informations du ticket.
     */
    suspend fun parseReceiptImage(context: Context, imagePath: String): ParsedReceiptInfo = suspendCancellableCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                continuation.resume(defaultInfo())
                return@suspendCancellableCoroutine
            }

            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    Log.d("OcrReceiptParser", "OCR Text extracted successfully:\n$rawText")
                    val parsed = parseRawText(rawText)
                    continuation.resume(parsed)
                }
                .addOnFailureListener { e ->
                    Log.e("OcrReceiptParser", "OCR parsing failed: ${e.message}", e)
                    continuation.resume(defaultInfo())
                }
        } catch (e: Exception) {
            Log.e("OcrReceiptParser", "OCR unexpected error: ${e.message}", e)
            continuation.resume(defaultInfo())
        }
    }

    private fun defaultInfo() = ParsedReceiptInfo(
        title = "",
        warrantyMonths = 24, // 2 ans par défaut
        barcode = null,
        category = "Divers",
        notes = null
    )

    /**
     * Analyse le texte brut extrait d'un ticket de caisse.
     */
    fun parseRawText(text: String): ParsedReceiptInfo {
        if (text.isBlank()) return defaultInfo()

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        // 1. Recherche du magasin
        var brand = ""
        for (store in STORES) {
            if (text.contains(store, ignoreCase = true)) {
                brand = store
                break
            }
        }

        // Si aucun magasin de la liste n'est trouvé, prendre la 1ère ou 2ème ligne non-numérique comme magasin probable
        if (brand.isEmpty() && lines.isNotEmpty()) {
            val candidate = lines.firstOrNull { l -> 
                l.length in 3..25 && !l.any { it.isDigit() } 
            }
            if (candidate != null) {
                brand = candidate
            } else {
                brand = lines[0].take(18)
            }
        }

        // 2. Recherche du nom du produit / article
        // Souvent c'est une ligne qui mentionne un nom de produit courant ou le premier gros mot sur le haut
        var article = ""
        val ignoredWords = listOf("ticket", "tva", "caisse", "carte", "tel", "fax", "total", "ht", "ttc", "paiement", "banque", "somme", "euro", "facture", "remise")
        for (line in lines) {
            if (line.length in 5..35 && 
                !line.contains("Total", true) && 
                !line.contains("EUR", true) && 
                !line.contains("€", true) &&
                !ignoredWords.any { line.contains(it, true) }) {
                article = line
                break
            }
        }

        val finalTitle = when {
            article.isNotEmpty() && brand.isNotEmpty() -> "$brand - $article"
            brand.isNotEmpty() -> brand
            article.isNotEmpty() -> article
            else -> "Nouveau Ticket scanné"
        }

        // 3. Recherche de la durée de garantie (ans / mois)
        var warrantyMonths = 24 // 2 ans par défaut (loi de conformité européenne standard)
        
        // Patterns de recherche de garantie
        val textLower = text.lowercase()
        // Regex pour capturer un nombre suivi de "an" ou "ans" ou "mois" ou "warranty" ou "garantie"
        val warrantyRegex = """(\d+)\s*(ans?|mois|year|years|months|garantie|warranty)""".toRegex()
        val matches = warrantyRegex.findAll(textLower)
        for (match in matches) {
            val numValue = match.groupValues[1].toIntOrNull() ?: continue
            val unit = match.groupValues[2]
            
            if (unit.startsWith("an") || unit.startsWith("year")) {
                warrantyMonths = numValue * 12
                break
            } else if (unit.startsWith("moi") || unit.startsWith("month")) {
                warrantyMonths = numValue
                break
            }
        }

        // 4. Reconstitution du code barre (recherche EAN-13 ou EAN-8)
        // Les codes-barres standards EAN-13 ont exactement 13 chiffres. Les UPC ont 12 chiffres. EAN-8 ont 8 chiffres.
        val digits13Regex = """\b\d{13}\b""".toRegex()
        val digits12Regex = """\b\d{12}\b""".toRegex()
        val digits8Regex = """\b\d{8}\b""".toRegex()

        val barcode = digits13Regex.find(text)?.value 
            ?: digits12Regex.find(text)?.value 
            ?: digits8Regex.find(text)?.value

        // 5. Recherche de catégorie d'après le texte
        var category = "Divers"
        for ((catName, keywords) in CATEGORIES) {
            if (keywords.any { textLower.contains(it) }) {
                category = catName
                break
            }
        }

        val notes = if (barcode != null) "Code-barres détecté d'après OCR: $barcode" else "Scanné automatiquement via OCR."

        return ParsedReceiptInfo(
            title = finalTitle,
            warrantyMonths = warrantyMonths,
            barcode = barcode,
            category = category,
            notes = notes
        )
    }
}
