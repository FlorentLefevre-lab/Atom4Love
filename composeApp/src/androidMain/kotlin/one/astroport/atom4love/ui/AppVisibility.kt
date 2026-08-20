package one.astroport.atom4love.ui

/**
 * L'application est-elle sous les yeux ?
 *
 * ⚠ **Un fait de processus, pas de composition.** La leçon du 19/08 vaut ici
 * aussi : ce qui doit décider quand l'écran ne vit plus ne peut pas vivre dans
 * le recomposeur, qui est en pause à ce moment précis. Et ce n'est pas non plus
 * un fait de `ChatHost`, dont le cycle de vie s'arrête avec l'activité : la
 * balise, elle, survit à tout et c'est elle qui réveille.
 *
 * Écrit par `MainActivity.onStart`/`onStop`, lu par
 * [one.astroport.atom4love.proximity.ProximityService].
 */
object AppVisibility {
    @Volatile
    var onScreen: Boolean = false
}
