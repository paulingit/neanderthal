package au.com.outware.neanderthal.data.repository

import android.content.Context
import android.content.SharedPreferences
import au.com.outware.neanderthal.data.model.Variant
import au.com.outware.neanderthal.util.CharSequenceDeserializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.lang.reflect.Modifier
import java.util.*

/**
 * @author timmutton
 */
class VariantSharedPreferencesRepository(val klass: Class<out Any>,
                                         val context: Context,
                                         baseVariants: Map<String, Any>,
                                         defaultVariant: String): VariantRepository {
    companion object {
        const val SHARED_PREFERENCES_FILE_NAME = "_neanderthal_preferences"
        const val VARIANT_LIST = "variant_list"
        const val DEFAULT_VARIANT_LIST = "default_variant_list"
        const val CURRENT_VARIANT = "current_variant"
        const val DEFAULT_VARIANT = "default_variant"
        const val VARIANT_DEFAULT = "_default"
        const val VARIANT_STRUCTURE = "variant_structure"
    }

    private val sharedPreferences: SharedPreferences
    private val editor: SharedPreferences.Editor
    private val gson: Gson = GsonBuilder().registerTypeAdapter(CharSequence::class.java, CharSequenceDeserializer()).create()

    init {
        val formattedPackageName = context.packageName.capitalize().replace(".", "_")
        sharedPreferences = context.getSharedPreferences("$formattedPackageName$SHARED_PREFERENCES_FILE_NAME", Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        val structure = klass.declaredFields
                .filter { field -> !Modifier.isPrivate(field.modifiers) && !Modifier.isTransient(field.modifiers) }
                .map { field -> field.name }
                .toHashSet()

        if(!sharedPreferences.getStringSet(VARIANT_STRUCTURE, structure).equals(structure)) {
            editor.clear()
        }

        storeDefaults(baseVariants)

        if (!sharedPreferences.contains(VARIANT_LIST)) {
            editor.putStringSet(VARIANT_LIST, baseVariants.keys)

            for (variant in baseVariants) {
                editor.putString(variant.key, gson.toJson(variant.value))
            }

            editor.putStringSet(VARIANT_STRUCTURE, structure)

            // Set the default/current variant to the one supplied, or the first if the supplied default doesnt exist
            val currentVariant = if (baseVariants.containsKey(defaultVariant)) defaultVariant else baseVariants.keys.first()
            editor.putString(CURRENT_VARIANT, currentVariant)
            editor.putString(DEFAULT_VARIANT, currentVariant)
            
            editor.apply()
        }
    }

    private fun storeDefaults(baseVariants: Map<String, Any>) {
        editor.putStringSet(DEFAULT_VARIANT_LIST,
                baseVariants.map { variant -> variant.key + VARIANT_DEFAULT }.toSet())

        baseVariants.forEach { variant ->
            editor.putString(variant.key + VARIANT_DEFAULT, gson.toJson(variant.value))
        }
    }

    override fun addVariant(variant: Variant) {
        val variantList = sharedPreferences.getStringSet(VARIANT_LIST, null)
        if(!variantList.add(variant.name)) {
            editor.putStringSet(VARIANT_LIST, variantList)
        }

        editor.putString(variant.name, gson.toJson(variant.configuration))
        editor.apply()
    }

    override fun removeVariant(name: String) {
        val variantList = sharedPreferences.getStringSet(VARIANT_LIST, null)
        variantList.remove(name)
        editor.putStringSet(VARIANT_LIST, variantList)

        editor.remove(name)
        editor.apply()
    }

    override fun getVariants(): List<Variant> {
        val variantNames = sharedPreferences.getStringSet(VARIANT_LIST, emptySet())
        val variants = ArrayList<Variant>(variantNames.size)

        for(name in variantNames) {
            variants.add(Variant(name, gson.fromJson(sharedPreferences.getString(name, null), klass)))
        }

        return variants
    }

    override fun getVariant(name: String): Variant? {
        if(sharedPreferences.contains(name)) {
            return Variant(name, gson.fromJson(sharedPreferences.getString(name, null), klass))
        } else {
            return null
        }
    }

    override fun setCurrentVariant(name: String) {
        editor.putString(CURRENT_VARIANT, name).apply()
    }

    override fun getCurrentVariant(): Variant? {
        if(!sharedPreferences.contains(CURRENT_VARIANT)) {
            return null
        }

        val name = sharedPreferences.getString(CURRENT_VARIANT, null);

        if(sharedPreferences.contains(name)) {
            return Variant(name, gson.fromJson(sharedPreferences.getString(name, null), klass))
        } else {
            return null
        }
    }

    override fun resetVariants(){
        getVariants().forEach { variant ->
            removeVariant(variant.name!!)
        }

        sharedPreferences.getStringSet(DEFAULT_VARIANT_LIST, emptySet()).map { name ->
            Variant(name.removeSuffix(VARIANT_DEFAULT), gson.fromJson(sharedPreferences.getString(name, null), klass))
        }.forEach { variant ->
            addVariant(variant)
        }

        setCurrentVariant(sharedPreferences.getString(DEFAULT_VARIANT, null))
    }
}