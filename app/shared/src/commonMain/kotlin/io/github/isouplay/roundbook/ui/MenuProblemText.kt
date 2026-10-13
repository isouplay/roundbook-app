package io.github.isouplay.roundbook.ui

import androidx.compose.runtime.Composable
import io.github.isouplay.roundbook.core.menu.MenuField
import io.github.isouplay.roundbook.core.menu.MenuPlace
import io.github.isouplay.roundbook.core.menu.MenuProblem
import io.github.isouplay.roundbook.core.menu.MenuProblemKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.*

/** The problem of a menu file as German message, e.g. „Getränk „Pils“: 900 Vol.-% ist nicht möglich (0 bis 96 Vol.-%)“. */
@Composable
fun menuProblemText(problem: MenuProblem): String {
    val what = kindText(problem)
    return if (problem.place == MenuPlace.File) {
        what
    } else {
        stringResource(Res.string.problem_at, placeText(problem.place), what)
    }
}

@Composable
private fun placeText(place: MenuPlace): String = when (place) {
    MenuPlace.File -> stringResource(Res.string.problem_place_file)
    MenuPlace.Menu -> stringResource(Res.string.problem_place_menu)
    is MenuPlace.Category -> stringResource(Res.string.problem_place_category, place.name)
    is MenuPlace.Drink -> stringResource(Res.string.problem_place_drink, place.name)
    is MenuPlace.DrinkNumber -> stringResource(Res.string.problem_place_drink_number, place.number.toString())
    is MenuPlace.Size -> {
        val parent = placeText(place.parent)
        val label = place.label
        if (label != null) {
            stringResource(Res.string.problem_place_size_label, parent, label)
        } else {
            stringResource(Res.string.problem_place_size_number, parent, place.number.toString())
        }
    }
    is MenuPlace.MixTier -> stringResource(Res.string.problem_place_mix_tier, place.id)
    is MenuPlace.Mix -> stringResource(Res.string.problem_place_mix, placeText(place.tier), place.name)
}

@Composable
private fun fieldText(field: MenuField?): String = stringResource(
    when (field) {
        null -> Res.string.field_value
        MenuField.BAR_NAME -> Res.string.field_bar_name
        MenuField.CURRENCY -> Res.string.field_currency
        MenuField.VERSION_LABEL -> Res.string.field_version_label
        MenuField.VALID_FROM -> Res.string.field_valid_from
        MenuField.CATEGORIES -> Res.string.field_categories
        MenuField.DRINKS -> Res.string.field_drinks
        MenuField.MIX_TIERS -> Res.string.field_mix_tiers
        MenuField.MIXES -> Res.string.field_mixes
        MenuField.SIZES -> Res.string.field_sizes
        MenuField.NAME -> Res.string.field_name
        MenuField.ICON -> Res.string.field_icon
        MenuField.NOTE -> Res.string.field_note
        MenuField.ABV -> Res.string.field_abv
        MenuField.SIZE_LABEL -> Res.string.field_size_label
        MenuField.PRICE -> Res.string.field_price
        MenuField.VOLUME -> Res.string.field_volume
        MenuField.SPIRIT -> Res.string.field_spirit
        MenuField.MIXER -> Res.string.field_mixer
        MenuField.MATCH -> Res.string.field_match
        MenuField.CATEGORY -> Res.string.field_category
        MenuField.ALLERGENS -> Res.string.field_allergens
        MenuField.ADDITIVES -> Res.string.field_additives
    },
)

private fun unitOf(field: MenuField?): StringResource? = when (field) {
    MenuField.ABV -> Res.string.unit_abv
    MenuField.PRICE -> Res.string.unit_price
    MenuField.VOLUME -> Res.string.unit_volume
    MenuField.SPIRIT -> Res.string.unit_spirit
    else -> null
}

@Composable
private fun kindText(p: MenuProblem): String {
    val text = p.text ?: ""
    val number = p.number?.let(::formatDecimal) ?: ""
    val limit = p.limit?.let(::formatDecimal) ?: ""
    return when (p.kind) {
        MenuProblemKind.FILE_TOO_LARGE ->
            stringResource(Res.string.problem_file_too_large, ((p.limit ?: 0.0) / (1024 * 1024)).let(::formatDecimal))
        MenuProblemKind.NOT_JSON -> stringResource(Res.string.problem_not_json)
        MenuProblemKind.NOT_RECOGNIZED -> stringResource(Res.string.problem_not_recognized)
        MenuProblemKind.PRICE_HISTORY_NOT_SUPPORTED -> stringResource(Res.string.problem_price_history)
        MenuProblemKind.NESTED_FORMAT_NOT_SUPPORTED -> stringResource(Res.string.problem_nested_format)
        MenuProblemKind.FORMAT_VERSION_TOO_NEW -> stringResource(Res.string.problem_format_version, number)
        MenuProblemKind.INVALID_DATE ->
            if (p.text != null) stringResource(Res.string.problem_invalid_date, text)
            else stringResource(Res.string.problem_invalid_date_plain)
        MenuProblemKind.NOT_AN_OBJECT ->
            if (p.field != null) stringResource(Res.string.problem_not_an_object, fieldText(p.field))
            else stringResource(Res.string.problem_not_an_object_plain)
        MenuProblemKind.NOT_A_LIST -> stringResource(Res.string.problem_not_a_list, fieldText(p.field))
        MenuProblemKind.NOT_TEXT -> stringResource(Res.string.problem_not_text, fieldText(p.field))
        MenuProblemKind.TEXT_TOO_LONG -> stringResource(Res.string.problem_text_too_long, fieldText(p.field), limit)
        MenuProblemKind.NOT_A_NUMBER ->
            if (p.text != null) stringResource(Res.string.problem_not_a_number_text, fieldText(p.field), text)
            else stringResource(Res.string.problem_not_a_number, fieldText(p.field))
        MenuProblemKind.NUMBER_OUT_OF_RANGE -> {
            val unit = unitOf(p.field)?.let { stringResource(it) } ?: ""
            if (p.field == MenuField.ABV) {
                // „900 Vol.-%“ already says what is meant.
                stringResource(Res.string.problem_abv_out_of_range, number, unit, limit)
            } else {
                stringResource(Res.string.problem_number_out_of_range, fieldText(p.field), number, unit, limit)
            }
        }
        MenuProblemKind.NOT_A_BOOLEAN -> stringResource(Res.string.problem_not_a_boolean, fieldText(p.field))
        MenuProblemKind.INVALID_ID -> stringResource(Res.string.problem_invalid_id, text)
        MenuProblemKind.TOO_MANY ->
            stringResource(Res.string.problem_too_many, (p.count ?: 0).toString(), fieldText(p.field), limit)
        MenuProblemKind.PRICE_MISSING -> stringResource(Res.string.problem_price_missing)
        MenuProblemKind.PRICE_TOO_PRECISE -> stringResource(Res.string.problem_price_too_precise, number)
        MenuProblemKind.SPIRIT_EXCEEDS_VOLUME -> stringResource(
            Res.string.problem_spirit_exceeds_volume,
            number,
            p.secondNumber?.let(::formatDecimal) ?: "",
        )
        MenuProblemKind.UNKNOWN_ALLERGEN -> stringResource(Res.string.problem_unknown_allergen, text)
        MenuProblemKind.DUPLICATE_ALLERGEN -> stringResource(Res.string.problem_duplicate_allergen, text)
        MenuProblemKind.UNKNOWN_ADDITIVE -> stringResource(Res.string.problem_unknown_additive, text)
        MenuProblemKind.DUPLICATE_ADDITIVE -> stringResource(Res.string.problem_duplicate_additive, text)
        MenuProblemKind.UNKNOWN_MIX_TIER -> stringResource(Res.string.problem_unknown_mix_tier, text)
        MenuProblemKind.DUPLICATE_CATEGORY_ID -> stringResource(Res.string.problem_duplicate_category_id, text)
        MenuProblemKind.DRINK_WITHOUT_NAME -> stringResource(Res.string.problem_drink_without_name)
        MenuProblemKind.UNKNOWN_CATEGORY -> stringResource(Res.string.problem_unknown_category, text)
        MenuProblemKind.DUPLICATE_DRINK_ID -> stringResource(Res.string.problem_duplicate_drink_id, text)
        MenuProblemKind.DUPLICATE_SIZE_LABEL -> stringResource(Res.string.problem_duplicate_size_label)
        MenuProblemKind.DRINK_WITHOUT_PRICE -> stringResource(Res.string.problem_drink_without_price)
        MenuProblemKind.MIX_WITHOUT_NAME -> stringResource(Res.string.problem_mix_without_name)
    }
}
