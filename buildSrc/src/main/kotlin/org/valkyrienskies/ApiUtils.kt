package org.valkyrienskies

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.kotlin.dsl.MutablePropertyDelegate
import org.gradle.kotlin.dsl.extra
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty



// region Extra Property delegate
fun Project.extraProperty(name: String) = PropertyDelegateProvider { _: Any?, property ->
    if (property.returnType.isMarkedNullable)
        NullableExtraPropertyDelegate(this.extra, name)
    else
        NonNullExtraPropertyDelegate(this.extra, name)
}

private class NonNullExtraPropertyDelegate(
    private val extra: ExtraPropertiesExtension,
    private val name: String
) : MutablePropertyDelegate {

    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        if (!extra.has(name)) cannotGetExtraProperty("does not exist")
        else uncheckedCast(extra.get(name) ?: cannotGetExtraProperty("is null"))

    override fun <T> setValue(receiver: Any?, property: KProperty<*>, value: T) =
        extra.set(property.name, value)

    private
    fun cannotGetExtraProperty(reason: String): Nothing =
        throw InvalidUserCodeException("Cannot get non-null extra property '$name' as it $reason")
}
private class NullableExtraPropertyDelegate(
    private val extra: ExtraPropertiesExtension,
    private val name: String
) : MutablePropertyDelegate {

    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        uncheckedCast(if (extra.has(name)) extra.get(name) else null)

    override fun <T> setValue(receiver: Any?, property: KProperty<*>, value: T) =
        extra.set(property.name, value)
}

@Suppress("unchecked_cast", "nothing_to_inline")
inline fun <T> uncheckedCast(obj: Any?): T =
    obj as T
// endregion

