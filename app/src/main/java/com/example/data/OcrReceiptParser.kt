package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Calendar
import java.util.Date
import kotlin.coroutines.resume

data class ParsedReceiptInfo(
    val title: String,
    val warrantyMonths: Int,
    val isWarrantyDetected: Boolean,
    val barcode: String?,
    val category: String,
    val purchaseDate: Long?,
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
        isWarrantyDetected = false,
        barcode = null,
        category = "Divers",
        purchaseDate = null,
        notes = null
    )

    /**
     * Tente d'extraire la date d'achat (format dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy ou versions 2 chiffres année)
     */
    private fun extractPurchaseDate(text: String): Long? {
        val dateRegex = """\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""".toRegex()
        val match = dateRegex.find(text) ?: return null
        return try {
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt() - 1 // Calendar mois commence à 0
            var year = match.groupValues[3].toInt()
            if (year < 100) {
                year += 2000 // ex: 26 -> 2026
            }

            if (day in 1..31 && month in 0..11 && year in 2000..2050) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.HOUR_OF_DAY, 12)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

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

        // 3. Recherche de la date d'achat d'après OCR
        val purchaseDate = extractPurchaseDate(text)

        // 4. Recherche de la durée de garantie (ans / mois)
        var warrantyMonths = 24 // 2 ans par défaut
        var isWarrantyDetected = false
        
        // Patterns de recherche de garantie
        val textLower = text.lowercase()
        val warrantyRegex = """(\d+)\s*(ans?|mois|year|years|months|garantie|warranty)""".toRegex()
        val matches = warrantyRegex.findAll(textLower)
        for (match in matches) {
            val numValue = match.groupValues[1].toIntOrNull() ?: continue
            val unit = match.groupValues[2]
            
            if (unit.startsWith("an") || unit.startsWith("year")) {
                warrantyMonths = numValue * 12
                isWarrantyDetected = true
                break
            } else if (unit.startsWith("moi") || unit.startsWith("month")) {
                warrantyMonths = numValue
                isWarrantyDetected = true
                break
            }
        }

        // Si le mot "garantie" apparaît mais qu'aucune durée n'est détectée directement après, on suppose tout de même la conformité
        if (!isWarrantyDetected) {
            if (textLower.contains("garantie constructeur") || textLower.contains("garantie de conformite") || textLower.contains("garantie 2 ans")) {
                isWarrantyDetected = true
            }
        }

        // 5. Reconstitution du code barre (recherche EAN-13 ou EAN-8)
        val digits13Regex = """\b\d{13}\b""".toRegex()
        val digits12Regex = """\b\d{12}\b""".toRegex()
        val digits8Regex = """\b\d{8}\b""".toRegex()

        val barcode = digits13Regex.find(text)?.value 
            ?: digits12Regex.find(text)?.value 
            ?: digits8Regex.find(text)?.value

        // 6. Recherche de catégorie d'après le texte
        var category = "Divers"
        for ((catName, keywords) in CATEGORIES) {
            if (keywords.any { textLower.contains(it) }) {
                category = catName
                break
            }
        }

        val notes = StringBuilder().apply {
            if (isWarrantyDetected) {
                append("Garantie détectée via OCR : ${warrantyMonths / 12} an(s).")
            } else {
                append("Garantie de 2 ans supposée d'après la réglementation légale en France.")
            }
            if (barcode != null) {
                append("\nCode-barres détecté d'après OCR: $barcode")
            }
        }.toString()

        return ParsedReceiptInfo(
            title = finalTitle,
            warrantyMonths = warrantyMonths,
            isWarrantyDetected = isWarrantyDetected,
            barcode = barcode,
            category = category,
            purchaseDate = purchaseDate,
            notes = notes
        )
    }
}
