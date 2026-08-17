package one.astroport.atom4love.geo

import android.util.Log
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Autocomplétion des communes via l'API Adresse de data.gouv.fr (Base Adresse
 * Nationale) : `type=municipality` ne renvoie que villes et villages, avec le
 * centroïde en coordonnées — exactement ce qu'attend le SALT de la clé LOVE.
 * Couverture France uniquement ; pour un lieu étranger, le géocodeur Android
 * de [PlaceResolver] reste le repli (touche « Terminé » du clavier).
 */
object CommuneApi {

    private const val TAG = "CommuneApi"
    private const val ENDPOINT = "https://api-adresse.data.gouv.fr/search/"

    /** Une proposition de la liste : nom, contexte administratif, centroïde. */
    data class Commune(
        val name: String,
        val postcode: String,
        val context: String,      // « 83, Var, Provence-Alpes-Côte d'Azur »
        val lat: Double,
        val lon: Double,
    ) {
        /** Le libellé qui entre dans la fiche d'incarnation. */
        val placeName: String get() = "$name, France"
    }

    @Serializable
    private data class GeoResponse(val features: List<Feature> = emptyList())

    @Serializable
    private data class Feature(
        val properties: Properties = Properties(),
        val geometry: Geometry = Geometry(),
    )

    @Serializable
    private data class Properties(
        val name: String = "",
        val postcode: String = "",
        val context: String = "",
    )

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Les communes correspondant à la saisie — vide si hors ligne ou < 3 caractères. */
    suspend fun search(query: String, limit: Int = 6): List<Commune> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 3) return@withContext emptyList()
            val url = ENDPOINT +
                "?q=${URLEncoder.encode(q, "UTF-8")}" +
                "&type=municipality&autocomplete=1&limit=$limit"
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    json.decodeFromString<GeoResponse>(resp.body.string())
                        .features.mapNotNull { f ->
                            val coords = f.geometry.coordinates    // GeoJSON : [lon, lat]
                            if (coords.size < 2 || f.properties.name.isBlank()) return@mapNotNull null
                            Commune(
                                name = f.properties.name,
                                postcode = f.properties.postcode,
                                context = f.properties.context,
                                lat = coords[1],
                                lon = coords[0],
                            )
                        }
                }
            }.onFailure { Log.w(TAG, "recherche commune impossible pour « $q »", it) }
                .getOrDefault(emptyList())
        }
}
