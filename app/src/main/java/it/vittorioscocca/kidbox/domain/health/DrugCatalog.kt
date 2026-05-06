package it.vittorioscocca.kidbox.domain.health

import androidx.compose.ui.graphics.Color

/** Allineato a [DrugCatalog.swift] iOS — stessi 15 farmaci comuni, stessa logica search/color/icon. */
data class DrugCatalogEntry(
    val id: String,
    val name: String,
    val activeIngredient: String,
    val category: String,
    val form: String? = null,
    val iconName: String,
    val iconColor: Color,
) {
    constructor(
        name: String,
        activeIngredient: String,
        category: String,
        form: String? = null,
        iconName: String,
        iconColor: Color,
    ) : this(
        id = "${name.lowercase()}|${activeIngredient.lowercase()}|${category.lowercase()}|${(form ?: "").lowercase()}",
        name = name,
        activeIngredient = activeIngredient,
        category = category,
        form = form,
        iconName = iconName,
        iconColor = iconColor,
    )
}

object DrugCatalog {
    private val purpleAntibio = Color(0xFF9966D9)
    private val tint = Color(0xFF5599D9)

    val common: List<DrugCatalogEntry> = listOf(
        DrugCatalogEntry("Tachipirina", "Paracetamolo", "Antipiretico", iconName = "thermometer", iconColor = Color.Red),
        DrugCatalogEntry("Nurofen", "Ibuprofene", "Antidolorifico", iconName = "healing", iconColor = Color(0xFFFF9800)),
        DrugCatalogEntry("Augmentin", "Amoxicillina + Ac. clavulanico", "Antibiotico", iconName = "medication", iconColor = purpleAntibio),
        DrugCatalogEntry("Zimox", "Amoxicillina", "Antibiotico", iconName = "medication", iconColor = purpleAntibio),
        DrugCatalogEntry("Amoxil", "Amoxicillin", "Antibiotico", iconName = "medication", iconColor = purpleAntibio),
        DrugCatalogEntry("Zithromax", "Azithromycin", "Antibiotico", iconName = "medication", iconColor = purpleAntibio),
        DrugCatalogEntry("Moment", "Ibuprofene", "Antidolorifico", iconName = "healing", iconColor = Color(0xFFFF9800)),
        DrugCatalogEntry("Zerinol", "Paracetamolo + Clorfenamina", "Antipiretico", iconName = "thermometer", iconColor = Color.Red),
        DrugCatalogEntry("Claritromicina", "Claritromicina", "Antibiotico", iconName = "medication", iconColor = purpleAntibio),
        DrugCatalogEntry("Fluimucil", "N-acetilcisteina", "Mucolitico", iconName = "air", iconColor = Color(0xFF009688)),
        DrugCatalogEntry("Rinowash", "Soluzione salina", "Nasale", iconName = "water_drop", iconColor = Color.Blue),
        DrugCatalogEntry("Aerius", "Desloratadina", "Antistaminico", iconName = "spa", iconColor = Color(0xFF4CAF50)),
        DrugCatalogEntry("Zyrtec", "Cetirizina", "Antistaminico", iconName = "spa", iconColor = Color(0xFF4CAF50)),
        DrugCatalogEntry("Bentelan", "Betametasone", "Cortisonico", iconName = "vaccines", iconColor = Color(0xFFE91E63)),
        DrugCatalogEntry("Deltacortene", "Prednisone", "Cortisonico", iconName = "vaccines", iconColor = Color(0xFFE91E63)),
    )

    fun search(query: String, custom: List<DrugCatalogEntry> = emptyList()): List<DrugCatalogEntry> {
        val all = deduplicated(common + custom)
        if (query.isBlank()) return all
        val q = query.lowercase()
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.activeIngredient.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                (it.form?.lowercase()?.contains(q) == true)
        }
    }

    fun colorForCategory(category: String): Color = when (category) {
        "Antipiretico" -> Color.Red
        "Antidolorifico" -> Color(0xFFFF9800)
        "Antibiotico" -> purpleAntibio
        "Mucolitico" -> Color(0xFF009688)
        "Nasale" -> Color.Blue
        "Antistaminico" -> Color(0xFF4CAF50)
        "Cortisonico" -> Color(0xFFE91E63)
        else -> tint
    }

    fun iconNameFor(category: String, form: String?): String {
        form?.let {
            when (it) {
                "Liquido" -> return "opacity"
                "Compressa" -> return "medication"
                "Supposta" -> return "circle"
                "Gocce" -> return "opacity"
                "Sciroppo" -> return "restaurant"
                "Polvere" -> return "grain"
            }
        }
        return when (category) {
            "Antipiretico" -> "thermometer"
            "Antidolorifico" -> "healing"
            "Antibiotico" -> "medication"
            "Mucolitico" -> "air"
            "Nasale" -> "water_drop"
            "Antistaminico" -> "spa"
            "Cortisonico" -> "vaccines"
            else -> "vaccines"
        }
    }

    private fun deduplicated(entries: List<DrugCatalogEntry>): List<DrugCatalogEntry> {
        val seen = mutableSetOf<String>()
        val out = ArrayList<DrugCatalogEntry>()
        for (item in entries) {
            val key = "${item.name.lowercase()}|${item.activeIngredient.lowercase()}"
            if (seen.add(key)) out.add(item)
        }
        return out
    }
}
