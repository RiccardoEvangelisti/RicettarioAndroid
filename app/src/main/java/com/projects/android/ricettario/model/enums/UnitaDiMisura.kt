package com.projects.android.ricettario.model.enums

import com.google.gson.annotations.SerializedName

enum class UnitaDiMisura(value: String) { @SerializedName("0")
CHILOGRAMMO("Kg"),

	@SerializedName("1")
	GRAMMO("g"),

	@SerializedName("2")
	QUANTOBASTA("q.b");

	private val valueString: String = value

	override fun toString(): String {
		return valueString
	}
}